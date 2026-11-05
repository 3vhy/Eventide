package dev.eventide.client.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.eventide.client.Config;
import dev.eventide.client.Hooks;
import dev.eventide.client.EventideClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * The skin Skin Protect shows when it's set to a player's skin (looked up by name from Mojang) or to
 * a skin file in the game folder's eventide/skins. Downloads and file reads happen in the background;
 * the texture is made on the game's thread, and old 64x32 skins are converted like Minecraft does.
 */
public final class CustomSkins {
	public static final String SOURCE_DEFAULT = "Default skin";
	public static final String SOURCE_PLAYER = "A player's skin";
	public static final String SOURCE_FILE = "Skin file";
	public static final String SOURCE_BUILTIN = "Eventide skin";
	/** Eventide's own skins, drawn for it (assets/eventide/skins), by name. */
	public static final String[] BUILTIN = {"Grim Reaper", "Timber", "Void Walker", "Astronaut", "Phantom"};
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("eventide", "custom_skin");
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private record Loaded(String key, byte[] png, boolean slim) {
	}

	private static PlayerSkin skin = null;
	private static String skinKey = null;
	private static String requestedKey = null;
	private static volatile Loaded pending = null;
	private static volatile String status = "";

	private CustomSkins() {
	}

	/** The folder skin files go in (created when first needed). */
	public static Path folder() {
		return dev.eventide.client.Migration.dataDir().resolve("skins");
	}

	/** PNG files in the skins folder, by name. */
	public static List<String> files() {
		List<String> names = new ArrayList<>();
		Path folder = folder();
		if (!Files.isDirectory(folder)) return names;
		try (Stream<Path> list = Files.list(folder)) {
			list.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
					.map(path -> path.getFileName().toString())
					.sorted(String.CASE_INSENSITIVE_ORDER)
					.forEach(names::add);
		} catch (IOException e) {
			EventideClient.LOG.warn("Couldn't list skin files", e);
		}
		return names;
	}

	public static void openFolder() {
		try {
			Files.createDirectories(folder());
			Util.getPlatform().openPath(folder());
		} catch (IOException e) {
			status = "Couldn't open the skins folder";
		}
	}

	/** What Skin Protect wants right now, e.g. "player:notch" or "file:red.png:slim"; null for the default skin. */
	private static String wantedKey() {
		if (Modules.SKIN_PROTECT_SOURCE.is(SOURCE_PLAYER)) {
			String name = Modules.SKIN_PROTECT_PLAYER.get().trim();
			return PlayerList.isValidName(name) ? "player:" + name.toLowerCase(Locale.ROOT) : null;
		}
		if (Modules.SKIN_PROTECT_SOURCE.is(SOURCE_BUILTIN)) {
			return "builtin:" + Modules.SKIN_PROTECT_BUILTIN.get().toLowerCase(Locale.ROOT).replace(' ', '_');
		}
		if (Modules.SKIN_PROTECT_SOURCE.is(SOURCE_FILE)) {
			String file = Config.get().skinFile;
			return file.isBlank() ? null : "file:" + file + ":" + (Modules.SKIN_PROTECT_ARMS.is("Slim") ? "slim" : "wide");
		}
		return null;
	}

	/** The chosen skin when it's ready, otherwise null (Skin Protect then shows a default skin). */
	public static PlayerSkin current() {
		String wanted = wantedKey();
		return wanted != null && wanted.equals(skinKey) ? skin : null;
	}

	/** What happened to the chosen skin, for the settings page. */
	public static String status() {
		if (!Modules.SKIN_PROTECT.enabled()) return "Skin Protect is off";
		if (Modules.SKIN_PROTECT_SOURCE.is(SOURCE_DEFAULT)) return "Showing Minecraft's default skins";
		if (wantedKey() == null) {
			return Modules.SKIN_PROTECT_SOURCE.is(SOURCE_PLAYER) ? "Type a player's name" : "Pick a skin file";
		}
		return status;
	}

	/** Starts loading a newly chosen skin, and turns a finished download into a texture. Game thread. */
	static void tick() {
		if (!Modules.SKIN_PROTECT.enabled()) return;
		String wanted = wantedKey();
		if (wanted == null) return;
		Loaded ready = pending;
		if (ready != null && ready.key().equals(wanted) && !wanted.equals(skinKey)) {
			pending = null;
			skin = upload(ready);
			skinKey = skin != null ? wanted : null;
			if (skin == null) requestedKey = null;
		}
		if (wanted.equals(requestedKey)) return;
		requestedKey = wanted;
		status = "Loading...";
		if (wanted.startsWith("builtin:")) {
			String id = wanted.substring("builtin:".length());
			try (var in = CustomSkins.class.getResourceAsStream("/assets/eventide/skins/" + id + ".png")) {
				if (in == null) {
					status = "Eventide doesn't have that skin";
					return;
				}
				pending = new Loaded(wanted, in.readAllBytes(), false);
				status = "Showing " + Modules.SKIN_PROTECT_BUILTIN.get();
			} catch (IOException e) {
				status = "Couldn't read that skin";
			}
			return;
		}
		if (wanted.startsWith("player:")) {
			String name = Modules.SKIN_PROTECT_PLAYER.get().trim();
			CompletableFuture.runAsync(() -> fetchPlayer(wanted, name), Util.backgroundExecutor());
		} else {
			String file = Config.get().skinFile;
			boolean slim = wanted.endsWith(":slim");
			CompletableFuture.runAsync(() -> readFile(wanted, file, slim), Util.ioPool());
		}
	}

	private static void readFile(String key, String file, boolean slim) {
		try {
			Path path = folder().resolve(file).normalize();
			if (!path.startsWith(folder()) || !Files.isRegularFile(path)) {
				status = file + " isn't in the skins folder";
				return;
			}
			pending = new Loaded(key, Files.readAllBytes(path), slim);
			status = "Showing " + file;
		} catch (IOException e) {
			status = "Couldn't read " + file;
		}
	}

	/** Mojang's public profile lookup: name -> id -> skin URL (and whether it uses slim arms) -> PNG. */
	private static void fetchPlayer(String key, String name) {
		try {
			HttpResponse<String> lookup = get("https://api.mojang.com/users/profiles/minecraft/" + name);
			if (lookup.statusCode() == 404 || lookup.statusCode() == 204 || lookup.body().isBlank()) {
				status = "No player called " + name;
				return;
			}
			JsonObject account = JsonParser.parseString(lookup.body()).getAsJsonObject();
			String id = account.get("id").getAsString();
			String exactName = account.has("name") ? account.get("name").getAsString() : name;
			JsonObject profile = JsonParser.parseString(
					get("https://sessionserver.mojang.com/session/minecraft/profile/" + id).body()).getAsJsonObject();
			JsonObject textures = null;
			JsonArray properties = profile.getAsJsonArray("properties");
			for (JsonElement property : properties) {
				JsonObject object = property.getAsJsonObject();
				if (object.get("name").getAsString().equals("textures")) {
					String json = new String(Base64.getDecoder().decode(object.get("value").getAsString()), StandardCharsets.UTF_8);
					textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
				}
			}
			if (textures == null || !textures.has("SKIN")) {
				status = exactName + " uses a default skin";
				return;
			}
			JsonObject skinInfo = textures.getAsJsonObject("SKIN");
			String url = skinInfo.get("url").getAsString().replaceFirst("^http://", "https://");
			boolean slim = skinInfo.has("metadata") && skinInfo.getAsJsonObject("metadata").has("model")
					&& skinInfo.getAsJsonObject("metadata").get("model").getAsString().equals("slim");
			HttpResponse<byte[]> png = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(),
					HttpResponse.BodyHandlers.ofByteArray());
			if (png.statusCode() != 200) throw new IOException("skin download returned " + png.statusCode());
			pending = new Loaded(key, png.body(), slim);
			status = "Showing " + exactName + "'s skin";
		} catch (Exception e) {
			status = "Couldn't get " + name + "'s skin";
			EventideClient.LOG.warn("Couldn't get {}'s skin", name, e);
		}
	}

	private static HttpResponse<String> get(String url) throws IOException, InterruptedException {
		return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private static PlayerSkin upload(Loaded loaded) {
		try {
			NativeImage image = normalize(NativeImage.read(loaded.png()));
			Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(() -> "Eventide custom skin", image));
			Hooks.customSkinLoaded = true;
			return PlayerSkin.insecure(new ClientAsset.ResourceTexture(TEXTURE, TEXTURE), null, null,
					loaded.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE);
		} catch (IOException | RuntimeException e) {
			status = "That file isn't a Minecraft skin (64x64 PNG)";
			EventideClient.LOG.warn("Couldn't load a custom skin", e);
			return null;
		}
	}

	/** Skins are 64x64. Old 64x32 ones get their left arm and leg copied over, mirrored, like Minecraft does. */
	static NativeImage normalize(NativeImage image) throws IOException {
		int w = image.getWidth();
		int h = image.getHeight();
		if (w != 64 || (h != 64 && h != 32)) {
			image.close();
			throw new IOException("Skins are 64x64 or 64x32 pixels, this one is " + w + "x" + h);
		}
		if (h == 64) return image;
		NativeImage full = new NativeImage(64, 64, true);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 64; x++) full.setPixel(x, y, image.getPixel(x, y));
		}
		image.close();
		int[][] copies = {
				{4, 16, 16, 32, 4, 4}, {8, 16, 16, 32, 4, 4}, {0, 20, 24, 32, 4, 12}, {4, 20, 16, 32, 4, 12},
				{8, 20, 8, 32, 4, 12}, {12, 20, 16, 32, 4, 12}, {44, 16, -8, 32, 4, 4}, {48, 16, -8, 32, 4, 4},
				{40, 20, 0, 32, 4, 12}, {44, 20, -8, 32, 4, 12}, {48, 20, -16, 32, 4, 12}, {52, 20, -8, 32, 4, 12}
		};
		for (int[] copy : copies) {
			for (int dy = 0; dy < copy[5]; dy++) {
				for (int dx = 0; dx < copy[4]; dx++) {
					int argb = full.getPixel(copy[0] + dx, copy[1] + dy);
					full.setPixel(copy[0] + copy[2] + (copy[4] - 1 - dx), copy[1] + copy[3] + dy, argb);
				}
			}
		}
		return full;
	}
}
