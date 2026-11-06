package dev.eventide.client.gui;

import dev.eventide.client.Config;
import dev.eventide.client.Draw;
import dev.eventide.client.EventideClient;
import dev.eventide.client.compat.Compat;
import dev.eventide.client.compat.CompatTitleScreen;
import dev.eventide.client.gui.menu.MenuHost;
import dev.eventide.client.module.Module.Category;
import dev.eventide.client.music.MusicArt;
import dev.eventide.client.ui.Anim;
import dev.eventide.client.ui.Ease;
import dev.eventide.client.ui.Logo;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Type.Weight;
import dev.eventide.client.ui.Widgets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Eventide's main menu: on a deep violet haze with a faint dot grid, a greeting with your head, a big two-tone
 * Eventide wordmark with a soft glow, Singleplayer (filled in the theme colour), Multiplayer and Realms as round
 * buttons, a Continue card that takes you straight back to the world or server you last played on, and a dock
 * at the bottom (Options, the Eventide menu, Mods, Language, Quit). Everything is laid out on a column sized
 * from the window, in the reference's units, and eases in as the menu opens.
 */
public final class MainMenuScreen extends CompatTitleScreen {
	/** The content column's width in the reference's units; everything below is measured in them. */
	private static final float COLUMN = 1185;
	private static final long INTRO_MS = 280;
	private static final String[] DOCK = {"options", "eventide", "mods", "language", "quit"};
	private static final String[] DOCK_ICONS = {"settings", "", "layout-grid", "languages", "power"};
	private static final String[] DOCK_LABELS = {"Options", "Eventide", "Mods", "Language", "Quit"};

	private final long openedAt = System.currentTimeMillis();
	private final Map<String, Anim> hover = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, float[]> centres = new HashMap<>();

	private record Hit(String id, float x1, float y1, float x2, float y2, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	public MainMenuScreen() {
	}

	/** Swaps Minecraft's own title screen for this one when Main Menu is on. */
	public static Screen replace(Screen screen) {
		if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.TitleScreen.class
				&& dev.eventide.client.module.Modules.MAIN_MENU.enabled()) {
			return new MainMenuScreen();
		}
		return screen;
	}

	/** Where a button was last drawn ("singleplayer", "multiplayer", "realms", "continue" or a dock button), for the in-game test. */
	public float[] centre(String id) {
		return centres.get(id);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected void drawBackdrop(Draw d) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) return;
		d.fill(0, 0, this.width, this.height, 0xFF0F0D26);
		double t = System.currentTimeMillis() / 1000.0;
		float w = this.width;
		float h = this.height;
		haze(d, w * 0.2f + (float) Math.sin(t * 0.07) * w * 0.03f, h * 0.24f, h * 0.7f, 0x6E3A3296);
		haze(d, w * 0.82f, h * 0.7f + (float) Math.cos(t * 0.05) * h * 0.04f, h * 0.8f, 0x664B41BC);
		haze(d, w * 0.55f + (float) Math.cos(t * 0.06) * w * 0.02f, h * 0.36f, h * 0.52f, 0x3D6A5CE6);
		haze(d, w * 0.12f, h * 0.92f, h * 0.6f, 0x58302A84);
		haze(d, w * 0.5f, h * 1.05f, h * 0.6f, 0x483A3394);
		float step = Math.max(9, h / 21f);
		int dot = 0x10FFFFFF;
		for (float y = step / 2; y < h; y += step) {
			for (float x = step / 2; x < w; x += step) Paint.rect(d, x - 0.5f, y - 0.5f, x + 0.5f, y + 0.5f, 0, dot);
		}
	}

	private static void haze(Draw d, float cx, float cy, float radius, int color) {
		Paint.glow(d, cx, cy, radius * 0.18f, radius * 0.82f, color);
	}

	@Override
	protected void draw(Draw d, int mouseX, int mouseY, float delta) {
		Minecraft mc = Minecraft.getInstance();
		hits.clear();
		long now = System.currentTimeMillis();
		float column = Math.min(this.height * 0.8f, this.width * 0.86f);
		float u = column / COLUMN;
		float cx = this.width / 2f;
		float left = cx - column / 2f;
		float dockH = 145 * u;
		float content = 705 * u;
		float bottomRoom = dockH + 56 * u;
		float top = Math.max(6, (this.height - bottomRoom - content) / 2f - 14 * u);

		float in = intro(now, 0);
		float old = Paint.opacity(Paint.opacity() * in);
		greeting(d, mc, cx, top + 4 * u * (1 - in), u);
		Paint.opacity(old);

		in = intro(now, 50);
		old = Paint.opacity(Paint.opacity() * in);
		wordmark(d, cx, top + (612 - 405) * u + 6 * u * (1 - in), u);
		Paint.opacity(old);

		in = intro(now, 100);
		old = Paint.opacity(Paint.opacity() * in);
		float row = top + (777 - 405) * u + 6 * u * (1 - in);
		float bh = 113 * u;
		float gap = 33 * u;
		float[] widths = {438 * u, 372 * u, 0};
		widths[2] = column - widths[0] - widths[1] - 2 * gap;
		float x = left;
		button(d, "singleplayer", "Singleplayer", "play", true, x, row, widths[0], bh, u, mouseX, mouseY,
				() -> mc.setScreen(new SelectWorldScreen(this)));
		x += widths[0] + gap;
		button(d, "multiplayer", "Multiplayer", "server", false, x, row, widths[1], bh, u, mouseX, mouseY,
				() -> mc.setScreen(mc.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this)));
		x += widths[1] + gap;
		button(d, "realms", "Realms", "cloud", false, x, row, widths[2], bh, u, mouseX, mouseY,
				() -> mc.setScreen(new com.mojang.realmsclient.RealmsMainScreen(this)));
		Paint.opacity(old);

		in = intro(now, 150);
		Config.LastPlayed last = LastPlayed.get();
		if (last != null) {
			old = Paint.opacity(Paint.opacity() * in);
			continueCard(d, last, left, top + (940 - 405) * u + 6 * u * (1 - in), column, 168 * u, u, mouseX, mouseY);
			Paint.opacity(old);
		}

		in = intro(now, 200);
		old = Paint.opacity(Paint.opacity() * in);
		float dockTop = Math.max(top + content + 28 * u, this.height - 36 * u - dockH) + 6 * u * (1 - in);
		dock(d, mc, cx, dockTop, dockH, u, mouseX, mouseY);
		Paint.opacity(old);

		float small = Math.max(3.4f, 18 * u);
		Type.draw(d, "Eventide " + EventideClient.version() + " · Minecraft " + EventideClient.minecraftVersion(), 6, this.height - 6 - small, small,
				Weight.MEDIUM, Tokens.TEXT_3);
		String legal = "Copyright Mojang AB. Do not distribute!";
		Type.right(d, legal, this.width - 6, this.height - 6 - small, small, Weight.MEDIUM, Tokens.TEXT_3);
	}

	/** How far an element starting {@code delay} ms after the menu opened has eased in (0..1). */
	private float intro(long now, long delay) {
		return Ease.OUT.apply(Math.max(0, Math.min(1, (now - openedAt - delay) / (double) INTRO_MS)));
	}

	private Anim hover(String id) {
		return hover.computeIfAbsent(id, k -> new Anim(0, Tokens.T_HOVER, Ease.OUT));
	}

	private static String greetingWord() {
		int hour = LocalTime.now().getHour();
		if (hour < 5) return "Good night";
		if (hour < 12) return "Good morning";
		if (hour < 18) return "Good afternoon";
		return "Good evening";
	}

	/** "Good evening, <you>" with your head, on a small dark pill. */
	private void greeting(Draw d, Minecraft mc, float cx, float top, float u) {
		String text = greetingWord() + ", " + dev.eventide.client.module.NameProtect.apply(mc.getUser().getName());
		float h = 70 * u;
		float cap = 24 * u;
		float face = 40 * u;
		float pad = 26 * u;
		float w = pad + face + 20 * u + Type.width(text, cap, Weight.MEDIUM) + pad + 6 * u;
		float x = cx - w / 2f;
		Paint.rect(d, x, top, x + w, top + h, h / 2f, 0xE0110E22);
		Paint.stroke(d, x, top, x + w, top + h, h / 2f, hair(d), 0x1FFFFFFF);
		MenuHost.face(d, MenuNameTag.ownSkin(mc), x + pad, top + (h - face) / 2f, face, face / 2f);
		Type.draw(d, text, x + pad + face + 20 * u, top + (h - cap) / 2f, cap, Weight.MEDIUM, 0xFFE4E1F0);
	}

	/** "Even" in near-white and "tide" in lavender, big and bold, with a soft glow behind. */
	private void wordmark(Draw d, float cx, float middle, float u) {
		float cap = 112 * u;
		String first = "Even";
		String second = "tide";
		float w1 = Type.width(first, cap, Weight.BOLD);
		float w2 = Type.width(second, cap, Weight.BOLD);
		float x = cx - (w1 + w2) / 2f;
		int lavender = Accent.lighten(Accent.get(), 0.48f);
		Paint.glow(d, cx, middle, 40 * u, 260 * u, Paint.fade(lavender, 0.2f));
		Type.draw(d, first, x, middle - cap / 2f, cap, Weight.BOLD, 0xFFECE9F5);
		Type.draw(d, second, x + w1, middle - cap / 2f, cap, Weight.BOLD, lavender);
	}

	/** A round button: the primary one filled in the theme colour with a glow, the others dark with a fine edge. */
	private void button(Draw d, String id, String label, String icon, boolean primary, float x, float y, float w, float h, float u, int mx, int my,
			Runnable action) {
		boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
		Anim lit = hover(id);
		lit.set(over);
		float t = lit.get();
		float r = h / 2f;
		int accent = Accent.get();
		if (primary) {
			int fill = Accent.lighten(accent, 0.2f + 0.08f * t);
			Paint.shadow(d, x, y, x + w, y + h, r, 0, 3 * u, 46 * u, 0, Paint.fade(fill, 0.42f + 0.18f * t));
			Paint.rect(d, x, y, x + w, y + h, r, fill);
			Paint.stroke(d, x, y, x + w, y + h, r, hair(d), Paint.fade(Accent.lighten(accent, 0.6f), 0.55f));
		} else {
			Paint.shadow(d, x, y, x + w, y + h, r, 0, 2 * u, 26 * u, 0, 0x40000000);
			Paint.rect(d, x, y, x + w, y + h, r, Widgets.mixColor(0xEC100D21, 0xF21C1838, t));
			Paint.stroke(d, x, y, x + w, y + h, r, hair(d), Widgets.mixColor(0x1FFFFFFF, Paint.fade(Accent.lighten(accent, 0.4f), 0.55f), t));
		}
		float cap = 30 * u;
		float iconSize = 34 * u;
		float gap = 24 * u;
		float content = iconSize + gap + Type.width(label, cap, Weight.BOLD);
		float cx = x + (w - content) / 2f;
		float cy = y + h / 2f;
		Paint.icon(d, icon, cx, cy - iconSize / 2f, iconSize, 1.3f, icon.equals("play") || icon.equals("cloud"), 0xFFFFFFFF, null);
		Type.draw(d, label, cx + iconSize + gap, cy - cap / 2f, cap, Weight.BOLD, 0xFFFFFFFF);
		hits.add(new Hit(id, x, y, x + w, y + h, action));
		centres.put(id, new float[] {x + w / 2f, y + h / 2f});
	}

	/** Continue: the world or server you last played on, its icon, how long ago, and a round play button. */
	private void continueCard(Draw d, Config.LastPlayed last, float x, float y, float w, float h, float u, int mx, int my) {
		boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
		Anim lit = hover("continue");
		lit.set(over);
		float t = lit.get();
		float r = 46 * u;
		Paint.shadow(d, x, y, x + w, y + h, r, 0, 2 * u, 30 * u, 0, 0x44000000);
		Paint.rect(d, x, y, x + w, y + h, r, Widgets.mixColor(0xE6110E22, 0xEE18142F, t));
		Paint.stroke(d, x, y, x + w, y + h, r, hair(d), Widgets.mixColor(0x1AFFFFFF, Paint.fade(Accent.lighten(Accent.get(), 0.4f), 0.45f), t));
		float icon = 96 * u;
		float ix = x + 30 * u;
		float iy = y + (h - icon) / 2f;
		byte[] png = LastPlayed.icon(last);
		if (png == null || !MusicArt.PLACE.draw(d, png, last.time, ix, iy, icon, 16 * u)) {
			Paint.rect(d, ix, iy, ix + icon, iy + icon, 16 * u, 0xFF221D3E);
			float glyph = icon * 0.5f;
			Paint.icon(d, last.kind.equals("server") ? "server" : "globe", ix + (icon - glyph) / 2f, iy + (icon - glyph) / 2f, glyph, Accent.lighten(Accent.get(), 0.45f));
		}
		float tx = ix + icon + 34 * u;
		float playR = 46 * u;
		float pcx = x + w - 40 * u - playR;
		float maxW = pcx - playR - 24 * u - tx;
		float smallCap = 21 * u;
		float nameCap = 30 * u;
		Type.draw(d, "Continue · " + LastPlayed.ago(last.time), tx, y + h / 2f - 32 * u, smallCap, Weight.MEDIUM, Paint.fade(Accent.lighten(Accent.get(), 0.55f), 0.9f));
		Type.draw(d, Type.fit(last.name, maxW, nameCap, Weight.BOLD), tx, y + h / 2f + 6 * u, nameCap, Weight.BOLD, 0xFFFFFFFF);
		int fill = Accent.lighten(Accent.get(), 0.2f + 0.08f * t);
		float pcy = y + h / 2f;
		Paint.shadow(d, pcx - playR, pcy - playR, pcx + playR, pcy + playR, playR, 0, 2 * u, 28 * u, 0, Paint.fade(fill, 0.4f));
		Paint.circle(d, pcx, pcy, playR * (1 + 0.05f * t), fill);
		float glyph = 30 * u;
		Paint.icon(d, "play", pcx - glyph / 2f + 2 * u, pcy - glyph / 2f, glyph, 1.2f, true, 0xFFFFFFFF, null);
		hits.add(new Hit("continue", x, y, x + w, y + h, () -> LastPlayed.resume(this)));
		centres.put("continue", new float[] {pcx, pcy});
	}

	/** The dock: Options, the Eventide menu, Mods (with Mod Menu), Language and, after a divider, Quit. */
	private void dock(Draw d, Minecraft mc, float cx, float y, float h, float u, int mx, int my) {
		boolean mods = modMenu() != null;
		List<Integer> shown = new ArrayList<>();
		for (int i = 0; i < DOCK.length; i++) if (i != 2 || mods) shown.add(i);
		float step = 120 * u;
		float pad = 70 * u;
		float divider = 38 * u;
		float w = pad * 2 + step * (shown.size() - 1) + divider;
		float x = cx - w / 2f;
		float r = h / 2f;
		Paint.shadow(d, x, y, x + w, y + h, r, 0, 2 * u, 30 * u, 0, 0x44000000);
		Paint.rect(d, x, y, x + w, y + h, r, 0xE6110E22);
		Paint.stroke(d, x, y, x + w, y + h, r, hair(d), 0x1AFFFFFF);
		float ix = x + pad;
		float cy = y + h / 2f;
		float size = 44 * u;
		String label = null;
		float labelX = 0;
		for (int n = 0; n < shown.size(); n++) {
			int i = shown.get(n);
			if (i == 4) {
				float lx = ix - step / 2f + divider / 2f;
				Paint.rect(d, lx - hair(d) / 2f, cy - h * 0.22f, lx + hair(d) / 2f, cy + h * 0.22f, 0, 0x26FFFFFF);
				ix += divider;
			}
			String id = DOCK[i];
			boolean over = Math.abs(mx - ix) < step / 2f && my >= y && my < y + h;
			Anim lit = hover(id);
			lit.set(over);
			float t = lit.get();
			if (t > 0.01f) Paint.circle(d, ix, cy, 40 * u, Paint.fade(0xFFFFFFFF, 0.08f * t));
			int color = Widgets.mixColor(0xFFCFCBDF, 0xFFFFFFFF, t);
			if (i == 1) Logo.mark(d, ix - size / 2f, cy - size / 2f, size, 1, System.currentTimeMillis());
			else Paint.icon(d, DOCK_ICONS[i], ix - size / 2f, cy - size / 2f, size, color);
			final int which = i;
			hits.add(new Hit(id, ix - step / 2f, y, ix + step / 2f, y + h, () -> dockAction(mc, which)));
			centres.put(id, new float[] {ix, cy});
			if (over) {
				label = DOCK_LABELS[i];
				labelX = ix;
			}
			ix += step;
		}
		if (label != null) {
			float cap = 20 * u;
			Type.center(d, label, labelX, y - 18 * u - cap, cap, Weight.SEMIBOLD, Tokens.TEXT_2);
		}
	}

	private void dockAction(Minecraft mc, int which) {
		switch (which) {
			case 0 -> mc.setScreen(Compat.optionsScreen(this));
			case 1 -> MenuHost.show(this, Category.RENDER);
			case 2 -> {
				Screen mods = modMenu();
				if (mods != null) mc.setScreen(mods);
			}
			case 3 -> mc.setScreen(new LanguageSelectScreen(this, mc.options, mc.getLanguageManager()));
			default -> mc.stop();
		}
	}

	/** Mod Menu's list of mods, when it's installed. */
	private Screen modMenu() {
		try {
			Class<?> mods = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			return (Screen) mods.getConstructor(Screen.class).newInstance(this);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}

	private static float hair(Draw d) {
		return 1f / Math.max(1, Paint.density(d));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) return false;
		for (Hit hit : hits) {
			if (hit.contains(event.x(), event.y())) {
				Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
						net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
				hit.action().run();
				return true;
			}
		}
		return false;
	}
}
