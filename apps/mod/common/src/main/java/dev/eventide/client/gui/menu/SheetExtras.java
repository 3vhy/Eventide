package dev.eventide.client.gui.menu;

import dev.eventide.client.Config;
import dev.eventide.client.Config.Waypoint;
import dev.eventide.client.Draw;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.hud.HudWidget;
import dev.eventide.client.hud.HudWidgets;
import dev.eventide.client.knife.KnifeModel;
import dev.eventide.client.module.CustomSkins;
import dev.eventide.client.module.Knives;
import dev.eventide.client.module.Module;
import dev.eventide.client.module.Modules;
import dev.eventide.client.module.NameDetect;
import dev.eventide.client.module.ProtectedText;
import dev.eventide.client.module.PlayerList;
import dev.eventide.client.module.Setting;
import dev.eventide.client.module.TextHotkeys;
import dev.eventide.client.module.TextRules;
import dev.eventide.client.module.Waypoints;
import dev.eventide.client.hud.SkyLyrics;
import dev.eventide.client.music.Lyrics;
import dev.eventide.client.render.ShaderPacks;
import dev.eventide.client.sky.LivingSky;
import dev.eventide.client.sky.SkyType;
import dev.eventide.client.music.MusicBridge;
import dev.eventide.client.music.NowPlaying;
import dev.eventide.client.ui.Grad;
import dev.eventide.client.ui.Hits;
import dev.eventide.client.ui.Keys;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Type.Weight;
import dev.eventide.client.ui.Widgets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The parts of a settings sheet that aren't plain settings: the Player List's names, Name Protect's
 * text changes, Text Hotkeys, Skin Protect's skin file, the Music Player's status, Waypoints, the
 * Knives slot picker, and a button into the HUD editor for every HUD widget.
 */
final class SheetExtras implements SettingsSheet.Extras {
	private enum Focus {
		NONE, PLAYER, RULE_FIND, RULE_REPLACE, HOTKEY_KEY, HOTKEY_TEXT, WAYPOINT, BIND
	}

	private static final float ROW = 62;
	private static final float FIELD = 36;
	private static final float ITEM = 44;
	private static final float ITEM_GAP = 6;
	private static final float SECTION = 26;
	private static final float HINT = 24;
	private static final float EMPTY = 40;
	private static final float SLOT_GAP = 6;
	private static final float CHIP = 30;

	private final MenuHost screen;
	private Focus focus = Focus.NONE;
	private String playerName = "";
	private String ruleFind = "";
	private String ruleReplace = "";
	private String hotkeyText = "";
	private int hotkeyKey = -1;
	private boolean hotkeyMouse = false;
	private long keyPickedAt = 0;
	private Waypoint renaming = null;
	private String waypointName = "";
	private int knifeMenu = -1;
	private KeyMapping binding = null;
	private float mx;
	private float my;

	SheetExtras(MenuHost screen) {
		this.screen = screen;
	}

	private static HudWidget widgetOf(Module module) {
		for (HudWidget widget : HudWidgets.ALL) {
			if (widget.module == module) return widget;
		}
		return null;
	}

	@Override
	public boolean has(Module module) {
		return widgetOf(module) != null || MenuHost.keyMapping(module) != null || module == Modules.THEME || module == Modules.MUSIC || module == Modules.LYRICS || module == Modules.SKIN_PROTECT
				|| module == Modules.PLAYER_LIST || module == Modules.NAME_PROTECT || module == Modules.TEXT_HOTKEYS
				|| module == Modules.WAYPOINTS || module == Modules.KNIVES || module == Modules.SKIES;
	}

	@Override
	public boolean first(Module module) {
		return module == Modules.KNIVES || module == Modules.SKIES;
	}

	@Override
	public float height(Module module, float width) {
		float h = 0;
		if (MenuHost.keyMapping(module) != null) h += ROW;
		if (widgetOf(module) != null) h += ROW;
		if (module == Modules.THEME) h += ROW;
		if (module == Modules.MUSIC) h += 2 * ROW;
		if (module == Modules.LYRICS) h += ROW + (SkyLyrics.blocked() && Modules.LYRICS_PLACE.is("Sky") ? ROW : 0);
		if (module == Modules.SKIN_PROTECT) h += 2 * ROW;
		if (module == Modules.PLAYER_LIST) h += playersHeight(width);
		if (module == Modules.NAME_PROTECT) h += foundHeight() + listHeight(Config.get().textRules.size());
		if (module == Modules.TEXT_HOTKEYS) h += listHeight(Config.get().textHotkeys.size());
		if (module == Modules.WAYPOINTS) h += waypointsHeight();
		if (module == Modules.KNIVES) h += knivesHeight(width);
		if (module == Modules.SKIES) h += skiesHeight(width);
		return h;
	}

	@Override
	public void draw(Draw d, Module module, float x, float y, float w, float mouseX, float mouseY, Hits hits, long now) {
		mx = mouseX;
		my = mouseY;
		y -= 12;
		KeyMapping mapping = MenuHost.keyMapping(module);
		if (mapping != null) {
			drawBind(d, hits, module, mapping, x, y, w);
			y += ROW;
		}
		if (widgetOf(module) != null) {
			row(d, x, y, w, "Position", "Drag it anywhere in the layout editor", 150);
			button(d, hits, x + w - 132, y + 13, x + w, y + 13 + FIELD, "move", "Edit layout", false, screen::openHudEditor);
			y += ROW;
		}
		if (module == Modules.THEME) {
			row(d, x, y, w, "HUD layout", "Put every HUD widget back where it started", 150);
			button(d, hits, x + w - 112, y + 13, x + w, y + 13 + FIELD, "rotate-ccw", "Reset", false, () -> {
				for (HudWidget each : HudWidgets.ALL) each.resetPosition();
				screen.view().notice("HUD layout reset");
			});
			y += ROW;
		}
		if (module == Modules.MUSIC) {
			String status = MusicBridge.status();
			boolean connected = status.equals("Connected");
			float tw = Type.width(status, 11, Weight.SEMIBOLD);
			row(d, x, y, w, "Status", "Reads what's playing from Windows' media controls", tw + 30);
			Type.right(d, status, x + w, y + 25, 11, Weight.SEMIBOLD, connected ? Tokens.MINT : Tokens.TEXT_2);
			if (connected) Widgets.pulseDot(d, x + w - tw - 12, y + 30.5f, 3.5f, Tokens.MINT, now);
			else Paint.circle(d, x + w - tw - 12, y + 30.5f, 3.5f, Tokens.TEXT_3);
			y += ROW;
			drawLyricsStatus(d, hits, x, y, w);
			y += ROW;
		}
		if (module == Modules.LYRICS) {
			drawLyricsStatus(d, hits, x, y, w);
			y += ROW;
			if (SkyLyrics.blocked() && Modules.LYRICS_PLACE.is("Sky")) {
				row(d, x, y, w, "Shader pack on", "A shader pack draws the sky, so lyrics can't go in it. Pick Screen to see them", 0);
				y += ROW;
			}
		}
		if (module == Modules.SKIN_PROTECT) drawSkinFile(d, hits, x, y, w);
		if (module == Modules.PLAYER_LIST) drawPlayers(d, hits, x, y, w, now);
		if (module == Modules.NAME_PROTECT) drawRules(d, hits, x, y, w, now);
		if (module == Modules.TEXT_HOTKEYS) drawHotkeys(d, hits, x, y, w, now);
		if (module == Modules.WAYPOINTS) drawWaypoints(d, hits, x, y, w, now);
		if (module == Modules.KNIVES) drawKnives(d, hits, x, y, w);
		if (module == Modules.SKIES) drawSkies(d, hits, x, y, w);
	}

	private boolean over(float x1, float y1, float x2, float y2) {
		return mx >= x1 && mx < x2 && my >= y1 && my < y2;
	}

	/** The key that works a module: its key cap, and a click to pick a new one (Esc keeps the old). */
	private void drawBind(Draw d, Hits hits, Module module, KeyMapping mapping, float x, float y, float w) {
		boolean picking = focus == Focus.BIND && binding == mapping;
		String what = module == Modules.THEME ? "Opens this menu" : module == Modules.KNIVES ? "Plays the knife's inspect" : "Works " + module.name;
		row(d, x, y, w, "Key", picking ? "Press the new key, or Esc to keep it" : what + ". Click to change it", 150);
		String label = picking ? "Press a key" : mapping.getTranslatedKeyMessage().getString();
		float bw = Math.max(110, Type.width(label, 10.5f, Weight.SEMIBOLD) + 44);
		float bx = x + w - bw;
		float by = y + 13;
		boolean hover = over(bx, by, x + w, by + FIELD);
		if (picking) {
			Widgets.accentPill(d, bx, by, x + w, by + FIELD, 0.8f);
		} else {
			Widgets.control(d, bx, by, x + w, by + FIELD, 10, hover ? 1 : 0);
		}
		Paint.icon(d, "keyboard", bx + 13, by + (FIELD - 14) / 2f, 14, picking ? 0xFFFFFFFF : Accent.text());
		Type.draw(d, Type.fit(label, bw - 44, 10.5f, Weight.SEMIBOLD), bx + 34, by + (FIELD - 10.5f) / 2f, 10.5f, Weight.SEMIBOLD,
				picking ? 0xFFFFFFFF : Tokens.TEXT);
		hits.add(bx, by, x + w, by + FIELD, () -> {
			focus = Focus.BIND;
			binding = mapping;
		});
	}

	/** Whether the next key pressed becomes a module's key. */
	boolean pickingBind() {
		return focus == Focus.BIND && binding != null;
	}

	/** Makes {@code key} the key being picked (Esc keeps the old one). */
	void bindKey(int key) {
		KeyMapping mapping = binding;
		focus = Focus.NONE;
		binding = null;
		if (mapping == null || key == Keys.ESCAPE) return;
		Minecraft mc = Minecraft.getInstance();
		mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(key));
		KeyMapping.resetMapping();
		mc.options.save();
		screen.view().notice(mapping.getTranslatedKeyMessage().getString() + " it is");
	}

	/** A row's name and one line about it, leaving {@code reserved} on the right for its control. */
	private static void row(Draw d, float x, float y, float w, String name, String description, float reserved) {
		Type.draw(d, Type.fit(name, w - reserved, 11.5f, Weight.SEMIBOLD), x, y + 15, 11.5f, Weight.SEMIBOLD, Tokens.TEXT);
		Type.draw(d, Type.fit(description, w - reserved, 9.5f, Weight.MEDIUM), x, y + 34, 9.5f, Weight.MEDIUM, Tokens.TEXT_3);
	}

	private static void section(Draw d, float x, float y, String label) {
		Type.draw(d, label, x, y + 5, 9.5f, Weight.SEMIBOLD, Tokens.TEXT_2);
	}

	/** A pill button: the accent when {@code primary}, a quiet control otherwise. */
	private void button(Draw d, Hits hits, float x1, float y1, float x2, float y2, String icon, String label, boolean primary,
			Runnable action) {
		float h = y2 - y1;
		boolean hover = over(x1, y1, x2, y2);
		float cap = 11;
		float iconSize = icon == null ? 0 : 14;
		float contentW = iconSize + (icon == null ? 0 : 8) + Type.width(label, cap, Weight.SEMIBOLD);
		float cx = (x1 + x2) / 2f - contentW / 2f;
		if (primary) {
			Widgets.accentPill(d, x1, y1, x2, y2, hover ? 1 : 0.6f);
		} else {
			Widgets.control(d, x1, y1, x2, y2, h / 2f, hover ? 1 : 0);
		}
		if (icon != null) {
			Paint.icon(d, icon, cx, y1 + (h - iconSize) / 2f, iconSize, primary ? 0xFFFFFFFF : hover ? 0xFFFFFFFF : Tokens.LILAC);
			cx += iconSize + 8;
		}
		Type.draw(d, label, cx, y1 + (h - cap) / 2f, cap, Weight.SEMIBOLD, primary ? 0xFFFFFFFF : Tokens.TEXT);
		hits.add(x1, y1, x2, y2, action);
	}

	/** A round icon button, like a list row's delete. */
	private void iconButton(Draw d, Hits hits, float cx, float cy, String icon, boolean danger, boolean lit, Runnable action) {
		float r = 15;
		boolean hover = over(cx - r, cy - r, cx + r, cy + r);
		Paint.circle(d, cx, cy, r, hover ? danger ? 0x33FF5C84 : 0x1FFFFFFF : 0x0AFFFFFF);
		Paint.ring(d, cx, cy, r, 1, hover && danger ? Grad.down(0x66FF5C84, 0x1AFF5C84) : Grad.down(0x33FFFFFF, 0x08FFFFFF));
		int color = hover ? danger ? Tokens.LOW : Tokens.TEXT : lit ? Tokens.TEXT_2 : Tokens.TEXT_3;
		Paint.icon(d, icon, cx - 7, cy - 7, 14, color);
		hits.add(cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1, action);
	}

	/** One item in a list: a faint glass bar. */
	private void item(Draw d, float x, float y, float w) {
		boolean hover = over(x, y, x + w, y + ITEM);
		Paint.rect(d, x, y, x + w, y + ITEM, 14, hover ? 0x12FFFFFF : 0x0AFFFFFF);
		Paint.stroke(d, x, y, x + w, y + ITEM, 14, 1, hover ? Grad.down(0x4DCDB6FF, 0x1ACDB6FF) : Grad.down(0x26FFFFFF, 0x0AFFFFFF));
	}

	private void field(Draw d, Hits hits, float x1, float y, float x2, String icon, String text, String placeholder, Focus which, long now) {
		boolean focused = focus == which;
		Widgets.field(d, x1, y, x2, y + FIELD, icon, text, placeholder, null, focused, over(x1, y, x2, y + FIELD) ? 1 : 0, now);
		hits.add(x1, y, x2, y + FIELD, () -> focus = which);
	}

	private static void hint(Draw d, float x, float y, float w, String text) {
		Type.draw(d, Type.fit(text, w, 9.5f, Weight.MEDIUM), x, y + 4, 9.5f, Weight.MEDIUM, Tokens.TEXT_3);
	}

	private static void empty(Draw d, float x, float y, float w, String text) {
		Paint.rect(d, x, y, x + w, y + EMPTY - 6, 14, 0x06FFFFFF);
		Type.center(d, Type.fit(text, w - 24, 10, Weight.MEDIUM), x + w / 2f, y + (EMPTY - 6 - 10) / 2f, 10, Weight.MEDIUM, Tokens.TEXT_3);
	}

	private static float listHeight(int items) {
		return SECTION + FIELD + 10 + HINT + (items == 0 ? EMPTY : items * (ITEM + ITEM_GAP));
	}

	/** Whether the song playing has lyrics (synced, not synced, searching, or why not), and a button to look again. */
	private void drawLyricsStatus(Draw d, Hits hits, float x, float y, float w) {
		String status = Lyrics.status();
		String detail = Lyrics.detail();
		Lyrics.State state = Lyrics.state();
		int color = state == Lyrics.State.SYNCED ? Tokens.MINT : state == Lyrics.State.UNSYNCED ? Tokens.GOLD
				: state == Lyrics.State.NOT_FOUND || state == Lyrics.State.UNREACHABLE ? Tokens.LOW : Tokens.TEXT_2;
		float buttonW = 104;
		float statusW = Math.min(w * 0.42f, Type.width(status, 11, Weight.SEMIBOLD));
		String about = detail.isEmpty() ? "For the song playing now, from LRCLIB, NetEase or lyrics.ovh" : detail;
		row(d, x, y, w, "Lyrics", about, buttonW + statusW + 34);
		button(d, hits, x + w - buttonW, y + 13, x + w, y + 13 + FIELD, "refresh-cw", "Reload", false, () -> {
			NowPlaying song = MusicBridge.current();
			if (song == null) {
				screen.view().notice("Play a song first");
				return;
			}
			Lyrics.reload(song);
			screen.view().notice("Looking for the lyrics again");
		});
		float sx = x + w - buttonW - 14;
		Type.right(d, Type.fit(status, w * 0.42f, 11, Weight.SEMIBOLD), sx, y + 25, 11, Weight.SEMIBOLD, color);
		Paint.circle(d, sx - statusW - 9, y + 30.5f, 3.5f, color);
	}

	private void drawSkinFile(Draw d, Hits hits, float x, float y, float w) {
		List<String> files = CustomSkins.files();
		row(d, x, y, w, "Skin file", "PNG skins in the eventide/skins folder", 230);
		float right = x + w;
		iconButton(d, hits, right - 16, y + 31, "folder-open", false, true, CustomSkins::openFolder);
		float bx2 = right - 42;
		float bx1 = bx2 - 176;
		float by = y + 15;
		boolean hover = over(bx1, by, bx2, by + 32);
		Widgets.control(d, bx1, by, bx2, by + 32, 16, hover ? 1 : 0);
		String current = Config.get().skinFile;
		String shown = files.isEmpty() ? "No files yet" : current.isBlank() || !files.contains(current) ? "Pick one" : current;
		Type.center(d, Type.fit(shown, 176 - 56, 10.5f, Weight.SEMIBOLD), (bx1 + bx2) / 2f, by + (32 - 10.5f) / 2f, 10.5f, Weight.SEMIBOLD,
				files.isEmpty() ? Tokens.TEXT_3 : Tokens.TEXT);
		if (!files.isEmpty()) {
			Paint.icon(d, "chevron-left", bx1 + 9, by + 9, 14, Tokens.TEXT_2);
			Paint.icon(d, "chevron-right", bx2 - 23, by + 9, 14, Tokens.TEXT_2);
			hits.add(bx1, by, (bx1 + bx2) / 2f, by + 32, () -> pickSkinFile(files, -1));
			hits.add((bx1 + bx2) / 2f, by, bx2, by + 32, () -> pickSkinFile(files, 1));
		}
		y += ROW;
		String status = CustomSkins.status();
		float sw = Math.min(w * 0.55f, Type.width(status, 10.5f, Weight.SEMIBOLD));
		row(d, x, y, w, "Status", "What Skin Protect is showing right now", sw + 16);
		Type.right(d, Type.fit(status, w * 0.55f, 10.5f, Weight.SEMIBOLD), right, y + 25, 10.5f, Weight.SEMIBOLD, Tokens.TEXT_2);
	}

	private static void pickSkinFile(List<String> files, int step) {
		int index = files.indexOf(Config.get().skinFile);
		index = index < 0 ? (step > 0 ? 0 : files.size() - 1) : Math.floorMod(index + step, files.size());
		Config.get().skinFile = files.get(index);
		Config.saveSoon();
		while (!Modules.SKIN_PROTECT_SOURCE.is(CustomSkins.SOURCE_FILE)) Modules.SKIN_PROTECT_SOURCE.next();
	}

	private float playersHeight(float width) {
		List<String> suggestions = PlayerList.suggestions(Minecraft.getInstance(), playerName, 6);
		int count = PlayerList.names().size();
		return SECTION + FIELD + 10 + (suggestions.isEmpty() ? 0 : 38) + (count == 0 ? EMPTY : count * (ITEM + ITEM_GAP));
	}

	/** The Player List: add a name (with names on the server as suggestions), then everyone on the list. */
	private void drawPlayers(Draw d, Hits hits, float x, float y, float w, long now) {
		Minecraft mc = Minecraft.getInstance();
		section(d, x, y, "Players on your list");
		y += SECTION;
		float addW = 92;
		field(d, hits, x, y, x + w - addW - 8, "user-plus", playerName, "Add a player by name", Focus.PLAYER, now);
		button(d, hits, x + w - addW, y, x + w, y + FIELD, "plus", "Add", true, this::addPlayer);
		y += FIELD + 10;

		List<String> suggestions = PlayerList.suggestions(mc, playerName, 6);
		if (!suggestions.isEmpty()) {
			float sx = x;
			for (String name : suggestions) {
				float pw = Type.width(name, 10, Weight.SEMIBOLD) + 34;
				if (sx + pw > x + w) break;
				boolean hover = over(sx, y, sx + pw, y + 28);
				Widgets.control(d, sx, y, sx + pw, y + 28, 14, hover ? 1 : 0);
				Paint.icon(d, "plus", sx + 10, y + 8, 12, hover ? Tokens.LILAC : Tokens.TEXT_2);
				Type.draw(d, name, sx + 26, y + 9, 10, Weight.SEMIBOLD, hover ? Tokens.TEXT : Tokens.TEXT_2);
				hits.add(sx, y, sx + pw, y + 28, () -> {
					if (PlayerList.add(name)) screen.view().notice("Added " + name);
				});
				sx += pw + 6;
			}
			y += 38;
		}

		List<String> names = new ArrayList<>(PlayerList.names());
		if (names.isEmpty()) {
			empty(d, x, y, w, "Nobody yet. Add friends, rivals or staff to see when they're on");
			return;
		}
		for (String name : names) {
			boolean online = PlayerList.isOnline(name);
			PlayerInfo info = online ? PlayerList.info(mc, name) : null;
			item(d, x, y, w);
			float face = 24;
			MenuScreen.face(d, info != null ? info.getSkin() : DefaultPlayerSkin.get(UUIDUtil.createOfflinePlayerUUID(name)), x + 12,
					y + (ITEM - face) / 2f, face, 7);
			Type.draw(d, name, x + 12 + face + 12, y + (ITEM - 11) / 2f, 11, Weight.SEMIBOLD, online ? Tokens.TEXT : Tokens.TEXT_2);
			float delX = x + w - 24;
			iconButton(d, hits, delX, y + ITEM / 2f, "trash-2", true, false, () -> {
				PlayerList.remove(name);
				screen.view().notice("Removed " + name);
			});
			String state = online ? "Online" : "Offline";
			float pw = Type.width(state, 9.5f, Weight.SEMIBOLD) + 30;
			float px = delX - 24 - pw;
			float py = y + (ITEM - 24) / 2f;
			Paint.rect(d, px, py, px + pw, py + 24, 12, online ? 0x1F4FE0B0 : 0x0AFFFFFF);
			Paint.circle(d, px + 12, py + 12, 3, online ? Tokens.MINT : Tokens.TEXT_3);
			Type.draw(d, state, px + 20, py + (24 - 9.5f) / 2f, 9.5f, Weight.SEMIBOLD, online ? Tokens.MINT : Tokens.TEXT_3);
			y += ITEM + ITEM_GAP;
		}
	}

	private static float foundHeight() {
		if (!NameDetect.on()) return 0;
		int count = NameDetect.found().size();
		return SECTION + (count == 0 ? EMPTY : count * (ITEM + ITEM_GAP)) + 10;
	}

	/**
	 * What Auto found on screen, live: each thing as found, what it shows as instead, where it was, and an
	 * eye to leave it as it is. The found text is drawn as it really is (protection paused) so you can see it.
	 */
	private float drawFound(Draw d, Hits hits, float x, float y, float w) {
		if (!NameDetect.on()) return y;
		List<NameDetect.Found> list = NameDetect.found();
		section(d, x, y, list.isEmpty() ? "Found on screen" : "Found on screen  ·  " + list.size());
		y += SECTION;
		if (list.isEmpty()) {
			empty(d, x, y, w, "Nothing yet. On a server, Auto finds your name, stats and its address");
			return y + EMPTY + 10;
		}
		int accent = Accent.get();
		for (NameDetect.Found item : list) {
			boolean off = item.excluded();
			item(d, x, y, w);
			float badge = 28;
			float by = y + (ITEM - badge) / 2f;
			Paint.rect(d, x + 10, by, x + 10 + badge, by + badge, 9, off ? 0x0AFFFFFF : Paint.fade(accent, 0.22f));
			Paint.icon(d, item.kind().icon, x + 10 + (badge - 14) / 2f, by + (badge - 14) / 2f, 14, off ? Tokens.TEXT_3 : 0xFFFFFFFF);
			float tx = x + 10 + badge + 12;
			float eyeX = x + w - 24;
			float room = eyeX - 22 - tx;
			float half = room * 0.5f;
			float ty = y + 10;
			ProtectedText.paused(() -> {
				Type.draw(d, Type.fit(item.shown(), half - 18, 10.5f, Weight.SEMIBOLD), tx, ty, 10.5f, Weight.SEMIBOLD, off ? Tokens.TEXT_2 : Tokens.TEXT);
				return null;
			});
			Paint.icon(d, "arrow-right", tx + half - 14, ty - 1, 12, off ? Tokens.TEXT_3 : Tokens.LILAC);
			Type.draw(d, Type.fit(off ? "left as it is" : item.replacement(), half, 10.5f, Weight.SEMIBOLD), tx + half + 4, ty, 10.5f, Weight.SEMIBOLD,
					off ? Tokens.TEXT_3 : Tokens.LILAC);
			Type.draw(d, Type.fit(item.kind().label + "  ·  " + item.where(), room, 9, Weight.MEDIUM), tx, y + 28, 9, Weight.MEDIUM, Tokens.TEXT_3);
			if (item.kind() != NameDetect.Kind.NAME) {
				iconButton(d, hits, eyeX, y + ITEM / 2f, off ? "eye" : "eye-off", false, !off, () -> {
					NameDetect.toggleExcluded(item);
					screen.view().notice(off ? "Hiding " + item.kind().label.toLowerCase(Locale.ROOT) + " again" : "Leaving it as it is");
				});
			}
			y += ITEM + ITEM_GAP;
		}
		return y + 10;
	}

	/** Name Protect: what Auto found, then your own text changes (find this, show that) for anything else. */
	private void drawRules(Draw d, Hits hits, float x, float y, float w, long now) {
		y = drawFound(d, hits, x, y, w);
		section(d, x, y, NameDetect.on() ? "Your own changes (advanced)" : "Change text on screen");
		y += SECTION;
		float addW = 44;
		float fieldW = (w - addW - 16) / 2f;
		field(d, hits, x, y, x + fieldW, "search", ruleFind, "Find, like Money: #", Focus.RULE_FIND, now);
		field(d, hits, x + fieldW + 8, y, x + 2 * fieldW + 8, "pencil", ruleReplace, "Show instead", Focus.RULE_REPLACE, now);
		plusButton(d, hits, x + w - addW, y, this::addRule);
		y += FIELD + 10;
		hint(d, x, y, w, "# matches any number and * any word. Works on scoreboards, chat, tab and nametags");
		y += HINT;
		List<Config.TextRule> rules = new ArrayList<>(Config.get().textRules);
		if (rules.isEmpty()) {
			empty(d, x, y, w, "No changes yet. Try Kills: #  to  Kills: 9999");
			return;
		}
		for (Config.TextRule rule : rules) {
			item(d, x, y, w);
			float half = (w - 70) / 2f;
			float ty = y + (ITEM - 10.5f) / 2f;
			Type.draw(d, Type.fit(rule.find, half - 8, 10.5f, Weight.SEMIBOLD), x + 14, ty, 10.5f, Weight.SEMIBOLD, Tokens.TEXT);
			Paint.icon(d, "arrow-right", x + 14 + half, y + (ITEM - 13) / 2f, 13, Tokens.LILAC);
			boolean blank = rule.replace.isEmpty();
			Type.draw(d, Type.fit(blank ? "(nothing)" : rule.replace, half - 8, 10.5f, Weight.SEMIBOLD), x + 14 + half + 22, ty, 10.5f,
					Weight.SEMIBOLD, blank ? Tokens.TEXT_3 : Tokens.TEXT);
			iconButton(d, hits, x + w - 24, y + ITEM / 2f, "trash-2", true, false, () -> {
				TextRules.remove(rule);
				screen.view().notice("Removed " + rule.find);
			});
			y += ITEM + ITEM_GAP;
		}
	}

	private void plusButton(Draw d, Hits hits, float x, float y, Runnable action) {
		boolean hover = over(x, y, x + FIELD + 8, y + FIELD);
		Widgets.accentPill(d, x, y, x + FIELD + 8, y + FIELD, hover ? 1 : 0.6f);
		Paint.icon(d, "plus", x + (FIELD + 8 - 16) / 2f, y + (FIELD - 16) / 2f, 16, 0xFFFFFFFF);
		hits.add(x, y, x + FIELD + 8, y + FIELD, action);
	}

	/** Text Hotkeys: pick a key, type a message or /command, then every hotkey with its key. */
	private void drawHotkeys(Draw d, Hits hits, float x, float y, float w, long now) {
		section(d, x, y, "Your hotkeys");
		y += SECTION;
		float keyW = 118;
		float addW = 44;
		boolean picking = focus == Focus.HOTKEY_KEY;
		String keyLabel = picking ? "Press a key" : hotkeyKey < 0 ? "Pick a key" : TextHotkeys.keyName(hotkeyKey, hotkeyMouse);
		boolean hover = over(x, y, x + keyW, y + FIELD);
		if (picking) {
			Widgets.accentPill(d, x, y, x + keyW, y + FIELD, 0.8f);
		} else {
			Widgets.control(d, x, y, x + keyW, y + FIELD, FIELD / 2f, hover ? 1 : 0);
		}
		Paint.icon(d, "keyboard", x + 14, y + (FIELD - 14) / 2f, 14, picking ? 0xFFFFFFFF : Tokens.LILAC);
		Type.draw(d, Type.fit(keyLabel, keyW - 46, 10.5f, Weight.SEMIBOLD), x + 36, y + (FIELD - 10.5f) / 2f, 10.5f, Weight.SEMIBOLD,
				picking ? 0xFFFFFFFF : hotkeyKey < 0 ? Tokens.TEXT_2 : Tokens.TEXT);
		hits.add(x, y, x + keyW, y + FIELD, () -> focus = Focus.HOTKEY_KEY);
		field(d, hits, x + keyW + 8, y, x + w - addW - 8, "message-square", hotkeyText, "Message or /command", Focus.HOTKEY_TEXT, now);
		plusButton(d, hits, x + w - addW, y, this::addHotkey);
		y += FIELD + 10;
		hint(d, x, y, w, "Start with / to run a command. Never fires while you type");
		y += HINT;
		List<Config.TextHotkey> hotkeys = new ArrayList<>(Config.get().textHotkeys);
		if (hotkeys.isEmpty()) {
			empty(d, x, y, w, "No hotkeys yet. Try F6 for /spawn, or G for gg");
			return;
		}
		for (Config.TextHotkey hotkey : hotkeys) {
			item(d, x, y, w);
			String key = TextHotkeys.keyName(hotkey).toUpperCase(Locale.ROOT);
			float kw = Widgets.kbd(d, x + 12, y + (ITEM - 9.5f * 2.4f) / 2f, key, 9.5f);
			boolean command = hotkey.text.startsWith("/");
			float tx = x + 12 + kw + 12;
			Type.draw(d, Type.fit(hotkey.text, x + w - 50 - tx, 10.5f, Weight.SEMIBOLD), tx, y + (ITEM - 10.5f) / 2f, 10.5f, Weight.SEMIBOLD,
					command ? Tokens.LILAC : Tokens.TEXT);
			iconButton(d, hits, x + w - 24, y + ITEM / 2f, "trash-2", true, false, () -> {
				TextHotkeys.remove(hotkey);
				screen.view().notice("Removed the " + TextHotkeys.keyName(hotkey) + " hotkey");
			});
			y += ITEM + ITEM_GAP;
		}
	}

	private float waypointsHeight() {
		Minecraft mc = Minecraft.getInstance();
		int count = Waypoints.worldKey(mc) == null ? 0 : Waypoints.current(mc).size();
		return SECTION + FIELD + 12 + (count == 0 ? EMPTY : count * (ITEM + 8 + ITEM_GAP));
	}

	/** Waypoints in this world: add one where you stand, then each with its colour, name, place and distance. */
	private void drawWaypoints(Draw d, Hits hits, float x, float y, float w, long now) {
		Minecraft mc = Minecraft.getInstance();
		String world = Waypoints.worldKey(mc);
		boolean inWorld = world != null && mc.player != null;
		section(d, x, y, inWorld ? "Waypoints in " + world.substring(world.indexOf(':') + 1) : "Waypoints");
		y += SECTION;
		if (inWorld) {
			button(d, hits, x, y, x + 150, y + FIELD, "map-pin", "Add one here", true, () -> {
				Waypoint added = Waypoints.addHere(mc);
				if (added != null) screen.view().notice("Added " + added.name);
			});
			Type.draw(d, "Click a name to rename it", x + 166, y + (FIELD - 9.5f) / 2f, 9.5f, Weight.MEDIUM, Tokens.TEXT_3);
		} else {
			Type.draw(d, "Join a world or server to add waypoints", x, y + (FIELD - 10.5f) / 2f, 10.5f, Weight.MEDIUM, Tokens.TEXT_2);
		}
		y += FIELD + 12;
		List<Waypoint> list = inWorld ? new ArrayList<>(Waypoints.current(mc)) : List.of();
		if (list.isEmpty()) {
			empty(d, x, y, w, inWorld ? "No waypoints yet. Stand somewhere and add one" : "Your waypoints show here in game");
			return;
		}
		float h = ITEM + 8;
		for (Waypoint waypoint : list) {
			boolean hover = over(x, y, x + w, y + h);
			Paint.rect(d, x, y, x + w, y + h, 14, hover ? 0x12FFFFFF : 0x0AFFFFFF);
			Paint.stroke(d, x, y, x + w, y + h, 14, 1, hover ? 0x26CDB6FF : 0x12FFFFFF);
			int color = waypoint.color | 0xFF000000;
			float dot = 9;
			float dcx = x + 22;
			float dcy = y + h / 2f;
			Paint.circle(d, dcx, dcy, dot, color);
			Paint.ring(d, dcx, dcy, dot, 1, 0x40FFFFFF);
			hits.add(dcx - dot - 2, dcy - dot - 2, dcx + dot + 2, dcy + dot + 2, () -> {
				int[] swatches = Setting.Color.SWATCHES;
				int index = 0;
				for (int i = 0; i < swatches.length; i++) if (swatches[i] == color) index = i;
				waypoint.color = swatches[(index + 1) % swatches.length];
				Config.saveSoon();
			});
			float tx = x + 42;
			boolean editing = waypoint == renaming && focus == Focus.WAYPOINT;
			if (editing) {
				Widgets.field(d, tx, y + 8, tx + 170, y + h - 8, "pencil", waypointName, "Name", null, true, 0, now);
			} else {
				Type.draw(d, Type.fit(waypoint.name, 170, 11, Weight.SEMIBOLD), tx, y + 12, 11, Weight.SEMIBOLD, Tokens.TEXT);
				String place = waypoint.x + ", " + waypoint.y + ", " + waypoint.z + "  ·  " + dimensionName(waypoint.dimension);
				Type.draw(d, Type.fit(place, 200, 9, Weight.MEDIUM), tx, y + 31, 9, Weight.MEDIUM, Tokens.TEXT_3);
				hits.add(tx, y + 6, tx + 170, y + 26, () -> {
					renaming = waypoint;
					waypointName = waypoint.name;
					focus = Focus.WAYPOINT;
				});
			}
			float right = x + w - 24;
			iconButton(d, hits, right, y + h / 2f, "trash-2", true, false, () -> Waypoints.remove(mc, waypoint));
			iconButton(d, hits, right - 34, y + h / 2f, waypoint.visible ? "eye" : "eye-off", false, waypoint.visible, () -> {
				waypoint.visible = !waypoint.visible;
				Config.saveSoon();
			});
			String distance = Math.round(Waypoints.distance(mc, waypoint)) + "m";
			Type.right(d, distance, right - 58, y + (h - 10.5f) / 2f, 10.5f, Weight.SEMIBOLD, Tokens.LILAC);
			y += h + ITEM_GAP;
		}
	}

	private static final float PREVIEW_H = 150;
	private static final float THUMB_GAP = 8;
	private static final float THUMB_LABEL = 20;

	private static float thumbWidth(float width) {
		return (width - 4 * THUMB_GAP) / 5f;
	}

	private float skiesHeight(float width) {
		float th = thumbWidth(width) * 9 / 16f;
		int rows = (SkyType.values().length + 4) / 5;
		return PREVIEW_H + 14 + rows * (th + THUMB_LABEL) + 6 + (LivingSky.gpuMillis() >= 0 ? ROW : 0) + (ShaderPacks.inUse() ? ROW : 0);
	}

	/**
	 * The Skies picker, like the website's: a big live preview of the sky with its name, then every sky as a
	 * live thumbnail; the one you see has a glowing border, and picking another crossfades to it.
	 */
	private void drawSkies(Draw d, Hits hits, float x, float y, float w) {
		float tw = thumbWidth(w);
		float th = tw * 9 / 16f;
		boolean live = LivingSky.drawThumbnails(192, 108, 720, 264);
		SkyType current = LivingSky.selected();
		int accent = Accent.get();
		Paint.shadow(d, x, y, x + w, y + PREVIEW_H, 18, 0, 8, 24, -6, 0x8C000000);
		if (live) Paint.image(d, LivingSky.preview(), x, y, x + w, y + PREVIEW_H, 18, 0, 1, 1, 0, 0xFFFFFFFF, true);
		else Paint.rect(d, x, y, x + w, y + PREVIEW_H, 18, Tokens.SURFACE);
		Paint.rect(d, x, y + PREVIEW_H * 0.45f, x + w, y + PREVIEW_H, 18, Grad.down(0x00050410, 0xB3050410));
		Paint.stroke(d, x, y, x + w, y + PREVIEW_H, 18, 1, Grad.down(0x40FFFFFF, 0x0DFFFFFF));
		Type.draw(d, current.label, x + 18, y + PREVIEW_H - 46, 15, Weight.BOLD, 0xFFFFFFFF);
		Type.draw(d, Type.fit(current.description, w - 36, 9.5f, Weight.MEDIUM), x + 18, y + PREVIEW_H - 22, 9.5f, Weight.MEDIUM, 0xD9F2EEFF);
		y += PREVIEW_H + 14;
		SkyType[] all = SkyType.values();
		for (int i = 0; i < all.length; i++) {
			SkyType sky = all[i];
			float tx = x + (i % 5) * (tw + THUMB_GAP);
			float ty = y + (i / 5) * (th + THUMB_LABEL);
			boolean active = sky == current;
			boolean hover = over(tx, ty, tx + tw, ty + th);
			if (active) Paint.shadow(d, tx, ty, tx + tw, ty + th, 10, 0, 0, 12, 0, Accent.glow(0.85f));
			if (live) Paint.image(d, LivingSky.thumbnail(sky), tx, ty, tx + tw, ty + th, 10, 0, 1, 1, 0, 0xFFFFFFFF, true);
			else Paint.rect(d, tx, ty, tx + tw, ty + th, 10, Tokens.SURFACE);
			if (active) {
				Paint.stroke(d, tx, ty, tx + tw, ty + th, 10, 2, Accent.lighten(accent, 0.3f));
			} else {
				Paint.stroke(d, tx, ty, tx + tw, ty + th, 10, 1, hover ? Grad.down(0x99E2D4FF, 0x40CDB6FF) : Grad.down(0x33FFFFFF, 0x0DFFFFFF));
			}
			Type.center(d, Type.fit(sky.label, tw, 8.5f, Weight.SEMIBOLD), tx + tw / 2f, ty + th + 6, 8.5f, Weight.SEMIBOLD,
					active ? Tokens.TEXT : hover ? Tokens.TEXT_2 : Tokens.TEXT_3);
			hits.add(tx, ty, tx + tw, ty + th + THUMB_LABEL - 4, () -> {
				LivingSky.select(sky);
				if (!Modules.SKIES.enabled()) Modules.SKIES.set(true);
			});
		}
		y += ((all.length + 4) / 5) * (th + THUMB_LABEL) + 6;
		double cost = LivingSky.gpuMillis();
		if (cost >= 0) {
			String label = String.format(Locale.ROOT, "%.2f ms a frame", cost);
			row(d, x, y, w, "Cost", "How long your graphics card spends on the sky", Type.width(label, 11, Weight.SEMIBOLD) + 16);
			Type.right(d, label, x + w, y + 25, 11, Weight.SEMIBOLD, cost <= 0.6 ? Tokens.MINT : Tokens.GOLD);
			y += ROW;
		}
		if (ShaderPacks.inUse()) {
			row(d, x, y, w, "Shader pack on", "Your shader pack draws the sky, so Skies waits until you turn it off", 0);
		}
	}

	private static int markedSlots() {
		int count = 0;
		for (int i = 0; i < 9; i++) if (Knives.slot(i) != null) count++;
		return count;
	}

	private static float slotSize(float width) {
		return (width - 8 * SLOT_GAP) / 9f;
	}

	private static float knifeChipsHeight() {
		int rows = (KnifeModel.values().length + 3) / 4;
		return rows * CHIP + (rows - 1) * SLOT_GAP + 10;
	}

	private float knivesHeight(float width) {
		if (knifeMenu >= 0 && Knives.slot(knifeMenu) == null) knifeMenu = -1;
		int marked = markedSlots();
		return SECTION + slotSize(width) + 12 + HINT + marked * (ITEM + ITEM_GAP) + (knifeMenu >= 0 ? knifeChipsHeight() : 0) + 8 + ROW;
	}

	private static ItemStack hotbarItem(int slot) {
		Player player = Minecraft.getInstance().player;
		return player == null ? ItemStack.EMPTY : player.getInventory().getItem(slot);
	}

	/** An item's picture, {@code size} across, sharp and in place at any menu scale. */
	private static void itemIcon(Draw d, ItemStack stack, float x, float y, float size) {
		d.push();
		d.translate(x, y);
		d.scale(size / 16f);
		d.item(stack, 0, 0);
		d.pop();
	}

	/**
	 * The Knives slot picker: your hotbar as nine glass slots with the items in them. Click a slot to hold it
	 * as a knife (it lights up), right click to change its knife; each knife slot then gets a row with a
	 * picker for its knife. Under them, a button that puts the viewmodel back.
	 */
	private void drawKnives(Draw d, Hits hits, float x, float y, float w) {
		Player player = Minecraft.getInstance().player;
		int selected = player == null ? -1 : player.getInventory().getSelectedSlot();
		section(d, x, y, "Knife slots");
		y += SECTION;
		float size = slotSize(w);
		int accent = Accent.get();
		for (int i = 0; i < 9; i++) {
			final int slot = i;
			float sx = x + i * (size + SLOT_GAP);
			KnifeModel model = Knives.slot(i);
			boolean marked = model != null;
			boolean hover = over(sx, y, sx + size, y + size);
			if (marked) {
				Paint.shadow(d, sx, y, sx + size, y + size, 12, 0, 0, 10, 0, Accent.glow(hover ? 0.9f : 0.65f));
				Paint.rect(d, sx, y, sx + size, y + size, 12, Paint.fade(accent, hover ? 0.3f : 0.22f));
				Paint.stroke(d, sx, y, sx + size, y + size, 12, 1.5f, Accent.lighten(accent, 0.25f));
			} else {
				Widgets.control(d, sx, y, sx + size, y + size, 12, hover ? 1 : 0);
			}
			ItemStack stack = hotbarItem(i);
			if (!stack.isEmpty()) {
				itemIcon(d, stack, sx + (size - 24) / 2f, y + (size - 24) / 2f, 24);
			} else if (marked) {
				Paint.icon(d, "sword", sx + (size - 16) / 2f, y + (size - 16) / 2f, 16, 0xFFFFFFFF);
			}
			Type.draw(d, String.valueOf(i + 1), sx + 6, y + 5, 7, Weight.SEMIBOLD, marked ? 0xFFFFFFFF : Tokens.TEXT_3);
			if (i == selected) Paint.rect(d, sx + size / 2f - 6, y + size + 4, sx + size / 2f + 6, y + size + 6, 1, 0x99FFFFFF);
			hits.add(sx, y, sx + size, y + size, () -> {
				Knives.toggleSlot(slot);
				knifeMenu = -1;
				KnifeModel now = Knives.slot(slot);
				screen.view().notice(now == null ? "Slot " + (slot + 1) + " is back to normal" : "Slot " + (slot + 1) + " is a " + now.label);
			}, () -> {
				if (Knives.slot(slot) == null) Knives.toggleSlot(slot);
				else Knives.cycleSlot(slot, 1);
			});
		}
		y += size + 12;
		hint(d, x, y, w, player == null ? "Click a slot to hold it as a knife. Your items show here in game" : "Click a slot to hold it as a knife, right click to change which");
		y += HINT;
		for (int i = 0; i < 9; i++) {
			KnifeModel model = Knives.slot(i);
			if (model == null) continue;
			final int slot = i;
			item(d, x, y, w);
			float badge = 28;
			float by = y + (ITEM - badge) / 2f;
			Paint.rect(d, x + 10, by, x + 10 + badge, by + badge, 9, Paint.fade(accent, 0.22f));
			Paint.stroke(d, x + 10, by, x + 10 + badge, by + badge, 9, 1, Paint.fade(Accent.lighten(accent, 0.25f), 0.7f));
			ItemStack stack = hotbarItem(i);
			if (!stack.isEmpty()) itemIcon(d, stack, x + 10 + (badge - 20) / 2f, by + (badge - 20) / 2f, 20);
			else Type.center(d, String.valueOf(i + 1), x + 10 + badge / 2f, by + (badge - 10) / 2f, 10, Weight.SEMIBOLD, 0xFFFFFFFF);
			float tx = x + 10 + badge + 12;
			String held = stack.isEmpty() ? "Empty slot" : stack.getHoverName().getString();
			Type.draw(d, "Slot " + (i + 1), tx, y + 11, 10.5f, Weight.SEMIBOLD, Tokens.TEXT);
			Type.draw(d, Type.fit(held, 120, 9, Weight.MEDIUM), tx, y + 28, 9, Weight.MEDIUM, Tokens.TEXT_3);
			float delX = x + w - 24;
			iconButton(d, hits, delX, y + ITEM / 2f, "x", true, false, () -> {
				Knives.setSlot(slot, null);
				if (knifeMenu == slot) knifeMenu = -1;
			});
			float px2 = delX - 24;
			float px1 = px2 - 150;
			float py = y + (ITEM - 30) / 2f;
			boolean open = knifeMenu == slot;
			boolean hover = over(px1, py, px2, py + 30);
			if (open) {
				Paint.rect(d, px1, py, px2, py + 30, 15, Paint.fade(accent, 0.25f));
				Paint.stroke(d, px1, py, px2, py + 30, 15, 1, Accent.lighten(accent, 0.25f));
			} else {
				Widgets.control(d, px1, py, px2, py + 30, 15, hover ? 1 : 0);
			}
			Paint.icon(d, "sword", px1 + 12, py + 8, 14, open ? 0xFFFFFFFF : Tokens.LILAC);
			Type.draw(d, Type.fit(model.label, 150 - 62, 10.5f, Weight.SEMIBOLD), px1 + 34, py + (30 - 10.5f) / 2f, 10.5f, Weight.SEMIBOLD, Tokens.TEXT);
			Paint.icon(d, open ? "chevron-up" : "chevron-down", px2 - 24, py + 8, 14, Tokens.TEXT_2);
			hits.add(px1, py, px2, py + 30, () -> knifeMenu = open ? -1 : slot, () -> Knives.cycleSlot(slot, 1));
			y += ITEM + ITEM_GAP;
			if (open) {
				KnifeModel[] all = KnifeModel.values();
				float chipW = (w - 3 * SLOT_GAP) / 4f;
				for (int m = 0; m < all.length; m++) {
					KnifeModel each = all[m];
					float cx = x + (m % 4) * (chipW + SLOT_GAP);
					float cy = y + (m / 4) * (CHIP + SLOT_GAP);
					boolean current = each == model;
					boolean chipHover = over(cx, cy, cx + chipW, cy + CHIP);
					if (current) {
						Widgets.accentPill(d, cx, cy, cx + chipW, cy + CHIP, 0.9f);
					} else {
						Widgets.control(d, cx, cy, cx + chipW, cy + CHIP, CHIP / 2f, chipHover ? 1 : 0);
					}
					Type.center(d, Type.fit(each.label, chipW - 16, 10, Weight.SEMIBOLD), cx + chipW / 2f, cy + (CHIP - 10) / 2f, 10, Weight.SEMIBOLD,
							current ? 0xFFFFFFFF : chipHover ? Tokens.TEXT : Tokens.TEXT_2);
					hits.add(cx, cy, cx + chipW, cy + CHIP, () -> {
						Knives.setSlot(slot, each);
						knifeMenu = -1;
					});
				}
				y += knifeChipsHeight();
			}
		}
		y += 8;
		row(d, x, y, w, "Viewmodel", "Put the knife's place, turn, size and speeds back", 120);
		button(d, hits, x + w - 112, y + 13, x + w, y + 13 + FIELD, "rotate-ccw", "Reset", false, () -> {
			Knives.resetViewmodel();
			screen.view().notice("Knife viewmodel reset");
		});
	}

	private static String dimensionName(String key) {
		if (key.contains("the_nether")) return "Nether";
		if (key.contains("the_end")) return "End";
		if (key.contains("overworld")) return "Overworld";
		int slash = key.lastIndexOf('/');
		return slash >= 0 ? key.substring(slash + 1).replace("]", "").trim() : key;
	}

	private void addPlayer() {
		String name = playerName.trim();
		if (name.isEmpty()) {
			screen.view().notice("Type a name first");
			focus = Focus.PLAYER;
		} else if (!PlayerList.isValidName(name)) {
			screen.view().notice("That isn't a Minecraft name");
		} else if (!PlayerList.add(name)) {
			screen.view().notice(name + " is already on the list");
		} else {
			screen.view().notice("Added " + name);
			playerName = "";
		}
	}

	private void addRule() {
		if (ruleFind.isBlank()) {
			screen.view().notice("Type what to find first");
			focus = Focus.RULE_FIND;
			return;
		}
		TextRules.add(ruleFind, ruleReplace);
		screen.view().notice("Changing " + ruleFind.trim());
		ruleFind = "";
		ruleReplace = "";
		focus = Focus.RULE_FIND;
	}

	private void addHotkey() {
		if (hotkeyKey < 0) {
			screen.view().notice("Pick a key first");
			focus = Focus.HOTKEY_KEY;
			return;
		}
		if (hotkeyText.isBlank()) {
			screen.view().notice("Type a message or command first");
			focus = Focus.HOTKEY_TEXT;
			return;
		}
		TextHotkeys.add(hotkeyKey, hotkeyMouse, hotkeyText);
		screen.view().notice(TextHotkeys.keyName(hotkeyKey, hotkeyMouse) + " sends " + hotkeyText.trim());
		hotkeyText = "";
		hotkeyKey = -1;
		hotkeyMouse = false;
		focus = Focus.NONE;
	}

	private void finishRename() {
		if (renaming != null && !waypointName.isBlank()) {
			renaming.name = waypointName.trim();
			Config.saveSoon();
		}
		renaming = null;
	}

	/** Whether the next key (or extra mouse button) is a new hotkey's key. */
	boolean pickingKey() {
		return focus == Focus.HOTKEY_KEY;
	}

	/** An extra mouse button pressed while picking a hotkey's key; left and right clicks cancel. */
	void pickMouse(int button) {
		if (button >= 2) {
			hotkeyKey = button;
			hotkeyMouse = true;
		}
		focus = Focus.HOTKEY_TEXT;
	}

	@Override
	public boolean keyPressed(int key) {
		if (focus == Focus.HOTKEY_KEY) {
			if (key != Keys.ESCAPE) {
				hotkeyKey = key;
				hotkeyMouse = false;
				keyPickedAt = System.currentTimeMillis();
			}
			focus = Focus.HOTKEY_TEXT;
			return true;
		}
		if (key == Keys.BACKSPACE) {
			switch (focus) {
				case PLAYER -> playerName = dropLast(playerName);
				case RULE_FIND -> ruleFind = dropLast(ruleFind);
				case RULE_REPLACE -> ruleReplace = dropLast(ruleReplace);
				case HOTKEY_TEXT -> hotkeyText = dropLast(hotkeyText);
				case WAYPOINT -> waypointName = dropLast(waypointName);
				default -> {
				}
			}
			return true;
		}
		if (Keys.enter(key)) {
			switch (focus) {
				case PLAYER -> addPlayer();
				case RULE_FIND -> focus = Focus.RULE_REPLACE;
				case RULE_REPLACE -> addRule();
				case HOTKEY_TEXT -> addHotkey();
				case WAYPOINT -> {
					finishRename();
					focus = Focus.NONE;
				}
				default -> {
				}
			}
			return true;
		}
		if (key == Keys.ESCAPE) {
			stopTyping();
			return true;
		}
		if (key == Keys.TAB && focus == Focus.RULE_FIND) {
			focus = Focus.RULE_REPLACE;
			return true;
		}
		return true;
	}

	@Override
	public boolean charTyped(String typed) {
		if (System.currentTimeMillis() - keyPickedAt < 150) return true;
		switch (focus) {
			case PLAYER -> {
				if (playerName.length() < 16 && typed.matches("[A-Za-z0-9_]")) playerName += typed;
			}
			case RULE_FIND -> {
				if (ruleFind.length() < 48) ruleFind += typed;
			}
			case RULE_REPLACE -> {
				if (ruleReplace.length() < 48) ruleReplace += typed;
			}
			case HOTKEY_TEXT -> {
				if (hotkeyText.length() < TextHotkeys.MAX_LENGTH) hotkeyText += typed;
			}
			case WAYPOINT -> {
				if (waypointName.length() < 24) waypointName += typed;
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean typing() {
		return focus != Focus.NONE;
	}

	@Override
	public void stopTyping() {
		if (focus == Focus.WAYPOINT) finishRename();
		focus = Focus.NONE;
		binding = null;
	}

	private static String dropLast(String text) {
		return text.isEmpty() ? text : text.substring(0, text.length() - 1);
	}
}
