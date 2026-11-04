package dev.eventide.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the player changes in Eventide's menus, saved to config/eventide.json. The module
 * switches, settings and HUD layout belong to the active profile; waypoints and the rest are shared.
 */
public final class Config {
	public static final String DEFAULT_PROFILE = "Default";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config instance = new Config();
	private static long saveAt = 0;

	/** Module id -> on/off. Missing entries use the module's default. */
	public Map<String, Boolean> modules = new LinkedHashMap<>();
	/** "module.setting" -> value as text. Missing entries use the setting's default. */
	public Map<String, String> settings = new LinkedHashMap<>();
	/** HUD widget id -> [x, y], each 0..1 across the free space on screen. */
	public Map<String, double[]> hud = new LinkedHashMap<>();
	/** Panels mod menu: each panel's spot and whether it's folded, as {x, y, folded}. */
	public Map<String, int[]> panels = new LinkedHashMap<>();

	/** Name of the profile the fields above belong to. */
	public String profile = DEFAULT_PROFILE;
	/** Saved copies of the other profiles. */
	public Map<String, Profile> profiles = new LinkedHashMap<>();

	/** World or server key -> its waypoints. */
	public Map<String, List<Waypoint>> waypoints = new LinkedHashMap<>();

	/** Names on the Player List, in the order they were added. */
	public List<String> players = new ArrayList<>();
	/** Name Protect's text changes: what to look for on screen and what to show instead. */
	public List<TextRule> textRules = new ArrayList<>();
	/** Name Protect's auto-detected text the player chose to leave as it is, by key (like "number:Kills"). */
	public List<String> protectExcluded = new ArrayList<>();
	/** Text Hotkeys: each key and the chat message or /command it sends. */
	public List<TextHotkey> textHotkeys = new ArrayList<>();
	/** The skin file (in eventide/skins) Skin Protect shows when set to use a file. */
	public String skinFile = "";

	/** Overrides the Discord application ID the mod was built with. */
	public String discordAppId = "";
	/** The player's own options, put back when the Eventide module that changes them is turned off. */
	public double savedDamageTilt = 1.0;
	public double savedFovEffects = 1.0;

	/** A saved setup: which modules are on, their settings and where the HUD widgets are. */
	public static final class Profile {
		public Map<String, Boolean> modules = new LinkedHashMap<>();
		public Map<String, String> settings = new LinkedHashMap<>();
		public Map<String, double[]> hud = new LinkedHashMap<>();
	}

	/** One Name Protect text change. In {@code find}, # stands for any number and * for any word. */
	public static final class TextRule {
		public String find = "";
		public String replace = "";
	}

	/** One Text Hotkey: a GLFW key (or mouse button when {@code mouse}) and what it sends. */
	public static final class TextHotkey {
		public int key = -1;
		public boolean mouse = false;
		public String text = "";
	}

	/** A marked place in one world. */
	public static final class Waypoint {
		public String name = "Waypoint";
		public int x;
		public int y;
		public int z;
		public String dimension = "minecraft:overworld";
		public int color = 0xFF5BC8E6;
		public boolean visible = true;
	}

	public static Config get() {
		return instance;
	}

	private static int version = 0;

	/**
	 * Goes up whenever a mod is switched or a setting changes (or a profile or the file is loaded), so mods
	 * and settings can keep their value instead of looking it up every time they're asked.
	 */
	public static int version() {
		return version;
	}

	/** Call after changing {@link #modules} or {@link #settings}. */
	public static void changed() {
		version++;
	}

	private static Path file() {
		return Migration.configFile();
	}

	public static void load() {
		Path path = file();
		if (!Files.isRegularFile(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Config loaded = GSON.fromJson(reader, Config.class);
			if (loaded != null) {
				if (loaded.modules == null) loaded.modules = new LinkedHashMap<>();
				if (loaded.settings == null) loaded.settings = new LinkedHashMap<>();
				if (loaded.hud == null) loaded.hud = new LinkedHashMap<>();
				if (loaded.panels == null) loaded.panels = new LinkedHashMap<>();
				loaded.panels.values().removeIf(spot -> spot == null || spot.length < 3);
				if (loaded.profile == null || loaded.profile.isBlank()) loaded.profile = DEFAULT_PROFILE;
				if (loaded.profiles == null) loaded.profiles = new LinkedHashMap<>();
				if (loaded.waypoints == null) loaded.waypoints = new LinkedHashMap<>();
				if (loaded.discordAppId == null) loaded.discordAppId = "";
				if (loaded.players == null) loaded.players = new ArrayList<>();
				if (loaded.textRules == null) loaded.textRules = new ArrayList<>();
				loaded.textRules.removeIf(rule -> rule == null || rule.find == null || rule.find.isBlank());
				loaded.textRules.forEach(rule -> {
					if (rule.replace == null) rule.replace = "";
				});
				if (loaded.protectExcluded == null) loaded.protectExcluded = new ArrayList<>();
				loaded.protectExcluded.removeIf(key -> key == null || key.isBlank());
				if (loaded.textHotkeys == null) loaded.textHotkeys = new ArrayList<>();
				loaded.textHotkeys.removeIf(hotkey -> hotkey == null || hotkey.key < 0 || hotkey.text == null || hotkey.text.isBlank());
				if (loaded.skinFile == null) loaded.skinFile = "";
				migrateBlades(loaded.modules, loaded.settings);
				Migration.renameValues(loaded.settings);
				dev.eventide.client.module.Knives.migrate(loaded.settings);
				for (Profile profile : loaded.profiles.values()) {
					if (profile != null && profile.modules != null && profile.settings != null) {
						migrateBlades(profile.modules, profile.settings);
						Migration.renameValues(profile.settings);
						dev.eventide.client.module.Knives.migrate(profile.settings);
					}
				}
				instance = loaded;
				changed();
			}
		} catch (Exception e) {
			EventideClient.LOG.warn("Couldn't read eventide.json, using defaults", e);
		}
	}

	/**
	 * Blades became Knives: its switch carries over, and its swords-as-katanas (or scythes) setup becomes
	 * Knives' auto mode with the same blade, so nothing looks different until the player changes it.
	 */
	public static void migrateBlades(Map<String, Boolean> modules, Map<String, String> settings) {
		Boolean on = modules.remove("blades");
		String style = settings.remove("blades.style");
		String axes = settings.remove("blades.axes");
		String others = settings.remove("blades.others");
		if (on == null && style == null && axes == null && others == null) return;
		if (on != null) modules.putIfAbsent("knives", on);
		settings.putIfAbsent("knives.auto", "true");
		settings.putIfAbsent("knives.auto_type", "true".equals(axes) ? "Swords and axes" : "Swords");
		settings.putIfAbsent("knives.model", style != null ? style : "Katana");
		if (others != null) settings.putIfAbsent("knives.others", others);
	}

	public static void save() {
		saveAt = 0;
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			EventideClient.LOG.warn("Couldn't save eventide.json", e);
		}
	}

	/** Saves about a second later, so dragging or clicking quickly doesn't hit the disk every frame. */
	public static void saveSoon() {
		saveAt = System.currentTimeMillis() + 1000;
	}

	static void tick() {
		if (saveAt != 0 && System.currentTimeMillis() >= saveAt) {
			save();
		}
	}

	// Profiles

	/** Every profile name, the active one included, in the order they were made. */
	public List<String> profileNames() {
		List<String> names = new ArrayList<>();
		names.add(profile);
		for (String name : profiles.keySet()) {
			if (!name.equals(profile)) names.add(name);
		}
		names.sort((a, b) -> a.equals(DEFAULT_PROFILE) ? -1 : b.equals(DEFAULT_PROFILE) ? 1 : 0);
		return names;
	}

	/** Starts a new profile as a copy of the current setup and switches to it. */
	public boolean createProfile(String name) {
		String clean = name.trim();
		if (clean.isEmpty() || clean.equals(profile) || profiles.containsKey(clean)) return false;
		profiles.put(profile, snapshot());
		profile = clean;
		profiles.remove(clean);
		saveSoon();
		return true;
	}

	/** Switches to another saved profile, keeping the current one as it is. */
	public void loadProfile(String name) {
		Profile target = profiles.get(name);
		if (target == null || name.equals(profile)) return;
		profiles.put(profile, snapshot());
		profiles.remove(name);
		modules = new LinkedHashMap<>(target.modules);
		settings = new LinkedHashMap<>(target.settings);
		hud = copyHud(target.hud);
		profile = name;
		changed();
		saveSoon();
	}

	public boolean deleteProfile(String name) {
		if (name.equals(profile) || name.equals(DEFAULT_PROFILE)) return false;
		boolean removed = profiles.remove(name) != null;
		if (removed) saveSoon();
		return removed;
	}

	private Profile snapshot() {
		Profile copy = new Profile();
		copy.modules = new LinkedHashMap<>(modules);
		copy.settings = new LinkedHashMap<>(settings);
		copy.hud = copyHud(hud);
		return copy;
	}

	private static Map<String, double[]> copyHud(Map<String, double[]> source) {
		Map<String, double[]> copy = new LinkedHashMap<>();
		if (source != null) source.forEach((key, value) -> copy.put(key, value == null ? null : value.clone()));
		return copy;
	}

	// Waypoints

	public List<Waypoint> waypoints(String worldKey) {
		return waypoints.computeIfAbsent(worldKey, key -> new ArrayList<>());
	}
}
