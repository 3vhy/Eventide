package dev.eventide.client.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.eventide.client.EventideClient;
import dev.eventide.client.module.Modules;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Asks Windows what's playing, for the Music HUD. Windows keeps the current song of every media app
 * (Spotify, browsers, Apple Music...) in its media controls; a small PowerShell script that ships
 * with the mod reads them once a second and prints a JSON line, which a background thread picks up.
 * The script only runs while the Music Player or Sky Lyrics is on, and quits on its own when the game closes.
 */
public final class MusicBridge {
	private static final String SCRIPT_RESOURCE = "/assets/eventide/music/now-playing.ps1";
	private static final long RESTART_DELAY_MS = 15_000;
	private static final int MAX_FAILURES = 4;
	private static final AtomicLong ART_VERSIONS = new AtomicLong();
	private static final PlaybackClock CLOCK = new PlaybackClock();

	private static volatile NowPlaying current = null;
	private static volatile String status = "Off";
	private static Process process = null;
	private static long nextStartAt = 0;
	private static int failures = 0;

	private MusicBridge() {
	}

	/** Windows only: the media controls it reads are a Windows feature. */
	public static boolean supported() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	public static NowPlaying current() {
		return current;
	}

	/** A short line for the settings page: running, nothing playing, or what went wrong. */
	public static String status() {
		if (!supported()) return "Needs Windows";
		NowPlaying song = current;
		if (song != null && song.art() == null && !song.artProblem().isEmpty()) return "No cover: " + song.artProblem();
		return status;
	}

	/** Seconds into {@code song} right now, smooth between Windows' reports (see {@link PlaybackClock}). */
	public static double position(NowPlaying song) {
		return CLOCK.seconds(song);
	}

	/**
	 * Starts or stops the script to match the Music Player and Sky Lyrics switches (either needs to know
	 * what's playing), and restarts it if it stopped.
	 */
	public static void tick() {
		boolean wanted = (Modules.MUSIC.enabled() || Modules.LYRICS.enabled()) && supported();
		if (!wanted) {
			if (process != null) stop();
			failures = 0;
			status = "Off";
			return;
		}
		if (process != null && process.isAlive()) return;
		long now = System.currentTimeMillis();
		if (process != null) {
			process = null;
			current = null;
			failures++;
			nextStartAt = now + RESTART_DELAY_MS;
			EventideClient.LOG.warn("Music HUD helper stopped ({}): {}", failures, status);
		}
		if (failures >= MAX_FAILURES || now < nextStartAt) return;
		start();
	}

	private static void start() {
		try {
			Path script = installScript();
			ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
					"-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", script.toString(),
					"-ParentId", Long.toString(ProcessHandle.current().pid()), "-CommandFile", commandFile().toString());
			builder.redirectError(ProcessBuilder.Redirect.DISCARD);
			Process started = builder.start();
			process = started;
			status = "Starting";
			Thread reader = new Thread(() -> read(started), "Eventide music");
			reader.setDaemon(true);
			reader.start();
		} catch (Exception e) {
			failures++;
			nextStartAt = System.currentTimeMillis() + RESTART_DELAY_MS;
			status = "Couldn't start PowerShell";
			EventideClient.LOG.warn("Couldn't start the Music HUD helper", e);
		}
	}

	private static Path commandFile() {
		return dev.eventide.client.Migration.dataDir().resolve("music-command.txt");
	}

	/**
	 * Asks the app that's playing to play or pause ("toggle"), skip ("next") or go back ("previous"), through
	 * Windows' media controls; the helper picks it up within a fifth of a second. Play and pause show at once.
	 * Returns whether it could be asked (Windows only, with the helper running).
	 */
	public static boolean command(String what) {
		if (!supported() || process == null) return false;
		try {
			Files.writeString(commandFile(), what, StandardCharsets.UTF_8);
		} catch (IOException e) {
			EventideClient.LOG.warn("Couldn't send the music command {}", what, e);
			return false;
		}
		NowPlaying song = current;
		if (song != null && what.equals("toggle")) current = song.toggled();
		return true;
	}

	/** Copies the script next to the game, so PowerShell can run it, unless it's already there unchanged. */
	private static Path installScript() throws IOException {
		byte[] bytes;
		try (InputStream in = MusicBridge.class.getResourceAsStream(SCRIPT_RESOURCE)) {
			if (in == null) throw new IOException("The music script is missing from the mod");
			bytes = in.readAllBytes();
		}
		Path target = dev.eventide.client.Migration.dataDir().resolve("now-playing.ps1");
		Files.createDirectories(target.getParent());
		if (!Files.isRegularFile(target) || !Arrays.equals(Files.readAllBytes(target), bytes)) Files.write(target, bytes);
		return target;
	}

	private static void read(Process started) {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("{")) handle(line);
			}
		} catch (IOException | RuntimeException e) {
			status = "Stopped";
		}
	}

	/** Takes one line from the script. Package-private so the in-game test can feed it a sample. */
	static void handle(String line) {
		JsonObject json = JsonParser.parseString(line).getAsJsonObject();
		if (json.has("error")) {
			status = "Problem: " + text(json, "error");
			return;
		}
		if (json.has("none")) {
			current = null;
			CLOCK.clear();
			status = "Nothing playing";
			return;
		}
		NowPlaying previous = current;
		String key = text(json, "key");
		String app = text(json, "app");
		boolean sameSong = previous != null && previous.key().equals(key);
		byte[] art = sameSong ? previous.art() : null;
		long artVersion = sameSong ? previous.artVersion() : ART_VERSIONS.incrementAndGet();
		if (json.has("art")) {
			String encoded = text(json, "art");
			art = encoded.isEmpty() ? null : Base64.getDecoder().decode(encoded);
			artVersion = ART_VERSIONS.incrementAndGet();
		}
		boolean sameApp = previous != null && previous.app().equals(app);
		byte[] icon = sameApp ? previous.icon() : null;
		long iconVersion = sameApp ? previous.iconVersion() : ART_VERSIONS.incrementAndGet();
		if (json.has("icon")) {
			String encoded = text(json, "icon");
			icon = encoded.isEmpty() ? null : Base64.getDecoder().decode(encoded);
			iconVersion = ART_VERSIONS.incrementAndGet();
		}
		String artProblem = json.has("artError") ? text(json, "artError") : sameSong ? previous.artProblem() : "";
		if (json.has("artError") && (previous == null || !artProblem.equals(previous.artProblem()))) {
			EventideClient.LOG.info("Music HUD: no cover for \"{}\" yet: {}", text(json, "title"), artProblem);
		}
		if (json.has("iconError") && !sameApp) {
			EventideClient.LOG.info("Music HUD: no icon for {}: {}", app, text(json, "iconError"));
		}
		current = new NowPlaying(key, app, text(json, "title"), text(json, "artist"), text(json, "album"),
				json.has("playing") && json.get("playing").getAsBoolean(), number(json, "position"),
				number(json, "duration"), System.nanoTime(), art, artVersion, icon, iconVersion, artProblem,
				json.has("volume") ? (int) Math.round(number(json, "volume")) : -1,
				json.has("muted") && json.get("muted").getAsBoolean());
		CLOCK.report(current);
		status = "Connected";
	}

	/** For the in-game test, which can't play real music: pretends a song is on. */
	public static void simulate(String line) {
		handle(line);
	}

	private static String text(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value == null || value.isJsonNull() ? "" : value.getAsString();
	}

	private static double number(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value == null || value.isJsonNull() ? 0 : value.getAsDouble();
	}

	public static void stop() {
		Process running = process;
		process = null;
		current = null;
		CLOCK.clear();
		if (running != null) {
			running.descendants().forEach(ProcessHandle::destroy);
			running.destroy();
		}
	}
}
