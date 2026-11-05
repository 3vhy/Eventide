package dev.eventide.client.gui.menu;

import dev.eventide.client.Config;
import dev.eventide.client.Draw;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.module.Module;
import dev.eventide.client.module.Module.Category;
import dev.eventide.client.module.Modules;
import dev.eventide.client.ui.Anim;
import dev.eventide.client.ui.Ease;
import dev.eventide.client.ui.Hits;
import dev.eventide.client.ui.Keys;
import dev.eventide.client.ui.Logo;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Sky;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Type.Weight;
import dev.eventide.client.ui.Widgets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Eventide mod menu: one window with a sidebar of sections down the left (each with how many of its
 * mods are on), the section's title and a search field across the top, and its mods as cards in a grid,
 * each with its icon on a tile, its name, a line or two about it, a settings button and a switch. Click a
 * card to switch it; its settings open in a panel built into the right of the window, and the grid
 * makes room for it. The sidebar's foot holds Edit HUD, the Screen / Floating switch and your profile.
 *
 * <p>Keys: arrows move between cards, Space switches one, Enter opens its settings, Tab and Shift+Tab
 * change section, {@code /} searches (or just start typing), Esc steps back out. Everything is laid out in
 * layout units on a window {@link #WIN_W} x {@link #WIN_H}: on screen it's scaled to fit the window, and in
 * Floating mode {@link FloatingMenu} draws the same window on a panel in the world. This class has no
 * Minecraft screen in it; {@link MenuScreen} and {@link FloatingMenuScreen} host it.
 */
public final class MenuView {
	/** What the menu needs from the game. */
	public interface Host {
		/** The key on a module's card (C for Zoom), or null. */
		String keyFor(Module module);

		String playerName();

		/** Draws your head in a {@code size} square with corners of {@code radius}. */
		void drawFace(Draw d, float x, float y, float size, float radius);

		boolean inWorld();

		void openHudEditor();

		/** Esc at the top, or the close button: out of the menu. */
		void back();

		void clickSound();

		SettingsSheet.Extras extras();

		/** Whether the menu is floating in the world right now (rather than on the screen). */
		default boolean floating() {
			return false;
		}

		/** Moves the menu between the screen and the world, keeping where you are in it. */
		default void setFloating(boolean floating) {
		}
	}

	/** A section in the sidebar: the Profile page or a module category. */
	public record Tab(String name, String icon, String tagline, Category category) {
	}

	/** The design canvas the screen menu is fitted into. */
	static final float DESIGN_W = 1600;
	static final float DESIGN_H = 900;
	/** The window, in layout units. */
	public static final float WIN_W = 1440;
	public static final float WIN_H = 800;
	private static final float WIN_R = 22;
	private static final float SIDE_W = 250;
	private static final float PAD = 30;
	private static final float NAV_TOP = 98;
	private static final float NAV_H = 40;
	private static final float NAV_GAP = 4;
	private static final float GRID_TOP = 112;
	private static final float CARD_H = 88;
	private static final float GAP = 14;
	private static final float MIN_CARD_W = 300;
	private static final float TILE = 40;
	private static final float SEARCH_W = 300;
	private static final float HEAD_H = 38;
	private static final int REVEAL_STAGGER = 18;
	private static final int REVEAL_TIME = 300;

	public static final List<Tab> TABS = tabs();

	private final Host host;
	private final Hits hits = new Hits();
	private final SettingsSheet sheet;
	private final long openedAt = System.currentTimeMillis();
	private final Map<Module, CardState> cards = new HashMap<>();
	private final Map<String, List<String>> wraps = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
			return size() > 256;
		}
	};
	private final Anim[] navHover = new Anim[TABS.size()];
	private final Anim navY = new Anim(0, Tokens.T_INDICATOR, Ease.OUT);
	private final Anim searchHover = new Anim(0, Tokens.T_COLOR, Ease.OUT);
	private final Anim editHover = new Anim(0, Tokens.T_COLOR, Ease.OUT);
	private final Anim closeHover = new Anim(0, Tokens.T_COLOR, Ease.OUT);
	private final Anim chipHover = new Anim(0, Tokens.T_COLOR, Ease.OUT);
	private final Anim modeAt = new Anim(0, Tokens.T_INDICATOR, Ease.OUT);
	private final Anim gridRight = new Anim(WIN_W - PAD, Tokens.T_SHEET, Ease.OUT);

	private int tab;
	private long tabSince = System.currentTimeMillis();
	private String search = "";
	private boolean searchFocused = false;
	private double scroll = 0;
	private double shownScroll = 0;
	private double maxScroll = 0;
	private long lastFrame = 0;
	private long frames = 0;
	private boolean laidOut = false;
	private String newProfile = "";
	private boolean typingProfile = false;
	private String notice = null;
	private long noticeAt = 0;
	private Module focused = null;
	private int columns = 3;
	private List<Module> lastShown = List.of();

	/** The scale from layout units to GUI units and the window's corner in layout units, worked out each frame on screen. */
	private float unit = 1;
	private float winX = 0;
	private float winY = 0;

	public MenuView(Host host, Category start) {
		this.host = host;
		this.sheet = new SettingsSheet(host.extras(), host::clickSound);
		for (int i = 0; i < TABS.size(); i++) navHover[i] = new Anim(0, Tokens.T_HOVER, Ease.OUT);
		tab = indexOf(start);
		modeAt.snap(host.floating() ? 1 : 0);
	}

	private static List<Tab> tabs() {
		List<Tab> list = new ArrayList<>();
		for (Category category : Category.values()) list.add(new Tab(category.label, category.icon, category.tagline, category));
		list.add(new Tab("Profiles", "layers", "Your setups, one click apart", null));
		return List.copyOf(list);
	}

	/** The section for a category (the Profiles page for null). */
	public static int indexOf(Category category) {
		for (int i = 0; i < TABS.size(); i++) {
			if (TABS.get(i).category() == category) return i;
		}
		return 0;
	}

	public Category category() {
		return TABS.get(tab).category();
	}

	/** Opens a module's settings straight away. */
	public MenuView openSettings(Module module) {
		tab = indexOf(module.category);
		focused = module;
		sheet.open(module);
		return this;
	}

	/** Shows the Profiles page. */
	public MenuView openProfiles() {
		tab = indexOf(null);
		return this;
	}

	public MenuView search(String text) {
		search = text;
		searchFocused = !text.isEmpty();
		scroll = 0;
		return this;
	}

	public String searchText() {
		return search;
	}

	/** The middle of a module's card as last drawn, in GUI units, or null if it isn't on screen. */
	public float[] cardCenter(Module module) {
		CardState state = cards.get(module);
		if (state == null || state.drawnAt != frames) return null;
		return new float[] {(winX + state.cx) * unit, (winY + state.cy) * unit};
	}

	/** The middle of a sidebar section as last drawn, in GUI units. */
	public float[] tabCenter(int index) {
		float y = NAV_TOP + index * (NAV_H + NAV_GAP) + (index == TABS.size() - 1 ? 14 : 0) + NAV_H / 2f;
		return new float[] {(winX + SIDE_W / 2f) * unit, (winY + y) * unit};
	}

	public boolean sheetOpen() {
		return sheet.isOpen();
	}

	public Module sheetModule() {
		return sheet.module();
	}

	/** The card the keyboard is on, or null. */
	public Module focused() {
		return focused;
	}

	/** The open panel's first row as last drawn ({x, y} in GUI units) and the layout scale, for clicking its parts. */
	public float[] sheetContent() {
		float[] origin = sheet.contentOrigin();
		return new float[] {(winX + origin[0]) * unit, (winY + origin[1]) * unit, unit};
	}

	/** The middle of knife slot {@code slot} in the Knives settings as last drawn, in GUI units, for the in-game test. */
	public float[] knifeSlot(int slot) {
		float[] at = ((SheetExtras) sheet.extras()).knifeSlotsAt;
		float cx = at[0] + slot * (at[2] + 6) + at[2] / 2f;
		return new float[] {(winX + cx) * unit, (winY + at[1] + at[2] / 2f) * unit};
	}

	/** The middle of the first knife slot's knife picker in the Knives settings as last drawn, in GUI units, for the in-game test. */
	public float[] knifePicker() {
		float[] at = ((SheetExtras) sheet.extras()).knifePickerAt;
		return new float[] {(winX + (at[0] + at[2]) / 2f) * unit, (winY + (at[1] + at[3]) / 2f) * unit};
	}

	/** Draws the menu over a {@code guiW} x {@code guiH} screen, its window scaled to fit; the mouse is in GUI units. */
	public void draw(Draw d, float guiW, float guiH, double mouseX, double mouseY) {
		long now = System.currentTimeMillis();
		unit = Math.min(guiW / DESIGN_W, guiH / DESIGN_H);
		float canvasW = guiW / unit;
		float canvasH = guiH / unit;
		winX = (canvasW - WIN_W) / 2f;
		winY = (canvasH - WIN_H) / 2f;
		float intro = Ease.OUT.apply((now - openedAt) / (double) Tokens.T_OPEN);
		float old = Paint.opacity(Paint.opacity() * intro);
		d.push();
		d.scale(unit);
		Sky.backdrop(d, canvasW, canvasH, host.inWorld(), 1);
		if (Modules.THEME_SNOW.get()) Sky.stars(d, canvasW, canvasH, (float) (Modules.THEME_SNOW_AMOUNT.get() / 100.0) * 0.6f, 1, now);
		float lift = (1 - intro) * 10;
		d.translate(winX, winY + lift);
		drawWindow(d, (float) (mouseX / unit) - winX, (float) (mouseY / unit) - winY - lift, now);
		d.pop();
		Paint.opacity(old);
	}

	/**
	 * Draws the window with its top left at the current origin, in layout units, for Floating mode; the
	 * mouse is in the window's layout units (negative when it's off the window).
	 */
	public void drawPanel(Draw d, float mx, float my) {
		unit = 1;
		winX = 0;
		winY = 0;
		drawWindow(d, mx, my, System.currentTimeMillis());
	}

	private void drawWindow(Draw d, float mx, float my, long now) {
		frames++;
		long frame = System.nanoTime();
		double dt = lastFrame == 0 ? 0 : Math.min(0.1, (frame - lastFrame) / 1e9);
		lastFrame = frame;
		shownScroll += (scroll - shownScroll) * Math.min(1.0, dt * 16);
		if (Math.abs(scroll - shownScroll) < 0.3) shownScroll = scroll;
		hits.clear();
		modeAt.set(host.floating() ? 1 : 0);

		boolean overSheet = sheet.over(mx, my);
		float hx = overSheet ? -9999 : mx;
		float hy = overSheet ? -9999 : my;
		int accent = Accent.get();
		Paint.shadow(d, 0, 0, WIN_W, WIN_H, WIN_R, 0, 24, 70, -6, 0xB3000000);
		Paint.shadow(d, 0, 0, WIN_W, WIN_H, WIN_R, 0, 0, 90, -30, Paint.fade(accent, 0.16f));
		Paint.rect(d, 0, 0, WIN_W, WIN_H, WIN_R, Tokens.WINDOW);
		d.enableScissor(0, 0, Math.round(WIN_W), Math.round(WIN_H));
		Paint.shadow(d, -220, -260, 460, 140, 200, 0, 0, 260, 0, Paint.fade(accent, 0.2f));
		Paint.shadow(d, WIN_W - 380, WIN_H - 120, WIN_W + 200, WIN_H + 260, 200, 0, 0, 260, 0, Paint.fade(Accent.secondary(), 0.09f));
		d.disableScissor();
		Paint.rect(d, 0, 0, SIDE_W, WIN_H, WIN_R, Tokens.SIDEBAR);
		Paint.rect(d, SIDE_W - WIN_R, 0, SIDE_W, WIN_H, 0, Tokens.SIDEBAR);
		Paint.rect(d, SIDE_W, 0, SIDE_W + 1, WIN_H, 0, 0x14FFFFFF);
		Paint.stroke(d, 0, 0, WIN_W, WIN_H, WIN_R, 1, Tokens.WINDOW_EDGE);
		Paint.rect(d, WIN_R * 1.5f, 0, WIN_W - WIN_R * 1.5f, 1, 0, 0x1FFFFFFF);

		drawSidebar(d, hx, hy, now);
		float x0 = SIDE_W + PAD;
		float sheetT = sheet.openness();
		gridRight.set(sheet.isOpen() ? WIN_W - PAD - SettingsSheet.WIDTH - 22 : WIN_W - PAD);
		drawHeader(d, x0, hx, hy, now);
		Tab current = TABS.get(tab);
		if (current.category() == null && search.isEmpty()) {
			drawProfiles(d, x0, hx, hy, now);
		} else {
			drawModules(d, x0, hx, hy, now, (float) dt);
		}
		if (sheetT > 0.001f || sheet.isOpen()) {
			sheet.draw(d, WIN_W - PAD - SettingsSheet.WIDTH, GRID_TOP - 12, WIN_W - PAD, WIN_H - 22, mx, my, now);
		}
		drawNotice(d, now);
	}

	private void drawSidebar(Draw d, float mx, float my, long now) {
		Logo.lockup(d, 26, 30, 30, 10.5f, now);
		Type.draw(d, "Modules", 26, 76, 9, Weight.SEMIBOLD, Tokens.TEXT_3);
		float x1 = 14;
		float x2 = SIDE_W - 14;
		float activeY = NAV_TOP + tab * (NAV_H + NAV_GAP) + (tab == TABS.size() - 1 ? 14 : 0);
		navY.set(activeY);
		if (!laidOut) {
			navY.snap(activeY);
			laidOut = true;
		}
		boolean searching = !search.isEmpty();
		float ay = navY.get();
		int accent = Accent.get();
		if (!searching) {
			Paint.rect(d, x1, ay, x2, ay + NAV_H, 11, Paint.fade(accent, 0.16f));
			Paint.stroke(d, x1, ay, x2, ay + NAV_H, 11, 1, Paint.fade(accent, 0.32f));
			Paint.shadow(d, x1, ay + 10, x1 + 3, ay + NAV_H - 10, 1.5f, 0, 0, 10, 0, Paint.fade(accent, 0.8f));
			Paint.rect(d, x1, ay + 11, x1 + 3, ay + NAV_H - 11, 1.5f, Accent.lighten(accent, 0.2f));
		}
		for (int i = 0; i < TABS.size(); i++) {
			Tab each = TABS.get(i);
			float y = NAV_TOP + i * (NAV_H + NAV_GAP) + (i == TABS.size() - 1 ? 14 : 0);
			if (i == TABS.size() - 1) Paint.rect(d, 26, y - 10, SIDE_W - 26, y - 9, 0, 0x12FFFFFF);
			boolean active = i == tab && !searching;
			boolean hover = within(mx, my, x1, y, x2, y + NAV_H);
			navHover[i].set(hover && !active);
			float h = navHover[i].get();
			if (h > 0.01f) Paint.rect(d, x1, y, x2, y + NAV_H, 11, Paint.fade(0x0FFFFFFF, h));
			int iconColor = active ? Accent.text() : Widgets.mixColor(Tokens.TEXT_3, Tokens.TEXT_2, h);
			Paint.icon(d, each.icon(), x1 + 16, y + (NAV_H - 16) / 2f, 16, iconColor);
			Type.draw(d, each.name(), x1 + 44, y + (NAV_H - 11f) / 2f, 11f, Weight.SEMIBOLD, active ? Tokens.TEXT : Widgets.mixColor(Tokens.TEXT_2, Tokens.TEXT, h));
			if (each.category() != null) {
				int on = 0;
				int total = 0;
				for (Module module : Modules.in(each.category())) {
					if (!module.toggleable()) continue;
					total++;
					if (module.enabled()) on++;
				}
				if (total > 0) {
					String count = on + "/" + total;
					float cap = 8.5f;
					float cw = Type.width(count, cap, Weight.SEMIBOLD) + 12;
					float cx = x2 - 10 - cw;
					float cy = y + (NAV_H - 18) / 2f;
					Paint.rect(d, cx, cy, cx + cw, cy + 18, 9, active ? Paint.fade(accent, 0.28f) : 0x0FFFFFFF);
					Type.center(d, count, cx + cw / 2f, cy + (18 - cap) / 2f, cap, Weight.SEMIBOLD, active ? Tokens.TEXT : Tokens.TEXT_3);
				}
			}
			int index = i;
			hits.add(x1, y, x2, y + NAV_H, () -> switchTab(index));
		}
		drawSidebarFoot(d, mx, my);
	}

	private void drawSidebarFoot(Draw d, float mx, float my) {
		float x1 = 14;
		float x2 = SIDE_W - 14;
		float chipH = 54;
		float chipY = WIN_H - 14 - chipH;
		boolean chip = within(mx, my, x1, chipY, x2, chipY + chipH);
		chipHover.set(chip);
		float ch = chipHover.get();
		Paint.rect(d, x1, chipY, x2, chipY + chipH, 12, Widgets.mixColor(0x0AFFFFFF, 0x16FFFFFF, ch));
		Paint.stroke(d, x1, chipY, x2, chipY + chipH, 12, 1, Widgets.mixColor(0x12FFFFFF, 0x26FFFFFF, ch));
		host.drawFace(d, x1 + 12, chipY + 11, 32, 8);
		Type.draw(d, Type.fit(host.playerName(), x2 - x1 - 76, 11, Weight.SEMIBOLD), x1 + 54, chipY + 14, 11, Weight.SEMIBOLD, Tokens.TEXT);
		Type.draw(d, Type.fit(Config.get().profile, x2 - x1 - 76, 9, Weight.MEDIUM), x1 + 54, chipY + 31, 9, Weight.MEDIUM, Tokens.TEXT_3);
		Paint.icon(d, "chevron-right", x2 - 24, chipY + (chipH - 14) / 2f, 14, Widgets.mixColor(Tokens.TEXT_3, Tokens.TEXT, ch));
		hits.add(x1, chipY, x2, chipY + chipH, () -> switchTab(indexOf(null)));

		float segH = 32;
		float segY = chipY - 12 - segH;
		String[] labels = {"Screen", "Floating"};
		int over = -1;
		float half = (x2 - x1) / 2f;
		if (within(mx, my, x1, segY, x1 + half, segY + segH)) over = 0;
		else if (within(mx, my, x1 + half, segY, x2, segY + segH)) over = 1;
		Widgets.segmented(d, x1, segY, x2, segY + segH, null, labels, modeAt.get(), over);
		hits.add(x1, segY, x1 + half, segY + segH, () -> {
			if (host.floating()) host.setFloating(false);
		});
		hits.add(x1 + half, segY, x2, segY + segH, () -> {
			if (!host.floating()) host.setFloating(true);
		});
		Type.draw(d, "Menu display", x1 + 4, segY - 18, 9, Weight.SEMIBOLD, Tokens.TEXT_3);

		float editH = 34;
		float editY = segY - 30 - editH;
		boolean edit = within(mx, my, x1, editY, x2, editY + editH);
		editHover.set(edit);
		float e = editHover.get();
		Widgets.control(d, x1, editY, x2, editY + editH, 10, e);
		Paint.icon(d, "move", x1 + 14, editY + (editH - 14) / 2f, 14, Widgets.mixColor(Tokens.TEXT_2, Tokens.TEXT, e));
		Type.draw(d, "Edit HUD layout", x1 + 38, editY + (editH - 10.5f) / 2f, 10.5f, Weight.SEMIBOLD, Tokens.TEXT);
		hits.add(x1, editY, x2, editY + editH, host::openHudEditor);
	}

	private void drawHeader(Draw d, float x0, float mx, float my, long now) {
		boolean searching = !search.isEmpty();
		Tab current = TABS.get(tab);
		String title = searching ? "Search" : current.name();
		List<Module> shown = shownModules();
		String subtitle;
		if (searching) {
			subtitle = shown.isEmpty() ? "Nothing matches \"" + search.trim() + "\"" : shown.size() + (shown.size() == 1 ? " result" : " results") + " for \""
					+ search.trim() + "\"";
		} else {
			subtitle = current.tagline();
		}
		float t = Ease.OUT.apply((now - tabSince) / (double) Tokens.T_CAPTION);
		float old = Paint.opacity(Paint.opacity() * t);
		float rise = (1 - t) * 4;
		Type.draw(d, title, x0, 32 + rise, 21, Weight.BOLD, Tokens.TEXT);
		float tw = Type.width(title, 21, Weight.BOLD);
		if (current.category() != null || searching) {
			int on = 0;
			int total = 0;
			for (Module module : shown) {
				if (!module.toggleable()) continue;
				total++;
				if (module.enabled()) on++;
			}
			if (total > 0) {
				String count = on + " of " + total + " on";
				float cap = 9.5f;
				float pw = 22 + Type.width(count, cap, Weight.SEMIBOLD) + 12;
				float px = x0 + tw + 14;
				float py = 32 + rise + 1;
				Paint.rect(d, px, py, px + pw, py + 21, 10.5f, 0x0FFFFFFF);
				Paint.stroke(d, px, py, px + pw, py + 21, 10.5f, 1, 0x14FFFFFF);
				Widgets.pulseDot(d, px + 12, py + 10.5f, 3, on > 0 ? Accent.get() : Tokens.TEXT_3, now);
				Type.draw(d, count, px + 22, py + (21 - cap) / 2f, cap, Weight.SEMIBOLD, Tokens.TEXT_2);
			}
		}
		Type.draw(d, subtitle, x0, 64 + rise, 11, Weight.MEDIUM, Tokens.TEXT_2);
		Paint.opacity(old);

		float right = WIN_W - PAD;
		float cx1 = right - HEAD_H;
		boolean close = within(mx, my, cx1, 28, right, 28 + HEAD_H);
		closeHover.set(close);
		Widgets.iconButton(d, cx1, 28, right, 28 + HEAD_H, "x", closeHover.get(), false);
		hits.add(cx1, 28, right, 28 + HEAD_H, host::back);
		float sx2 = cx1 - 10;
		float sx1 = sx2 - SEARCH_W;
		boolean searchHovered = within(mx, my, sx1, 28, sx2, 28 + HEAD_H);
		searchHover.set(searchHovered);
		Widgets.search(d, sx1, 28, sx2, 28 + HEAD_H, search, "Search mods", "/", searchFocused, searchHover.get(), now);
		hits.add(sx1, 28, sx2, 28 + HEAD_H, () -> searchFocused = true);
		if (category() == Category.HUD && !searching) {
			String label = "Edit layout";
			float cap = 10f;
			float w = 14 + 14 + 8 + Type.width(label, cap, Weight.SEMIBOLD) + 16;
			float bx = sx1 - 10 - w;
			boolean hover = within(mx, my, bx, 28, bx + w, 28 + HEAD_H);
			Widgets.control(d, bx, 28, bx + w, 28 + HEAD_H, 11, hover ? 1 : 0);
			Paint.icon(d, "move", bx + 14, 28 + (HEAD_H - 14) / 2f, 14, hover ? Tokens.TEXT : Tokens.TEXT_2);
			Type.draw(d, label, bx + 36, 28 + (HEAD_H - cap) / 2f, cap, Weight.SEMIBOLD, Tokens.TEXT);
			hits.add(bx, 28, bx + w, 28 + HEAD_H, host::openHudEditor);
		}
	}

	private List<Module> shownModules() {
		if (!search.isEmpty()) {
			String query = search.trim().toLowerCase(Locale.ROOT);
			List<Module> found = new ArrayList<>();
			for (Module module : Modules.ALL) {
				if (module.name.toLowerCase(Locale.ROOT).contains(query)) found.add(module);
			}
			for (Module module : Modules.ALL) {
				if (found.contains(module)) continue;
				if (module.description.toLowerCase(Locale.ROOT).contains(query) || module.category.label.toLowerCase(Locale.ROOT).contains(query)) {
					found.add(module);
				}
			}
			return found;
		}
		Category category = category();
		return category == null ? List.of() : Modules.in(category);
	}

	private void drawModules(Draw d, float x0, float mx, float my, long now, float dt) {
		List<Module> shown = shownModules();
		lastShown = shown;
		float right = gridRight.get();
		float target = gridRight.target() - x0;
		columns = Math.max(1, Math.min(3, (int) ((target + GAP) / (MIN_CARD_W + GAP))));
		float cardW = (target - (columns - 1) * GAP) / columns;
		float viewTop = GRID_TOP - 12;
		float viewBottom = WIN_H - 18;
		int rows = (shown.size() + columns - 1) / columns;
		float contentH = rows * (CARD_H + GAP) - GAP + 12;
		maxScroll = Math.max(0, contentH - (viewBottom - GRID_TOP));
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		shownScroll = Math.max(0, Math.min(shownScroll, maxScroll));

		d.enableScissor(Math.round(x0 - 20), Math.round(viewTop), Math.round(WIN_W), Math.round(viewBottom));
		hits.clip(x0 - 20, viewTop, right + 4, viewBottom);
		boolean mouseInGrid = my >= viewTop && my < viewBottom && mx < right + 4;
		for (int i = 0; i < shown.size(); i++) {
			Module module = shown.get(i);
			float tx = x0 + (i % columns) * (cardW + GAP);
			float ty = GRID_TOP + (i / columns) * (CARD_H + GAP);
			CardState state = state(module);
			state.x.set(tx);
			state.y.set(ty);
			state.w.set(cardW);
			if (!state.placed) {
				state.x.snap(tx);
				state.y.snap(ty);
				state.w.snap(cardW);
				state.placed = true;
			}
			float x = state.x.get();
			float y = state.y.get() - (float) shownScroll;
			float w = state.w.get();
			if (y > viewBottom + 10 || y + CARD_H < viewTop - 10) continue;
			float reveal = Ease.OUT.apply((now - tabSince - Math.min(i, 12) * REVEAL_STAGGER) / (double) REVEAL_TIME);
			drawCard(d, module, x, y, w, reveal, mouseInGrid ? mx : -9999, mouseInGrid ? my : -9999, now);
		}
		hits.unclip();
		d.disableScissor();
		if (maxScroll > 0) {
			float barH = viewBottom - GRID_TOP;
			float thumb = Math.max(40, barH * barH / (barH + (float) maxScroll));
			float ty = GRID_TOP + (barH - thumb) * (float) (shownScroll / maxScroll);
			float bx = right + 8;
			Paint.rect(d, bx, GRID_TOP, bx + 3, GRID_TOP + barH, 1.5f, 0x0DFFFFFF);
			Paint.rect(d, bx, ty, bx + 3, ty + thumb, 1.5f, 0x33FFFFFF);
		}
		if (shown.isEmpty()) {
			boolean searching = !search.isEmpty();
			float cx = (x0 + right) / 2f;
			Paint.icon(d, searching ? "search" : "sparkles", cx - 14, GRID_TOP + 60, 28, Tokens.TEXT_3);
			String hint = searching ? "Try another word, or Esc to clear the search" : "Nothing here yet";
			Type.center(d, hint, cx, GRID_TOP + 104, 11.5f, Weight.MEDIUM, Tokens.TEXT_3);
		}
	}

	private CardState state(Module module) {
		return cards.computeIfAbsent(module, m -> new CardState(m.enabled()));
	}

	private void drawCard(Draw d, Module module, float x, float y, float w, float reveal, float mx, float my, long now) {
		if (reveal <= 0) return;
		CardState state = state(module);
		state.drawnAt = frames;
		state.cx = x + w / 2f;
		state.cy = y + CARD_H / 2f;
		boolean on = module.enabled();
		state.knob.set(on);
		state.lit.set(on);
		boolean hover = within(mx, my, x, y, x + w, y + CARD_H);
		state.hover.set(hover);
		boolean chosen = sheet.isOpen() && sheet.module() == module || focused == module && !sheet.isOpen();
		state.chosen.set(chosen);
		float h = state.hover.get();
		float old = Paint.opacity(Paint.opacity() * reveal);
		float top = y + (1 - reveal) * Tokens.REVEAL_RISE;
		float lit = module.toggleable() ? state.lit.get() : 1;

		Widgets.card(d, x, top, x + w, top + CARD_H, Tokens.R_CARD, h, module.toggleable() ? lit : 0, state.chosen.get());
		Widgets.tile(d, x + 18, top + (CARD_H - TILE) / 2f, TILE, module.icon(), lit, h);

		boolean hasSettings = module == Modules.PROFILES || SettingsSheet.hasPage(module, sheet.extras());
		float toggleX = x + w - 18 - 40;
		Runnable openSettings = module == Modules.PROFILES ? () -> switchTab(indexOf(null)) : () -> {
			focused = null;
			sheet.open(module);
		};
		if (module.toggleable()) Widgets.toggle(d, toggleX, top + 18, 40, 22, state.knob.get(), state.lit.get());
		float sx2 = module.toggleable() ? toggleX - 8 : x + w - 18;
		float sx1 = sx2 - 26;
		float sy1 = top + 16;
		if (hasSettings) {
			boolean settingsHover = within(mx, my, sx1, sy1, sx2, sy1 + 26);
			state.settings.set(settingsHover);
			float sh = state.settings.get();
			boolean open = sheet.isOpen() && sheet.module() == module;
			Widgets.iconButton(d, sx1, sy1, sx2, sy1 + 26, module.toggleable() ? "sliders-horizontal" : "chevron-right", Math.max(sh, h * 0.35f), open);
		}

		float nameX = x + 18 + TILE + 14;
		float nameRight = (hasSettings ? sx1 : module.toggleable() ? toggleX : x + w - 18) - 10;
		String key = host.keyFor(module);
		float keyW = key != null ? Widgets.kbdWidth(key, 7.5f) + 8 : 0;
		String name = Type.fit(module.name, nameRight - nameX - keyW, 12.5f, Weight.SEMIBOLD);
		Type.draw(d, name, nameX, top + 22, 12.5f, Weight.SEMIBOLD, Tokens.TEXT);
		if (key != null) Widgets.kbd(d, nameX + Type.width(name, 12.5f, Weight.SEMIBOLD) + 8, top + 19, key, 7.5f);
		List<String> lines = wrap(module.description, x + w - 18 - nameX, 9.5f);
		for (int i = 0; i < lines.size(); i++) {
			Type.draw(d, lines.get(i), nameX, top + 46 + i * 14, 9.5f, Weight.MEDIUM, Tokens.TEXT_2);
		}
		Paint.opacity(old);

		if (reveal < 0.5f) return;
		Runnable primary = module.toggleable() ? () -> toggle(module) : openSettings;
		hits.add(x, top, x + w, top + CARD_H, primary, hasSettings ? openSettings : null);
		if (module.toggleable()) hits.add(toggleX - 4, top + 12, toggleX + 44, top + 46, () -> toggle(module), hasSettings ? openSettings : null);
		if (hasSettings) hits.add(sx1 - 2, sy1 - 2, sx2 + 2, sy1 + 28, openSettings, openSettings);
	}

	private List<String> wrap(String text, float width, float cap) {
		String key = Math.round(width) + "|" + text;
		List<String> cached = wraps.get(key);
		if (cached != null) return cached;
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		String[] words = text.split(" ");
		for (int i = 0; i < words.length; i++) {
			String next = line.isEmpty() ? words[i] : line + " " + words[i];
			if (Type.width(next, cap, Weight.MEDIUM) <= width || line.isEmpty()) {
				line = new StringBuilder(next);
				continue;
			}
			if (lines.size() == 1) {
				StringBuilder rest = new StringBuilder(line);
				for (int j = i; j < words.length; j++) rest.append(' ').append(words[j]);
				lines.add(Type.fit(rest.toString(), width, cap, Weight.MEDIUM));
				wraps.put(key, lines);
				return lines;
			}
			lines.add(line.toString());
			line = new StringBuilder(words[i]);
		}
		if (!line.isEmpty()) lines.add(line.toString());
		wraps.put(key, lines);
		return lines;
	}

	private void toggle(Module module) {
		focused = null;
		module.toggle();
	}

	private void drawProfiles(Draw d, float x0, float mx, float my, long now) {
		Config config = Config.get();
		float reveal = Ease.OUT.apply((now - tabSince) / (double) REVEAL_TIME);
		float old = Paint.opacity(Paint.opacity() * reveal);
		float rise = (1 - reveal) * Tokens.REVEAL_RISE;
		float right = gridRight.target();
		int cols = Math.max(1, Math.min(3, (int) ((right - x0 + GAP) / (MIN_CARD_W + GAP))));
		float cardW = (right - x0 - (cols - 1) * GAP) / cols;
		float y = GRID_TOP + rise;
		float youH = 2 * CARD_H + GAP;
		Widgets.card(d, x0, y, x0 + cardW, y + youH, Tokens.R_CARD, 0);
		Paint.shadow(d, x0 + cardW / 2f - 40, y + 20, x0 + cardW / 2f + 40, y + 100, 16, 0, 0, 40, 0, Paint.fade(Accent.get(), 0.25f));
		float face = 64;
		float fx = x0 + 22;
		float fy = y + 22;
		host.drawFace(d, fx, fy, face, 14);
		Type.draw(d, Type.fit(host.playerName(), cardW - face - 66, 15, Weight.BOLD), fx + face + 16, fy + 14, 15, Weight.BOLD, Tokens.TEXT);
		Type.draw(d, Type.fit("Using " + config.profile, cardW - face - 66, 10, Weight.MEDIUM), fx + face + 16, fy + 38, 10, Weight.MEDIUM, Tokens.TEXT_2);
		float ex = x0 + 22;
		float ew = cardW - 44;
		float ey = y + youH - 22 - 36;
		boolean editHovered = within(mx, my, ex, ey, ex + ew, ey + 36);
		Widgets.control(d, ex, ey, ex + ew, ey + 36, 11, editHovered ? 1 : 0);
		float lw = 14 + 8 + Type.width("Edit HUD layout", 10.5f, Weight.SEMIBOLD);
		Paint.icon(d, "move", ex + (ew - lw) / 2f, ey + 11, 14, Tokens.TEXT_2);
		Type.draw(d, "Edit HUD layout", ex + (ew - lw) / 2f + 22, ey + (36 - 10.5f) / 2f, 10.5f, Weight.SEMIBOLD, Tokens.TEXT);
		hits.add(ex, ey, ex + ew, ey + 36, host::openHudEditor);

		List<String> names = config.profileNames();
		int slot = 1;
		for (String name : names) {
			if (cols > 1 && slot % cols == 0) slot++;
			float cx = x0 + (slot % cols) * (cardW + GAP);
			float cy = GRID_TOP + (slot / cols) * (CARD_H + GAP) + rise;
			drawProfileCard(d, config, name, cx, cy, cardW, mx, my);
			slot++;
		}
		if (cols > 1 && slot % cols == 0) slot++;
		float cx = x0 + (slot % cols) * (cardW + GAP);
		float cy = GRID_TOP + (slot / cols) * (CARD_H + GAP) + rise;
		drawNewProfile(d, cx, cy, cardW, mx, my, now);
		Paint.opacity(old);
	}

	private void drawProfileCard(Draw d, Config config, String name, float x, float y, float w, float mx, float my) {
		boolean active = name.equals(config.profile);
		boolean hover = within(mx, my, x, y, x + w, y + CARD_H);
		Widgets.card(d, x, y, x + w, y + CARD_H, Tokens.R_CARD, hover ? 1 : 0, active ? 1 : 0, active ? 1 : 0);
		Widgets.tile(d, x + 18, y + (CARD_H - TILE) / 2f, TILE, "layers", active ? 1 : 0, hover ? 1 : 0);
		float nx = x + 18 + TILE + 14;
		Type.draw(d, Type.fit(name, w - (nx - x) - 90, 12.5f, Weight.SEMIBOLD), nx, y + 26, 12.5f, Weight.SEMIBOLD, Tokens.TEXT);
		Type.draw(d, active ? "In use now" : "Click to switch to it", nx, y + 48, 9.5f, Weight.MEDIUM, Tokens.TEXT_2);
		if (active) {
			float pw = 12 + Type.width("In use", 9.5f, Weight.SEMIBOLD) + 12;
			float px = x + w - 18 - pw;
			Paint.rect(d, px, y + 18, px + pw, y + 40, 11, Paint.fade(Accent.get(), 0.24f));
			Type.draw(d, "In use", px + 12, y + 29 - 4.75f, 9.5f, Weight.SEMIBOLD, Accent.text());
			return;
		}
		hits.add(x, y, x + w, y + CARD_H, () -> {
			config.loadProfile(name);
			notice("Switched to " + name);
		});
		if (!name.equals(Config.DEFAULT_PROFILE)) {
			float bx2 = x + w - 18;
			float bx1 = bx2 - 28;
			float by = y + 16;
			boolean delHover = within(mx, my, bx1, by, bx2, by + 28);
			Paint.rect(d, bx1, by, bx2, by + 28, 8, delHover ? 0x33FF5C84 : 0x0AFFFFFF);
			Paint.icon(d, "trash-2", bx1 + 7, by + 7, 14, delHover ? Tokens.LOW : Tokens.TEXT_3);
			hits.add(bx1, by, bx2, by + 28, () -> {
				if (config.deleteProfile(name)) notice("Deleted " + name);
			});
		}
	}

	private void drawNewProfile(Draw d, float x, float y, float w, float mx, float my, long now) {
		Paint.rect(d, x, y, x + w, y + CARD_H, Tokens.R_CARD, 0x06FFFFFF);
		Paint.stroke(d, x, y, x + w, y + CARD_H, Tokens.R_CARD, 1, 0x1AFFFFFF);
		Type.draw(d, "New profile from your setup", x + 18, y + 18, 11.5f, Weight.SEMIBOLD, Tokens.TEXT);
		float fy = y + CARD_H - 16 - 32;
		float bw = 78;
		float fx2 = x + w - 18 - bw - 8;
		Widgets.field(d, x + 18, fy, fx2, fy + 32, "pencil", newProfile, "Name it", null, typingProfile, 0, now);
		hits.add(x + 18, fy, fx2, fy + 32, () -> typingProfile = true);
		float bx = x + w - 18 - bw;
		boolean hover = within(mx, my, bx, fy, bx + bw, fy + 32);
		Widgets.accentPill(d, bx, fy, bx + bw, fy + 32, hover ? 1 : 0);
		Type.center(d, "Create", bx + bw / 2f, fy + (32 - 10.5f) / 2f, 10.5f, Weight.SEMIBOLD, 0xFFFFFFFF);
		hits.add(bx, fy, bx + bw, fy + 32, this::createProfile);
	}

	private void createProfile() {
		if (newProfile.isBlank()) {
			notice("Type a name first");
			typingProfile = true;
			return;
		}
		if (Config.get().createProfile(newProfile)) {
			notice("Created " + newProfile.trim());
			newProfile = "";
			typingProfile = false;
		} else {
			notice("That name is taken");
		}
	}

	/** A short message in a pill at the bottom of the window for a moment, like "Added Steve". */
	public void notice(String text) {
		notice = text;
		noticeAt = System.currentTimeMillis();
	}

	private void drawNotice(Draw d, long now) {
		if (notice == null) return;
		long age = now - noticeAt;
		if (age > 2600) return;
		float in = Ease.OUT.apply(age / (double) Tokens.T_COLOR);
		float out = age > 2400 ? 1 - (age - 2400) / 200f : 1;
		float a = Math.min(in, out);
		float cap = 10.5f;
		float w = Type.width(notice, cap, Weight.SEMIBOLD) + 18 + 16 + 22;
		float x = SIDE_W + (WIN_W - SIDE_W - w) / 2f;
		float y = WIN_H - 70 + (1 - in) * 8;
		float old = Paint.opacity(Paint.opacity() * a);
		Widgets.glass(d, x, y, x + w, y + 38, 19);
		Paint.stroke(d, x, y, x + w, y + 38, 19, 1, Paint.fade(Accent.get(), 0.45f));
		Paint.icon(d, "circle-check", x + 16, y + 11, 16, Accent.text());
		Type.draw(d, notice, x + 16 + 16 + 10, y + (38 - cap) / 2f, cap, Weight.SEMIBOLD, Tokens.TEXT);
		Paint.opacity(old);
	}

	private static boolean within(float x, float y, float x1, float y1, float x2, float y2) {
		return x >= x1 && x < x2 && y >= y1 && y < y2;
	}

	private void switchTab(int index) {
		if (index == tab && search.isEmpty()) return;
		tab = index;
		tabSince = System.currentTimeMillis();
		scroll = 0;
		shownScroll = 0;
		search = "";
		searchFocused = false;
		typingProfile = false;
		focused = null;
		for (CardState state : cards.values()) state.placed = false;
		sheet.close();
	}

	/** A click at (x, y) in GUI units (the screen menu); returns whether the menu used it. */
	public boolean mouseClicked(double x, double y, int button) {
		return clickLayout((float) (x / unit) - winX, (float) (y / unit) - winY, button);
	}

	/** A click at (x, y) in the window's layout units; returns whether the menu used it. */
	public boolean clickLayout(float mx, float my, int button) {
		if (sheet.isOpen() && sheet.mouseClicked(mx, my, button)) return true;
		Runnable action = hits.at(mx, my, button);
		searchFocused = false;
		typingProfile = false;
		if (action == null) {
			if (sheet.isOpen() && mx >= 0 && mx < WIN_W && my >= 0 && my < WIN_H && mx < gridRight.get()) sheet.close();
			return mx >= 0 && mx < WIN_W && my >= 0 && my < WIN_H;
		}
		host.clickSound();
		action.run();
		return true;
	}

	public boolean mouseDragged(double x, double y) {
		return dragLayout((float) (x / unit) - winX, (float) (y / unit) - winY);
	}

	public boolean dragLayout(float mx, float my) {
		return sheet.isOpen() && sheet.mouseDragged(mx, my);
	}

	public boolean mouseReleased() {
		return sheet.isOpen() && sheet.mouseReleased();
	}

	public boolean mouseScrolled(double x, double y, double amount) {
		return scrollLayout((float) (x / unit) - winX, (float) (y / unit) - winY, amount);
	}

	public boolean scrollLayout(float mx, float my, double amount) {
		if (sheet.isOpen() && sheet.over(mx, my)) return sheet.mouseScrolled(mx, my, amount);
		scroll = Math.max(0, Math.min(maxScroll, scroll - amount * 70));
		return true;
	}

	/** A key press; returns whether the menu used it. */
	public boolean keyPressed(int key, boolean ctrl) {
		return keyPressed(key, ctrl, false);
	}

	public boolean keyPressed(int key, boolean ctrl, boolean shift) {
		if (sheet.isOpen()) {
			if (sheet.keyPressed(key)) return true;
			if (key == Keys.ESCAPE || key == Keys.LEFT && !sheet.typing()) {
				sheet.close();
				return true;
			}
			if (key == Keys.TAB) {
				sheet.close();
			} else {
				return false;
			}
		}
		if (typingProfile) {
			if (key == Keys.BACKSPACE) {
				if (!newProfile.isEmpty()) newProfile = newProfile.substring(0, newProfile.length() - 1);
				return true;
			}
			if (Keys.enter(key)) {
				createProfile();
				return true;
			}
			if (key == Keys.ESCAPE) {
				typingProfile = false;
				return true;
			}
			return true;
		}
		if (key == Keys.ESCAPE) {
			if (!search.isEmpty() || searchFocused) {
				search = "";
				searchFocused = false;
				tabSince = System.currentTimeMillis();
				return true;
			}
			if (focused != null) {
				focused = null;
				return true;
			}
			host.back();
			return true;
		}
		if (ctrl && key == 70) {
			searchFocused = true;
			return true;
		}
		if (key == Keys.TAB || key == Keys.PAGE_DOWN || key == Keys.PAGE_UP) {
			boolean back = key == Keys.PAGE_UP || key == Keys.TAB && shift;
			switchTab(Math.floorMod(tab + (back ? -1 : 1), TABS.size()));
			return true;
		}
		if (searchFocused) {
			if (key == Keys.BACKSPACE) {
				if (!search.isEmpty()) {
					search = ctrl ? "" : search.substring(0, search.length() - 1);
					scroll = 0;
				}
				return true;
			}
			if (Keys.enter(key) || key == Keys.DOWN) {
				searchFocused = false;
				if (!lastShown.isEmpty()) focused = lastShown.get(0);
				return true;
			}
			return key != Keys.LEFT && key != Keys.RIGHT && key != Keys.UP;
		}
		if (key == Keys.LEFT || key == Keys.RIGHT || key == Keys.UP || key == Keys.DOWN) {
			moveFocus(key);
			return true;
		}
		if (focused != null && (Keys.enter(key) || key == Keys.SPACE)) {
			boolean settings = SettingsSheet.hasPage(focused, sheet.extras());
			if (key == Keys.SPACE && focused.toggleable() || Keys.enter(key) && !settings) {
				if (focused.toggleable()) focused.toggle();
			} else if (settings) {
				sheet.open(focused);
			}
			host.clickSound();
			return true;
		}
		return false;
	}

	private void moveFocus(int key) {
		List<Module> shown = lastShown;
		if (shown.isEmpty()) return;
		int at = focused == null ? -1 : shown.indexOf(focused);
		if (at < 0) {
			focused = shown.get(0);
		} else {
			int next = switch (key) {
				case Keys.LEFT -> at - 1;
				case Keys.RIGHT -> at + 1;
				case Keys.UP -> at - columns;
				default -> at + columns;
			};
			if (next >= 0 && next < shown.size()) focused = shown.get(next);
		}
		int index = shown.indexOf(focused);
		float top = (index / columns) * (CARD_H + GAP);
		float view = WIN_H - 18 - GRID_TOP;
		if (top < scroll) scroll = top;
		else if (top + CARD_H > scroll + view) scroll = top + CARD_H - view;
	}

	/** A typed character; returns whether the menu used it. */
	public boolean charTyped(String typed) {
		if (typed.isEmpty() || typed.codePointAt(0) < 32) return false;
		if (sheet.isOpen()) return sheet.charTyped(typed);
		if (typingProfile) {
			if (newProfile.length() < 24) newProfile += typed;
			return true;
		}
		if (!searchFocused) {
			if (typed.equals("/")) {
				searchFocused = true;
				return true;
			}
			if (typed.equals(" ")) return false;
			if (!Character.isLetterOrDigit(typed.codePointAt(0))) return false;
			searchFocused = true;
		}
		if (search.length() < 32) {
			search += typed;
			scroll = 0;
			focused = null;
			tabSince = System.currentTimeMillis();
		}
		return true;
	}

	/** Whether a text field has the keyboard, so keys like the menu key shouldn't close it. */
	public boolean typing() {
		return searchFocused || typingProfile || sheet.typing();
	}

	/** Per-card animation state, made once per module. */
	private static final class CardState {
		final Anim hover = new Anim(0, Tokens.T_HOVER, Ease.OUT);
		final Anim settings = new Anim(0, Tokens.T_COLOR, Ease.OUT);
		final Anim chosen = new Anim(0, Tokens.T_COLOR, Ease.OUT);
		final Anim x = new Anim(0, Tokens.T_SHEET, Ease.OUT);
		final Anim y = new Anim(0, Tokens.T_SHEET, Ease.OUT);
		final Anim w = new Anim(0, Tokens.T_SHEET, Ease.OUT);
		final Anim knob;
		final Anim lit;
		boolean placed = false;
		long drawnAt = -1;
		float cx;
		float cy;

		CardState(boolean on) {
			knob = new Anim(on ? 1 : 0, Tokens.T_SWITCH_KNOB, Ease.SPRING);
			lit = new Anim(on ? 1 : 0, Tokens.T_SWITCH_FILL, Ease.OUT);
		}
	}
}
