package dev.eventide.client.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.eventide.client.Config;
import dev.eventide.client.Draw;
import dev.eventide.client.EventideClient;
import dev.eventide.client.FreezeWatch;
import dev.eventide.client.Hooks;
import dev.eventide.client.compat.Compat;
import dev.eventide.client.compat.CompatScreen;
import dev.eventide.client.gui.HudEditorScreen;
import dev.eventide.client.gui.PauseMenuButton;
import dev.eventide.client.gui.UiFont;
import dev.eventide.client.gui.menu.FloatingMenu;
import dev.eventide.client.gui.menu.FloatingMenuScreen;
import dev.eventide.client.gui.menu.MenuScreen;
import dev.eventide.client.hud.CpsTracker;
import dev.eventide.client.hud.HudRenderer;
import dev.eventide.client.hud.HudWidget;
import dev.eventide.client.hud.HudWidgets;
import dev.eventide.client.hud.Pills;
import dev.eventide.client.knife.KnifeModel;
import dev.eventide.client.knife.KnifeRenderer;
import dev.eventide.client.module.ChatTweaks;
import dev.eventide.client.module.ConfirmDisconnect;
import dev.eventide.client.module.CustomSkins;
import dev.eventide.client.module.Freelook;
import dev.eventide.client.module.Knives;
import dev.eventide.client.module.Module.Category;
import dev.eventide.client.module.Module;
import dev.eventide.client.module.Modules;
import dev.eventide.client.module.NameProtect;
import dev.eventide.client.module.PlayerList;
import dev.eventide.client.module.PopTracker;
import dev.eventide.client.module.StaffList;
import dev.eventide.client.module.Target;
import dev.eventide.client.module.TextHotkeys;
import dev.eventide.client.module.TextRules;
import dev.eventide.client.music.Lyrics;
import dev.eventide.client.music.MusicBridge;
import dev.eventide.client.ui.Sky;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;

/**
 * Runs only in CI: starts the real game with Eventide loaded, plays a short scripted session and
 * takes screenshots of every Eventide screen. Fails if one of Eventide's features didn't work.
 */
public final class EventideGameTest implements FabricClientGameTest {
	/** A pixel-art cover and app icon, standing in for what Windows hands the music helper. */
	private static final String SAMPLE_ART = "iVBORw0KGgoAAAANSUhEUgAAACgAAAAoCAYAAACM/rhtAAADBklEQVR4nO1XXUgUURg9d2asl6DHngoxDAtNa1ERXVxdMPvDyPIhDPpdwxSpBC2wCMk0ykgN08oW7EE0IzJSBDUxKQ1jSyhDiiAIfAkFCXwQe5p1Z+69M/furroPHfhg9/s535l755u5Q3ZsKVxCBEMhREEwNvy5XTpXtMaQFx99cgkAhnxtAICspFPCVzfka5PKDwYkIeaM8BYPfnyI7N1nV0SIzm3uQXZuLQrrPTgw8QBuxzlhvx1IYmzxqg/Jh09XkJxYI5RLdm0rXRWBY74Kbiw1qY4bU4iiItD6xxtg9okar9ZKnC6ex6kRohqSc9PKYfaJIpRaXh21gmbre3c76BXVTUggr19y/OUVvQdHx88L56an3Kd8ikIUyNjrtzVS+c7UZiFxztRmZr0mugU6DmZeE942GfA4FUJUrLSJoGf4OrPWdkhk7eWbq5TPleG1FOfK8CIv+wabM91xU3pIXgxU4pC7VrbMj6GR48hytgvxEWfyrYg+D1IPagDo7r8IAMjPqV9tPRSoKX7WV4YjufcA8CdLFIFcMjUAljW40hr9W9zVW4Kje5tCEhVuGKa4YH+z8LR29ZaELfeH2sOPuzNa1nxIptGNWOQzY1IfTR2vPEF9YFnZNLr9IllxqVfdsbwn4X/NLS7/ZHFriqYFzf114Sm2ry8MqT4Q3xY7KD5Ni2ILnJz3ImHDCesOCwCvXggLtMvMp6jrNLAMANOv2+S8138hVnl29WaY+TQ1it73iZkWAIDvzyM4NhXZLgSLQ0dFtZsTyTH8q6saQOvjw/Ccfm7wa/pqcZsz4uO/jCffiZkWpGxePjmXV2VacrJQUe3G999zVD9NDdjzpoZ9cBzYQ4lJiymzbaBGabhQmS4tzAz94u7WjgIAyPsvs/4HdWP9GEam7lBFzrhL/t+suI7O1s6QBZphEFjgKeAmuhLob9viUkfYBZlhEGhGXPRGW4Kpn3MhCbDrwZwQEWHmXFmhoj0MAmWE8RraCZXtQWb/Lq35acYKyloLsMN/gaEi4gX+A2HP4KtuJtSoAAAAAElFTkSuQmCC";
	private static final String SAMPLE_ICON = "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAb0lEQVR4nMWTQQ6AIAwEp8SHyi/kBfIL+SleIBJojJSDc2pId9k2IBTyfmQmkBQFwFnErUYs4ha3IgbYPnVd51P7sJigNZsy8GG43ZZgeYQOfYndnG+MCSbEuoGyKPWs8P9LdPVXWZAUxdXCIga4AUM9HdZO1//vAAAAAElFTkSuQmCC";
	/** A song as the Windows helper reports it, since CI can't play real music. */
	private static final String SAMPLE_SONG = "{\"key\":\"test\",\"app\":\"Spotify.exe\",\"title\":\"Snowfall\","
			+ "\"artist\":\"Eventide Test Band\",\"album\":\"CI\",\"playing\":true,\"position\":61,\"duration\":235,\"volume\":64,\"muted\":false,"
			+ "\"art\":\"" + SAMPLE_ART + "\",\"icon\":\"" + SAMPLE_ICON + "\"}";
	/** The same, paused with the sound muted, for the paused close-up. */
	private static final String PAUSED_SONG = "{\"key\":\"test2\",\"app\":\"Spotify.exe\",\"title\":\"Midnight Drive\","
			+ "\"artist\":\"Pixel Lights\",\"album\":\"CI\",\"playing\":false,\"position\":134,\"duration\":201,\"volume\":0,\"muted\":true,"
			+ "\"art\":\"" + SAMPLE_ART + "\",\"icon\":\"" + SAMPLE_ICON + "\"}";
	/** An old-style 64x32 skin, to check Skin Protect converts it like Minecraft does. */
	private static final String LEGACY_SKIN = "iVBORw0KGgoAAAANSUhEUgAAAEAAAAAgCAYAAACinX6EAAAASUlEQVR42u3QAQkAIBAEwRPNYSbDGu9Bg/wsbIEZ+9w3k3R9JZXOAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACADn2s5CHgcZHXdQAAAABJRU5ErkJggg==";

	/**
	 * One HUD widget on its own, half again its size, for close-up screenshots: the music HUD low on
	 * the Eventide sky (clear of toasts), the others on plain grey like 67's pictures, the module list in
	 * the top right corner where it sits in game.
	 */
	/** Your player drawn large on grey, from the front and turned each way, to compare a 3D skin with its reference. */
	private static final class SkinShowcase extends CompatScreen {
		SkinShowcase() {
			super(Component.literal("Skin close-up"));
		}

		@Override
		protected void drawBackdrop(Draw d) {
			d.fill(0, 0, this.width, this.height, 0xFFC9CACD);
		}

		@Override
		protected void draw(Draw d, int mouseX, int mouseY, float delta) {
			Minecraft mc = Minecraft.getInstance();
			int third = this.width / 3;
			int top = 4;
			int bottom = this.height - 4;
			int scale = Math.round((bottom - top) / 2.6f);
			float eye = top + (bottom - top) * 0.5f;
			for (int i = 0; i < 3; i++) {
				int x0 = i * third;
				int x1 = x0 + third;
				float look = i == 1 ? (x0 + x1) / 2f : i == 0 ? x1 + 60 : x0 - 60;
				d.entity(mc.player, x0, top, x1, bottom, scale, look, eye);
			}
		}
	}

	private static final class MusicShowcase extends CompatScreen {
		private final HudWidget widget;
		private final List<HudWidget> row;

		MusicShowcase() {
			this(HudWidgets.MUSIC);
		}

		MusicShowcase(HudWidget widget, HudWidget... more) {
			super(Component.literal("HUD close-up"));
			this.widget = widget;
			List<HudWidget> all = new ArrayList<>();
			all.add(widget);
			all.addAll(List.of(more));
			this.row = all;
		}

		@Override
		protected void drawBackdrop(Draw d) {
			if (widget == HudWidgets.MUSIC) {
				float unit = Math.min(this.width / 1600f, this.height / 900f);
				d.push();
				d.scale(unit);
				Sky.backdrop(d, this.width / unit, this.height / unit, false, 1);
				d.pop();
			} else {
				d.fill(0, 0, this.width, this.height, 0xFF4B4C4F);
			}
		}

		@Override
		protected void draw(Draw d, int mouseX, int mouseY, float delta) {
			Minecraft mc = Minecraft.getInstance();
			float scale = 1.5f;
			if (row.size() > 1) {
				int gap = 4;
				float total = -gap;
				for (HudWidget each : row) total += each.width(mc) + gap;
				d.push();
				d.translate((this.width - total * scale) / 2f, (this.height - widget.height(mc) * scale) / 2f);
				d.scale(scale);
				int x = 0;
				for (HudWidget each : row) {
					each.draw(d, x, 0, mc, true);
					x += each.width(mc) + gap;
				}
				d.pop();
				return;
			}
			int w = Math.round(widget.width(mc) * scale);
			int h = Math.round(widget.height(mc) * scale);
			d.push();
			if (widget == HudWidgets.MODULE_LIST) {
				d.translate(this.width - widget.width(mc) * scale - 6, 6);
			} else if (widget == HudWidgets.MUSIC) {
				d.translate((this.width - w) / 2f, Math.max(4, this.height - h - 10));
			} else if (widget == HudWidgets.STAFF_LIST || widget == HudWidgets.PLAYER_LIST || widget == HudWidgets.POPS) {
				d.translate((this.width - w) / 2f, 40);
			} else {
				d.translate((this.width - w) / 2f, (this.height - h) / 2f);
			}
			d.scale(scale);
			widget.draw(d, 0, 0, mc, widget != HudWidgets.MUSIC);
			d.pop();
		}
	}

	/** How many of the chat's wrapped lines contain {@code text}. */
	private static int chatLinesWith(net.minecraft.client.Minecraft client, String text) {
		int count = 0;
		for (String line : ((ChatTweaks.ShownLines) client.gui.getChat()).eventide$shownLines()) {
			if (line.contains(text)) count++;
		}
		return count;
	}

	/**
	 * Wraps, cuts and measures a line holding the player's name at many widths, the way chat,
	 * tooltips and floating text do. Returns what went wrong, or null.
	 */
	private static String nameWrapProblem(net.minecraft.client.Minecraft client) {
		String own = client.getUser().getName();
		String text = "§6[Practice] §f" + own + " §7- §a12 kills §7- best friends: " + own + ", Steve, " + own;
		net.minecraft.network.chat.Style plain = net.minecraft.network.chat.Style.EMPTY;
		try {
			for (int width = 1; width <= 420; width += 3) {
				client.font.split(net.minecraft.network.chat.Component.literal(text), width);
				for (var line : client.font.getSplitter().splitLines(net.minecraft.network.chat.Component.literal(text), width, plain)) {
					if (line.getString().contains(own)) return "your real name in a wrapped line at width " + width + ": " + line.getString();
				}
				client.font.getSplitter().splitLines(text, width, plain);
				client.font.getSplitter().findLineBreak(text, width, plain);
				if (client.font.substrByWidth(net.minecraft.network.chat.Component.literal(text), width).getString().contains(own)) {
					return "your real name in cut text at width " + width;
				}
				String head = client.font.plainSubstrByWidth(text, width);
				if (!text.startsWith(head)) return "cut text isn't the start of the text at width " + width;
			}
			for (int skip = 0; skip <= text.length(); skip++) {
				int from = skip;
				int[] bad = {-1};
				net.minecraft.util.StringDecomposer.iterateFormatted(text, skip, plain, plain, (index, style, codepoint) -> {
					if (index < from || index + Character.charCount(codepoint) > text.length()) bad[0] = index;
					return true;
				});
				if (bad[0] >= 0) return "position " + bad[0] + " reported when reading from " + skip;
			}
		} catch (RuntimeException e) {
			return e.toString();
		}
		return null;
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> failed = new ArrayList<>();
		String focus = System.getProperty("eventide.testFocus", "");
		if (!focus.isEmpty()) {
			runFocused(context, focus, failed);
			if (!failed.isEmpty()) throw new AssertionError("Eventide checks that failed: " + String.join(", ", failed));
			return;
		}

		context.waitTicks(40);
		shot(context, "eventide-main-menu");
		int[] fontWidths = context.computeOnClient(client -> new int[] {
				client.font.width(UiFont.bold("PANELS AND MUSIC")),
				client.font.width(Component.literal("PANELS AND MUSIC").withStyle(net.minecraft.ChatFormatting.BOLD))});
		EventideClient.LOG.info("eventide-check: \"PANELS AND MUSIC\" is {} wide in the clean font, {} in Minecraft's bold", fontWidths[0],
				fontWidths[1]);
		check(failed, "clean font (Inter) loads from the mod's resources", fontWidths[0] > 20 && fontWidths[0] != fontWidths[1]);
		check(failed, "the title screen is Minecraft's own again", context.computeOnClient(client -> client.screen instanceof TitleScreen));
		List<String> unlisted = new ArrayList<>();
		for (java.lang.reflect.Field field : Modules.class.getFields()) {
			try {
				if (Module.class.isAssignableFrom(field.getType()) && !Modules.ALL.contains((Module) field.get(null))) unlisted.add(field.getName());
			} catch (IllegalAccessException e) {
				unlisted.add(field.getName());
			}
		}
		EventideClient.LOG.info("eventide-check: modules missing from the menu: {}", unlisted);
		check(failed, "every mod is in the menu (Modules.ALL)", unlisted.isEmpty());
		check(failed, "main menu keeps Singleplayer, Multiplayer, Realms, Options and Quit", context.computeOnClient(client -> {
			int found = 0;
			for (AbstractWidget widget : Compat.widgets(client.screen)) {
				if (widget.visible && widget.getMessage().getContents() instanceof TranslatableContents key
						&& List.of("menu.singleplayer", "menu.multiplayer", "menu.online", "menu.options", "menu.quit").contains(key.getKey())) {
					found++;
				}
			}
			return found == 5;
		}));
		// Mod Menu (CI adds it): Eventide's own entry in its list, with the new icon and name
		boolean modMenuOpen = context.computeOnClient(client -> {
			try {
				Class<?> mods = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
				client.setScreen((net.minecraft.client.gui.screens.Screen) mods.getConstructor(net.minecraft.client.gui.screens.Screen.class)
						.newInstance(client.screen));
				return true;
			} catch (ReflectiveOperationException | RuntimeException e) {
				EventideClient.LOG.info("eventide-check: Mod Menu isn't loaded, so its list isn't pictured");
				return false;
			}
		});
		if (modMenuOpen) {
			context.waitTicks(20);
			shot(context, "eventide-modmenu");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);
		}

		context.getInput().pressKey(EventideClient.openSettingsKey);
		context.waitTicks(30);
		check(failed, "Right Shift on the main menu opens the menu", context.computeOnClient(client ->
				client.screen instanceof MenuScreen menu && menu.view().category() == Category.RENDER));
		shot(context, "eventide-menu-from-menu");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);
		check(failed, "Esc in the menu goes back to the main menu", context.computeOnClient(client -> client.screen instanceof TitleScreen));
		context.runOnClient(client -> Modules.THEME_MENU_MODE.set("Floating"));
		context.getInput().pressKey(EventideClient.openSettingsKey);
		context.waitTicks(5);
		check(failed, "Right Shift on the main menu opens the menu on screen, even set to Floating (there's no world)",
				context.computeOnClient(client -> client.screen instanceof MenuScreen));
		context.runOnClient(client -> Modules.THEME_MENU_MODE.reset());
		context.setScreen(() -> new HudEditorScreen(new TitleScreen()));
		context.waitTicks(10);
		shot(context, "eventide-edit-hud-from-menu");
		context.setScreen(() -> Compat.optionsScreen(new TitleScreen()));
		context.waitTicks(10);
		shot(context, "eventide-options-buttons");

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(200);
			context.runOnClient(client -> {
				Modules.COORDS.set(true);
				Modules.CLOCK.set(true);
				Modules.CRYSTAL_OPTIMIZER.set(true);
				Modules.SHINY_POTS.set(true);
				Modules.CROSSHAIR.set(true);
				Modules.NAMETAGS.set(true);
				Modules.KEYSTROKES.sliderSetting("background").set(25);
				Modules.MUSIC.set(true);
				MusicBridge.simulate(SAMPLE_SONG);
				Modules.MENU_NAME_HUD.set(true);
				Modules.POTIONS.set(true);
				Modules.PLAYER_LIST_OFFLINE.set(true);
				Modules.PLAYER_LIST_TITLE.set("Friends");
				PlayerList.add(client.getUser().getName());
				PlayerList.add("Steve");
			});

			var server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set day");
			server.runCommand("item replace entity @a armor.head with diamond_helmet");
			server.runCommand("item replace entity @a armor.chest with diamond_chestplate[damage=420]");
			server.runCommand("item replace entity @a armor.legs with netherite_leggings");
			server.runCommand("item replace entity @a armor.feet with diamond_boots[damage=380]");
			server.runCommand("item replace entity @a weapon.mainhand with netherite_sword");
			server.runCommand("item replace entity @a weapon.offhand with totem_of_undying");
			server.runCommand("give @a totem_of_undying 4");
			server.runCommand("give @a end_crystal 64");
			server.runCommand("give @a obsidian 64");
			server.runCommand("give @a experience_bottle 64");
			server.runCommand("give @a enchanted_golden_apple 3");
			server.runCommand("give @a splash_potion[potion_contents={potion:\"minecraft:strong_healing\"}] 3");
			server.runCommand("effect give @a speed 90 1");
			server.runCommand("effect give @a strength 40 0");
			server.runCommand("scoreboard objectives add kills dummy \"Kills\"");
			server.runCommand("scoreboard objectives setdisplay sidebar kills");
			server.runCommand("scoreboard objectives setdisplay list kills");
			server.runCommand("scoreboard players set Steve kills 29");
			server.runCommand("scoreboard players set Alex kills 17");
			server.runCommand("scoreboard players set @p kills 3");
			server.runCommand("execute at @p run summon zombie ~ ~ ~3 {NoAI:1b,Silent:1b,PersistenceRequired:1b,"
					+ "equipment:{head:{id:\"minecraft:diamond_helmet\",count:1},offhand:{id:\"minecraft:totem_of_undying\",count:1}}}");
			server.runCommand("tellraw @a \"Eventide chat test\"");
			server.runCommand("tellraw @a \"Eventide chat test\"");
			String longLine = "eventide-long-start " + "the quick brown fox jumps over the lazy dog ".repeat(8) + "eventide-long-end";
			server.runCommand("tellraw @a \"" + longLine + "\"");
			server.runCommand("tellraw @a \"" + longLine + "\"");
			context.runOnClient(client -> {
				client.player.setYRot(0f);
				client.player.setXRot(10f);
				Config.Waypoint ahead = new Config.Waypoint();
				ahead.name = "Base";
				ahead.x = client.player.blockPosition().getX();
				ahead.y = client.player.blockPosition().getY();
				ahead.z = client.player.blockPosition().getZ() + 12;
				ahead.dimension = dev.eventide.client.module.Waypoints.dimension(client);
				dev.eventide.client.module.Waypoints.current(client).add(ahead);
			});
			context.waitTicks(40);
			shot(context, "eventide-hud");
			check(failed, "chat stacks repeated messages", context.computeOnClient(client -> ChatTweaks.repeats() >= 2));
			check(failed, "a stacked repeat takes only its own lines off the chat", context.computeOnClient(client ->
					chatLinesWith(client, "Eventide chat test") == 1 && chatLinesWith(client, "eventide-long-start") == 1
							&& chatLinesWith(client, "eventide-long-end") == 1));
			check(failed, "music HUD shows the playing song", context.computeOnClient(client ->
					MusicBridge.current() != null && MusicBridge.current().title().equals("Snowfall")
							&& HudWidgets.MUSIC.visible(client)));
			check(failed, "music cover uploaded as a texture (NativeImage, DynamicTexture)", Hooks.musicArtUploaded);
			check(failed, "music app icon uploaded as a texture", Hooks.musicIconUploaded);
			check(failed, "menu logo on the HUD with Show in game, clear of the music HUD", context.computeOnClient(client -> {
				int w = client.getWindow().getGuiScaledWidth();
				int h = client.getWindow().getGuiScaledHeight();
				int logoBottom = HudWidgets.MENU_LOGO.position(client, w, h)[1] + HudWidgets.MENU_LOGO.height(client);
				int[] music = HudWidgets.MUSIC.position(client, w, h);
				boolean clear = logoBottom <= music[1] || HudWidgets.MENU_LOGO.position(client, w, h)[0] + HudWidgets.MENU_LOGO.width(client) <= music[0];
				return HudWidgets.MENU_LOGO.shown() && HudWidgets.MENU_LOGO.visible(client) && clear;
			}));
			server.runCommand("bossbar add eventide:test \"Boss\"");
			server.runCommand("bossbar set eventide:test players @a");
			context.waitTicks(10);
			int logoY = context.computeOnClient(client -> HudWidgets.MENU_LOGO.position(client,
					client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight())[1]);
			EventideClient.LOG.info("eventide-check: top-centre HUD top with a boss bar: {}", logoY);
			check(failed, "widgets at the top centre move below boss bars (BossHealthOverlay)", logoY > 17);
			shot(context, "eventide-music-under-bossbar");
			server.runCommand("bossbar remove eventide:test");
			context.setScreen(() -> new MusicShowcase(HudWidgets.MENU_LOGO));
			context.waitTicks(5);
			shot(context, "eventide-menu-logo-closeup");
			context.runOnClient(client -> Modules.MENU_NAME_SHOW.set("Logo and name"));
			context.waitTicks(5);
			shot(context, "eventide-menu-logo-closeup-name");
			context.runOnClient(client -> {
				Modules.MENU_NAME_SHOW.reset();
				Modules.MENU_NAME_HUD.reset();
			});
			check(failed, "menu logo leaves the HUD when Show in game is off", context.computeOnClient(client ->
					!HudWidgets.MENU_LOGO.shown()));
			context.runOnClient(client -> Modules.CPS.sliderSetting("background").set(0));
			context.setScreen(() -> new MusicShowcase(HudWidgets.FPS, HudWidgets.CPS, HudWidgets.PING));
			context.waitTicks(5);
			shot(context, "eventide-stats-closeup-cps-without-glass");
			context.runOnClient(client -> {
				Modules.CPS.sliderSetting("background").reset();
				Modules.THEME_PANEL_COLOR.set(0xFFFF5A5F);
			});
			context.setScreen(() -> new MusicShowcase(HudWidgets.FPS, HudWidgets.CPS, HudWidgets.PING));
			context.waitTicks(5);
			shot(context, "eventide-stats-closeup");
			context.runOnClient(client -> Modules.THEME_PANEL_COLOR.reset());
			context.setScreen(() -> new MusicShowcase(HudWidgets.FPS, HudWidgets.CPS, HudWidgets.PING));
			context.waitTicks(5);
			shot(context, "eventide-stats-closeup-violet");
			context.setScreen(() -> new MusicShowcase(HudWidgets.COORDS, HudWidgets.CLOCK, HudWidgets.TPS));
			context.waitTicks(5);
			shot(context, "eventide-coords-closeup");
			context.setScreen(() -> new MusicShowcase(HudWidgets.TARGET));
			context.waitTicks(15);
			shot(context, "eventide-target-closeup");
			context.setScreen(() -> new MusicShowcase(HudWidgets.POTIONS, HudWidgets.ARMOR));
			context.waitTicks(5);
			shot(context, "eventide-potions-closeup");
			check(failed, "potion effects widget lists your effects", context.computeOnClient(client -> HudWidgets.POTIONS.visible(client)));
			context.setScreen(MusicShowcase::new);
			context.waitTicks(10);
			shot(context, "eventide-music-closeup");
			context.runOnClient(client -> MusicBridge.simulate(PAUSED_SONG));
			context.waitTicks(10);
			shot(context, "eventide-music-closeup-paused");
			context.runOnClient(client -> {
				MusicBridge.simulate(SAMPLE_SONG);
				Modules.MUSIC_COLOR.set(0xFFF58AC8);
			});
			context.waitTicks(10);
			shot(context, "eventide-music-closeup-pink");
			context.runOnClient(client -> Modules.MUSIC_COLOR.reset());
			context.setScreen(() -> new MusicShowcase(HudWidgets.KEYSTROKES));
			context.runOnClient(client -> {
				client.getToastManager().clear();
				Modules.KEYSTROKES.sliderSetting("background").reset();
				client.options.keyAttack.setDown(true);
			});
			context.waitTicks(5);
			shot(context, "eventide-keystrokes-closeup");
			context.runOnClient(client -> {
				client.options.keyAttack.setDown(false);
				client.options.keyUse.setDown(true);
				client.options.keyJump.setDown(true);
				Modules.KEYSTROKES_PRESSED.set(0xFFFF5FA2);
			});
			context.waitTicks(5);
			shot(context, "eventide-keystrokes-closeup-pink");
			check(failed, "keystrokes pressed colour can be changed", Modules.KEYSTROKES_PRESSED.get() == 0xFFFF5FA2);
			context.runOnClient(client -> {
				client.options.keyUse.setDown(false);
				client.options.keyJump.setDown(false);
				Modules.KEYSTROKES_PRESSED.reset();
			});
			List<String> listedPlayers = context.computeOnClient(client -> List.copyOf(PlayerList.names()));
			context.runOnClient(client -> {
				PlayerList.names().clear();
				Modules.THEME_PANEL_COLOR.set(0xFFFF5FA2);
			});
			context.setScreen(() -> new MusicShowcase(HudWidgets.PLAYER_LIST));
			context.waitTicks(5);
			shot(context, "eventide-player-list-closeup");
			context.runOnClient(client -> {
				PlayerList.names().addAll(listedPlayers);
				for (String name : List.of("OGSummer", "BobIsFound", "Frenk_btw", "Munkerlich", "Napooo_", "U_vv")) {
					PlayerList.names().add(name);
				}
			});
			context.waitTicks(3);
			shot(context, "eventide-player-list-closeup-joining");
			context.waitTicks(12);
			shot(context, "eventide-player-list-closeup-more");
			check(failed, "player list shows six and +N MORE", context.computeOnClient(client ->
					PlayerList.names().size() == listedPlayers.size() + 6 && HudWidgets.PLAYER_LIST.visible(client)));
			context.runOnClient(client -> {
				PlayerList.names().clear();
				PlayerList.names().addAll(listedPlayers);
			});
			boolean popsOn = context.computeOnClient(client -> Modules.POPS.enabled());
			context.runOnClient(client -> Modules.POPS.set(false));
			context.setScreen(() -> new MusicShowcase(HudWidgets.POPS));
			context.waitTicks(5);
			shot(context, "eventide-pops-closeup");
			context.runOnClient(client -> Modules.POPS.set(popsOn));
			context.setScreen(() -> new MusicShowcase(HudWidgets.STAFF_LIST));
			context.waitTicks(10);
			shot(context, "eventide-staff-list-closeup");
			context.runOnClient(client -> {
				StaffList.simulate("Frenk_btw", "owner", false);
				StaffList.simulate("Kai", "mod", false);
			});
			context.waitTicks(4);
			shot(context, "eventide-staff-list-closeup-joining");
			context.waitTicks(30);
			context.runOnClient(client -> StaffList.simulate("Kai", "mod", true));
			context.waitTicks(12);
			shot(context, "eventide-staff-list-closeup-spectating");
			check(failed, "staff list sorts by rank and marks spectators", context.computeOnClient(client -> {
				List<StaffList.Staff> staff = StaffList.current(client);
				return staff.size() == 2 && staff.get(0).rank().equals("OWNER") && staff.get(1).spectating();
			}));
			context.runOnClient(client -> {
				StaffList.leave("Frenk_btw");
				StaffList.leave("Kai");
			});
			context.waitTicks(3);
			shot(context, "eventide-staff-list-closeup-leaving");
			context.waitTicks(20);
			shot(context, "eventide-staff-list-closeup-clear");
			check(failed, "staff list drawn", Hooks.staffListDrawn);
			check(failed, "staff ranks read from the tab list", "mod".equals(StaffList.rankOf("[Sr.Mod] Kai"))
					&& "admin".equals(StaffList.rankOf("§c§lADMIN §fAlex")) && StaffList.rankOf("[VIP] Steve") == null);
			context.runOnClient(client -> StaffList.stopSimulating());
			context.setScreen(() -> new MusicShowcase(HudWidgets.MODULE_LIST));
			context.waitTicks(5);
			shot(context, "eventide-module-list-closeup");
			context.runOnClient(client -> Modules.THEME_PANEL_COLOR.reset());
			check(failed, "module list shows the mods that are on", context.computeOnClient(client ->
					HudWidgets.MODULE_LIST.visible(client) && HudWidgets.MODULE_LIST.height(client) >= 8 * 5));
			context.setScreen(() -> null);
			context.waitTicks(5);
			check(failed, "player list sees who is online", context.computeOnClient(client ->
					Hooks.playerListTicked && PlayerList.isOnline(client.getUser().getName()) && !PlayerList.isOnline("Steve")
							&& HudWidgets.PLAYER_LIST.visible(client)));
			context.runOnClient(client -> PlayerList.askAbout(client.getConnection(), "msg", client.getUser().getName()));
			context.waitTicks(20);
			String checks = context.computeOnClient(client -> PlayerList.describeChecks());
			EventideClient.LOG.info("eventide-check: player list server checks: {}", checks);
			check(failed, "player list asks the server who's online (/msg suggestions)", context.computeOnClient(client ->
					Hooks.playerListAskedServer && PlayerList.checkedWithServer("Steve") && !PlayerList.seenByServer("Steve")
							&& PlayerList.seenByServer(client.getUser().getName())));

			skyLyrics(context, singleplayer, failed);
			context.runOnClient(client -> {
				MusicBridge.simulate(SAMPLE_SONG);
				Lyrics.simulate(MusicBridge.current(), "[00:50.00] Snow falls on the quiet town\n[00:55.00] Every light is burning down\n"
						+ "[01:05.00] Hold on, we're almost home\n[01:20.00] Under violet skies we roam");
			});
			context.setScreen(MusicShowcase::new);
			context.waitTicks(12);
			shot(context, "eventide-music-closeup-lyrics");
			context.setScreen(() -> null);
			context.runOnClient(client -> {
				Modules.HOTBAR.set(true);
				client.getToastManager().clear();
			});
			context.waitTicks(5);
			shot(context, "eventide-hotbar");
			check(failed, "the glass hotbar replaces Minecraft's", Hooks.hotbarDrawn);
			check(failed, "the hotbar shows where you're playing", Hooks.serverAddressDrawn);
			context.runOnClient(client -> Modules.HOTBAR_SELECTED.set("Accent"));
			context.getInput().pressKey(InputConstants.KEY_3);
			context.waitTicks(5);
			shot(context, "eventide-hotbar-accent");
			context.getInput().pressKey(InputConstants.KEY_1);
			context.runOnClient(client -> Modules.HOTBAR_SELECTED.reset());
			check(failed, "lyrics read from LRC", Lyrics.parse("[00:01.50] a\n[00:03.00][00:09.00] b").size() == 3);
			context.runOnClient(client -> Modules.LYRICS.set(false));
			knives(context, singleplayer, failed, false);
			killEffects(context, singleplayer, failed);
			worldFx(context, singleplayer, failed);
			cosmetics(context, singleplayer, failed);
			extras(context, singleplayer, failed);
			hudLook(context, singleplayer, failed);
			skies(context, singleplayer, failed, false);
			performance(context, failed);
			context.runOnClient(client -> client.player.setXRot(10f));

			// Pop a totem: chat message, pop counter and the "no totem in offhand" warning
			server.runCommand("damage @p 1000 minecraft:generic");
			context.waitTicks(30);
			shot(context, "eventide-totem-pop");
			check(failed, "totem pop counted", context.computeOnClient(client -> !PopTracker.top(1).isEmpty()));

			// Hit the zombie for the target HUD
			context.runOnClient(client -> {
				client.player.setYRot(0f);
				client.player.setXRot(10f);
			});
			context.waitTicks(5);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(10);
			check(failed, "CPS counts clicks (MouseHandler.onButton)", context.computeOnClient(client -> CpsTracker.left() >= 1));
			context.runOnClient(client -> Modules.SWING_SPEED.set(true));
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(5);
			check(failed, "swing speed changes your arm swing (LivingEntity.getCurrentSwingDuration)", Hooks.swingSpeedRan);
			check(failed, "swing speed slows pulling out an item (ItemInHandRenderer.tick)", Hooks.equipSpeedRan);
			context.runOnClient(client -> Modules.SWING_SPEED.set(false));
			shot(context, "eventide-target-hud");
			check(failed, "target HUD picks up who you hit", context.computeOnClient(client -> Target.get() != null));

			// Name Protect (with text changes) and Skin Protect (with a skin file) stay on through freelook,
			// so the third person view shows them
			context.runOnClient(client -> {
				try {
					java.nio.file.Files.createDirectories(CustomSkins.folder());
					java.nio.file.Files.write(CustomSkins.folder().resolve("ci-skin.png"),
							java.util.Base64.getDecoder().decode(LEGACY_SKIN));
				} catch (java.io.IOException e) {
					throw new RuntimeException(e);
				}
				Config.get().skinFile = "ci-skin.png";
				while (!Modules.SKIN_PROTECT_SOURCE.is(CustomSkins.SOURCE_FILE)) Modules.SKIN_PROTECT_SOURCE.next();
				Modules.NAME_PROTECT.set(true);
				Modules.NAME_PROTECT_NAME.set("Streamer");
				TextRules.add("Money: #", "Money: 1T");
				TextRules.add("vhys", "team");
				Modules.SKIN_PROTECT.set(true);
				Compat.chat(net.minecraft.network.chat.Component.literal(client.getUser().getName() + " is streaming"));
				Compat.chat(net.minecraft.network.chat.Component.literal("Money: 500.9T  Team: vhys"));
			});
			context.waitTicks(10);
			shot(context, "eventide-name-protect");
			check(failed, "name protect swaps your name", context.computeOnClient(client ->
					NameProtect.apply("<" + client.getUser().getName() + "> hi").equals("<Streamer> hi")
							&& NameProtect.apply(client.getUser().getName() + "son").equals(client.getUser().getName() + "son")));
			check(failed, "name protect hook (StringDecomposer)", Hooks.nameProtectRan);
			check(failed, "text changes apply to plain text", context.computeOnClient(client ->
					dev.eventide.client.module.NameProtect.apply("Money: 500.9T, Team: vhys").equals("Money: 1T, Team: team")));
			check(failed, "text changes apply across coloured pieces", context.computeOnClient(client -> {
				var line = net.minecraft.network.chat.Component.literal("Money: ").withStyle(net.minecraft.ChatFormatting.YELLOW)
						.append(net.minecraft.network.chat.Component.literal("500.9T").withStyle(net.minecraft.ChatFormatting.GREEN));
				return TextRules.apply(line).getString().equals("Money: 1T") && Hooks.textRuleRan;
			}));
			// A swapped name longer or shorter than the real one, in text that gets wrapped and cut, like
			// the text floating above players on CrystalPractice (which crashed Eventide 0.4.1)
			for (String shownAs : new String[] {"A much longer streamer name", "Me"}) {
				context.runOnClient(client -> Modules.NAME_PROTECT_NAME.set(shownAs));
				context.waitTicks(2);
				String problem = context.computeOnClient(EventideGameTest::nameWrapProblem);
				EventideClient.LOG.info("eventide-check: wrapping text with your name shown as \"{}\": {}", shownAs,
						problem == null ? "fine" : problem);
				check(failed, "name protect with a " + (shownAs.length() > 8 ? "longer" : "shorter") + " name wraps and cuts text safely",
						problem == null);
			}
			context.runOnClient(client -> Modules.NAME_PROTECT_NAME.set("A much longer streamer name"));
			context.runOnClient(client -> client.player.setXRot(0f));
			context.waitTicks(2);
			String ownName = context.computeOnClient(client -> client.getUser().getName());
			server.runCommand("execute at @p run summon text_display ^ ^1.6 ^3 {line_width:40,billboard:\"center\",text:\"[Practice] "
					+ ownName + " - 12 kills - " + ownName + "\"}");
			context.waitTicks(10);
			shot(context, "eventide-name-protect-wrapped");
			check(failed, "floating text with your name in it draws (text display, line wrapping)",
					context.computeOnClient(client -> Hooks.wrapProtectRan && client.level != null));
			server.runCommand("kill @e[type=text_display]");
			nameDetect(context, singleplayer, failed);
			context.runOnClient(client -> {
				Modules.NAME_PROTECT_NAME.set("Streamer");
				client.player.setXRot(10f);
			});
			context.waitTicks(2);
			check(failed, "skin protect wears a 64x32 skin file", context.computeOnClient(client ->
					Hooks.customSkinLoaded && CustomSkins.current() != null));

			int normalFov = context.computeOnClient(client -> client.options.fov().get());
			context.getInput().holdKey(EventideClient.zoomKey);
			context.waitTicks(20);
			shot(context, "eventide-zoom");
			int zoomedFov = context.computeOnClient(client -> client.options.fov().get());
			context.getInput().releaseKey(EventideClient.zoomKey);
			context.waitTicks(20);
			int afterFov = context.computeOnClient(client -> client.options.fov().get());
			EventideClient.LOG.info("eventide-check: zoom FOV {} -> {} -> {}", normalFov, zoomedFov, afterFov);
			check(failed, "zoom lowers the FOV", zoomedFov < normalFov);
			check(failed, "zoom gives the FOV back", afterFov == normalFov);

			// Freelook: third person while held, back to first person after
			context.getInput().holdKey(EventideClient.freelookKey);
			context.waitTicks(10);
			shot(context, "eventide-freelook");
			check(failed, "freelook switches to third person", context.computeOnClient(client ->
					Freelook.active() && client.options.getCameraType() == CameraType.THIRD_PERSON_BACK));
			context.getInput().releaseKey(EventideClient.freelookKey);
			context.waitTicks(5);
			check(failed, "freelook puts the camera back", context.computeOnClient(client ->
					!Freelook.active() && client.options.getCameraType() == CameraType.FIRST_PERSON));
			check(failed, "skin protect hook (PlayerInfo.getSkin)", Hooks.skinProtectRan);
			context.runOnClient(client -> {
				Modules.SKIN_PROTECT_PLAYER.set("jeb_");
				while (!Modules.SKIN_PROTECT_SOURCE.is(CustomSkins.SOURCE_PLAYER)) Modules.SKIN_PROTECT_SOURCE.next();
			});
			context.waitTicks(100);
			String playerSkinStatus = context.computeOnClient(client -> CustomSkins.status());
			EventideClient.LOG.info("eventide-check: skin by player name: {}", playerSkinStatus);
			context.runOnClient(client -> {
				Modules.NAME_PROTECT.set(false);
				Modules.SKIN_PROTECT.set(false);
			});
			nametagCloseup(context, singleplayer, failed);

			// Tab list with ping numbers and the scoreboard objective
			context.getInput().holdKey(options -> options.keyPlayerList);
			context.waitTicks(5);
			shot(context, "eventide-tab-list");
			context.getInput().releaseKey(options -> options.keyPlayerList);

			// The scoreboard made bigger and blue
			context.runOnClient(client -> {
				Modules.SCOREBOARD_SIZE.set(150);
				Modules.SCOREBOARD_BACKGROUND.set(65);
				Modules.SCOREBOARD_COLOR.set(0xFF1A4FBA);
			});
			context.waitTicks(5);
			shot(context, "eventide-scoreboard-styled");
			context.runOnClient(client -> {
				Modules.SCOREBOARD_BACKGROUND.reset();
				Modules.SCOREBOARD_COLOR.reset();
			});

			// Inventory with highlighted PvP items
			context.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
			context.waitTicks(10);
			shot(context, "eventide-inventory-highlight");
			check(failed, "name tag in the corner of the inventory", Hooks.menuNameDrawn);
			context.setScreen(() -> null);
			context.waitTicks(5);

			context.runOnClient(client -> {
				TextHotkeys.add(GLFW.GLFW_KEY_F6, false, "/me Eventide hotkey command");
				TextHotkeys.add(GLFW.GLFW_KEY_G, false, "Eventide hotkey chat");
				TextHotkeys.add(GLFW.GLFW_MOUSE_BUTTON_4, true, "gg");
				TextHotkeys.update(client, hotkey -> false);
				TextHotkeys.update(client, hotkey -> !hotkey.mouse && hotkey.key == GLFW.GLFW_KEY_F6);
				TextHotkeys.update(client, hotkey -> !hotkey.mouse && hotkey.key == GLFW.GLFW_KEY_F6);
				TextHotkeys.update(client, hotkey -> false);
				TextHotkeys.update(client, hotkey -> !hotkey.mouse && hotkey.key == GLFW.GLFW_KEY_G);
			});
			context.waitTicks(10);
			check(failed, "text hotkeys run a command once per press", context.computeOnClient(client ->
					chatLinesWith(client, "Eventide hotkey command") == 1));
			check(failed, "text hotkeys send a chat message", context.computeOnClient(client ->
					chatLinesWith(client, "Eventide hotkey chat") == 1));
			check(failed, "text hotkeys turn the mod on and name their keys", context.computeOnClient(client ->
					Modules.TEXT_HOTKEYS.enabled() && Config.get().textHotkeys.size() == 3
							&& TextHotkeys.keyName(GLFW.GLFW_KEY_F6, false).equals("F6")
							&& TextHotkeys.keyName(GLFW.GLFW_MOUSE_BUTTON_4, true).equals("Mouse 4")));

			menu(context, failed);
			context.setScreen(() -> new HudEditorScreen(null));
			context.waitTicks(10);
			shot(context, "eventide-edit-hud");
			double sizeBefore = context.computeOnClient(client -> Modules.SCOREBOARD_SIZE.get());
			context.runOnClient(client -> {
				var widget = HudWidgets.SCOREBOARD;
				float scale = dev.eventide.client.hud.HudRenderer.scaleFor(widget);
				int screenW = client.getWindow().getGuiScaledWidth();
				int screenH = client.getWindow().getGuiScaledHeight();
				int[] pos = widget.position(client, Math.round(screenW / scale), Math.round(screenH / scale));
				double x = (pos[0] + widget.width(client) / 2.0) * scale;
				double y = (pos[1] + widget.height(client) / 2.0) * scale;
				client.screen.mouseScrolled(x, y, 0, -1);
			});
			check(failed, "scrolling over the scoreboard in the HUD editor resizes it",
					context.computeOnClient(client -> Modules.SCOREBOARD_SIZE.get() < sizeBefore));
			context.runOnClient(client -> Modules.SCOREBOARD_SIZE.reset());

			for (Category category : List.of(Category.COMBAT, Category.HUD, Category.COSMETICS, Category.CLIENT)) {
				context.setScreen(() -> new MenuScreen(null, category));
				context.waitTicks(25);
				shot(context, "eventide-menu-" + category.label.toLowerCase(java.util.Locale.ROOT));
			}
			context.runOnClient(client -> TextRules.add("Kills: #", "Kills: 9999"));
			for (Module module : List.of(Modules.CROSSHAIR, Modules.PLAYER_LIST, Modules.NAME_PROTECT, Modules.TEXT_HOTKEYS,
					Modules.SKIN_PROTECT, Modules.WAYPOINTS, Modules.MUSIC, Modules.LYRICS, Modules.THEME)) {
				context.setScreen(() -> MenuScreen.settings(null, module));
				context.waitTicks(15);
				shot(context, "eventide-menu-" + module.id.replace('_', '-'));
			}
			context.runOnClient(client -> List.copyOf(Config.get().textHotkeys).forEach(TextHotkeys::remove));
			context.runOnClient(client -> Config.get().createProfile("Crystal PvP"));
			context.setScreen(() -> MenuScreen.profiles(null));
			context.waitTicks(20);
			shot(context, "eventide-menu-profiles");
			check(failed, "profiles can be created", context.computeOnClient(client ->
					Config.get().profile.equals("Crystal PvP") && Config.get().profileNames().contains(Config.DEFAULT_PROFILE)));
			context.runOnClient(client -> Config.get().loadProfile(Config.DEFAULT_PROFILE));
			context.runOnClient(client -> Modules.THEME_PANEL_COLOR.set(0xFF5BC8E6));
			context.setScreen(() -> new MenuScreen(null, Category.RENDER));
			context.waitTicks(20);
			shot(context, "eventide-menu-cyan");
			context.runOnClient(client -> {
				Modules.THEME_PANEL_COLOR.reset();
				Modules.THEME_FONT.set("Minecraft");
			});
			context.setScreen(() -> new MenuScreen(null, Category.RENDER));
			context.waitTicks(20);
			shot(context, "eventide-menu-minecraft-font");
			context.runOnClient(client -> Modules.THEME_FONT.reset());

			// Pause menu: the Eventide button, then Confirm Disconnect
			context.setScreen(() -> null);
			context.waitTicks(5);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);
			shot(context, "eventide-pause-menu");
			int[] eventideButton = context.computeOnClient(client -> {
				AbstractWidget ours = null;
				AbstractWidget stats = null;
				AbstractWidget disconnect = null;
				AbstractWidget modMenu = null;
				int eventideButtons = 0;
				for (AbstractWidget widget : Compat.widgets(client.screen)) {
					if (!widget.visible) continue;
					String label = widget.getMessage().getString();
					if (label.equals(PauseMenuButton.LABEL)) ours = widget;
					if (label.startsWith("Eventide")) eventideButtons++;
					if (label.equals("Mods")) modMenu = widget;
					if (ConfirmDisconnect.isDisconnect(widget.getMessage())) disconnect = widget;
					if (widget.getMessage().getContents() instanceof TranslatableContents key && key.getKey().equals("gui.stats")) stats = widget;
				}
				if (ours == null || stats == null || disconnect == null) return null;
				AbstractWidget above = modMenu != null && modMenu.getY() > stats.getY() && modMenu.getY() < ours.getY() ? modMenu : stats;
				boolean row = ours.getY() == above.getY() + above.getHeight() + 4 && ours.getX() == disconnect.getX() && ours.getWidth() == disconnect.getWidth();
				return new int[] {ours.getX() + ours.getWidth() / 2, ours.getY() + ours.getHeight() / 2, row ? 1 : 0, eventideButtons};
			});
			check(failed, "the pause menu has one Eventide button, a full row under Advancements and Statistics",
					eventideButton != null && eventideButton[2] == 1 && eventideButton[3] == 1);
			if (eventideButton != null) {
				int pauseScale = context.computeOnClient(client -> client.getWindow().getGuiScale());
				context.getInput().setCursorPos(eventideButton[0] * pauseScale, eventideButton[1] * pauseScale);
				context.waitTicks(2);
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
				context.waitTicks(15);
				check(failed, "the pause menu's Eventide button opens the menu", context.computeOnClient(client -> client.screen instanceof MenuScreen));
				context.getInput().pressKey(InputConstants.KEY_ESCAPE);
				context.waitTicks(5);
			}
			context.getInput().pressKey(EventideClient.openSettingsKey);
			context.waitTicks(15);
			shot(context, "eventide-menu-from-pause");
			check(failed, "Right Shift on the pause menu opens the menu", context.computeOnClient(client -> client.screen instanceof MenuScreen));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);
			check(failed, "closing the menu goes back to the pause menu", context.computeOnClient(client -> client.screen instanceof PauseScreen));
			int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());

			int[] quit = context.computeOnClient(client -> {
				for (AbstractWidget widget : Compat.widgets(client.screen)) {
					if (widget.getMessage().getContents() instanceof TranslatableContents key
							&& (key.getKey().equals("menu.returnToMenu") || key.getKey().equals("menu.disconnect"))) {
						return new int[] {widget.getX() + widget.getWidth() / 2, widget.getY() + widget.getHeight() / 2};
					}
				}
				return null;
			});
			if (quit != null) {
				context.getInput().setCursorPos(quit[0] * scale, quit[1] * scale);
				context.waitTicks(2);
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
				context.waitTicks(5);
				shot(context, "eventide-confirm-disconnect");
				check(failed, "disconnect needs a second click", context.computeOnClient(client ->
						client.screen instanceof PauseScreen && client.level != null && ConfirmDisconnect.waiting()));
			} else {
				check(failed, "found the pause menu's quit button", false);
			}
			context.setScreen(() -> null);
			context.waitTicks(5);
		}

		context.waitTicks(10);
		check(failed, "Eventide main menu after leaving a world",
				context.computeOnClient(client -> client.screen instanceof TitleScreen));
		int freezesBefore = FreezeWatch.freezes();
		context.runOnClient(client -> {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		context.waitTicks(10);
		check(failed, "freeze watch logs what a frozen game was doing", FreezeWatch.freezes() > freezesBefore);
		check(failed, "window title hook (Minecraft.updateTitle)", Hooks.titleHookRan);
		String title = dev.eventide.client.WindowBranding.title();
		EventideClient.LOG.info("eventide-check: window title is \"{}\"", title);
		check(failed, "window title starts with Minecraft, for Medal", title.startsWith("Minecraft"));
		check(failed, "tab list ping numbers (PlayerTabOverlay)", Hooks.tabPingRan);
		check(failed, "item highlighter (AbstractContainerScreen slots)", Hooks.slotHighlightRan);
		check(failed, "freelook camera (Camera.setRotation)", Hooks.freelookCameraRan);
		check(failed, "own nametag in third person (shouldShowName)", Hooks.ownNametagRan);
		check(failed, "nametags drawn in Eventide's style (submitNameTag)", Hooks.styledNametagRan);
		check(failed, "your name tag carries the Eventide Badge (AvatarRenderer.extractRenderState)", Hooks.badgeShown);
		check(failed, "shiny pots (ItemStack.hasFoil)", Hooks.shinyPotRan);
		check(failed, "TPS from server time (handleSetTime)", Hooks.timePacketRan);
		check(failed, "custom crosshair replaces Minecraft's", Hooks.crosshairRan);
		check(failed, "scoreboard drawn through Eventide", Hooks.scoreboardRan);
		check(failed, "totem pop hook (ClientPacketListener.handleEntityEvent)", Hooks.entityEventHookRan);
		if (!failed.isEmpty()) {
			throw new AssertionError("Eventide checks that failed: " + String.join(", ", failed));
		}
	}

	/**
	 * The mod menu in a world: on screen (the sidebar, cards, keyboard, search and a settings panel), then
	 * floating in the air (drawn in the world, clicked through the panel, seen from the side).
	 */
	private static void menu(ClientGameTestContext context, List<String> failed) {
		int menuScale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.setScreen(() -> null);
		context.waitTicks(3);
		context.getInput().pressKey(EventideClient.openSettingsKey);
		context.waitTicks(30);
		check(failed, "Right Shift opens the menu", context.computeOnClient(client ->
				client.screen instanceof MenuScreen menu && menu.view().category() == Category.RENDER));
		shot(context, "eventide-menu");
		boolean zoomBefore = context.computeOnClient(client -> Modules.ZOOM.enabled());
		float[] zoomCard = context.computeOnClient(client -> ((MenuScreen) client.screen).view().cardCenter(Modules.ZOOM));
		check(failed, "the menu lays out a card for each module", zoomCard != null);
		if (zoomCard != null) {
			context.getInput().setCursorPos(zoomCard[0] * menuScale, zoomCard[1] * menuScale);
			context.waitTicks(2);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(5);
			check(failed, "clicking a card switches its module", context.computeOnClient(client -> Modules.ZOOM.enabled() != zoomBefore));
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(5);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitTicks(15);
			check(failed, "right-clicking a card opens its settings", context.computeOnClient(client ->
					client.screen instanceof MenuScreen menu && menu.view().sheetModule() == Modules.ZOOM));
			shot(context, "eventide-menu-zoom-settings");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);
		}
		check(failed, "zoom is back the way it was", context.computeOnClient(client -> Modules.ZOOM.enabled() == zoomBefore));
		check(failed, "the mouse leaves no keyboard focus behind", context.computeOnClient(client ->
				client.screen instanceof MenuScreen menu && menu.view().focused() == null));
		context.getInput().pressKey(InputConstants.KEY_DOWN);
		context.getInput().pressKey(InputConstants.KEY_RIGHT);
		context.waitTicks(5);
		check(failed, "the arrow keys move between cards", context.computeOnClient(client ->
				client.screen instanceof MenuScreen menu && menu.view().focused() != null));
		shot(context, "eventide-menu-keyboard");
		Module focusedCard = context.computeOnClient(client -> ((MenuScreen) client.screen).view().focused());
		boolean focusedBefore = focusedCard != null && context.computeOnClient(client -> focusedCard.enabled());
		context.getInput().pressKey(InputConstants.KEY_SPACE);
		context.waitTicks(5);
		check(failed, "Space switches the card the keyboard is on", focusedCard != null && context.computeOnClient(client ->
				focusedCard.enabled() != focusedBefore));
		context.getInput().pressKey(InputConstants.KEY_SPACE);
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(15);
		check(failed, "Enter opens the settings of the card the keyboard is on", context.computeOnClient(client ->
				client.screen instanceof MenuScreen menu && menu.view().sheetModule() == focusedCard));
		context.getInput().pressKey(InputConstants.KEY_DOWN);
		context.waitTicks(5);
		shot(context, "eventide-menu-settings-keyboard");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);
		context.getInput().pressKey(InputConstants.KEY_TAB);
		context.waitTicks(10);
		check(failed, "Tab moves to the next section", context.computeOnClient(client ->
				client.screen instanceof MenuScreen menu && menu.view().category() == Category.PLAYER));
		context.getInput().typeChars("totem");
		context.waitTicks(10);
		check(failed, "typing in the menu searches", context.computeOnClient(client ->
				client.screen instanceof MenuScreen menu && menu.view().searchText().equals("totem")));
		shot(context, "eventide-menu-search");
		context.setScreen(() -> MenuScreen.settings(null, Modules.CROSSHAIR));
		context.waitTicks(15);
		float[] origin = context.computeOnClient(client -> ((MenuScreen) client.screen).view().sheetContent());
		shot(context, "eventide-menu-crosshair");
		context.setScreen(() -> MenuScreen.settings(null, Modules.KNIVES));
		context.waitTicks(15);
		shot(context, "eventide-menu-knives-settings");
		check(failed, "a settings panel lays out its rows", origin != null && origin[1] > 0);

		context.runOnClient(client -> Modules.THEME_MENU_MODE.set("Floating"));
		context.setScreen(() -> null);
		context.waitTicks(3);
		context.getInput().pressKey(EventideClient.openSettingsKey);
		context.waitTicks(40);
		check(failed, "Theme > Menu display > Floating opens the menu in the world", context.computeOnClient(client ->
				client.screen instanceof FloatingMenuScreen));
		check(failed, "the floating menu is drawn in the world (GameRenderer)", context.computeOnClient(client -> FloatingMenu.drawnFrames() > 0));
		shot(context, "eventide-menu-floating");
		boolean floatZoom = context.computeOnClient(client -> Modules.ZOOM.enabled());
		float[] floatCard = context.computeOnClient(client -> {
			float[] layout = ((FloatingMenuScreen) client.screen).view().cardCenter(Modules.ZOOM);
			return layout == null ? null : FloatingMenu.project(layout[0], layout[1]);
		});
		check(failed, "the floating menu lays out its cards", floatCard != null);
		if (floatCard != null) {
			context.getInput().setCursorPos(floatCard[0] * menuScale, floatCard[1] * menuScale);
			context.waitTicks(3);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(5);
			check(failed, "clicking a card on the floating menu switches its module", context.computeOnClient(client ->
					Modules.ZOOM.enabled() != floatZoom));
			context.runOnClient(client -> Modules.ZOOM.set(floatZoom));
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitTicks(15);
			check(failed, "right-clicking a floating card opens its settings", context.computeOnClient(client ->
					client.screen instanceof FloatingMenuScreen menu && menu.view().sheetModule() == Modules.ZOOM));
			shot(context, "eventide-menu-floating-settings");
		}
		float yawBefore = context.computeOnClient(client -> client.player.getYRot());
		context.runOnClient(client -> client.player.turn(-260, 40));
		context.waitTicks(10);
		check(failed, "you can look around the floating menu", context.computeOnClient(client -> client.player.getYRot() != yawBefore));
		shot(context, "eventide-menu-floating-side");
		context.getInput().pressKey(InputConstants.KEY_R);
		context.waitTicks(10);
		shot(context, "eventide-menu-floating-recentred");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(10);
		check(failed, "Esc closes the floating menu", context.computeOnClient(client -> client.screen == null));
		context.runOnClient(client -> {
			Modules.THEME_MENU_MODE.reset();
			client.player.turn(260, -40);
		});
		context.waitTicks(5);
	}

	/**
	 * Kill Effects: a zombie you hit dies and its effect plays (from the server's death event); then each effect
	 * is pictured on its own in front of you.
	 */
	private static void killEffects(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<String> failed) {
		var server = singleplayer.getServer();
		server.runCommand("time set midnight");
		context.runOnClient(client -> {
			Modules.KILL_EFFECTS.set(true);
			Modules.KILL_EFFECTS_MOBS.set(true);
			client.getToastManager().clear();
		});
		face(context, 0, 10);
		server.runCommand("execute as @p at @s run summon zombie ^ ^ ^4 {NoAI:1b,Silent:1b,Tags:[\"eventide_kill\"]}");
		context.waitTicks(10);
		context.runOnClient(client -> {
			for (var entity : client.level.entitiesForRendering()) {
				if (entity.getType() == net.minecraft.world.entity.EntityType.ZOMBIE) dev.eventide.client.module.KillEffects.onAttack(entity);
			}
		});
		server.runCommand("kill @e[tag=eventide_kill]");
		context.waitTicks(4);
		check(failed, "a mob you hit dying plays its kill effect (death event)", context.computeOnClient(client ->
				Hooks.killEffectRan && dev.eventide.client.module.KillEffects.running() > 0));
		shot(context, "eventide-kill-effect");
		context.waitTicks(40);
		String[] names = {"nebula", "soul", "starfall", "thunder", "shatter"};
		for (int i = 0; i < names.length; i++) {
			int kind = i;
			context.runOnClient(client -> {
				var look = client.player.getLookAngle();
				dev.eventide.client.module.KillEffects.start(kind, client.player.getX() + look.x * 4, client.player.getY(), client.player.getZ() + look.z * 4, 1.9);
			});
			context.waitTicks(kind == 2 ? 11 : kind == 1 ? 14 : 3);
			shot(context, "eventide-kill-effect-" + names[i]);
			context.waitTicks(30);
		}
		context.runOnClient(client -> {
			Modules.KILL_EFFECTS.set(false);
			Modules.KILL_EFFECTS_MOBS.reset();
		});
		server.runCommand("time set noon");
	}

	/**
	 * Heaven lifting a body, Hit Glow on a mob you hit, the Weather Changer's rain with Ripple's rings, a jump
	 * circle, and Hand Aura round an empty hand.
	 */
	private static void worldFx(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<String> failed) {
		var server = singleplayer.getServer();
		server.runCommand("time set midnight");
		server.runCommand("weather clear");
		check(failed, "the server passes Eventide's skin-settings bit on, so other Eventide players can see the badge", context.computeOnClient(client ->
				(client.player.getEntityData().get(dev.eventide.client.mixin.AvatarAccessor.eventide$customisation())
						& dev.eventide.client.module.EventideBadge.MARK) != 0));
		context.runOnClient(client -> {
			Modules.KILL_EFFECTS.set(true);
			Modules.KILL_EFFECTS_MOBS.set(true);
			Modules.KILL_EFFECTS_STYLE.set("Heaven");
			client.getToastManager().clear();
		});
		face(context, 0, 5);
		server.runCommand("execute as @p at @s run summon zombie ^ ^ ^4 {NoAI:1b,Silent:1b,Tags:[\"eventide_heaven\"]}");
		context.waitTicks(10);
		context.runOnClient(client -> {
			for (var entity : client.level.entitiesForRendering()) {
				if (entity.getType() == net.minecraft.world.entity.EntityType.ZOMBIE) {
					dev.eventide.client.module.KillEffects.onAttack(entity);
					dev.eventide.client.module.HitGlow.onAttack(entity);
				}
			}
		});
		server.runCommand("damage @e[tag=eventide_heaven,limit=1] 2 minecraft:player_attack");
		context.waitTicks(2);
		shot(context, "eventide-hit-glow");
		check(failed, "a mob you hit glows in your colour (Minecraft.shouldEntityAppearGlowing, Entity.getTeamColor)", Hooks.hitGlowShown);
		context.waitTicks(15);
		server.runCommand("kill @e[tag=eventide_heaven]");
		context.waitTicks(12);
		shot(context, "eventide-kill-heaven");
		check(failed, "Heaven lifts the body into the light (LivingEntityRenderer.extractRenderState)", Hooks.heavenLifted);
		context.waitTicks(40);
		context.runOnClient(client -> {
			Modules.KILL_EFFECTS.set(false);
			Modules.KILL_EFFECTS_MOBS.reset();
			Modules.KILL_EFFECTS_STYLE.reset();
			Modules.WEATHER.set(true);
			Modules.RIPPLE.set(true);
		});
		face(context, 20, 35);
		context.waitTicks(40);
		check(failed, "Weather Changer rains on your screen while the server's weather stays clear", context.computeOnClient(client ->
				client.level.getRainLevel(1) > 0.9f && !client.getSingleplayerServer().overworld().isRaining()));
		check(failed, "Ripple rings spread on the ground in the rain", context.computeOnClient(client -> dev.eventide.client.module.GroundFx.rings() > 0));
		shot(context, "eventide-rain-ripples");
		context.runOnClient(client -> Modules.WEATHER_TYPE.set("Snow"));
		context.waitTicks(30);
		shot(context, "eventide-weather-snow");
		context.runOnClient(client -> {
			Modules.WEATHER.set(false);
			Modules.WEATHER_TYPE.reset();
			Modules.RIPPLE.set(false);
			Modules.JUMP_CIRCLES.set(true);
		});
		context.waitTicks(25);
		context.runOnClient(client -> {
			var look = client.player.getLookAngle();
			dev.eventide.client.module.GroundFx.jumpAt(client.player.getX() + look.x * 3, client.player.getY(), client.player.getZ() + look.z * 3);
		});
		context.waitTicks(5);
		shot(context, "eventide-jump-circle");
		check(failed, "a jump circle opens on the ground", Hooks.jumpCircleShown);
		context.runOnClient(client -> Modules.JUMP_CIRCLES.set(false));
		server.runCommand("item replace entity @p hotbar.8 with minecraft:air");
		context.getInput().pressKey(GLFW.GLFW_KEY_9);
		face(context, 0, 10);
		context.waitTicks(20);
		shot(context, "eventide-hand-aura");
		check(failed, "Hand Aura glows round an empty hand (ItemInHandRenderer.renderPlayerArm)", Hooks.handAuraDrawn);
		context.getInput().pressKey(GLFW.GLFW_KEY_1);

		context.runOnClient(client -> Modules.NO_PARTICLES.set(true));
		check(failed, "No Particles keeps particles off the screen, but lets Eventide's own through (ParticleEngine.createParticle)",
				context.computeOnClient(client -> {
					boolean blocked = client.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.FLAME, client.player.getX(),
							client.player.getY() + 1, client.player.getZ(), 0, 0, 0) == null;
					boolean[] ours = {false};
					dev.eventide.client.module.ParticleFilter.ours(() -> ours[0] = client.particleEngine.createParticle(
							net.minecraft.core.particles.ParticleTypes.FLAME, client.player.getX(), client.player.getY() + 1, client.player.getZ(), 0, 0, 0) != null);
					return blocked && ours[0];
				}));
		context.runOnClient(client -> {
			Modules.NO_PARTICLES.set(false);
			Modules.NO_EXPLOSION_PARTICLES.set(true);
		});
		check(failed, "No Explosion Particles hides explosions only", context.computeOnClient(client ->
				client.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION, client.player.getX(), client.player.getY() + 1,
						client.player.getZ(), 0, 0, 0) == null && client.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.FLAME,
						client.player.getX(), client.player.getY() + 1, client.player.getZ(), 0, 0, 0) != null));
		context.runOnClient(client -> {
			Modules.NO_EXPLOSION_PARTICLES.set(false);
			Modules.FPS_CAP.set(true);
			Modules.FPS_CAP_LIMIT.set(60);
		});
		check(failed, "FPS Cap caps the frame rate (FramerateLimitTracker)", context.computeOnClient(client ->
				client.getFramerateLimitTracker().getFramerateLimit() <= 60));
		context.runOnClient(client -> {
			Modules.FPS_CAP.set(false);
			Modules.FPS_CAP_LIMIT.reset();
			Modules.COLOUR_GRADING.set(true);
			Modules.BLOCK_OUTLINE.set(true);
		});
		server.runCommand("execute as @p at @s run setblock ^ ^ ^2 minecraft:stone");
		face(context, 0, 30);
		context.waitTicks(10);
		check(failed, "Colour Grading puts its look on the picture (GameRenderer.setPostEffect)", context.computeOnClient(client ->
				client.gameRenderer.currentPostEffect() != null && client.gameRenderer.currentPostEffect().getNamespace().equals(EventideClient.MOD_ID)));
		shot(context, "eventide-colour-grading");
		context.runOnClient(client -> Modules.COLOUR_GRADING_PRESET.set("Noir"));
		context.waitTicks(5);
		shot(context, "eventide-colour-grading-noir");
		context.runOnClient(client -> {
			Modules.COLOUR_GRADING.set(false);
			Modules.COLOUR_GRADING_PRESET.reset();
		});
		context.waitTicks(3);
		check(failed, "turning Colour Grading off takes its look away", context.computeOnClient(client -> client.gameRenderer.currentPostEffect() == null));
		server.runCommand("execute as @p at @s run setblock ^ ^ ^2 minecraft:air");
		context.runOnClient(client -> {
			Modules.BLOCK_OUTLINE.set(false);
			Modules.DEATH_DISINTEGRATE.set(true);
		});
		face(context, 0, 5);
		server.runCommand("execute as @p at @s run summon zombie ^ ^ ^4 {NoAI:1b,Silent:1b,Tags:[\"eventide_dust\"]}");
		context.waitTicks(10);
		server.runCommand("kill @e[tag=eventide_dust]");
		context.waitTicks(8);
		shot(context, "eventide-death-disintegrate");
		check(failed, "Death Disintegrate crumbles the body away", Hooks.disintegrated);
		context.runOnClient(client -> Modules.DEATH_DISINTEGRATE.set(false));
		server.runCommand("time set noon");
	}

	/**
	 * Your player seen from the front in third person: each 3D skin with its weapon, then the hats and the wings
	 * on your own skin, and your knife in your hand.
	 */
	private static void cosmetics(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<String> failed) {
		var server = singleplayer.getServer();
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		List<Module> hudOn = context.computeOnClient(client -> {
			List<Module> on = new ArrayList<>();
			for (Module module : Modules.ALL) {
				if (module.category == Category.HUD && module.enabled()) on.add(module);
			}
			return on;
		});
		context.runOnClient(client -> {
			client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
			for (Module module : hudOn) module.set(false);
			client.getToastManager().clear();
		});
		face(context, 0, 15);
		context.getInput().pressKey(GLFW.GLFW_KEY_1);
		context.runOnClient(client -> Modules.MODEL_SKIN.set(true));
		for (String skin : dev.eventide.client.knife.ModelSkins.SKINS) {
			context.runOnClient(client -> Modules.MODEL_SKIN_TYPE.set(skin));
			context.waitTicks(10);
			shot(context, "eventide-skin-" + skin.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'));
		}
		skinCloseups(context);
		check(failed, "a 3D skin is drawn on your player (PlayerModel.setupAnim, the cosmetics layer)", Hooks.modelSkinDrawn);
		context.runOnClient(client -> {
			Modules.MODEL_SKIN.set(false);
			Modules.HATS.set(true);
			Modules.WINGS.set(true);
		});
		String[] wings = dev.eventide.client.knife.Cosmetics.WINGS;
		String[] hats = dev.eventide.client.knife.Cosmetics.HATS;
		for (int i = 0; i < hats.length; i++) {
			String hat = hats[i];
			String wing = wings[i % wings.length];
			context.runOnClient(client -> {
				Modules.HATS_TYPE.set(hat);
				Modules.WINGS_TYPE.set(wing);
			});
			context.waitTicks(8);
			shot(context, "eventide-cosmetics-" + hat.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'));
		}
		check(failed, "hats and wings are drawn on your player", Hooks.cosmeticsDrawn);
		context.runOnClient(client -> {
			Modules.HATS.set(false);
			client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		});
		for (String wing : wings) {
			context.runOnClient(client -> Modules.WINGS_TYPE.set(wing));
			context.waitTicks(6);
			shot(context, "eventide-wings-" + wing.toLowerCase(java.util.Locale.ROOT) + "-back");
		}
		context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
		context.runOnClient(client -> {
			Modules.HATS.set(false);
			Modules.WINGS.set(false);
			Modules.KNIVES.set(true);
			client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		});
		face(context, 30, 20);
		context.waitTicks(10);
		shot(context, "eventide-knife-third-person");
		check(failed, "your knife shows in your hand in third person", Hooks.knifeThirdPerson);
		context.runOnClient(client -> {
			client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
			for (Module module : hudOn) module.set(true);
		});
	}

	/** Each 3D skin up close on grey (SkinShowcase), holding its weapon, to set beside its reference picture. */
	private static void skinCloseups(ClientGameTestContext context) {
		boolean was = context.computeOnClient(client -> Modules.MODEL_SKIN.enabled());
		context.runOnClient(client -> {
			Modules.MODEL_SKIN.set(true);
			client.getToastManager().clear();
		});
		for (String skin : dev.eventide.client.knife.ModelSkins.SKINS) {
			context.runOnClient(client -> Modules.MODEL_SKIN_TYPE.set(skin));
			context.setScreen(SkinShowcase::new);
			context.waitTicks(4);
			shot(context, "eventide-skin-" + skin.toLowerCase(java.util.Locale.ROOT).replace(' ', '-') + "-closeup");
		}
		context.setScreen(() -> null);
		context.runOnClient(client -> Modules.MODEL_SKIN.set(was));
	}

	/** Sword Models, Item Physics, Shulker Preview, Stay Open, Own Nametag and UI Animations. */
	private static void extras(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<String> failed) {
		var server = singleplayer.getServer();
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		boolean knivesWere = context.computeOnClient(client -> Modules.KNIVES.enabled());
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.FIRST_PERSON);
			client.getToastManager().clear();
			Modules.KNIVES.set(false);
			Modules.SWORD_MODELS.set(true);
		});
		server.runCommand("item replace entity @p hotbar.7 with minecraft:diamond_sword");
		context.getInput().pressKey(GLFW.GLFW_KEY_8);
		face(context, 0, 10);
		context.waitTicks(25);
		check(failed, "Sword Models draws a diamond sword as its own 3D blade", context.computeOnClient(client ->
				Knives.modelFor(client.player, client.player.getMainHandItem(), 7) == KnifeModel.WRAITH));
		shot(context, "eventide-sword-models");
		context.runOnClient(client -> {
			Modules.SWORD_MODELS.set(false);
			Modules.KNIVES.set(knivesWere);
			Modules.ITEM_PHYSICS.set(true);
		});
		context.getInput().pressKey(GLFW.GLFW_KEY_1);
		server.runCommand("execute as @p at @s run summon item ^-0.7 ^0.6 ^2.4 {Item:{id:\"minecraft:diamond_sword\",count:1},PickupDelay:32767}");
		server.runCommand("execute as @p at @s run summon item ^0.7 ^0.6 ^2.6 {Item:{id:\"minecraft:golden_apple\",count:3},PickupDelay:32767}");
		server.runCommand("execute as @p at @s run summon item ^0 ^0.6 ^3.2 {Item:{id:\"minecraft:ender_pearl\",count:8},PickupDelay:32767}");
		face(context, 0, 50);
		context.waitTicks(50);
		check(failed, "Item Physics lays dropped items flat once they land (ItemEntityRenderer)", Hooks.itemPhysicsRan);
		shot(context, "eventide-item-physics");
		server.runCommand("kill @e[type=item]");
		context.runOnClient(client -> {
			Modules.ITEM_PHYSICS.set(false);
			Modules.SHULKER_PREVIEW.set(true);
		});
		server.runCommand("item replace entity @p hotbar.8 with minecraft:purple_shulker_box[minecraft:container=["
				+ "{slot:0,item:{id:\"minecraft:diamond\",count:24}},{slot:1,item:{id:\"minecraft:golden_apple\",count:6}},"
				+ "{slot:4,item:{id:\"minecraft:ender_pearl\",count:16}},{slot:9,item:{id:\"minecraft:netherite_sword\",count:1}},"
				+ "{slot:13,item:{id:\"minecraft:totem_of_undying\",count:1}},{slot:20,item:{id:\"minecraft:experience_bottle\",count:64}},"
				+ "{slot:26,item:{id:\"minecraft:enchanted_golden_apple\",count:2}}]]");
		context.waitTicks(5);
		context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		context.waitTicks(10);
		double[] box = context.computeOnClient(client -> {
			InventoryScreen screen = (InventoryScreen) client.screen;
			var at = (dev.eventide.client.mixin.AbstractContainerScreenAccessor) screen;
			double scale = client.getWindow().getGuiScale();
			for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
				if (slot.container == client.player.getInventory() && slot.getContainerSlot() == 8) {
					return new double[] {(at.eventide$left() + slot.x + 8) * scale, (at.eventide$top() + slot.y + 8) * scale};
				}
			}
			return new double[] {0, 0};
		});
		context.getInput().setCursorPos(box[0], box[1]);
		context.waitTicks(5);
		check(failed, "Shulker Preview shows a shulker box's contents when you hover it", Hooks.shulkerPreviewDrawn);
		shot(context, "eventide-shulker-preview");
		context.setScreen(() -> null);
		context.runOnClient(client -> {
			Modules.SHULKER_PREVIEW.set(true);
			Modules.STAY_OPEN.set(true);
		});
		context.setScreen(() -> new net.minecraft.client.gui.screens.ChatScreen("", false));
		context.waitTicks(5);
		context.runOnClient(client -> {
			((net.minecraft.client.gui.screens.ChatScreen) client.screen).handleChatInput("Eventide says hi", true);
			client.setScreen(null);
		});
		context.waitTicks(4);
		check(failed, "Stay Open opens the chat again after you send a message", context.computeOnClient(client ->
				client.screen instanceof net.minecraft.client.gui.screens.ChatScreen) && Hooks.stayOpenRan);
		context.setScreen(() -> null);
		context.runOnClient(client -> {
			Modules.STAY_OPEN.set(false);
			Modules.OWN_NAMETAG.set(true);
			client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		face(context, 0, 15);
		context.waitTicks(10);
		check(failed, "Own Nametag shows your name over your head in third person", context.computeOnClient(client ->
				dev.eventide.client.module.Nametags.showOwn(client.player)));
		shot(context, "eventide-own-nametag");
		context.runOnClient(client -> {
			Modules.OWN_NAMETAG.set(false);
			client.options.setCameraType(CameraType.FIRST_PERSON);
			Modules.UI_ANIMATIONS.set(false);
		});
		context.waitTicks(3);
		check(failed, "UI Animations off makes every glide snap into place", context.computeOnClient(client -> dev.eventide.client.ui.Anim.speed() == 0f));
		context.runOnClient(client -> Modules.UI_ANIMATIONS.set(true));
		context.waitTicks(3);
		check(failed, "UI Animations back on brings the glides back", context.computeOnClient(client -> dev.eventide.client.ui.Anim.speed() > 0f));
	}

	/**
	 * The HUD as the reference composes it: the Watermark top left, the Music Player top right with the module
	 * list under it, a server boss bar as a thin line, at night under the Galaxy sky; then close-ups.
	 */
	private static void hudLook(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<String> failed) {
		var server = singleplayer.getServer();
		float[] sharpWidths = context.computeOnClient(client -> {
			float[] out = new float[UiFont.SHARPNESS.length + 1];
			out[0] = client.font.width(UiFont.bold("Snowfall 12:34 Eventide Test Band"));
			for (int i = 0; i < UiFont.SHARPNESS.length; i++) out[i + 1] = client.font.width(UiFont.sharp("Snowfall 12:34 Eventide Test Band", 2, i));
			return out;
		});
		EventideClient.LOG.info("eventide-check: the same text in each sharp copy of the font: {}", java.util.Arrays.toString(sharpWidths));
		boolean same = context.computeOnClient(client -> {
			String sample = "Snowfall 12:34 Eventide Test Band";
			var splitter = client.font.getSplitter();
			float layout = splitter.stringWidth(UiFont.bold(sample));
			for (int i = 0; i < UiFont.SHARPNESS.length; i++) {
				float drawn = splitter.stringWidth(UiFont.sharp(sample, 2, i)) * dev.eventide.client.ui.Type.stretch(sample, dev.eventide.client.ui.Type.Weight.BOLD, i);
				if (Math.abs(drawn - layout) > 0.5f) return false;
			}
			return true;
		});
		check(failed, "text from every sharp copy of the font lands at the layout's width", same);
		server.runCommand("time set midnight");
		server.runCommand("bossbar add eventide:banner \"Join our community (/discord)\"");
		server.runCommand("bossbar set eventide:banner color blue");
		server.runCommand("bossbar set eventide:banner players @a");
		context.runOnClient(client -> {
			Modules.MUSIC.set(true);
			MusicBridge.simulate(SAMPLE_SONG);
			Modules.SKIES.set(true);
			dev.eventide.client.sky.LivingSky.select(dev.eventide.client.sky.SkyType.GALAXY);
			Modules.KEYSTROKES.set(false);
			client.getToastManager().clear();
			client.gui.getChat().clearMessages(false);
		});
		face(context, 30, -25);
		context.waitTicks(30);
		check(failed, "the Watermark shows on the HUD", context.computeOnClient(client -> HudWidgets.WATERMARK.shown()));
		shot(context, "eventide-hud-look");
		context.setScreen(() -> new MusicShowcase(HudWidgets.MUSIC));
		context.waitTicks(10);
		shot(context, "eventide-music-card");
		context.setScreen(() -> new MusicShowcase(HudWidgets.WATERMARK));
		context.waitTicks(10);
		shot(context, "eventide-watermark-closeup");
		context.setScreen(() -> null);
		context.runOnClient(client -> {
			Modules.MUSIC.set(false);
			Modules.MODULE_LIST.set(false);
			for (Module module : List.of(Modules.FPS, Modules.CLOCK, Modules.MEMORY, Modules.SERVER, Modules.COORDS)) module.set(true);
			float scale = HudRenderer.scaleFor(HudWidgets.CLOCK);
			int sw = Math.round(client.getWindow().getGuiScaledWidth() / scale);
			int sh = Math.round(client.getWindow().getGuiScaledHeight() / scale);
			int y = 60;
			int x = 40;
			for (HudWidget widget : List.of(HudWidgets.CLOCK, HudWidgets.MEMORY, HudWidgets.SERVER)) {
				int w = widget.width(client);
				int h = widget.height(client);
				widget.setPosition(x / (double) Math.max(1, sw - w), y / (double) Math.max(1, sh - h));
				x += w;
			}
			int fw = HudWidgets.FPS.width(client);
			HudWidgets.FPS.setPosition(40 / (double) Math.max(1, sw - fw), 90 / (double) Math.max(1, sh - HudWidgets.FPS.height(client)));
			int cw = HudWidgets.COORDS.width(client);
			HudWidgets.COORDS.setPosition(40 / (double) Math.max(1, sw - cw), 116 / (double) Math.max(1, sh - HudWidgets.COORDS.height(client)));
		});
		check(failed, "a pill dropped beside another sticks flush to it", context.computeOnClient(client -> {
			int w = HudWidgets.FPS.width(client);
			int h = HudWidgets.FPS.height(client);
			List<Pills.Placed> others = List.of(new Pills.Placed(HudWidgets.CLOCK, 100, 50, 40, h, 1f));
			int[] stuck = Pills.stick(HudWidgets.FPS, 146, 53, w, h, 1f, others);
			return stuck != null && stuck[0] == 140 && stuck[1] == 50;
		}));
		context.waitTicks(10);
		shot(context, "eventide-hud-pills");
		check(failed, "pills that touch are drawn as one bar", Hooks.pillsStuck);
		context.runOnClient(client -> {
			for (HudWidget widget : List.of(HudWidgets.CLOCK, HudWidgets.MEMORY, HudWidgets.SERVER, HudWidgets.FPS, HudWidgets.COORDS)) {
				widget.resetPosition();
			}
			for (Module module : List.of(Modules.FPS, Modules.CLOCK, Modules.MEMORY, Modules.SERVER, Modules.COORDS)) module.set(false);
			Modules.MODULE_LIST.set(true);
		});
		server.runCommand("bossbar remove eventide:banner");
		context.runOnClient(client -> {
			Modules.KEYSTROKES.set(true);
			Modules.SKIES.set(false);
		});
		server.runCommand("time set noon");
	}

	/** Runs one part of the test on its own (-PtestFocus=...), for quicker checks while working on it. */
	private static void runFocused(ClientGameTestContext context, String focus, List<String> failed) {
		EventideClient.LOG.info("eventide-check: running only the {} part of the test", focus);
		context.waitTicks(20);
		if (focus.contains("title")) {
			context.waitTicks(40);
			shot(context, "eventide-main-menu");
			context.getInput().pressKey(EventideClient.openSettingsKey);
			context.waitTicks(30);
			shot(context, "eventide-menu-from-menu");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);
		}
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(100);
			if (focus.contains("skies")) skies(context, singleplayer, failed, true);
			if (focus.contains("protect")) nameDetect(context, singleplayer, failed);
			if (focus.contains("nametags")) nametagCloseup(context, singleplayer, failed);
			if (focus.contains("perf")) performance(context, failed);
			if (focus.contains("lyrics")) skyLyrics(context, singleplayer, failed);
			if (focus.contains("knives")) knives(context, singleplayer, failed, true);
			if (focus.contains("menu")) menu(context, failed);
			if (focus.contains("kills")) killEffects(context, singleplayer, failed);
			if (focus.contains("fx")) worldFx(context, singleplayer, failed);
			if (focus.contains("cosmetics")) cosmetics(context, singleplayer, failed);
			if (focus.contains("extras")) extras(context, singleplayer, failed);
			if (focus.contains("skins")) skinCloseups(context);
			if (focus.contains("hud")) hudLook(context, singleplayer, failed);
			if (focus.contains("calibrate")) calibrateKnife(context, singleplayer);
			if (focus.contains("bench")) benchmark(context, singleplayer, 900);
		}
		context.waitTicks(10);
	}

	/** Frame times and memory allocated on the render thread, one sample a frame, taken by a HUD layer this test adds. */
	private static final class FrameRecorder {
		private static final FrameRecorder INSTANCE = new FrameRecorder();
		private final long[] nanos = new long[4096];
		private final long[] bytes = new long[4096];
		private volatile int count = -1;
		private long lastNanos;
		private long lastBytes;
		private boolean registered = false;

		void frame() {
			if (count < 0 || count >= nanos.length) return;
			long now = System.nanoTime();
			long allocated = allocatedBytes();
			if (lastNanos != 0) {
				nanos[count] = now - lastNanos;
				bytes[count] = allocated - lastBytes;
				count++;
			}
			lastNanos = now;
			lastBytes = allocated;
		}

		private static long allocatedBytes() {
			java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
			return bean instanceof com.sun.management.ThreadMXBean sun ? sun.getCurrentThreadAllocatedBytes() : 0;
		}

		void start() {
			lastNanos = 0;
			count = 0;
		}

		/** {average ms, median ms, 99th percentile ms, average KB allocated a frame, frames}. */
		double[] stop() {
			int n = Math.max(0, count);
			count = -1;
			if (n == 0) return new double[] {0, 0, 0, 0, 0};
			long[] sorted = java.util.Arrays.copyOf(nanos, n);
			java.util.Arrays.sort(sorted);
			double sum = 0;
			double alloc = 0;
			for (int i = 0; i < n; i++) {
				sum += nanos[i];
				alloc += bytes[i];
			}
			return new double[] {sum / n / 1e6, sorted[n / 2] / 1e6, sorted[Math.min(n - 1, (int) Math.ceil(n * 0.99) - 1)] / 1e6, alloc / n / 1024.0, n};
		}
	}

	/**
	 * Measures frame time and render-thread allocation over {@code frames} frames in three set scenes: the
	 * world with every Eventide mod off, the same with a typical HUD on, and the Eventide menu open. Logged as
	 * "eventide-bench" lines, so runs before and after a change can be compared on the same machine.
	 */
	private static void benchmark(ClientGameTestContext context, TestSingleplayerContext singleplayer, int frames) {
		var server = singleplayer.getServer();
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("scoreboard objectives add bench dummy \"Kills\"");
		server.runCommand("scoreboard objectives setdisplay sidebar bench");
		server.runCommand("scoreboard players set Steve bench 29");
		server.runCommand("scoreboard players set Alex bench 17");
		server.runCommand("effect give @p speed 600 1");
		server.runCommand("effect give @p strength 600 0");
		server.runCommand("item replace entity @p weapon.mainhand with netherite_sword");
		server.runCommand("item replace entity @p weapon.offhand with totem_of_undying");
		server.runCommand("item replace entity @p armor.head with diamond_helmet");
		server.runCommand("item replace entity @p armor.chest with diamond_chestplate");
		context.runOnClient(client -> {
			if (!FrameRecorder.INSTANCE.registered) {
				FrameRecorder.INSTANCE.registered = true;
				Compat.registerHud(net.minecraft.resources.Identifier.fromNamespaceAndPath("eventide", "bench"), d -> FrameRecorder.INSTANCE.frame());
			}
			client.options.renderDistance().set(8);
			client.options.fov().set(70);
			client.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
			client.getToastManager().clear();
		});
		face(context, 30, 5);
		context.waitTicks(200);
		List<Module> toggleable = new ArrayList<>();
		for (Module module : Modules.ALL) if (module.toggleable()) toggleable.add(module);
		java.util.Map<Module, Boolean> before = new java.util.HashMap<>();
		context.runOnClient(client -> {
			for (Module module : toggleable) {
				before.put(module, module.enabled());
				module.set(false);
			}
		});
		context.waitTicks(40);
		double[] off = measure(context, frames);
		context.runOnClient(client -> {
			for (Module module : List.of(Modules.FPS, Modules.CPS, Modules.PING, Modules.TPS, Modules.CLOCK, Modules.COORDS, Modules.KEYSTROKES,
					Modules.ARMOR, Modules.POTIONS, Modules.SCOREBOARD, Modules.MUSIC, Modules.MODULE_LIST, Modules.PLAYER_LIST, Modules.TARGET_HUD,
					Modules.CROSSHAIR, Modules.ZOOM, Modules.FREELOOK, Modules.NAME_PROTECT, Modules.CHAT, Modules.MENU_NAME, Modules.LYRICS)) {
				module.set(true);
			}
			Modules.MENU_NAME_HUD.set(true);
			MusicBridge.simulate(SAMPLE_SONG);
			Lyrics.simulate(MusicBridge.current(), "[00:50.00] Snow falls on the quiet town\n[00:55.00] Every light is burning down\n"
					+ "[01:05.00] Hold on, we're almost home\n[01:20.00] Under violet skies we roam\n[02:00.00] Under violet skies we roam");
			PlayerList.add("Steve");
			PlayerList.add(client.getUser().getName());
			TextRules.add("Kills: #", "Kills: 9999");
		});
		context.waitTicks(40);
		double[] hud = measure(context, frames);
		context.runOnClient(client -> Modules.PERFORMANCE_PRESET.set("Performance"));
		context.waitTicks(20);
		double[] lite = measure(context, frames);
		context.runOnClient(client -> Modules.PERFORMANCE_PRESET.set("Balanced"));
		context.waitTicks(5);
		context.runOnClient(client -> {
			for (dev.eventide.client.module.Setting setting : Modules.PERFORMANCE.settings()) setting.reset();
			Modules.SKIES_RESOLUTION.reset();
		});
		context.setScreen(() -> new MenuScreen(null, Category.HUD));
		context.waitTicks(40);
		double[] menu = measure(context, frames);
		context.setScreen(() -> null);
		context.runOnClient(client -> before.forEach(Module::set));
		report("world, Eventide mods off", off);
		report("world, typical Eventide HUD", hud);
		report("world, typical Eventide HUD, Performance preset", lite);
		report("Eventide menu open", menu);
		EventideClient.LOG.info("eventide-bench: Eventide HUD costs {} ms a frame on average ({} ms at the 99th percentile)",
				String.format(java.util.Locale.ROOT, "%.2f", hud[0] - off[0]), String.format(java.util.Locale.ROOT, "%.2f", hud[2] - off[2]));
	}

	private static double[] measure(ClientGameTestContext context, int frames) {
		context.runOnClient(client -> FrameRecorder.INSTANCE.start());
		int waited = 0;
		while (context.computeOnClient(client -> FrameRecorder.INSTANCE.count) < frames && waited < 20 * 120) {
			context.waitTicks(10);
			waited += 10;
		}
		return context.computeOnClient(client -> FrameRecorder.INSTANCE.stop());
	}

	private static void report(String scene, double[] result) {
		EventideClient.LOG.info("eventide-bench: {}: {} frames, {} ms average ({} FPS), {} ms median, {} ms 99th percentile ({} FPS 1% low), {} KB allocated a frame",
				scene, (int) result[4], String.format(java.util.Locale.ROOT, "%.2f", result[0]),
				String.format(java.util.Locale.ROOT, "%.1f", 1000 / Math.max(0.001, result[0])), String.format(java.util.Locale.ROOT, "%.2f", result[1]),
				String.format(java.util.Locale.ROOT, "%.2f", result[2]), String.format(java.util.Locale.ROOT, "%.1f", 1000 / Math.max(0.001, result[2])),
				String.format(java.util.Locale.ROOT, "%.1f", result[3]));
	}

	/**
	 * Sky Lyrics, on a platform high above spawn so the sky is open: they're drawn while the sky is, and
	 * then a wall, a tree, a cliff and a closed room each hide them like they hide the sky, while glass
	 * lets them through. The wall and the room are also checked pixel by pixel: the same view with the
	 * lyrics on and off may only differ in the sky.
	 */
	private static void skyLyrics(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<String> failed) {
		var server = singleplayer.getServer();
		String song = "{\"key\":\"sky\",\"app\":\"Spotify.exe\",\"title\":\"Northern Lights\",\"artist\":\"Eventide Test Band\",\"album\":\"CI\","
				+ "\"playing\":false,\"position\":120,\"duration\":240}";
		String lrc = "[01:40.00] The city falls asleep\n[01:58.00] Written in the northern sky\n[02:10.00] Every word becomes a star\n";
		boolean musicWasOn = context.computeOnClient(client -> Modules.MUSIC.enabled());
		boolean warningWasOn = context.computeOnClient(client -> Modules.TOTEM_WARNING.enabled());
		double[] home = context.computeOnClient(client -> new double[] {client.player.getX(), client.player.getY(), client.player.getZ(),
				client.player.getYRot(), client.player.getXRot()});
		int x = (int) Math.floor(home[0]);
		int y = (int) Math.floor(home[1]) + 40;
		int z = (int) Math.floor(home[2]);
		server.runCommand("fill " + (x - 14) + " " + (y - 1) + " " + (z - 6) + " " + (x + 14) + " " + (y - 1) + " " + (z + 24) + " minecraft:smooth_stone");
		server.runCommand("tp @p " + x + ".5 " + y + " " + z + ".5 0 -14");
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("effect clear @p");
		server.runCommand("item replace entity @p weapon.mainhand with air");
		server.runCommand("item replace entity @p weapon.offhand with air");
		context.runOnClient(client -> {
			client.gui.getChat().clearMessages(false);
			Modules.MUSIC.set(false);
			Modules.TOTEM_WARNING.set(false);
			Modules.LYRICS.set(true);
			Modules.LYRICS_PLACE.set("Sky");
			client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
			client.getToastManager().clear();
			MusicBridge.simulate(song);
			Lyrics.simulate(MusicBridge.current(), lrc);
		});
		face(context, 0, -14);
		context.waitTicks(40);
		Hooks.lyricsDrawn = false;
		context.waitTicks(5);
		check(failed, "sky lyrics are drawn while the sky is (SkyRenderer)", Hooks.lyricsDrawn);
		shot(context, "eventide-sky-lyrics-open");

		server.runCommand("fill " + (x + 1) + " " + y + " " + (z + 6) + " " + (x + 14) + " " + (y + 16) + " " + (z + 6) + " minecraft:red_concrete");
		context.waitTicks(10);
		Path walled = shotPath(context, "eventide-sky-lyrics-behind-wall");
		context.runOnClient(client -> Modules.LYRICS.set(false));
		context.waitTicks(5);
		Path walledOff = shotPath(context, "eventide-sky-lyrics-behind-wall-off");
		context.runOnClient(client -> Modules.LYRICS.set(true));
		if (walled != null && walledOff != null) {
			int[] diff = compareShots(walled, walledOff);
			EventideClient.LOG.info("eventide-check: lyrics on vs off: {} of {} wall pixels changed, {} of {} sky pixels changed", diff[0], diff[1], diff[2],
					diff[3]);
			check(failed, "a wall hides the sky lyrics behind it", diff[1] > 2000 && diff[0] <= diff[1] / 400);
			check(failed, "the sky lyrics still show in the open sky beside the wall", diff[2] > 150);
		}
		server.runCommand("fill " + (x + 1) + " " + y + " " + (z + 6) + " " + (x + 14) + " " + (y + 16) + " " + (z + 6) + " minecraft:air");

		server.runCommand("fill " + (x - 4) + " " + y + " " + (z + 7) + " " + (x - 4) + " " + (y + 5) + " " + (z + 7) + " minecraft:oak_log");
		server.runCommand("fill " + (x - 7) + " " + (y + 3) + " " + (z + 5) + " " + (x - 1) + " " + (y + 7) + " " + (z + 9)
				+ " minecraft:oak_leaves[persistent=true] replace minecraft:air");
		context.waitTicks(10);
		shot(context, "eventide-sky-lyrics-behind-tree");
		server.runCommand("fill " + (x - 7) + " " + y + " " + (z + 5) + " " + (x - 1) + " " + (y + 7) + " " + (z + 9) + " minecraft:air");

		server.runCommand("fill " + (x - 2) + " " + y + " " + (z + 10) + " " + (x + 14) + " " + (y + 5) + " " + (z + 16) + " minecraft:stone");
		server.runCommand("fill " + (x - 14) + " " + y + " " + (z + 10) + " " + (x - 3) + " " + (y + 2) + " " + (z + 16) + " minecraft:stone");
		context.waitTicks(10);
		shot(context, "eventide-sky-lyrics-behind-cliff");
		server.runCommand("fill " + (x - 14) + " " + y + " " + (z + 10) + " " + (x + 14) + " " + (y + 5) + " " + (z + 16) + " minecraft:air");

		server.runCommand("fill " + (x - 14) + " " + y + " " + (z + 5) + " " + (x + 14) + " " + (y + 20) + " " + (z + 5)
				+ " minecraft:light_blue_stained_glass");
		context.waitTicks(10);
		shot(context, "eventide-sky-lyrics-through-glass");
		server.runCommand("fill " + (x - 14) + " " + y + " " + (z + 5) + " " + (x + 14) + " " + (y + 20) + " " + (z + 5) + " minecraft:air");

		server.runCommand("fill " + (x - 3) + " " + (y - 1) + " " + (z - 3) + " " + (x + 3) + " " + (y + 4) + " " + (z + 3) + " minecraft:stone");
		server.runCommand("fill " + (x - 1) + " " + y + " " + (z - 1) + " " + (x + 1) + " " + (y + 2) + " " + (z + 1) + " minecraft:air");
		server.runCommand("tp @p " + x + ".5 " + y + " " + z + ".5 0 -14");
		face(context, 0, -14);
		context.waitTicks(10);
		Path inside = shotPath(context, "eventide-sky-lyrics-indoors");
		context.runOnClient(client -> Modules.LYRICS.set(false));
		context.waitTicks(5);
		Path insideOff = shotPath(context, "eventide-sky-lyrics-indoors-off");
		context.runOnClient(client -> Modules.LYRICS.set(true));
		if (inside != null && insideOff != null) {
			int[] diff = compareShots(inside, insideOff);
			EventideClient.LOG.info("eventide-check: indoors, lyrics on vs off: {} of {} pixels changed", diff[0] + diff[2], diff[1] + diff[3]);
			check(failed, "no sky lyrics leak into a closed room", diff[0] + diff[2] <= (diff[1] + diff[3]) / 400);
		}
		server.runCommand("fill " + (x - 3) + " " + (y - 1) + " " + (z - 3) + " " + (x + 3) + " " + (y + 4) + " " + (z + 3) + " minecraft:air");
		server.runCommand("fill " + (x - 14) + " " + (y - 1) + " " + (z - 6) + " " + (x + 14) + " " + (y - 1) + " " + (z + 24) + " minecraft:smooth_stone");
		server.runCommand("tp @p " + x + ".5 " + y + " " + z + ".5 0 -14");
		face(context, 0, -14);

		context.runOnClient(client -> Lyrics.simulate(MusicBridge.current(), "[01:40.00] The city falls asleep\n[01:50.00]\n[02:30.00] And we fly\n"));
		context.waitTicks(15);
		shot(context, "eventide-sky-lyrics-instrumental");
		context.runOnClient(client -> Lyrics.simulatePlain(MusicBridge.current(),
				"The city falls asleep\nWritten in the northern sky\nEvery word becomes a star\n\nAnd we fly\nOver violet seas\nUntil the morning\n"));
		context.waitTicks(15);
		shot(context, "eventide-sky-lyrics-unsynced");
		check(failed, "plain lyrics show, spread over the song", context.computeOnClient(client -> Lyrics.view(MusicBridge.current()).state()
				== Lyrics.State.UNSYNCED && !Lyrics.view(MusicBridge.current()).synced() && !Lyrics.lines(MusicBridge.current()).isEmpty()));

		server.runCommand("fill " + (x - 14) + " " + (y - 1) + " " + (z - 6) + " " + (x + 14) + " " + (y - 1) + " " + (z + 24) + " minecraft:air");
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.4f %.4f %.4f %.2f %.2f", home[0], home[1], home[2], home[3], home[4]));
		server.runCommand("item replace entity @p weapon.mainhand with netherite_sword");
		server.runCommand("item replace entity @p weapon.offhand with totem_of_undying");
		context.runOnClient(client -> {
			Modules.MUSIC.set(musicWasOn);
			Modules.TOTEM_WARNING.set(warningWasOn);
			Modules.LYRICS_PLACE.reset();
			client.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
		});
		face(context, (float) home[3], (float) home[4]);
		context.waitTicks(20);
	}

	/**
	 * Knives: the old Blades settings carry over, hotbar slots are marked as knives, and each knife is
	 * drawn, slashes and inspects. With {@code every}, each knife gets a picture idle, mid-draw, mid-slash
	 * and mid-inspect on a platform in the sky; otherwise a few pictures in place.
	 */
	private static void knives(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<String> failed, boolean every) {
		var server = singleplayer.getServer();
		check(failed, "Blades settings carry over to Knives", context.computeOnClient(client -> {
			java.util.Map<String, Boolean> modules = new java.util.HashMap<>(java.util.Map.of("blades", true));
			java.util.Map<String, String> settings = new java.util.HashMap<>(java.util.Map.of("blades.style", "Scythe", "blades.axes", "true"));
			Config.migrateBlades(modules, settings);
			return Boolean.TRUE.equals(modules.get("knives")) && !modules.containsKey("blades") && "Scythe".equals(settings.get("knives.model"))
					&& "Swords and axes".equals(settings.get("knives.auto_type")) && !settings.containsKey("blades.style");
		}));
		double[] home = context.computeOnClient(client -> new double[] {client.player.getX(), client.player.getY(), client.player.getZ(),
				client.player.getYRot(), client.player.getXRot()});
		int x = (int) Math.floor(home[0]);
		int y = (int) Math.floor(home[1]) + 40;
		int z = (int) Math.floor(home[2]);
		String[] items = {"netherite_sword", "diamond_sword", "stick", "iron_sword", "golden_sword", "blaze_rod", "netherite_sword", "iron_axe",
				"totem_of_undying"};
		for (int i = 0; i < 9; i++) server.runCommand("item replace entity @p hotbar." + i + " with " + items[i]);
		if (every) {
			server.runCommand("fill " + (x - 6) + " " + (y - 1) + " " + (z - 6) + " " + (x + 6) + " " + (y - 1) + " " + (z + 6) + " minecraft:smooth_stone");
			server.runCommand("tp @p " + x + ".5 " + y + " " + z + ".5 0 -8");
			server.runCommand("time set noon");
		}
		context.runOnClient(client -> {
			Modules.KNIVES.set(true);
			Modules.KNIVES_SLOTS.reset();
			for (int i = 0; i < KNIFE_SLOTS.length; i++) Knives.setSlot(i, KNIFE_SLOTS[i]);
			client.getToastManager().clear();
			client.gui.getChat().clearMessages(false);
		});
		face(context, 0, every ? -8 : 10);
		check(failed, "hotbar slots marked as knives keep their knife", context.computeOnClient(client ->
				Knives.slot(1) == KnifeModel.WRAITH && Knives.slot(7) == KnifeModel.REAPER && Knives.slot(8) == null
						&& Modules.KNIVES_SLOTS.get().contains("2:riptide")));
		check(failed, "old knife names find their new knives", context.computeOnClient(client -> {
			java.util.Map<String, String> old = new java.util.HashMap<>(java.util.Map.of("knives.model", "Karambit", "knives.finish", "Doppler (Blue)",
					"knives.slots", "0:butterfly,3:m9_bayonet,5:phantom", "knives.glove", "Sport"));
			Knives.migrate(old);
			return "Eclipse".equals(old.get("knives.model")) && "Midnight".equals(old.get("knives.finish"))
					&& "0:wraith,3:riptide,5:lumen".equals(old.get("knives.slots")) && !old.containsKey("knives.glove");
		}));
		context.runOnClient(client -> Knives.toggleSlot(8));
		check(failed, "a hotbar slot can be marked and unmarked", context.computeOnClient(client -> {
			boolean marked = Knives.slot(8) != null;
			Knives.toggleSlot(8);
			return marked && Knives.slot(8) == null;
		}));
		List<KnifeModel> shown = every ? List.of(KNIFE_SLOTS) : List.of(KnifeModel.ECLIPSE, KnifeModel.WRAITH, KnifeModel.PRISM);
		for (KnifeModel model : shown) {
			String name = model.id();
			context.getInput().pressKey(GLFW.GLFW_KEY_1 + List.of(KNIFE_SLOTS).indexOf(model));
			if (every) {
				context.waitTicks(6);
				shot(context, "eventide-knife-" + name + "-draw");
			}
			context.waitTicks(every ? 26 : 30);
			shot(context, "eventide-knife-" + name);
			if (every || model == KnifeModel.ECLIPSE) {
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
				context.waitTicks(4);
				shot(context, "eventide-knife-" + name + "-slash");
				context.waitTicks(16);
			}
			if (every || model == KnifeModel.WRAITH) {
				context.getInput().pressKey(EventideClient.inspectKey);
				context.waitTicks(every ? 22 : 26);
				shot(context, "eventide-knife-" + name + "-inspect");
				if (model == KnifeModel.WRAITH) check(failed, "the inspect key plays the knife's inspect", KnifeRenderer.inspecting()
						|| Hooks.knifeInspectRan);
				context.waitTicks(60);
			}
		}
		knifeSettings(context, failed);
		check(failed, "knives are drawn in your hand (ItemInHandRenderer)", Hooks.knifeRendered);
		check(failed, "knives sit in the fist at rest, not pushed back by the near-plane check", context.computeOnClient(client -> {
			for (KnifeModel model : KnifeModel.values()) {
				if (KnifeRenderer.restPush(model) > 0.001f) return false;
			}
			return true;
		}));
		float roughest = context.computeOnClient(client -> {
			float worst = 0;
			for (KnifeModel model : KnifeModel.values()) worst = Math.max(worst, KnifeRenderer.motionCheck(model));
			return worst;
		});
		EventideClient.LOG.info("eventide-check: knife motion, biggest change in turning speed between frames: {} deg/s", Math.round(roughest));
		check(failed, "knife animations move smoothly between frames, with no sudden stops or starts", roughest < 900);
		check(failed, "switching to a knife plays its draw", Hooks.knifeDrawRan);
		check(failed, "a swing plays a slash (LocalPlayer.swing)", Hooks.knifeSlashRan);
		check(failed, "the inspect key starts an inspect", Hooks.knifeInspectRan);
		if (every) {
			for (String finish : List.of("Crystal", "Engraved", "Nebula", "Onyx")) {
				context.runOnClient(client -> Modules.KNIVES_FINISH.set(finish));
				context.getInput().pressKey(GLFW.GLFW_KEY_3);
				context.waitTicks(30);
				shot(context, "eventide-knife-riptide-" + finish.toLowerCase(java.util.Locale.ROOT));
			}
			context.runOnClient(client -> {
				Modules.KNIVES_FINISH.reset();
				client.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.LEFT);
				client.options.broadcastOptions();
			});
			context.getInput().pressKey(GLFW.GLFW_KEY_1);
			context.waitTicks(30);
			shot(context, "eventide-knife-left-handed");
			context.runOnClient(client -> {
				client.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.RIGHT);
				client.options.broadcastOptions();
			});
			server.runCommand("fill " + (x - 6) + " " + (y - 1) + " " + (z - 6) + " " + (x + 6) + " " + (y - 1) + " " + (z + 6) + " minecraft:air");
			server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.4f %.4f %.4f %.2f %.2f", home[0], home[1], home[2], home[3], home[4]));
		}
		context.runOnClient(client -> {
			Modules.KNIVES.set(false);
			Modules.KNIVES_SLOTS.reset();
		});
		server.runCommand("item replace entity @p hotbar.0 with netherite_sword");
		context.getInput().pressKey(GLFW.GLFW_KEY_1);
		context.waitTicks(5);
	}

	/** The knives the test puts in hotbar slots 1 to 8. */
	private static final KnifeModel[] KNIFE_SLOTS = {KnifeModel.ECLIPSE, KnifeModel.WRAITH, KnifeModel.RIPTIDE, KnifeModel.PRISM,
			KnifeModel.VESPER, KnifeModel.SERAPH, KnifeModel.LUMEN, KnifeModel.REAPER};

	/**
	 * The Knives settings: the slot picker shows the hotbar, clicking slot 9 marks it, and a marked slot's
	 * knife picker opens its list of knives.
	 */
	private static void knifeSettings(ClientGameTestContext context, List<String> failed) {
		int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.setScreen(() -> MenuScreen.settings(null, Modules.KNIVES));
		context.waitTicks(20);
		shot(context, "eventide-menu-knives");
		float[] slot9 = context.computeOnClient(client -> ((MenuScreen) client.screen).view().knifeSlot(8));
		context.getInput().setCursorPos(slot9[0] * scale, slot9[1] * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
		check(failed, "clicking a slot in the Knives settings marks it", context.computeOnClient(client -> Knives.slot(8) != null));
		context.waitTicks(3);
		float[] picker = context.computeOnClient(client -> ((MenuScreen) client.screen).view().knifePicker());
		context.getInput().setCursorPos(picker[0] * scale, picker[1] * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(10);
		shot(context, "eventide-menu-knives-picker");
		context.runOnClient(client -> Knives.setSlot(8, null));
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	private static void escape(ClientGameTestContext context) {
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(8);
	}

	/** The average colour (r, g, b) of a part of a screenshot, given as shares of its width and height. */
	private static int[] averageColor(Path shot, float x1, float y1, float x2, float y2) {
		try {
			BufferedImage image = ImageIO.read(shot.toFile());
			long r = 0;
			long g = 0;
			long b = 0;
			long n = 0;
			for (int py = (int) (image.getHeight() * y1); py < (int) (image.getHeight() * y2); py++) {
				for (int px = (int) (image.getWidth() * x1); px < (int) (image.getWidth() * x2); px++) {
					int rgb = image.getRGB(px, py);
					r += (rgb >> 16) & 255;
					g += (rgb >> 8) & 255;
					b += rgb & 255;
					n++;
				}
			}
			return n == 0 ? null : new int[] {(int) (r / n), (int) (g / n), (int) (b / n)};
		} catch (java.io.IOException e) {
			return null;
		}
	}

	private static int colorDistance(int[] a, int[] b) {
		return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
	}

	/**
	 * Skies: from a platform high in the air, looking up over the horizon, every sky in turn (each checked to
	 * really replace Minecraft's sky), a switch caught mid-crossfade, daylight, a quarter-resolution sky, the
	 * horizon from the ground (fog fading into the sky), and the settings with their live thumbnails.
	 */
	private static void skies(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<String> failed, boolean every) {
		var server = singleplayer.getServer();
		double[] home = context.computeOnClient(client -> new double[] {client.player.getX(), client.player.getY(), client.player.getZ(),
				client.player.getYRot(), client.player.getXRot()});
		int x = (int) Math.floor(home[0]);
		int y = (int) Math.floor(home[1]) + 40;
		int z = (int) Math.floor(home[2]);
		server.runCommand("fill " + (x - 6) + " " + (y - 1) + " " + (z - 6) + " " + (x + 6) + " " + (y - 1) + " " + (z + 6) + " minecraft:smooth_stone");
		server.runCommand("tp @p " + x + ".5 " + y + " " + z + ".5 0 -16");
		server.runCommand("time set 18000");
		server.runCommand("weather clear");
		context.runOnClient(client -> {
			client.options.hideGui = true;
			client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
			client.getToastManager().clear();
		});
		face(context, 0, -16);
		context.waitTicks(30);
		Path vanilla = shotPath(context, "eventide-sky-vanilla");
		int[] vanillaSky = vanilla == null ? null : averageColor(vanilla, 0.2f, 0.05f, 0.8f, 0.4f);
		context.runOnClient(client -> {
			Modules.SKIES.set(true);
			Modules.SKIES_TIME.set("Night");
			Modules.SKIES_RESOLUTION.set("Full");
			dev.eventide.client.sky.LivingSky.select(dev.eventide.client.sky.SkyType.GALAXY);
		});
		Hooks.skyDrawn = false;
		context.waitTicks(25);
		check(failed, "Skies draws the sky (SkyRenderer.renderSkyDisc)", Hooks.skyDrawn);
		List<dev.eventide.client.sky.SkyType> shown = every ? List.of(dev.eventide.client.sky.SkyType.values())
				: List.of(dev.eventide.client.sky.SkyType.GALAXY, dev.eventide.client.sky.SkyType.AMETHYST, dev.eventide.client.sky.SkyType.FIRE);
		int replaced = 0;
		for (dev.eventide.client.sky.SkyType sky : shown) {
			server.runCommand(sky.day ? "time set noon" : "time set 18000");
			context.runOnClient(client -> {
				dev.eventide.client.sky.LivingSky.select(sky);
				Modules.SKIES_TIME.set(sky.day ? "Day" : "Night");
			});
			context.waitTicks(22);
			Path picture = shotPath(context, "eventide-sky-" + sky.id);
			int[] color = picture == null ? null : averageColor(picture, 0.2f, 0.05f, 0.8f, 0.4f);
			if (color != null && vanillaSky != null) {
				int distance = colorDistance(color, vanillaSky);
				EventideClient.LOG.info("eventide-check: {} sky averages {}, {}, {} ({} from Minecraft's)", sky.label, color[0], color[1], color[2], distance);
				if (distance > 24) replaced++;
			}
		}
		if (vanillaSky != null) check(failed, "every sky looks different from Minecraft's", replaced == shown.size());
		server.runCommand("time set 18000");
		context.runOnClient(client -> {
			Modules.SKIES_TIME.set("Night");
			dev.eventide.client.sky.LivingSky.select(dev.eventide.client.sky.SkyType.GALAXY);
		});
		context.waitTicks(22);
		context.runOnClient(client -> dev.eventide.client.sky.LivingSky.select(dev.eventide.client.sky.SkyType.AFTERGLOW));
		context.waitTicks(7);
		shot(context, "eventide-sky-crossfade");
		context.runOnClient(client -> {
			Modules.SKIES_TIME.set("Day");
			dev.eventide.client.sky.LivingSky.select(dev.eventide.client.sky.SkyType.GALAXY);
		});
		context.waitTicks(22);
		shot(context, "eventide-sky-galaxy-day");
		context.runOnClient(client -> {
			Modules.SKIES_TIME.set("Night");
			Modules.SKIES_RESOLUTION.set("Quarter");
		});
		context.waitTicks(8);
		shot(context, "eventide-sky-galaxy-quarter");
		context.runOnClient(client -> Modules.SKIES_RESOLUTION.reset());
		server.runCommand("fill " + (x - 6) + " " + (y - 1) + " " + (z - 6) + " " + (x + 6) + " " + (y - 1) + " " + (z + 6) + " minecraft:air");
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.4f %.4f %.4f 0 0", home[0], home[1], home[2]));
		face(context, 0, -2);
		context.waitTicks(30);
		shot(context, "eventide-sky-horizon");
		context.runOnClient(client -> client.options.hideGui = false);
		context.setScreen(() -> MenuScreen.settings(null, Modules.SKIES));
		context.waitTicks(30);
		shot(context, "eventide-menu-skies");
		check(failed, "Skies settings show live thumbnails", context.computeOnClient(client ->
				client.getTextureManager().getTexture(dev.eventide.client.sky.LivingSky.thumbnail(dev.eventide.client.sky.SkyType.FIRE)) != null));
		EventideClient.LOG.info("eventide-check: the sky takes the graphics card {} ms a frame", String.format(java.util.Locale.ROOT, "%.3f",
				dev.eventide.client.sky.LivingSky.gpuMillis()));
		context.setScreen(() -> null);
		context.runOnClient(client -> {
			Modules.SKIES.set(false);
			Modules.SKIES_TIME.reset();
			Modules.SKIES_SKY.reset();
		});
		server.runCommand("time set noon");
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.4f %.4f %.4f %.2f %.2f", home[0], home[1], home[2], home[3], home[4]));
		context.waitTicks(5);
	}

	/**
	 * Name Protect's Auto on a server-style sidebar built from teams (label in the prefix, value as the line's
	 * name, like real servers do): it finds the stats, the address and the rank tag by itself, hides them
	 * everywhere (the number may change), leaves your name to the name swap, and an excluded stat shows again.
	 */
	/**
	 * A mannequin (drawn like a player) named like a player, close in front of the camera, so the Eventide name
	 * tag (face, name, health and heart) is pictured big enough to compare with a reference.
	 */
	private static void nametagCloseup(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<String> failed) {
		var server = singleplayer.getServer();
		boolean waypoints = context.computeOnClient(client -> Modules.WAYPOINTS.enabled());
		boolean nametags = context.computeOnClient(client -> Modules.NAMETAGS.enabled());
		context.runOnClient(client -> {
			Modules.NAMETAGS.set(true);
			Modules.WAYPOINTS.set(false);
			client.player.setYRot(0f);
			client.player.setXRot(-16f);
		});
		server.runCommand("execute at @p rotated 0 0 run summon minecraft:mannequin ^ ^ ^2.2 {CustomName:\"Pickzugng\","
				+ "CustomNameVisible:1b,hide_description:1b,immovable:1b,Rotation:[180f,0f]}");
		context.waitTicks(20);
		Hooks.styledNametagRan = false;
		context.waitTicks(2);
		shot(context, "eventide-nametag-closeup");
		check(failed, "a mannequin's tag drawn in Eventide's style", Hooks.styledNametagRan);
		server.runCommand("kill @e[type=minecraft:mannequin]");
		context.runOnClient(client -> {
			Modules.WAYPOINTS.set(waypoints);
			Modules.NAMETAGS.set(nametags);
			client.player.setXRot(0f);
		});
		context.waitTicks(2);
	}

	private static void nameDetect(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<String> failed) {
		var server = singleplayer.getServer();
		String own = context.computeOnClient(client -> client.getUser().getName());
		boolean wasOn = context.computeOnClient(client -> Modules.NAME_PROTECT.enabled());
		server.runCommand("scoreboard objectives add auto dummy \"MY SERVER\"");
		server.runCommand("scoreboard objectives setdisplay sidebar auto");
		String[][] lines = {{"Kills: ", "12", ""}, {"Coins » ", "1234", ""}, {"", "7", " Wins"}, {"", "play.example.net", ""}, {"[MVP+] ", "Nobody", ""},
				{"Name: ", own, ""}};
		for (int i = 0; i < lines.length; i++) {
			server.runCommand("team add auto" + i);
			server.runCommand("team modify auto" + i + " prefix \"" + lines[i][0] + "\"");
			server.runCommand("team modify auto" + i + " suffix \"" + lines[i][2] + "\"");
			server.runCommand("team join auto" + i + " " + lines[i][1]);
			server.runCommand("scoreboard players set " + lines[i][1] + " auto " + (lines.length - i));
		}
		List<Config.TextRule> ownRules = context.computeOnClient(client -> {
			List<Config.TextRule> kept = new ArrayList<>(Config.get().textRules);
			Config.get().textRules.clear();
			Modules.NAME_PROTECT.set(true);
			Modules.NAME_PROTECT_NAME.set("Streamer");
			Modules.NAME_PROTECT_RANKS.set(true);
			Config.get().protectExcluded.clear();
			return kept;
		});
		context.waitTicks(10);
		List<String> keys = context.computeOnClient(client ->
				dev.eventide.client.module.NameDetect.found().stream().map(dev.eventide.client.module.NameDetect.Found::key).toList());
		EventideClient.LOG.info("eventide-check: Auto found {}", keys);
		check(failed, "Auto finds stats on the sidebar (label then number, number then label)",
				keys.contains("number:kills") && keys.contains("number:coins") && keys.contains("count:wins"));
		check(failed, "Auto finds the server address and the rank tag", keys.contains("address:play.example.net") && keys.contains("rank:[MVP+]"));
		check(failed, "Auto hides stats wherever they show, even once the number changes", context.computeOnClient(client ->
				NameProtect.apply("Kills: 12").equals("Kills: ???") && NameProtect.apply("Kills: 13").equals("Kills: ???")
						&& NameProtect.apply("7 Wins").equals("??? Wins")));
		check(failed, "Auto hides the address and rank tag in any text", context.computeOnClient(client ->
				NameProtect.apply("join play.example.net now").equals("join server.ip now") && NameProtect.apply("[MVP+] Nobody").equals("[Rank] Nobody")));
		check(failed, "Auto works on sidebar lines made of coloured pieces", context.computeOnClient(client -> {
			var line = net.minecraft.network.chat.Component.literal("Coins » ").withStyle(net.minecraft.ChatFormatting.GOLD)
					.append(net.minecraft.network.chat.Component.literal("1234").withStyle(net.minecraft.ChatFormatting.WHITE));
			return TextRules.apply(line).getString().equals("Coins » ???");
		}));
		check(failed, "your name on the sidebar is swapped, not hidden as a stat", context.computeOnClient(client ->
				NameProtect.apply("Name: " + own).equals("Name: Streamer") && !keys.contains("number:name")));
		shot(context, "eventide-name-protect-auto");
		context.setScreen(() -> MenuScreen.settings(null, Modules.NAME_PROTECT));
		context.waitTicks(15);
		shot(context, "eventide-menu-name-protect-auto");
		context.setScreen(() -> null);
		context.runOnClient(client -> dev.eventide.client.module.NameDetect.found().stream().filter(item -> item.key().equals("number:kills"))
				.findFirst().ifPresent(dev.eventide.client.module.NameDetect::toggleExcluded));
		context.waitTicks(3);
		check(failed, "a found stat can be left as it is", context.computeOnClient(client -> NameProtect.apply("Kills: 12").equals("Kills: 12")
				&& NameProtect.apply("Coins » 1234").equals("Coins » ???")));
		for (int i = 0; i < lines.length; i++) server.runCommand("team remove auto" + i);
		server.runCommand("scoreboard objectives remove auto");
		server.runCommand("scoreboard objectives setdisplay sidebar kills");
		context.runOnClient(client -> {
			Config.get().protectExcluded.clear();
			Config.get().textRules.addAll(ownRules);
			Modules.NAME_PROTECT_RANKS.reset();
			Modules.NAME_PROTECT.set(wasOn);
		});
		context.waitTicks(3);
	}

	/** Performance: a preset sets everything at once (shadows, blur, animations, particles, the sky's resolution); a change by hand makes it Custom. */
	private static void performance(ClientGameTestContext context, List<String> failed) {
		context.runOnClient(client -> Modules.PERFORMANCE_PRESET.set("Performance"));
		context.waitTicks(3);
		check(failed, "the Performance preset turns effects down", context.computeOnClient(client -> Modules.PERFORMANCE_SHADOWS.is("Off")
				&& Modules.SKIES_RESOLUTION.is("Quarter") && client.options.particles().get() == net.minecraft.server.level.ParticleStatus.DECREASED));
		context.waitTicks(10);
		shot(context, "eventide-hud-performance-preset");
		context.setScreen(() -> MenuScreen.settings(null, Modules.PERFORMANCE));
		context.waitTicks(15);
		shot(context, "eventide-menu-performance");
		context.setScreen(() -> null);
		context.runOnClient(client -> Modules.PERFORMANCE_SHADOWS.set("Simple"));
		context.waitTicks(3);
		check(failed, "changing a Performance option by hand makes the preset Custom", context.computeOnClient(client ->
				Modules.PERFORMANCE_PRESET.is("Custom")));
		context.runOnClient(client -> Modules.PERFORMANCE_PRESET.set("Balanced"));
		context.waitTicks(3);
		context.runOnClient(client -> {
			for (dev.eventide.client.module.Setting setting : Modules.PERFORMANCE.settings()) setting.reset();
			Modules.SKIES_RESOLUTION.reset();
		});
		context.waitTicks(3);
		check(failed, "Balanced puts everything back", context.computeOnClient(client -> Modules.PERFORMANCE_PRESET.is("Balanced")
				&& Modules.PERFORMANCE_SHADOWS.is("Full") && client.options.particles().get() == net.minecraft.server.level.ParticleStatus.ALL));
	}

	/**
	 * The knife lab (-PtestFocus=calibrate): every knife on a platform in the sky with the HUD off, held at
	 * rest, then frozen at points through its slash, stab, draw and inspect, to check the hold and the
	 * animations frame by frame.
	 */
	private static void calibrateKnife(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		var server = singleplayer.getServer();
		double[] home = context.computeOnClient(client -> new double[] {client.player.getX(), client.player.getY(), client.player.getZ()});
		int x = (int) Math.floor(home[0]);
		int y = (int) Math.floor(home[1]) + 40;
		int z = (int) Math.floor(home[2]);
		server.runCommand("fill " + (x - 8) + " " + (y - 1) + " " + (z - 8) + " " + (x + 8) + " " + (y - 1) + " " + (z + 8) + " minecraft:smooth_stone");
		server.runCommand("tp @p " + x + ".5 " + y + " " + z + ".5 0 -8");
		server.runCommand("time set noon");
		for (int i = 0; i < 8; i++) server.runCommand("item replace entity @p hotbar." + i + " with iron_sword");
		context.runOnClient(client -> {
			for (Module module : Modules.ALL) {
				if (module.category == Category.HUD) module.set(false);
			}
			Modules.KNIVES.set(true);
			Modules.KNIVES_SLOTS.reset();
			client.getToastManager().clear();
			client.gui.getChat().clearMessages(false);
		});
		face(context, 0, -8);
		String focus = System.getProperty("eventide.testFocus", "");
		String spec = focus.contains(":") ? focus.substring(focus.indexOf(':') + 1) : "";
		String only = spec.split(";")[0];
		java.util.Map<String, String> options = new java.util.HashMap<>();
		for (String part : spec.split(";")) {
			int eq = part.indexOf('=');
			if (eq > 0) options.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
		}
		boolean every = options.containsKey("every");
		context.runOnClient(client -> {
			if (options.containsKey("finish")) Modules.KNIVES_FINISH.set(options.get("finish"));
			if (options.containsKey("aura")) Modules.HAND_AURA.set(!options.get("aura").equals("off"));
			if (options.containsKey("edge")) Modules.KNIVES_GLOW.set(!options.get("edge").equals("off"));
			if (options.containsKey("glow")) Modules.KNIVES_GLOW_COLOR.set((int) Long.parseLong(options.get("glow"), 16));
			if (options.containsKey("time")) client.getSingleplayerServer().getCommands().performPrefixedCommand(
					client.getSingleplayerServer().createCommandSourceStack(), "time set " + options.get("time"));
		});
		context.getInput().pressKey(GLFW.GLFW_KEY_1);
		for (KnifeModel model : KnifeModel.values()) {
			if (!only.isEmpty() && !java.util.Arrays.asList(only.split(",")).contains(model.id())) continue;
			context.runOnClient(client -> Knives.setSlot(0, model));
			context.waitTicks(30);
			String name = model.id();
			context.runOnClient(client -> KnifeRenderer.preview(model, "channel0", 0));
			context.waitTicks(2);
			shot(context, "lab-" + name + "-rest");
			if (options.containsKey("revs") && model == KnifeModel.ECLIPSE) {
				String[] turns = options.get("revs").split("\\|");
				for (int i = 0; i < turns.length; i++) {
					String[] zyx = turns[i].split(",");
					context.runOnClient(client -> KnifeRenderer.reverseGrip(Float.parseFloat(zyx[0]), Float.parseFloat(zyx[1]), Float.parseFloat(zyx[2])));
					context.waitTicks(2);
					shot(context, "lab-" + name + "-rev" + i);
				}
			}
			List<String> frames = every ? List.of("slash0-15", "slash0-35", "slash0-55", "slash1-35", "use-35", "use-55", "draw-15", "draw-35", "draw-55",
					"inspect-20", "inspect-40", "inspect-60", "inspect-80") : List.of("slash0-35", "use-35", "draw-35", "inspect-20", "inspect-60");
			for (String frame : frames) {
				String which = frame.substring(0, frame.indexOf('-'));
				float t = Integer.parseInt(frame.substring(frame.indexOf('-') + 1)) / 100f;
				context.runOnClient(client -> KnifeRenderer.preview(model, which, t));
				context.waitTicks(2);
				shot(context, "lab-" + name + "-" + frame);
			}
			context.runOnClient(client -> KnifeRenderer.preview(null, null, 0));
		}
	}

	/** Turns the player to this yaw and pitch at once, with no turning in between. */
	private static void face(ClientGameTestContext context, float yaw, float pitch) {
		context.runOnClient(client -> {
			client.player.setYRot(yaw);
			client.player.setXRot(pitch);
			client.player.yRotO = yaw;
			client.player.xRotO = pitch;
		});
	}

	/** A screenshot's file, or null where in-world screenshots are skipped. */
	private static Path shotPath(ClientGameTestContext context, String name) {
		boolean sodiumOn26 = FabricLoader.getInstance().isModLoaded("sodium") && EventideClient.minecraftVersion().startsWith("26.");
		if (sodiumOn26 && context.computeOnClient(client -> client.level != null)) {
			EventideClient.LOG.info("eventide-check: screenshot {} skipped (Sodium on 26.1)", name);
			return null;
		}
		return context.takeScreenshot(name);
	}

	/**
	 * Compares two screenshots of the same view over the middle of the screen (clear of the HUD's corners):
	 * {changed blocks, blocks, changed sky, sky}, counting pixels. Sky is where the second picture is blue;
	 * everything else is a block.
	 */
	private static int[] compareShots(Path a, Path b) {
		try {
			BufferedImage first = ImageIO.read(a.toFile());
			BufferedImage second = ImageIO.read(b.toFile());
			int w = Math.min(first.getWidth(), second.getWidth());
			int h = Math.min(first.getHeight(), second.getHeight());
			int[] out = new int[4];
			for (int py = h / 10; py < h * 7 / 10; py++) {
				for (int px = w / 5; px < w * 4 / 5; px++) {
					int one = first.getRGB(px, py);
					int two = second.getRGB(px, py);
					int r = (two >> 16) & 255;
					int g = (two >> 8) & 255;
					int bl = two & 255;
					boolean sky = bl > r + 15 && bl > g;
					boolean changed = Math.abs(((one >> 16) & 255) - r) > 12 || Math.abs(((one >> 8) & 255) - g) > 12 || Math.abs((one & 255) - bl) > 12;
					out[sky ? 3 : 1]++;
					if (changed) out[sky ? 2 : 0]++;
				}
			}
			return out;
		} catch (java.io.IOException | RuntimeException e) {
			EventideClient.LOG.warn("eventide-check: couldn't compare {} and {}", a, b, e);
			return new int[] {1, 0, 0, 0};
		}
	}

	/**
	 * Sodium on 26.1 can't draw the world in the extra frame a test screenshot renders, so
	 * in-world screenshots are skipped there; everything else still runs.
	 */
	private static void shot(ClientGameTestContext context, String name) {
		boolean sodiumOn26 = FabricLoader.getInstance().isModLoaded("sodium")
				&& EventideClient.minecraftVersion().startsWith("26.");
		if (sodiumOn26 && context.computeOnClient(client -> client.level != null)) {
			EventideClient.LOG.info("eventide-check: screenshot {} skipped (Sodium on 26.1)", name);
			return;
		}
		context.takeScreenshot(name);
	}

	/** The visible widget on the current screen with this label, or null. */
	private static AbstractWidget find(Minecraft client, String label) {
		for (AbstractWidget widget : Compat.widgets(client.screen)) {
			if (widget.visible && widget.getMessage().getString().equals(label)) return widget;
		}
		return client.screen == null ? null : findIn(client.screen.children(), label, 0);
	}

	/** Looks through lists and other containers on a screen for a widget with this label. */
	private static AbstractWidget findIn(List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children, String label, int depth) {
		if (depth > 4) return null;
		for (var child : children) {
			if (child instanceof AbstractWidget widget && widget.visible && widget.getMessage().getString().equals(label)) return widget;
			if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container) {
				AbstractWidget found = findIn(container.children(), label, depth + 1);
				if (found != null) return found;
			}
		}
		return null;
	}

	/** Clicks the widget with this label on the current screen, if there is one. */
	private static void click(ClientGameTestContext context, int scale, String label) {
		int[] at = context.computeOnClient(client -> {
			AbstractWidget widget = find(client, label);
			return widget == null ? null : new int[] {widget.getX() + widget.getWidth() / 2, widget.getY() + widget.getHeight() / 2};
		});
		EventideClient.LOG.info("eventide-check: clicking {} at {}", label, at == null ? "nowhere (not found)" : at[0] + ", " + at[1]);
		if (at == null) return;
		context.getInput().setCursorPos(at[0] * scale, at[1] * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static void check(List<String> failed, String name, boolean ok) {
		EventideClient.LOG.info("eventide-check: {} -> {}", name, ok ? "OK" : "MISSING");
		if (!ok) failed.add(name);
	}
}
