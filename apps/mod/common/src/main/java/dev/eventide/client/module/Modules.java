package dev.eventide.client.module;

import dev.eventide.client.Config;
import dev.eventide.client.module.Module.Category;
import dev.eventide.client.module.Setting.Choice;
import dev.eventide.client.module.Setting.Color;
import dev.eventide.client.module.Setting.Slider;
import dev.eventide.client.module.Setting.Text;
import dev.eventide.client.module.Setting.Toggle;
import dev.eventide.client.music.MusicBridge;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Every Eventide feature and its settings, plus the per-tick work for the ones that change game options. */
public final class Modules {
	private Modules() {
	}

	/** A HUD module: every text one can hide its label, fade its background and pick a colour. */
	private static Module hud(String id, String name, String description, boolean on) {
		return hud(id, name, description, Category.HUD, on);
	}

	private static Module hud(String id, String name, String description, Category category, boolean on) {
		Module module = new Module(id, name, description, category, on);
		module.add(new Toggle("label", "Show label", "The name in front of the value, in the accent colour", true));
		background(module, 85);
		module.add(new Color("color", "Text colour", "Colour of the value", 0xFFF6F3FF));
		return module;
	}

	/** How big one widget is drawn, on top of the HUD size in Theme. Scroll over it in Edit HUD to change it. */
	private static Module sized(Module module) {
		module.add(new Slider("size", "Size", "Or scroll over it in Edit HUD", 50, 200, 5, 100, "%"));
		return module;
	}

	/** How see-through a HUD widget's glass is. 0% removes it and gives the text a shadow. */
	private static Module background(Module module) {
		return background(module, 60);
	}

	private static Module background(Module module, int percent) {
		module.add(new Slider("background", "Background", "0% makes the glass fully transparent", 0, 100, 5, percent, "%"));
		return module;
	}

	// Combat
	public static final Module TARGET_HUD = background(new Module("target_hud", "Target HUD",
			"Health, armour, combo and pops of whoever you fight", Category.COMBAT, true).icon("crosshair"), 85);
	public static final Toggle TARGET_ARMOR = TARGET_HUD.add(new Toggle("armor", "Armour", "Their armour's durability and held items", true));
	public static final Module COMBO = hud("combo", "Combo Counter", "Your hit streak, right where you look", Category.COMBAT, false)
			.icon("flame").markNew();
	public static final Module POPS = background(new Module("pops", "Totem Pops", "Every totem popped around you, counted",
			Category.COMBAT, true).icon("shield-plus"));
	public static final Module ITEMS = background(new Module("items", "Item Counter",
			"Your totems, crystals and pearls, counted", Category.COMBAT, true).icon("package"));
	public static final Toggle ITEMS_TOTEMS = ITEMS.add(new Toggle("totem", "Totems", "Totems of Undying", true));
	public static final Toggle ITEMS_CRYSTALS = ITEMS.add(new Toggle("crystal", "End crystals", "End crystals", true));
	public static final Toggle ITEMS_OBSIDIAN = ITEMS.add(new Toggle("obsidian", "Obsidian", "Obsidian", true));
	public static final Toggle ITEMS_XP = ITEMS.add(new Toggle("xp", "XP bottles", "Bottles o' Enchanting", true));
	public static final Toggle ITEMS_GAPPLES = ITEMS.add(new Toggle("gapple", "Golden apples", "Golden and enchanted golden apples", true));
	public static final Toggle ITEMS_PEARLS = ITEMS.add(new Toggle("pearl", "Ender pearls", "Ender pearls", false));
	public static final Toggle ITEMS_ANCHORS = ITEMS.add(new Toggle("anchor", "Anchors", "Respawn anchors and glowstone", false));
	public static final Module CRYSTAL_OPTIMIZER = new Module("crystal_optimizer", "Crystal Optimizer",
			"Crystals vanish the moment you hit them. Check the server's rules", Category.COMBAT, false).icon("gem");
	public static final Module POP_NOTIFIER = new Module("pop_notifier", "Pop Alerts",
			"A chat message when someone pops a totem", Category.COMBAT, true).icon("bell");
	public static final Module KILL_EFFECTS = new Module("kill_effects", "Kill Effects",
			"A burst of light in your colour where a player you fought falls", Category.COMBAT, false).icon("skull").markNew();
	public static final Choice KILL_EFFECTS_STYLE = KILL_EFFECTS.add(new Choice("style", "Effect", "What plays where they fall", 0,
			"Nebula Burst", "Soul Rise", "Starfall", "Thunder", "Shatter"));
	public static final Color KILL_EFFECTS_COLOR = KILL_EFFECTS.add(new Color("color", "Colour", "The effect's main colour", 0xFF9A4DFF));
	public static final Slider KILL_EFFECTS_SIZE = KILL_EFFECTS.add(new Slider("size", "Size", "How big and how full the effect is",
			50, 200, 10, 100, "%"));
	public static final Choice KILL_EFFECTS_WHO = KILL_EFFECTS.add(new Choice("who", "Whose deaths", "Only players you hit in the last few seconds, or any player",
			0, "Your kills", "Any player"));
	public static final Toggle KILL_EFFECTS_MOBS = KILL_EFFECTS.add(new Toggle("mobs", "Mobs too", "Mobs you kill get the effect as well", false));
	public static final Toggle KILL_EFFECTS_SOUND = KILL_EFFECTS.add(new Toggle("sound", "Sound", "A sound to go with it, only you hear", true));
	public static final Module TOTEM_WARNING = new Module("totem_warning", "Totem Warning",
			"A warning when your offhand has no totem but your inventory does", Category.COMBAT, true).icon("triangle-alert");
	public static final Module ITEM_HIGHLIGHTER = new Module("item_highlighter", "Item Highlighter",
			"PvP items lit up in your inventory, so you find them fast", Category.COMBAT, true).icon("highlighter").markNew();
	public static final Toggle HIGHLIGHT_TOTEMS = ITEM_HIGHLIGHTER.add(new Toggle("totem", "Totems", "Totems of Undying", true));
	public static final Toggle HIGHLIGHT_CRYSTALS = ITEM_HIGHLIGHTER.add(new Toggle("crystal", "End crystals", "End crystals", true));
	public static final Toggle HIGHLIGHT_OBSIDIAN = ITEM_HIGHLIGHTER.add(new Toggle("obsidian", "Obsidian", "Obsidian", false));
	public static final Toggle HIGHLIGHT_GAPPLES = ITEM_HIGHLIGHTER.add(new Toggle("gapple", "Golden apples", "Golden apples", true));
	public static final Toggle HIGHLIGHT_PEARLS = ITEM_HIGHLIGHTER.add(new Toggle("pearl", "Ender pearls", "Ender pearls", true));
	public static final Toggle HIGHLIGHT_XP = ITEM_HIGHLIGHTER.add(new Toggle("xp", "XP bottles", "Bottles o' Enchanting", false));
	public static final Color HIGHLIGHT_COLOR = ITEM_HIGHLIGHTER.add(new Color("color", "Colour", "Highlight colour", 0xFFA52BF5));
	public static final Module HURT_CAM = new Module("no_hurt_cam", "Hurt Cam",
			"Choose how much your view shakes when you're hit", Category.COMBAT, false).icon("heart-crack");
	public static final Slider HURT_CAM_STRENGTH = HURT_CAM.add(new Slider("strength", "Shake", "0% means no shake at all",
			0, 100, 5, 0, "%"));
	public static final Module JUMP_RESET = background(new Module("jump_reset", "Jump Reset",
			"How well you timed your jump after a hit, to take less knockback", Category.COMBAT, false).icon("arrow-big-up-dash").markNew());

	// Render
	public static final Module CROSSHAIR = new Module("crosshair", "Custom Crosshair",
			"Your aim, in Eventide colours. Make it yours", Category.RENDER, false).icon("locate-fixed").markNew();
	public static final Choice CROSSHAIR_STYLE = CROSSHAIR.add(new Choice("style", "Style", "Shape of the crosshair", 0,
			"Cross", "Cross + dot", "Dot", "Circle", "T"));
	public static final Slider CROSSHAIR_SIZE = CROSSHAIR.add(new Slider("size", "Size", "Length of each arm", 1, 10, 1, 4, ""));
	public static final Slider CROSSHAIR_GAP = CROSSHAIR.add(new Slider("gap", "Gap", "Space in the middle", 0, 6, 1, 2, ""));
	public static final Slider CROSSHAIR_THICKNESS = CROSSHAIR.add(new Slider("thickness", "Thickness", "Width of the lines",
			1, 3, 1, 1, ""));
	public static final Color CROSSHAIR_COLOR = CROSSHAIR.add(new Color("color", "Colour", "Crosshair colour", 0xFFF6F3FF));
	public static final Toggle CROSSHAIR_OUTLINE = CROSSHAIR.add(new Toggle("outline", "Outline", "A dark edge so it shows on bright blocks", true));
	public static final Toggle CROSSHAIR_TARGET = CROSSHAIR.add(new Toggle("target", "Lit on players",
			"Turns pink while you're aiming at a player", true));
	public static final Module ZOOM = new Module("zoom", "Zoom", "A smooth lens, one key away", Category.RENDER, true).icon("zoom-in");
	public static final Slider ZOOM_STRENGTH = ZOOM.add(new Slider("strength", "Zoom strength", "How far zoom goes in",
			2, 10, 0.5, 4, "x"));
	public static final Toggle ZOOM_SMOOTH = ZOOM.add(new Toggle("smooth", "Smooth camera", "Cinematic camera while zooming", true));
	public static final Toggle ZOOM_SCROLL = ZOOM.add(new Toggle("scroll", "Scroll to adjust", "Mouse wheel zooms further in or out", true));
	public static final Module FREELOOK = new Module("freelook", "Freelook",
			"Look around without turning your body", Category.RENDER, true).icon("scan-eye").markNew();
	public static final Choice FREELOOK_MODE = FREELOOK.add(new Choice("mode", "Key mode", "Hold the key or press it once",
			0, "Hold", "Toggle"));
	public static final Module NAMETAGS = new Module("nametags", "Nametags",
			"Tags with each player's face and health, and your own in third person", Category.RENDER, false).icon("tag").markNew();
	public static final Toggle NAMETAGS_OWN = NAMETAGS.add(new Toggle("own", "Show your own", "See your nametag in third person", true));
	public static final Choice NAMETAGS_STYLE = NAMETAGS.add(new Choice("style", "Style", "Each player's face and health beside their name, or Minecraft's own tag",
			0, "Eventide", "Minecraft"));
	public static final Module FOV_CHANGER = new Module("fov_changer", "FOV Changer",
			"How much your view widens when you sprint or fly", Category.RENDER, false).icon("focus").markNew();
	public static final Slider FOV_EFFECTS = FOV_CHANGER.add(new Slider("effects", "FOV effects",
			"How much sprinting, flying and speed change your FOV", 0, 100, 5, 0, "%"));
	public static final Module SHINY_POTS = new Module("shiny_pots", "Shiny Pots",
			"An enchantment glint on every potion", Category.RENDER, false).icon("flask-conical").markNew();
	public static final Module SWING_SPEED = new Module("swing_speed", "Swing Speed",
			"A slower (or faster) arm swing when you hit and place", Category.RENDER, false).icon("sword").markNew();
	public static final Slider SWING_SPEED_SPEED = SWING_SPEED.add(new Slider("speed", "Speed",
			"How fast your arm swings: under 1x is slower than normal", 0.2, 2.0, 0.05, 0.5, "x"));
	public static final Toggle SWING_SPEED_OTHERS = SWING_SPEED.add(new Toggle("others", "Other players too",
			"Everyone's swing on your screen, not just yours", false));
	public static final Module LYRICS = new Module("sky_lyrics", "Sky Lyrics",
			"The song you play, its words floating large in the sky ahead, behind anything in the way", Category.RENDER, false).icon("mic-vocal").markNew();
	public static final Choice LYRICS_PLACE = LYRICS.add(new Choice("place", "Where", "High in the sky ahead of you, or at the top of the screen",
			0, "Sky", "Screen"));
	public static final Slider LYRICS_SIZE = LYRICS.add(new Slider("size", "Size", "How big the words are", 50, 200, 10, 100, "%"));
	public static final Toggle LYRICS_NEXT = LYRICS.add(new Toggle("next", "Next line", "The line coming up, smaller under the one being sung", true));
	public static final Slider LYRICS_HEIGHT = LYRICS.add(new Slider("height", "Height", "How high in the sky the words hang", 6, 40, 1, 17, "°")
			.group("Placement"));
	public static final Slider LYRICS_TILT = LYRICS.add(new Slider("tilt", "Tilt", "Leans the words as if hung on the sky; 0° keeps them level",
			0, 20, 1, 7, "°"));
	public static final Choice LYRICS_FONT = LYRICS.add(new Choice("font", "Font", "Inter, Eventide's display face, or Minecraft's own", 0,
			"Clean", "Display", "Minecraft").group("Look"));
	public static final Color LYRICS_COLOR = LYRICS.add(new Color("color", "Text colour", "The words as they're sung", 0xFFFFFFFF));
	public static final Color LYRICS_ACCENT = LYRICS.add(new Color("accent", "Glow colour", "The glow round the words and their outline", 0xFF9A4DFF));
	public static final Slider LYRICS_GLOW = LYRICS.add(new Slider("glow", "Glow", "How strong the glow is; 0% for none", 0, 200, 5, 100, "%"));
	public static final Choice LYRICS_BACKDROP = LYRICS.add(new Choice("backdrop", "Backdrop", "A soft glow, an outline round the letters, or a dark panel",
			0, "Glow", "Outline", "Panel"));
	public static final Choice LYRICS_STYLE = LYRICS.add(new Choice("style", "Line change", "How a new line comes in", 0, "Rise", "Fade", "Scale", "Drift")
			.group("Motion"));
	public static final Slider LYRICS_SPEED = LYRICS.add(new Slider("speed", "Change speed", "How quickly lines come and go", 50, 200, 10, 100, "%"));
	public static final Toggle LYRICS_DEBUG = LYRICS.add(new Toggle("debug", "Debug log",
			"Write every lyrics search to the game log: what was tried, where it was found and why not", false).group("Advanced"));

	// Player
	public static final Module NAME_PROTECT = new Module("name_protect", "Name Protect",
			"Your name hidden everywhere, and any text on screen changed", Category.PLAYER, false).icon("user-x").markNew();
	public static final Text NAME_PROTECT_NAME = NAME_PROTECT.add(new Text("name", "Show me as",
			"The name shown instead of yours", "Player", 16));
	public static final Toggle NAME_PROTECT_AUTO = NAME_PROTECT.add(new Toggle("auto", "Auto",
			"Finds what to hide on the scoreboard and tab list by itself, no rules needed", true));
	public static final Toggle NAME_PROTECT_OTHERS = NAME_PROTECT.add(new Toggle("others", "Hide other players",
			"Other players' names become Player 1, Player 2 and so on", false));
	public static final Toggle NAME_PROTECT_NUMBERS = NAME_PROTECT.add(new Toggle("numbers", "Hide stats",
			"Kills, wins, coins and other numbers on the scoreboard show as ???", true));
	public static final Toggle NAME_PROTECT_ADDRESS = NAME_PROTECT.add(new Toggle("address", "Hide server address",
			"The server's address, wherever it shows", true));
	public static final Toggle NAME_PROTECT_RANKS = NAME_PROTECT.add(new Toggle("ranks", "Hide rank tags",
			"Tags like [MVP+] or [VIP] show as [Rank]", false));
	public static final Module SKIN_PROTECT = new Module("skin_protect", "Skin Protect",
			"Wear a default skin, any player's, or your own file", Category.PLAYER, false).icon("shirt").markNew();
	public static final Toggle SKIN_PROTECT_OWN = SKIN_PROTECT.add(new Toggle("own", "Your skin",
			"Change how you look", true));
	public static final Toggle SKIN_PROTECT_OTHERS = SKIN_PROTECT.add(new Toggle("others", "Other players",
			"Everyone else gets the same skin too", false));
	public static final Choice SKIN_PROTECT_SOURCE = SKIN_PROTECT.add(new Choice("source", "Show as",
			"Minecraft's default skins, any player's skin, or a skin file", 0,
			CustomSkins.SOURCE_DEFAULT, CustomSkins.SOURCE_PLAYER, CustomSkins.SOURCE_FILE));
	public static final Text SKIN_PROTECT_PLAYER = SKIN_PROTECT.add(new Text("player", "Player",
			"Whose skin to wear, for A player's skin", "", 16));
	public static final Choice SKIN_PROTECT_ARMS = SKIN_PROTECT.add(new Choice("arms", "Arms",
			"Classic or slim arms, for skin files", 0, "Classic", "Slim"));
	public static final Module PLAYER_LIST = sized(background(new Module("player_list", "Player List",
			"Friends and rivals, and when they're on your server", Category.PLAYER, true).icon("users").markNew(), 90));
	public static final Text PLAYER_LIST_TITLE = PLAYER_LIST.add(new Text("title", "Title", "The name at the top of the list",
			"Players", 16));
	public static final Toggle PLAYER_LIST_OFFLINE = PLAYER_LIST.add(new Toggle("offline", "Show offline players",
			"Also list players who aren't on, after the ones who are", false));
	public static final Toggle PLAYER_LIST_ALERTS = PLAYER_LIST.add(new Toggle("alerts", "Join alerts",
			"A chat message when someone on your list joins or leaves", true));
	public static final Toggle PLAYER_LIST_ASK = PLAYER_LIST.add(new Toggle("ask", "Ask the server",
			"For servers like DonutSMP whose tab list doesn't show everyone: checks /msg name suggestions", true));
	public static final Text PLAYER_LIST_COMMAND = PLAYER_LIST.add(new Text("command", "Check with",
			"The command whose name suggestions show who's online, like msg or tpa", "msg", 16));
	public static final Module STAFF_LIST = sized(background(new Module("staff_list", "Staff List",
			"The staff on your server's tab list, with their rank, and when they join or leave", Category.PLAYER, false)
			.icon("shield-user").markNew()));
	public static final Text STAFF_LIST_RANKS = STAFF_LIST.add(new Text("ranks", "Ranks",
			"The rank words that mark staff in the tab list, highest first, split by commas",
			"owner, admin, manager, dev, mod, helper, staff", 96));
	public static final Module TOGGLE_SPRINT = new Module("toggle_sprint", "Toggle Sprint",
			"Press sprint once and keep sprinting", Category.PLAYER, false).icon("footprints");
	public static final Toggle TOGGLE_SNEAK = TOGGLE_SPRINT.add(new Toggle("sneak", "Toggle sneak too", "Press sneak once to keep sneaking", false));

	// World
	public static final Module WAYPOINTS = new Module("waypoints", "Waypoints",
			"Mark places and see them on screen with their distance", Category.WORLD, true).icon("map-pin").markNew();
	public static final Toggle WAYPOINTS_DISTANCE = WAYPOINTS.add(new Toggle("distance", "Distance", "Show how far away each one is", true));
	public static final Module FULLBRIGHT = new Module("fullbright", "Fullbright",
			"See clearly in the darkest caves", Category.WORLD, false).icon("sun");
	public static final Module NO_FOG = new Module("no_fog", "No Fog", "A clear view all the way to the horizon",
			Category.WORLD, false).icon("cloud-fog").markNew();

	// Utility
	public static final Module CHAT = new Module("chat", "Chat",
			"Timestamps, stacked spam and a longer history", Category.UTILITY, true).icon("message-circle").markNew();
	public static final Toggle CHAT_TIMESTAMPS = CHAT.add(new Toggle("timestamps", "Timestamps", "[14:05] in front of every message", true));
	public static final Toggle CHAT_STACK = CHAT.add(new Toggle("stack", "Stack spam", "Repeated messages become one with (x3)", true));
	public static final Toggle CHAT_HISTORY = CHAT.add(new Toggle("history", "Longer history", "Keep 1000 lines instead of 100", true));
	public static final Module TEXT_HOTKEYS = new Module("text_hotkeys", "Text Hotkeys",
			"One key sends a message or runs a command, like gg or /home", Category.UTILITY, false).icon("command").markNew();
	public static final Module TAB_EDITOR = new Module("tab_editor", "Tab Editor",
			"Ping as numbers, and the parts you don't need hidden", Category.UTILITY, true).icon("table").markNew();
	public static final Toggle TAB_PING_NUMBERS = TAB_EDITOR.add(new Toggle("ping", "Ping as numbers", "137ms instead of bars", true));
	public static final Toggle TAB_HIDE_HEADER = TAB_EDITOR.add(new Toggle("header", "Hide header and footer", "Just the player list", false));
	public static final Module CONFIRM_DISCONNECT = new Module("confirm_disconnect", "Confirm Disconnect",
			"A second click before you leave a server", Category.UTILITY, true).icon("log-out").markNew();
	public static final Module BORDERLESS = new Module("borderless", "Borderless Fullscreen",
			"Fullscreen as a borderless window, so alt-tab is instant", Category.UTILITY, false).icon("maximize").markNew();

	// HUD
	public static final Module WATERMARK = sized(background(new Module("watermark", "Watermark",
			"Eventide's logo in the top left with your FPS and ping beside it", Category.HUD, true).icon("badge").markNew(), 90));
	public static final Toggle WATERMARK_FPS = WATERMARK.add(new Toggle("fps", "FPS", "Frames per second, with a dot that shows how smooth it is", true));
	public static final Toggle WATERMARK_PING = WATERMARK.add(new Toggle("ping", "Ping", "Your latency to the server, with a dot for its quality", true));
	public static final Module BOSS_BARS = new Module("boss_bars", "Boss Bars",
			"Server boss bars and banners as a thin glowing line in their colour", Category.HUD, true).icon("panels-top-left").markNew();
	public static final Module FPS = hud("fps", "FPS", "Frames per second, with a live graph (the Watermark shows them too)", false).icon("gauge");
	public static final Toggle FPS_GRAPH = FPS.add(new Toggle("graph", "Graph", "A small line of your last few seconds", true));
	public static final Module CPS = hud("cps", "CPS", "Clicks per second, left and right", true).icon("mouse-pointer-click");
	public static final Module PING = hud("ping", "Ping", "Your latency to the server, coloured by quality (the Watermark shows it too)", false).icon("wifi");
	public static final Toggle PING_COLOURS = PING.add(new Toggle("quality", "Colour by quality",
			"Mint when it's good, pink when it's bad", true));
	public static final Module TPS = hud("tps", "TPS", "The server's ticks per second", false).icon("activity").markNew();
	public static final Toggle TPS_COLOURS = TPS.add(new Toggle("quality", "Colour by quality",
			"Mint at 20, pink when the server lags", true));
	public static final Module CLOCK = hud("clock", "Clock", "The real-world time on your screen", false).icon("clock").markNew();
	public static final Toggle CLOCK_24H = CLOCK.add(new Toggle("24h", "24-hour time", "14:05 instead of 2:05 PM", true));
	public static final Toggle CLOCK_SECONDS = CLOCK.add(new Toggle("seconds", "Seconds", "Show seconds too", false));
	public static final Module COORDS = hud("coords", "Coordinates", "Where you are and which way you face", false).icon("compass");
	public static final Toggle COORDS_FACING = COORDS.add(new Toggle("facing", "Facing", "Which way you're looking", true));
	public static final Module KEYSTROKES = background(new Module("keystrokes", "Keystrokes",
			"WASD and your mouse buttons, lit as you press them", Category.HUD, true).icon("keyboard"), 85);
	public static final Toggle KEYSTROKES_MOUSE = KEYSTROKES.add(new Toggle("mouse", "Mouse buttons", "LMB and RMB with CPS", true));
	public static final Toggle KEYSTROKES_SPACE = KEYSTROKES.add(new Toggle("space", "Space bar", "The jump key", true));
	public static final Color KEYSTROKES_PRESSED = KEYSTROKES.add(new Color("pressed", "Pressed colour",
			"The colour a key lights up while you hold it", 0xFFA52BF5));
	public static final Module ARMOR = background(new Module("armor", "Armor Status",
			"Your armour's durability at a glance", Category.HUD, true).icon("shield-half"));
	public static final Choice ARMOR_LAYOUT = ARMOR.add(new Choice("layout", "Layout",
			"A column at the side, or a row like an armour bar", 0, "Column", "Row"));
	public static final Choice ARMOR_DURABILITY = ARMOR.add(new Choice("durability", "Durability",
			"How durability is shown", 0, "Percent", "Points", "Hidden"));
	public static final Toggle ARMOR_HAND = ARMOR.add(new Toggle("hand", "Held item", "Also show the item in your hand", false));
	public static final Module POTIONS = background(new Module("potions", "Potion Effects",
			"Your effects and how long they have left", Category.HUD, false).icon("flask-round").markNew(), 85);
	public static final Toggle POTIONS_BARS = POTIONS.add(new Toggle("bars", "Time bars", "A bar under each effect that empties as it runs out", true));
	public static final Module SCOREBOARD = new Module("scoreboard", "Scoreboard",
			"Move the server's scoreboard, hide its red numbers, or hide it", Category.HUD, true).icon("list-ordered").markNew();
	public static final Toggle SCOREBOARD_HIDE_NUMBERS = SCOREBOARD.add(new Toggle("numbers", "Hide numbers",
			"Hide the red score numbers on the right", false));
	public static final Slider SCOREBOARD_SIZE = SCOREBOARD.add(new Slider("size", "Size",
			"Or scroll over it in Edit HUD", 50, 200, 5, 100, "%"));
	public static final Slider SCOREBOARD_BACKGROUND = SCOREBOARD.add(new Slider("background", "Background",
			"How dark the box behind it is. 0% removes it", 0, 100, 5, 55, "%"));
	public static final Color SCOREBOARD_COLOR = SCOREBOARD.add(new Color("color", "Background colour",
			"Colour of the box behind it", 0xFF0A0814));
	public static final Toggle SCOREBOARD_TITLE_BAR = SCOREBOARD.add(new Toggle("title_bar", "Title bar",
			"A darker strip behind the title, like Minecraft's", true));
	public static final Module HOTBAR = new Module("hotbar", "Hotbar",
			"Your hotbar as one glass bar, with your server's address beside it if you like", Category.HUD, false).icon("boxes").markNew();
	public static final Choice HOTBAR_SELECTED = HOTBAR.add(new Choice("selected", "Selected slot",
			"The square behind the item in your hand", 0, "White", "Accent"));
	public static final Toggle HOTBAR_SERVER = HOTBAR.add(new Toggle("server", "Server address",
			"The server's address in a pill beside the hotbar", true));
	public static final Slider HOTBAR_BACKGROUND = HOTBAR.add(new Slider("background", "Background",
			"How dark the bar is", 0, 100, 5, 75, "%"));
	public static final Module MUSIC = sized(background(new Module("music", "Music Player",
			"The song playing on Spotify, YouTube or any other app", Category.HUD, false).icon("music").markNew(), 90));
	public static final Toggle MUSIC_ART = MUSIC.add(new Toggle("art", "Album art", "The cover next to the song", true));
	public static final Toggle MUSIC_PROGRESS = MUSIC.add(new Toggle("progress", "Progress bar", "Time played and time left", true));
	public static final Toggle MUSIC_VOLUME = MUSIC.add(new Toggle("volume", "Volume", "Windows' volume under the song", false));
	public static final Color MUSIC_COLOR = MUSIC.add(new Color("color", "Colour", "The progress bar and volume bar", 0xFFA52BF5));
	public static final Toggle MUSIC_PAUSED = MUSIC.add(new Toggle("paused", "Show when paused",
			"Keep it on screen while the music is paused", true));
	public static final Toggle MUSIC_LYRICS = MUSIC.add(new Toggle("lyrics", "Lyrics line",
			"The line being sung, above the player, filling in as it's sung (lyrics from LRCLIB)", true));
	public static final Module MODULE_LIST = sized(background(new Module("module_list", "Active Modules",
			"The mods you have on, listed down the side of the screen", Category.HUD, true).icon("list").markNew(), 90));
	public static final Toggle MODULE_LIST_HUD = MODULE_LIST.add(new Toggle("hud", "HUD mods",
			"Also list the HUD mods you can already see, like FPS and Keystrokes", false));
	public static final Toggle MODULE_LIST_GLOW = MODULE_LIST.add(new Toggle("glow", "Glow",
			"A soft glow round the accent line at each row's end", true));
	public static final Toggle MODULE_LIST_ICONS = MODULE_LIST.add(new Toggle("icons", "Icons", "Each mod's icon in front of its name", false));

	public static final Module SKIES = new Module("skies", "Skies",
			"Living skies drawn per pixel: Eventide's galaxy, nebulae, auroras, flame clouds and more", Category.RENDER, false).icon("moon-star").markNew();
	public static final Choice SKIES_SKY = SKIES.add(new Choice("sky", "Sky", "Which sky you see", 0, dev.eventide.client.sky.SkyType.labels()).hide());
	public static final Slider SKIES_SPEED = SKIES.add(new Slider("speed", "Speed", "How fast the sky moves", 0, 300, 5, 100, "%").group("Sky"));
	public static final Slider SKIES_BRIGHTNESS = SKIES.add(new Slider("brightness", "Brightness", "How bright the sky is", 20, 200, 5, 100, "%"));
	public static final Color SKIES_TINT = SKIES.add(new Color("tint", "Tint", "A colour over the sky; white leaves it as it is", 0xFFFFFFFF));
	public static final Choice SKIES_TIME = SKIES.add(new Choice("time", "Time of day", "Follow the world's clock, or keep one time of day", 0,
			"World time", "Night", "Dusk", "Day"));
	public static final Slider SKIES_NEBULA = SKIES.add(new Slider("nebula", "Nebula", "How strong the clouds of glowing gas are", 0, 200, 5, 100, "%")
			.group("Atmosphere"));
	public static final Slider SKIES_GLOW = SKIES.add(new Slider("glow", "Glow", "The light of the eclipse ring, the nebula's heart and the horizon",
			0, 200, 5, 100, "%"));
	public static final Toggle SKIES_STARS = SKIES.add(new Toggle("stars", "Stars", "Stars twinkling in the night skies", true));
	public static final Slider SKIES_STAR_DENSITY = SKIES.add(new Slider("star_density", "Star density", "How many stars there are", 25, 200, 5, 100, "%"));
	public static final Slider SKIES_METEORS = SKIES.add(new Slider("meteors", "Shooting stars", "How many streaks of light fall through the sky; 0% for none",
			0, 200, 5, 100, "%"));
	public static final Toggle SKIES_PARTICLES = SKIES.add(new Toggle("particles", "Weather particles",
			"Light rain, snow or embers around you with the Rain, Snow and Fire skies", true).group("Weather"));
	public static final Choice SKIES_END = SKIES.add(new Choice("end", "In the End", "Your sky in the End too, or the End's own", 0,
			"Your sky", "The End's"));
	public static final Toggle SKIES_SUN_MOON = SKIES.add(new Toggle("sun_moon", "Sun and moon", "Minecraft's sun and moon over the sky", true)
			.group("Advanced"));
	public static final Choice SKIES_RESOLUTION = SKIES.add(new Choice("resolution", "Sky resolution",
			"Half and Quarter draw the sky smaller and stretch it, for more FPS", 1, "Full", "Half", "Quarter"));

	// Cosmetics
	public static final Module KNIVES = new Module("knives", "Knives",
			"Swords (or the hotbar slots you pick) as Eventide's knives, held in your own arm, with a glowing edge and their own animations",
			Category.COSMETICS, false).icon("sword").markNew();
	public static final Text KNIVES_SLOTS = KNIVES.add(new Text("slots", "Knife slots", "Hotbar slots held as knives, and which knife",
			"", 200).hide());
	public static final Choice KNIVES_MODEL = KNIVES.add(new Choice("model", "Knife", "Your swords' knife, and the first pick for a slot you mark",
			4, dev.eventide.client.knife.KnifeModel.labels()).group("Knife"));
	public static final Choice KNIVES_FINISH = KNIVES.add(new Choice("finish", "Finish",
			"What the blade's made of; Signature dresses each knife in its own", 0, dev.eventide.client.knife.Finish.labels()));
	public static final Toggle KNIVES_GLOW = KNIVES.add(new Toggle("glow", "Edge glow",
			"A line of light round the blade and a soft glow round the knife", true).group("Glow"));
	public static final Color KNIVES_GLOW_COLOR = KNIVES.add(new Color("glow_color", "Glow colour", "The colour of the edge and its glow",
			0xFF9A4DFF));
	public static final Slider KNIVES_GLOW_STRENGTH = KNIVES.add(new Slider("glow_strength", "Glow strength", "How bright the edge and its glow are",
			0, 200, 5, 100, "%"));
	public static final Toggle KNIVES_HAND_GLOW = KNIVES.add(new Toggle("hand_glow", "Hand glow", "The same soft glow round your arm", true));
	public static final Toggle KNIVES_ARM = KNIVES.add(new Toggle("arm", "Show hand", "Your own arm holding the knife, in your skin", true).group("Hand"));
	public static final Toggle KNIVES_SLEEVE = KNIVES.add(new Toggle("sleeve", "Sleeve", "Your skin's sleeve layer on the arm, if it has one", true));
	public static final Toggle KNIVES_ANIMATIONS = KNIVES.add(new Toggle("animations", "Custom animations",
			"Off, knife slots show your usual held item again", true).group("Animation"));
	public static final Choice KNIVES_STYLE = KNIVES.add(new Choice("style", "Animation style",
			"Fluid, Snappy (quicker, less sway) or Heavy (slower, with more weight)", 0, "Fluid", "Snappy", "Heavy"));
	public static final Slider KNIVES_SWING_SPEED = KNIVES.add(new Slider("swing_speed", "Slash speed", "How fast slashes and stabs play",
			50, 200, 5, 100, "%"));
	public static final Slider KNIVES_DRAW_SPEED = KNIVES.add(new Slider("draw_speed", "Draw speed", "How fast the knife comes out",
			50, 200, 5, 100, "%"));
	public static final Slider KNIVES_INSPECT_SPEED = KNIVES.add(new Slider("inspect_speed", "Inspect speed", "How fast the inspect plays",
			50, 200, 5, 100, "%"));
	public static final Slider KNIVES_IDLE = KNIVES.add(new Slider("idle", "Idle sway", "How much the knife breathes and sways as you look around",
			0, 200, 5, 100, "%"));
	public static final Slider KNIVES_BOB = KNIVES.add(new Slider("bob", "Movement bob", "How much the knife bobs as you walk, dips as you sprint and lands with you",
			0, 200, 5, 100, "%"));
	public static final Toggle KNIVES_KICK = KNIVES.add(new Toggle("kick", "Camera kick", "A small kick of the view on each slash and stab", true));
	public static final Toggle KNIVES_AUTO = KNIVES.add(new Toggle("auto", "Auto knives",
			"Every item of a kind is a knife too, not just the slots you mark", true).group("Auto knives"));
	public static final Choice KNIVES_AUTO_TYPE = KNIVES.add(new Choice("auto_type", "Which items", "What Auto knives turns into knives", 0,
			"Swords", "Swords and axes", "Item ID"));
	public static final Text KNIVES_AUTO_ITEM = KNIVES.add(new Text("auto_item", "Item ID", "For Which items: Item ID, like minecraft:stick",
			"minecraft:stick", 48).validate(id -> id.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+"), "An item ID is lowercase, like minecraft:stick"));
	public static final Slider KNIVES_FOV = KNIVES.add(new Slider("fov", "Viewmodel FOV", "70 is Minecraft's own; lower brings the knife in bigger",
			50, 90, 1, 70, "").group("Viewmodel"));
	public static final Choice KNIVES_PRESET = KNIVES.add(new Choice("preset", "Viewmodel", "Where the arm sits: Minecraft's place, nearer the middle, or your left hand",
			0, "Classic", "Center", "Left-handed"));
	public static final Slider KNIVES_SCALE = KNIVES.add(new Slider("scale", "Size", "How big the knife is", 50, 200, 5, 100, "%"));
	public static final Slider KNIVES_X = KNIVES.add(new Slider("x", "Left / right", "Moves the arm sideways", -100, 100, 1, 0, "").group("Advanced"));
	public static final Slider KNIVES_Y = KNIVES.add(new Slider("y", "Up / down", "Moves the arm up or down", -100, 100, 1, 0, ""));
	public static final Slider KNIVES_Z = KNIVES.add(new Slider("z", "Near / far", "Moves the arm closer or further away", -100, 100, 1, 0, ""));
	public static final Slider KNIVES_PITCH = KNIVES.add(new Slider("pitch", "Tilt", "Tips the arm forwards or back", -90, 90, 1, 0, "°"));
	public static final Slider KNIVES_YAW = KNIVES.add(new Slider("yaw", "Turn", "Turns the arm left or right", -90, 90, 1, 0, "°"));
	public static final Slider KNIVES_ROLL = KNIVES.add(new Slider("roll", "Wrist", "Twists the wrist, turning the blade", -90, 90, 1, 0, "°"));
	public static final Slider KNIVES_LIGHT = KNIVES.add(new Slider("light", "Viewmodel light", "Keeps the knife lit in dark places; 0% follows the world's light exactly",
			0, 100, 5, 60, "%"));
	public static final Module BADGE = new Module("badge", "Eventide Badge",
			"The Eventide logo by the name of everyone playing with Eventide, on name tags and in the tab list", Category.COSMETICS, true)
			.icon("badge-check").markNew();
	public static final Toggle BADGE_SHARE = BADGE.add(new Toggle("share", "Show yours",
			"Lets other Eventide players see your badge (Eventide marks itself in the skin settings the server shares)", true));
	public static final Toggle BADGE_TAB = BADGE.add(new Toggle("tab", "In the tab list", "The badge by Eventide players' names in the tab list too",
			true));
	public static final Module MENU_NAME = new Module("menu_name", "Menu Logo",
			"The Eventide logo in the corner of menus, chests and your inventory, or on your HUD", Category.COSMETICS, true)
			.icon("badge").markNew();
	public static final Choice MENU_NAME_SHOW = MENU_NAME.add(new Choice("show", "Show", "The logo, your name, or both", 0,
			"Logo", "Logo and name", "Name"));
	public static final Toggle MENU_NAME_HEAD = MENU_NAME.add(new Toggle("head", "Show your head", "Your skin's face next to your name", true));
	public static final Toggle MENU_NAME_HUD = MENU_NAME.add(new Toggle("hud", "Show in game",
			"Keep it on your HUD while you play too, above the Music Player; move it in Edit HUD", false));

	// Client
	public static final Module THEME = new Module("theme", "Theme",
			"Accent colour, how the menu opens, fonts and the HUD's size", Category.CLIENT, true).icon("palette").alwaysOn();
	public static final Color THEME_PANEL_COLOR = THEME.add(new Color("panel_color", "Accent colour",
			"Switches, the chosen tab, bars and glows; Violet is Eventide's own", dev.eventide.client.gui.Accent.DEFAULT).group("Colour"));
	public static final Toggle THEME_RAINBOW = THEME.add(new Toggle("rainbow", "Rainbow",
			"The accent slowly cycles through the rainbow", false));
	public static final Choice THEME_MENU_MODE = THEME.add(new Choice("menu_mode", "Menu display",
			"On the screen, or floating in the air in front of you like a hologram", 0, "Screen", "Floating").group("Menu"));
	public static final Slider THEME_FLOAT_DISTANCE = THEME.add(new Slider("float_distance", "Floating distance",
			"How far in front of you the floating menu hangs", 1.0, 3.0, 0.1, 1.6, " blocks"));
	public static final Slider THEME_FLOAT_SCALE = THEME.add(new Slider("float_scale", "Floating size",
			"How big the floating menu is", 60, 150, 5, 100, "%"));
	public static final Choice THEME_FONT = THEME.add(new Choice("font", "Menu font",
			"Inter, like the website, or Minecraft's pixel font", 0, "Clean", "Minecraft"));
	public static final Toggle THEME_SNOW = THEME.add(new Toggle("snow", "Stars", "Twinkling stars behind Eventide's menus", true));
	public static final Slider THEME_SNOW_AMOUNT = THEME.add(new Slider("snow_amount", "Star amount", "How many stars",
			10, 100, 10, 50, "%"));
	public static final Slider THEME_HUD_SCALE = THEME.add(new Slider("hud_scale", "HUD size", "Makes every HUD widget bigger or smaller",
			50, 200, 5, 100, "%").group("HUD"));
	public static final Toggle THEME_ROUNDED_HUD = THEME.add(new Toggle("rounded_hud", "Rounded HUD",
			"Soft round corners on HUD widgets", true));
	public static final Choice THEME_WINDOW_TITLE = THEME.add(new Choice("window_title", "Window title",
			"Keep \"Minecraft\" in it so Medal, OBS and Discord recognise the game", 0,
			"Minecraft - Eventide", "Eventide").group("Window"));
	public static final Module PERFORMANCE = new Module("performance", "Performance",
			"Smoother frames on slower PCs: lighter effects and fewer particles, or one preset", Category.CLIENT, true).icon("gauge").alwaysOn()
			.markNew();
	public static final Choice PERFORMANCE_PRESET = PERFORMANCE.add(new Choice("preset", "Preset", "Sets everything below at once", 1,
			"Quality", "Balanced", "Performance", "Custom"));
	public static final Choice PERFORMANCE_SHADOWS = PERFORMANCE.add(new Choice("shadows", "Shadows and glows",
			"The soft shadows and glows under Eventide's menus and HUD", 0, "Full", "Simple", "Off"));
	public static final Choice PERFORMANCE_BLUR = PERFORMANCE.add(new Choice("blur", "Menu blur", "How much the world behind menus is blurred",
			0, "High", "Low", "Off"));
	public static final Choice PERFORMANCE_ANIMATIONS = PERFORMANCE.add(new Choice("animations", "Animations",
			"Movement in Eventide's menus and HUD", 0, "Full", "Quick", "Off"));
	public static final Toggle PERFORMANCE_PARTICLES = PERFORMANCE.add(new Toggle("particles", "Fewer particles",
			"Minecraft's particles on Decreased, and no weather particles from Skies", false));

	public static final Module DISCORD = new Module("discord", "Discord Presence",
			"Eventide and your server on your Discord profile", Category.CLIENT, true).icon("message-square");
	public static final Toggle DISCORD_SERVER = DISCORD.add(new Toggle("server", "Show server address",
			"Your server's address on your Discord profile", true));
	public static final Module PROFILES = new Module("profiles", "Profiles",
			"Save and switch between different mod and setting setups", Category.CLIENT, true).icon("layers").alwaysOn();

	/** Every module, in menu order: by tab, then as listed here. */
	public static final List<Module> ALL = List.of(
			TARGET_HUD, COMBO, POPS, ITEMS, CRYSTAL_OPTIMIZER, POP_NOTIFIER, TOTEM_WARNING, ITEM_HIGHLIGHTER, HURT_CAM, JUMP_RESET,
			SKIES, CROSSHAIR, ZOOM, FREELOOK, NAMETAGS, FOV_CHANGER, SHINY_POTS, SWING_SPEED, LYRICS,
			NAME_PROTECT, SKIN_PROTECT, PLAYER_LIST, STAFF_LIST, TOGGLE_SPRINT,
			WAYPOINTS, FULLBRIGHT, NO_FOG,
			CHAT, TEXT_HOTKEYS, TAB_EDITOR, CONFIRM_DISCONNECT, BORDERLESS,
			FPS, CPS, PING, TPS, CLOCK, COORDS, KEYSTROKES, ARMOR, POTIONS, SCOREBOARD, HOTBAR, MUSIC, MODULE_LIST,
			KNIVES, BADGE, MENU_NAME,
			THEME, PERFORMANCE, DISCORD, PROFILES);

	/** The modules in one tab, in order. */
	public static List<Module> in(Category category) {
		List<Module> list = new java.util.ArrayList<>();
		for (Module module : ALL) {
			if (module.category == category) list.add(module);
		}
		return list;
	}

	private static Boolean appliedToggleSprint = null;
	private static Boolean appliedToggleSneak = null;
	private static Double appliedHurtCam = null;
	private static Double appliedFovEffects = null;
	private static Boolean appliedNoFog = null;
	private static boolean addedNightVision = false;
	private static final int NIGHT_VISION_TICKS = 20 * 30;

	public static void tick(Minecraft mc) {
		syncToggleKeys(mc);
		syncHurtCam(mc);
		syncFovEffects(mc);
		syncFullbright(mc);
		syncFog();
		Zoom.tick(mc);
		Freelook.tick(mc);
		Target.tick(mc);
		Combo.tick(mc);
		JumpReset.tick(mc);
		ConfirmDisconnect.tick();
		NameProtect.tick(mc);
		NameDetect.tick(mc);
		Performance.tick(mc);
		TextRules.tick();
		TextHotkeys.tick(mc);
		CustomSkins.tick();
		PlayerList.tick(mc);
		KillEffects.tick(mc);
		MusicBridge.tick();
		if (LYRICS.enabled() || MUSIC.enabled() && MUSIC_LYRICS.get()) dev.eventide.client.music.Lyrics.want(MusicBridge.current());
		if (SKIES.enabled()) dev.eventide.client.sky.SkyWeather.tick(mc);
	}

	/** Uses Minecraft's own "Sprint: Toggle" and "Sneak: Toggle", so they behave exactly like vanilla. */
	private static void syncToggleKeys(Minecraft mc) {
		boolean sprint = TOGGLE_SPRINT.enabled();
		// Only touch the vanilla options when the module changes, so players who never use it keep
		// whatever they picked in Controls
		if (appliedToggleSprint == null || appliedToggleSprint != sprint) {
			if (appliedToggleSprint != null || sprint) mc.options.toggleSprint().set(sprint);
			appliedToggleSprint = sprint;
		}
		boolean sneak = sprint && TOGGLE_SNEAK.get();
		if (appliedToggleSneak == null || appliedToggleSneak != sneak) {
			if (appliedToggleSneak != null || sneak) mc.options.toggleCrouch().set(sneak);
			appliedToggleSneak = sneak;
		}
	}

	/** Uses the vanilla "Damage Tilt" accessibility option. */
	private static void syncHurtCam(Minecraft mc) {
		Double want = HURT_CAM.enabled() ? HURT_CAM_STRENGTH.get() / 100.0 : null;
		if (java.util.Objects.equals(want, appliedHurtCam)) return;
		if (want != null) {
			if (appliedHurtCam == null) {
				Config.get().savedDamageTilt = mc.options.damageTiltStrength().get();
				Config.saveSoon();
			}
			mc.options.damageTiltStrength().set(want);
		} else if (appliedHurtCam != null) {
			mc.options.damageTiltStrength().set(Config.get().savedDamageTilt);
		}
		appliedHurtCam = want;
	}

	/** Uses the vanilla "FOV Effects" accessibility option. */
	private static void syncFovEffects(Minecraft mc) {
		Double want = FOV_CHANGER.enabled() ? FOV_EFFECTS.get() / 100.0 : null;
		if (java.util.Objects.equals(want, appliedFovEffects)) return;
		if (want != null) {
			if (appliedFovEffects == null) {
				Config.get().savedFovEffects = mc.options.fovEffectScale().get();
				Config.saveSoon();
			}
			mc.options.fovEffectScale().set(want);
		} else if (appliedFovEffects != null) {
			mc.options.fovEffectScale().set(Config.get().savedFovEffects);
		}
		appliedFovEffects = want;
	}

	/** Uses the fog switch Minecraft keeps for debugging. */
	private static void syncFog() {
		boolean noFog = NO_FOG.enabled();
		if (appliedNoFog == null && !noFog) {
			appliedNoFog = false;
			return;
		}
		if (appliedNoFog != null && appliedNoFog == noFog) return;
		// toggleFog flips the switch and returns the new state; flip again if it landed wrong
		if (FogRenderer.toggleFog() == noFog) FogRenderer.toggleFog();
		appliedNoFog = noFog;
	}

	/** Fullbright through a hidden, client-only night vision effect. */
	private static void syncFullbright(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			addedNightVision = false;
			return;
		}
		if (FULLBRIGHT.enabled()) {
			MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
			if (current == null || (addedNightVision && current.getDuration() < NIGHT_VISION_TICKS - 100)) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, false, false, false));
				addedNightVision = true;
			}
		} else if (addedNightVision) {
			player.removeEffect(MobEffects.NIGHT_VISION);
			addedNightVision = false;
		}
	}
}
