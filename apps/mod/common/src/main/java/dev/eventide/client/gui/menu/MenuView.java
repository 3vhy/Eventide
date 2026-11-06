package dev.eventide.client.gui.menu;

import dev.eventide.client.Config;
import dev.eventide.client.Draw;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.module.Module;
import dev.eventide.client.module.Module.Category;
import dev.eventide.client.module.Modules;
import dev.eventide.client.ui.Anim;
import dev.eventide.client.ui.Ease;
import dev.eventide.client.ui.Grad;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Eventide mod menu: one window with a rail of sections down the left (Mods, Cosmetics, Client and
 * Profiles, with Floating and Edit HUD at its foot), the section's title, a line about it (or about the mod
 * under the mouse) and a search field across the top, a row of pills for the Mods categories, and the mods
 * as cards in a grid, each with its icon on a tile, its name, a line about it and a dot that lights when
 * it's on. Click a card to switch it; the gear on a hovered card, or a right click, opens its settings in a
 * panel built into the right of the window, and the grid makes room for it.
 *
 * <p>Keys: arrows move between cards, Space switches one, Enter opens its settings, Tab and Shift+Tab
 * move through the pills and sections, {@code /} searches (or just start typing), Esc steps back out. Everything is laid out in
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

	/** A place in the menu: a section of the rail and, in Mods, one of its pills (All when {@code category} is null). */
	public record Place(String section, String icon, String pill, String tagline, Category category) {
		boolean profiles() {
			return section.equals("Profiles");
		}
	}

	/** The design canvas the screen menu is fitted into. */
	static final float DESIGN_W = 1600;
	static final float DESIGN_H = 900;
	/** The window, in layout units. */
	public static final float WIN_W = 1500;
	public static final float WIN_H = 850;
	private static final float WIN_R = 30;
	private static final float RAIL_W = 148;
	private static final float RAIL_ITEM_W = 112;
	private static final float RAIL_ITEM_H = 100;
	private static final float RAIL_R = 22;
	private static final float RAIL_TOP = 128;
	private static final float RAIL_PITCH = 108;
	private static final float PAD = 36;
	private static final float HEAD_TOP = 32;
	private static final float SEARCH_W = 330;
	private static final float SEARCH_H = 52;
	private static final float PILL_TOP = 116;
	private static final float PILL_H = 40;
	private static final float CARD_H = 96;
	private static final float CARD_R = 16;
	private static final float GAP = 18;
	private static final float ROW_GAP = 16;
	private static final float MIN_CARD_W = 340;
	private static final float TILE = 56;
	/** The settings panel is drawn this much bigger than its own layout, to match the cards. */
	private static final float SHEET_SCALE = 1.2f;
	private static final float SHEET_W = SettingsSheet.WIDTH * SHEET_SCALE;
	private static final float SHEET_LEFT = WIN_W - PAD - SHEET_W;
	private static final int REVEAL_STAGGER = 16;
	private static final int REVEAL_TIME = 300;

	public static final List<Place> PLACES = places();
	/** The sections down the rail, each as the first place in it. */
	private static final List<Place> SECTIONS = sections();
	private static final int PROFILES = PLACES.size() - 1;

	private final Host host;
	private final Hits hits = new Hits();
	private final SettingsSheet sheet;
	private final long openedAt = System.currentTimeMillis();
	private final Map<Module, CardState> cards = new HashMap<>();
	private final Anim[] railHover = new Anim[SECTIONS.size() + 2];
	private final Anim railY = new Anim(0, 320, Ease.SPRING);
	private final Anim[] pillHover = new Anim[PLACES.size()];
	private final float[] pillMid = new float[PLACES.size()];
	private final Anim pillX = new Anim(0, 300, Ease.SPRING);
	private final Anim pillW = new Anim(0, 300, Ease.SPRING);
	private final Anim searchHover = new Anim(0, Tokens.T_COLOR, Ease.OUT);
	private final Anim searchFocus = new Anim(0, Tokens.T_COLOR, Ease.OUT);
	private final Anim gridRight = new Anim(WIN_W - PAD, Tokens.T_SHEET, Ease.OUT);

	private int place;
	/** The last pill chosen in Mods, to come back to from another section. */
	private int lastModsPlace = 0;
	private long placeSince = System.currentTimeMillis();
	private boolean pillsPlaced = false;
	/** The card under the mouse as last drawn, whose description the header shows. */
	private Module hovered = null;
	private String subtitle = "";
	private long subtitleSince = 0;
	private List<Module> allMods = null;
	private String search = "";
	private final float[] searchMid = new float[2];
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
		for (int i = 0; i < railHover.length; i++) railHover[i] = new Anim(0, Tokens.T_HOVER, Ease.OUT);
		for (int i = 0; i < pillHover.length; i++) pillHover[i] = new Anim(0, Tokens.T_HOVER, Ease.OUT);
		place = indexOf(start);
		if (PLACES.get(place).pill() != null) lastModsPlace = place;
	}

	private static List<Place> places() {
		List<Place> list = new ArrayList<>();
		list.add(new Place("Mods", "layout-grid", "All", "Every mod, all in one place", null));
		for (Category category : Category.values()) {
			if (category == Category.COSMETICS || category == Category.CLIENT) continue;
			list.add(new Place("Mods", "layout-grid", category.label, category.tagline, category));
		}
		list.add(new Place("Cosmetics", "shirt", null, Category.COSMETICS.tagline, Category.COSMETICS));
		list.add(new Place("Client", "settings", null, Category.CLIENT.tagline, Category.CLIENT));
		list.add(new Place("Profiles", "layers", null, "Your setups, one click apart", null));
		return List.copyOf(list);
	}

	private static List<Place> sections() {
		List<Place> list = new ArrayList<>();
		for (Place each : PLACES) {
			if (list.isEmpty() || !list.get(list.size() - 1).section().equals(each.section())) list.add(each);
		}
		return List.copyOf(list);
	}

	private static int sectionOf(int place) {
		String section = PLACES.get(place).section();
		for (int i = 0; i < SECTIONS.size(); i++) {
			if (SECTIONS.get(i).section().equals(section)) return i;
		}
		return 0;
	}

	/** The place showing a category's mods (Mods, All for null). */
	public static int indexOf(Category category) {
		for (int i = 0; i < PLACES.size(); i++) {
			if (PLACES.get(i).category() == category && !PLACES.get(i).profiles()) return i;
		}
		return 0;
	}

	/** The category on show: the chosen pill's in Mods, or the section's; null for All and on the Profiles page. */
	public Category category() {
		return PLACES.get(place).category();
	}

	/** Whether the menu is showing every mod (Mods, All, not searching). */
	public boolean showingAll() {
		return place == 0 && search.isEmpty();
	}

	/** Takes the place, search and open settings of another view of the menu, as when it moves between the screen and the world. */
	public MenuView follow(MenuView other) {
		place = other.place;
		lastModsPlace = other.lastModsPlace;
		search = other.search;
		if (other.sheetOpen()) openSettings(other.sheetModule());
		return this;
	}

	/** Opens a module's settings straight away. */
	public MenuView openSettings(Module module) {
		place = indexOf(module.category);
		if (PLACES.get(place).pill() != null) lastModsPlace = place;
		focused = module;
		sheet.open(module);
		return this;
	}

	/** Shows the Profiles page. */
	public MenuView openProfiles() {
		place = PROFILES;
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

	/** The middle of the search box as last drawn, in GUI units (for the in-game test). */
	public float[] searchCenter() {
		return new float[] {(winX + searchMid[0]) * unit, (winY + searchMid[1]) * unit};
	}

	/** The middle of a module's card as last drawn, in GUI units, or null if it isn't on screen. */
	public float[] cardCenter(Module module) {
		CardState state = cards.get(module);
		if (state == null || state.drawnAt != frames) return null;
		return new float[] {(winX + state.cx) * unit, (winY + state.cy) * unit};
	}

	/** The middle of a category's pill as last drawn, in GUI units (Mods, All for null), or null when the pills aren't on show. */
	public float[] pillCenter(Category category) {
		if (!pillsShown()) return null;
		return new float[] {(winX + pillMid[indexOf(category)]) * unit, (winY + PILL_TOP + PILL_H / 2f) * unit};
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
		return new float[] {(winX + fromSheetX(origin[0])) * unit, (winY + fromSheetY(origin[1])) * unit, unit * SHEET_SCALE};
	}

	/** The middle of knife slot {@code slot} in the Knives settings as last drawn, in GUI units, for the in-game test. */
	public float[] knifeSlot(int slot) {
		float[] at = ((SheetExtras) sheet.extras()).knifeSlotsAt;
		float cx = at[0] + slot * (at[2] + 6) + at[2] / 2f;
		return new float[] {(winX + fromSheetX(cx)) * unit, (winY + fromSheetY(at[1] + at[2] / 2f)) * unit};
	}

	/** The middle of the first knife slot's knife picker in the Knives settings as last drawn, in GUI units, for the in-game test. */
	public float[] knifePicker() {
		float[] at = ((SheetExtras) sheet.extras()).knifePickerAt;
		return new float[] {(winX + fromSheetX((at[0] + at[2]) / 2f)) * unit, (winY + fromSheetY((at[1] + at[3]) / 2f)) * unit};
	}

	/** A point in the window as the settings panel's own layout sees it. */
	private static float sheetX(float x) {
		return SHEET_LEFT + (x - SHEET_LEFT) / SHEET_SCALE;
	}

	private static float sheetY(float y) {
		return PILL_TOP + (y - PILL_TOP) / SHEET_SCALE;
	}

	/** A point of the settings panel's own layout, in the window. */
	private static float fromSheetX(float x) {
		return SHEET_LEFT + (x - SHEET_LEFT) * SHEET_SCALE;
	}

	private static float fromSheetY(float y) {
		return PILL_TOP + (y - PILL_TOP) * SHEET_SCALE;
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
		float pop = opening(now);
		float old = Paint.opacity(Paint.opacity() * intro);
		d.push();
		d.scale(unit);
		if (host.inWorld()) {
			Paint.rect(d, 0, 0, canvasW, canvasH, 0, Grad.down(0x7005030B, 0x9C0A0716));
			Sky.nebula(d, canvasW, canvasH, 0.55f);
		} else {
			Sky.backdrop(d, canvasW, canvasH, false, 1);
		}
		if (Modules.THEME_SNOW.get()) Sky.stars(d, canvasW, canvasH, (float) (Modules.THEME_SNOW_AMOUNT.get() / 100.0) * 0.6f, 1, now);
		float lift = (1 - intro) * 10;
		d.translate(winX + WIN_W / 2f, winY + WIN_H / 2f + lift);
		d.scale(pop);
		d.translate(-WIN_W / 2f, -WIN_H / 2f);
		float mx = ((float) (mouseX / unit) - winX - WIN_W / 2f) / pop + WIN_W / 2f;
		float my = ((float) (mouseY / unit) - winY - lift - WIN_H / 2f) / pop + WIN_H / 2f;
		drawWindow(d, mx, my, now);
		d.pop();
		Paint.opacity(old);
	}

	/** How big the window is as it opens: springing from a little small to a touch past full size and settling. */
	private float opening(long now) {
		if (Anim.speed() <= 0) return 1;
		double t = Math.min(1, (now - openedAt) * Anim.speed() / 420.0);
		return 0.93f + 0.07f * Ease.SPRING.apply(t);
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

		boolean overSheet = sheet.over(sheetX(mx), sheetY(my));
		float hx = overSheet ? -9999 : mx;
		float hy = overSheet ? -9999 : my;
		int accent = Accent.get();
		boolean floating = host.floating();
		Paint.shadow(d, 0, 0, WIN_W, WIN_H, WIN_R, 0, 30, 90, -8, 0xA6000000);
		Paint.shadow(d, 0, 0, WIN_W, WIN_H, WIN_R, 0, 0, 120, -30, Paint.fade(accent, 0.16f));
		Paint.rect(d, 0, 0, WIN_W, WIN_H, WIN_R, Grad.down(floating ? 0xF2171424 : 0xD6171424, floating ? 0xF70C0A14 : 0xE80C0A14));
		drawGlassLight(d, accent, now);
		Paint.rect(d, 0, 0, RAIL_W, WIN_H, WIN_R, 0x24000000);
		Paint.rect(d, RAIL_W - WIN_R, 0, RAIL_W, WIN_H, 0, 0x24000000);
		Paint.rect(d, RAIL_W, 0, RAIL_W + 1, WIN_H, 0, 0x14FFFFFF);
		Paint.stroke(d, 0, 0, WIN_W, WIN_H, WIN_R, 1, 0x2EFFFFFF);
		Widgets.shine(d, 0, 0, WIN_W, WIN_R * 2);

		drawRail(d, hx, hy, now);
		float x0 = RAIL_W + PAD;
		float sheetT = sheet.openness();
		gridRight.set(sheet.isOpen() ? SHEET_LEFT - 22 : WIN_W - PAD);
		drawHeader(d, x0, hx, hy, now);
		drawPills(d, x0, hx, hy);
		if (PLACES.get(place).profiles() && search.isEmpty()) {
			hovered = null;
			drawProfiles(d, x0, hx, hy, now);
		} else {
			drawModules(d, x0, hx, hy, now, (float) dt);
		}
		if (sheetT > 0.001f || sheet.isOpen()) {
			d.push();
			d.translate(SHEET_LEFT, PILL_TOP);
			d.scale(SHEET_SCALE);
			d.translate(-SHEET_LEFT, -PILL_TOP);
			sheet.draw(d, SHEET_LEFT, PILL_TOP, SHEET_LEFT + SettingsSheet.WIDTH, sheetY(WIN_H - 22), sheetX(mx), sheetY(my), now);
			d.pop();
		}
		drawNotice(d, now);
	}

	/**
	 * The light inside the glass: soft glows in the theme colour that drift slowly round the window, and, as
	 * the menu opens, a band of light that sweeps across it once.
	 */
	private void drawGlassLight(Draw d, int accent, long now) {
		double t = now / 1000.0;
		d.enableScissor(0, 0, Math.round(WIN_W), Math.round(WIN_H));
		float ax = -220 + (float) Math.sin(t * 0.21) * 70;
		float ay = -260 + (float) Math.cos(t * 0.17) * 50;
		Paint.shadow(d, ax, ay, ax + 680, ay + 400, 200, 0, 0, 260, 0, Paint.fade(accent, 0.18f));
		float bx = WIN_W - 380 + (float) Math.cos(t * 0.15) * 80;
		float by = WIN_H - 120 + (float) Math.sin(t * 0.19) * 50;
		Paint.shadow(d, bx, by, bx + 580, by + 380, 200, 0, 0, 260, 0, Paint.fade(Accent.secondary(), 0.09f));
		float cx = WIN_W * 0.55f + (float) Math.sin(t * 0.11) * 160;
		Paint.shadow(d, cx - 160, -200, cx + 160, 60, 140, 0, 0, 220, 0, Paint.fade(Accent.lighten(accent, 0.3f), 0.07f));
		if (Anim.speed() > 0) {
			float sweep = (float) ((now - openedAt) * Anim.speed() / 1200.0);
			if (sweep < 1) {
				float band = 240;
				float x = -band + (WIN_W + 2 * band) * Ease.OUT.apply(sweep);
				float strength = (float) Math.sin(Math.PI * sweep);
				int light = Paint.fade(0x2EFFFFFF, strength);
				Paint.rect(d, x - band, 0, x, WIN_H, 0, Grad.across(0x00FFFFFF, light));
				Paint.rect(d, x, 0, x + band, WIN_H, 0, Grad.across(light, 0x00FFFFFF));
			}
		}
		d.disableScissor();
	}

	private void drawRail(Draw d, float mx, float my, long now) {
		Logo.mark(d, RAIL_W / 2f - 27, 34, 54, 1, now);
		int accent = Accent.get();
		float x1 = (RAIL_W - RAIL_ITEM_W) / 2f;
		float x2 = x1 + RAIL_ITEM_W;
		int current = sectionOf(place);
		boolean searching = !search.isEmpty();
		float activeY = RAIL_TOP + current * RAIL_PITCH;
		railY.set(activeY);
		if (!laidOut) {
			railY.snap(activeY);
			laidOut = true;
		}
		if (!searching) drawRailLight(d, x1, railY.get(), x2, accent, 1);
		for (int i = 0; i < SECTIONS.size(); i++) {
			Place each = SECTIONS.get(i);
			int index = i;
			drawRailItem(d, i, each.icon(), each.section(), x1, RAIL_TOP + i * RAIL_PITCH, x2, i == current && !searching, mx, my, () -> switchSection(index));
		}
		float editY = WIN_H - 24 - RAIL_ITEM_H;
		float floatY = editY - RAIL_PITCH;
		boolean floating = host.floating();
		if (floating) drawRailLight(d, x1, floatY, x2, accent, 0.6f);
		drawRailItem(d, SECTIONS.size(), "app-window", "Floating", x1, floatY, x2, floating, mx, my, () -> host.setFloating(!host.floating()));
		drawRailItem(d, SECTIONS.size() + 1, "panels-top-left", "Edit HUD", x1, editY, x2, false, mx, my, host::openHudEditor);
	}

	/** The lit square behind the chosen rail item. */
	private static void drawRailLight(Draw d, float x1, float y, float x2, int accent, float strength) {
		float y2 = y + RAIL_ITEM_H;
		Paint.shadow(d, x1, y, x2, y2, RAIL_R, 0, 6, 28, -8, Paint.fade(accent, 0.34f * strength));
		Paint.rect(d, x1, y, x2, y2, RAIL_R, Grad.down(Paint.fade(accent, 0.3f * strength), Paint.fade(accent, 0.15f * strength)));
		Paint.stroke(d, x1, y, x2, y2, RAIL_R, 1, Paint.fade(Accent.lighten(accent, 0.3f), 0.35f * strength));
		Widgets.shine(d, x1, y, x2, RAIL_R);
	}

	private void drawRailItem(Draw d, int slot, String icon, String label, float x1, float y, float x2, boolean active, float mx, float my, Runnable action) {
		boolean hover = within(mx, my, x1, y, x2, y + RAIL_ITEM_H);
		railHover[slot].set(hover && !active);
		float h = railHover[slot].get();
		if (h > 0.01f) Paint.rect(d, x1, y, x2, y + RAIL_ITEM_H, RAIL_R, Paint.fade(0x10FFFFFF, h));
		int color = active ? Accent.text() : Widgets.mixColor(Tokens.TEXT_2, Tokens.TEXT, h);
		float size = 30;
		float cx = (x1 + x2) / 2f;
		Paint.icon(d, icon, cx - size / 2f, y + 22 - 2 * h, size, color);
		Type.center(d, label, cx, y + 68, 12, Weight.SEMIBOLD, color);
		hits.add(x1, y, x2, y + RAIL_ITEM_H, action);
	}

	private void drawHeader(Draw d, float x0, float mx, float my, long now) {
		boolean searching = !search.isEmpty();
		Place current = PLACES.get(place);
		String title = searching ? "Search" : current.section();
		String line;
		if (hovered != null) {
			line = hovered.description;
		} else if (searching) {
			List<Module> shown = shownModules();
			line = shown.isEmpty() ? "Nothing matches \"" + search.trim() + "\"" : shown.size() + (shown.size() == 1 ? " result" : " results") + " for \""
					+ search.trim() + "\"";
		} else {
			line = current.tagline();
		}
		if (!line.equals(subtitle)) {
			subtitle = line;
			subtitleSince = now;
		}
		float sx2 = WIN_W - PAD;
		float sx1 = sx2 - SEARCH_W;
		float t = Ease.OUT.apply((now - placeSince) / (double) Tokens.T_CAPTION);
		float old = Paint.opacity(Paint.opacity() * t);
		float rise = (1 - t) * 4;
		Type.draw(d, title, x0, HEAD_TOP + 6 + rise, 24, Weight.BOLD, Tokens.TEXT);
		float s = Ease.OUT.apply((now - subtitleSince) / 180.0);
		float faded = Paint.opacity(Paint.opacity() * s);
		Type.draw(d, Type.fit(subtitle, sx1 - x0 - 30, 13, Weight.MEDIUM), x0, HEAD_TOP + 41 + rise + (1 - s) * 3, 13, Weight.MEDIUM, Tokens.TEXT_2);
		Paint.opacity(faded);
		Paint.opacity(old);

		float sy = HEAD_TOP;
		boolean searchHovered = within(mx, my, sx1, sy, sx2, sy + SEARCH_H);
		searchHover.set(searchHovered);
		searchFocus.set(searchFocused);
		drawSearch(d, sx1, sy, sx2, sy + SEARCH_H, searchHover.get(), searchFocus.get(), now);
		hits.add(sx1, sy, sx2, sy + SEARCH_H, () -> searchFocused = true);
		searchMid[0] = (sx1 + sx2) / 2f;
		searchMid[1] = sy + SEARCH_H / 2f;
	}

	/** The search field: an outlined box with a magnifier, lit in the theme colour while it has the keyboard. */
	private void drawSearch(Draw d, float x1, float y1, float x2, float y2, float hover, float focus, long now) {
		int accent = Accent.get();
		float r = 18;
		if (focus > 0.01f) Paint.shadow(d, x1, y1, x2, y2, r, 0, 0, 24, -4, Paint.fade(accent, 0.32f * focus));
		Paint.rect(d, x1, y1, x2, y2, r, Widgets.mixColor(0x08FFFFFF, 0x10FFFFFF, Math.max(hover, focus)));
		int edge = Widgets.mixColor(Widgets.mixColor(0x30FFFFFF, 0x4AFFFFFF, hover), Paint.fade(Accent.lighten(accent, 0.25f), 0.9f), focus);
		Paint.stroke(d, x1, y1, x2, y2, r, 1.5f, edge);
		float h = y2 - y1;
		float cap = 13;
		Paint.icon(d, "search", x1 + 20, y1 + (h - 18) / 2f, 18, Widgets.mixColor(Tokens.TEXT_2, Tokens.TEXT, focus));
		float tx = x1 + 50;
		float ty = y1 + (h - cap) / 2f;
		String shown = search.isEmpty() ? "" : Type.fit(search, x2 - tx - 20, cap, Weight.MEDIUM);
		if (search.isEmpty()) Type.draw(d, "Search mods", tx, ty, cap, Weight.MEDIUM, Tokens.TEXT_3);
		else Type.draw(d, shown, tx, ty, cap, Weight.MEDIUM, Tokens.TEXT);
		if (searchFocused && (now / 530) % 2 == 0) {
			float cx = tx + Type.width(shown, cap, Weight.MEDIUM) + 1.5f;
			Paint.rect(d, cx, ty - 3, cx + 1.5f, ty + cap + 3, 0.75f, Accent.lighten(accent, 0.3f));
		}
	}

	private boolean pillsShown() {
		return PLACES.get(place).pill() != null && search.isEmpty();
	}

	/** Where the grid starts: under the pills in Mods, straight under the header elsewhere. */
	private float gridTop() {
		return pillsShown() ? PILL_TOP + PILL_H + 22 : PILL_TOP;
	}

	/** The Mods pills (All and each category), the chosen one lit, its light gliding across when you pick another. */
	private void drawPills(Draw d, float x0, float mx, float my) {
		if (!pillsShown()) {
			pillsPlaced = false;
			return;
		}
		int accent = Accent.get();
		float cap = 12.5f;
		float y = PILL_TOP;
		float r = PILL_H / 2f;
		float x = x0;
		float activeX = x0;
		float activeW = 0;
		for (int i = 0; i < PLACES.size(); i++) {
			Place each = PLACES.get(i);
			if (each.pill() == null) continue;
			float w = Type.width(each.pill(), cap, Weight.MEDIUM) + 44;
			pillMid[i] = x + w / 2f;
			if (i == place) {
				activeX = x;
				activeW = w;
			}
			boolean hover = within(mx, my, x, y, x + w, y + PILL_H);
			pillHover[i].set(hover && i != place);
			Paint.rect(d, x, y, x + w, y + PILL_H, r, Widgets.mixColor(0x12FFFFFF, 0x1FFFFFFF, pillHover[i].get()));
			int index = i;
			hits.add(x, y, x + w, y + PILL_H, () -> switchPlace(index));
			x += w + 12;
		}
		pillX.set(activeX);
		pillW.set(activeW);
		if (!pillsPlaced) {
			pillX.snap(activeX);
			pillW.snap(activeW);
			pillsPlaced = true;
		}
		float ax = pillX.get();
		float aw = pillW.get();
		Paint.shadow(d, ax, y, ax + aw, y + PILL_H, r, 0, 4, 20, -6, Paint.fade(accent, 0.36f));
		Paint.rect(d, ax, y, ax + aw, y + PILL_H, r, Grad.down(Paint.fade(accent, 0.44f), Paint.fade(accent, 0.28f)));
		Paint.stroke(d, ax, y, ax + aw, y + PILL_H, r, 1, Paint.fade(Accent.lighten(accent, 0.35f), 0.45f));
		Widgets.shine(d, ax, y, ax + aw, r);
		for (int i = 0; i < PLACES.size(); i++) {
			Place each = PLACES.get(i);
			if (each.pill() == null) continue;
			int color = i == place ? Tokens.TEXT : Widgets.mixColor(Tokens.TEXT_2, Tokens.TEXT, pillHover[i].get());
			Type.center(d, each.pill(), pillMid[i], y + (PILL_H - cap) / 2f, cap, Weight.MEDIUM, color);
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
		Place current = PLACES.get(place);
		if (current.profiles()) return List.of();
		if (current.category() != null) return Modules.in(current.category());
		if (allMods == null) {
			List<Module> all = new ArrayList<>();
			for (Place each : PLACES) {
				if (each.pill() == null || each.category() == null) continue;
				all.addAll(Modules.in(each.category()));
			}
			allMods = List.copyOf(all);
		}
		return allMods;
	}

	private void drawModules(Draw d, float x0, float mx, float my, long now, float dt) {
		List<Module> shown = shownModules();
		lastShown = shown;
		float right = gridRight.get();
		float target = gridRight.target() - x0;
		columns = Math.max(1, Math.min(3, (int) ((target + GAP) / (MIN_CARD_W + GAP))));
		float cardW = (target - (columns - 1) * GAP) / columns;
		float gridTop = gridTop();
		float viewTop = gridTop - 10;
		float viewBottom = WIN_H - 22;
		int rows = (shown.size() + columns - 1) / columns;
		float contentH = rows * (CARD_H + ROW_GAP) - ROW_GAP + 12;
		maxScroll = Math.max(0, contentH - (viewBottom - gridTop));
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		shownScroll = Math.max(0, Math.min(shownScroll, maxScroll));

		d.enableScissor(Math.round(x0 - 20), Math.round(viewTop), Math.round(WIN_W), Math.round(viewBottom));
		hits.clip(x0 - 20, viewTop, right + 4, viewBottom);
		boolean mouseInGrid = my >= viewTop && my < viewBottom && mx < right + 4;
		hovered = null;
		for (int i = 0; i < shown.size(); i++) {
			Module module = shown.get(i);
			float tx = x0 + (i % columns) * (cardW + GAP);
			float ty = gridTop + (i / columns) * (CARD_H + ROW_GAP);
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
			float reveal = Ease.OUT.apply((now - placeSince - Math.min(i, 12) * REVEAL_STAGGER) / (double) REVEAL_TIME);
			drawCard(d, module, x, y, w, reveal, mouseInGrid ? mx : -9999, mouseInGrid ? my : -9999, now);
		}
		hits.unclip();
		d.disableScissor();
		if (maxScroll > 0) {
			float barH = viewBottom - gridTop;
			float thumb = Math.max(40, barH * barH / (barH + (float) maxScroll));
			float ty = gridTop + (barH - thumb) * (float) (shownScroll / maxScroll);
			float bx = right + 11;
			Paint.rect(d, bx, gridTop, bx + 4, gridTop + barH, 2, 0x0DFFFFFF);
			Paint.rect(d, bx, ty, bx + 4, ty + thumb, 2, 0x38FFFFFF);
		}
		if (shown.isEmpty()) {
			boolean searching = !search.isEmpty();
			float cx = (x0 + right) / 2f;
			Paint.icon(d, searching ? "search" : "sparkles", cx - 16, gridTop + 60, 32, Tokens.TEXT_3);
			String hint = searching ? "Try another word, or Esc to clear the search" : "Nothing here yet";
			Type.center(d, hint, cx, gridTop + 108, 13, Weight.MEDIUM, Tokens.TEXT_3);
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
		state.lit.set(on);
		boolean hover = within(mx, my, x, y, x + w, y + CARD_H);
		if (hover) hovered = module;
		state.hover.set(hover);
		boolean open = sheet.isOpen() && sheet.module() == module;
		state.chosen.set(open || focused == module && !sheet.isOpen());
		float h = state.hover.get();
		float chosen = state.chosen.get();
		int accent = Accent.get();
		float old = Paint.opacity(Paint.opacity() * reveal);
		float top = y + (1 - reveal) * Tokens.REVEAL_RISE;
		float lit = module.toggleable() ? state.lit.get() : 0;
		float grow = (0.95f + 0.05f * Ease.SPRING.apply(reveal)) * (1 + 0.012f * h);
		float x2 = x + w;
		float y2 = top + CARD_H;
		float midX = x + w / 2f;
		float midY = top + CARD_H / 2f;
		d.push();
		d.translate(midX, midY - 3 * h);
		d.scale(grow);
		d.translate(-midX, -midY);

		if (h > 0.01f) Paint.shadow(d, x, top, x2, y2, CARD_R, 0, 12, 30, -10, Paint.fade(0xFF000000, 0.5f * h));
		if (lit > 0.01f) Paint.shadow(d, x, top, x2, y2, CARD_R, 0, 0, 30, -8, Paint.fade(accent, 0.2f * lit));
		Paint.rect(d, x, top, x2, y2, CARD_R, Grad.down(Widgets.mixColor(0x12FFFFFF, 0x1CFFFFFF, h), Widgets.mixColor(0x0BFFFFFF, 0x14FFFFFF, h)));
		if (lit > 0.01f) Paint.rect(d, x, top, x2, y2, CARD_R, Grad.down(Paint.fade(accent, 0.16f * lit), Paint.fade(accent, 0.08f * lit)));
		Paint.stroke(d, x, top, x2, y2, CARD_R, 1, Widgets.mixColor(0x0DFFFFFF, 0x1FFFFFFF, h));
		if (lit > 0.01f) Paint.stroke(d, x, top, x2, y2, CARD_R, 1.5f, Paint.fade(Accent.lighten(accent, 0.1f), 0.8f * lit));
		if (chosen > 0.01f) Paint.stroke(d, x - 3, top - 3, x2 + 3, y2 + 3, CARD_R + 3, 1.5f, Paint.fade(Accent.lighten(accent, 0.35f), 0.7f * chosen));
		Widgets.shine(d, x, top, x2, CARD_R);

		float tx = x + 20;
		drawTile(d, tx, top + (CARD_H - TILE) / 2f, module.icon(), lit, h, accent);

		boolean hasSettings = module == Modules.PROFILES || SettingsSheet.hasPage(module, sheet.extras());
		Runnable openSettings = module == Modules.PROFILES ? () -> switchPlace(PROFILES) : () -> {
			focused = null;
			sheet.open(module);
		};
		float dotX = x2 - 30;
		long since = now - state.toggledAt;
		if (module.toggleable()) {
			float pop = 0;
			if (since < 560 && Anim.speed() > 0) {
				float p = since / 560f;
				float ring = 8 + 30 * Ease.OUT.apply(p);
				int color = on ? Accent.lighten(accent, 0.25f) : 0xFFFFFFFF;
				Paint.ring(d, dotX, midY, ring, 2 * (1 - p) + 0.5f, Paint.fade(color, 0.6f * (1 - p)));
				if (since < 300) pop = (float) Math.sin(Math.PI * since / 300.0) * 0.4f;
			}
			drawDot(d, dotX, midY, lit, h, pop, accent);
		} else {
			Paint.icon(d, "chevron-right", dotX - 9, midY - 9, 18, Widgets.mixColor(Tokens.TEXT_3, Tokens.TEXT, h));
		}
		boolean gear = hasSettings && module.toggleable();
		float gearX = dotX - 40;
		if (gear) {
			boolean gearHover = within(mx, my, gearX - 15, midY - 15, gearX + 15, midY + 15);
			state.settings.set(gearHover);
			float sh = state.settings.get();
			float show = Math.max(Math.max(h, open ? 1 : 0), chosen);
			if (show > 0.01f) {
				float faded = Paint.opacity(Paint.opacity() * show);
				if (sh > 0.01f) Paint.circle(d, gearX, midY, 16, Paint.fade(0x17FFFFFF, sh));
				Paint.icon(d, "settings", gearX - 9, midY - 9, 18, open ? Accent.text() : Widgets.mixColor(Tokens.TEXT_3, Tokens.TEXT, sh));
				Paint.opacity(faded);
			}
		}

		float nameX = tx + TILE + 18;
		float nameRight = (gear ? gearX - 15 : dotX - 14) - 8;
		String key = host.keyFor(module);
		float keyCap = 8.5f;
		float keyW = key != null ? Widgets.kbdWidth(key, keyCap) + 10 : 0;
		float cap = 15.5f;
		String name = Type.fit(module.name, nameRight - nameX - keyW, cap, Weight.MEDIUM);
		Type.draw(d, name, nameX, top + 27, cap, Weight.MEDIUM, Tokens.TEXT);
		if (key != null) Widgets.kbd(d, nameX + Type.width(name, cap, Weight.MEDIUM) + 10, top + 24.5f, key, keyCap);
		Type.draw(d, Type.fit(module.description, nameRight - nameX, 12, Weight.MEDIUM), nameX, top + 56, 12, Weight.MEDIUM, Tokens.TEXT_2);
		d.pop();
		Paint.opacity(old);

		if (reveal < 0.5f) return;
		Runnable primary = module.toggleable() ? () -> toggle(module) : openSettings;
		hits.add(x, top, x2, y2, primary, hasSettings ? openSettings : null);
		if (gear) hits.add(gearX - 16, midY - 16, gearX + 16, midY + 16, openSettings, openSettings);
	}

	/** A card's icon tile: grey when off, filled with the theme colour (and glowing) when on. */
	private static void drawTile(Draw d, float x, float y, String icon, float lit, float hover, int accent) {
		float r = 14;
		Paint.rect(d, x, y, x + TILE, y + TILE, r, Widgets.mixColor(0x12FFFFFF, 0x1CFFFFFF, hover));
		if (lit > 0.01f) {
			Paint.shadow(d, x, y, x + TILE, y + TILE, r, 0, 6, 20, -6, Paint.fade(accent, 0.55f * lit));
			Paint.rect(d, x, y, x + TILE, y + TILE, r, Grad.down(Paint.fade(Accent.lighten(accent, 0.18f), lit), Paint.fade(accent, lit)));
			Widgets.shine(d, x, y, x + TILE, r);
		}
		float size = 24;
		int color = Widgets.mixColor(Widgets.mixColor(Tokens.TEXT_2, Tokens.TEXT, hover * 0.6f), 0xFFFFFFFF, lit);
		Paint.icon(d, icon, x + (TILE - size) / 2f, y + (TILE - size) / 2f, size, color);
	}

	/** The dot at a card's right: dim when off, a lit bead with a soft halo when on; {@code pop} swells it as it switches. */
	private static void drawDot(Draw d, float cx, float cy, float lit, float hover, float pop, int accent) {
		float grow = 1 + pop;
		if (lit < 0.99f) Paint.circle(d, cx, cy, 5 * grow, Paint.fade(Widgets.mixColor(0x3DFFFFFF, 0x5CFFFFFF, hover), 1 - lit));
		if (lit > 0.01f) {
			Paint.glow(d, cx, cy, 6 * grow, 14, Paint.fade(accent, 0.55f * lit));
			Paint.circle(d, cx, cy, 9 * grow, Paint.fade(accent, 0.42f * lit));
			Paint.circle(d, cx, cy, 5.5f * grow, Paint.fade(Accent.lighten(accent, 0.45f), lit));
		}
	}

	private void toggle(Module module) {
		focused = null;
		module.toggle();
		state(module).toggledAt = System.currentTimeMillis();
	}

	private void drawProfiles(Draw d, float x0, float mx, float my, long now) {
		Config config = Config.get();
		float reveal = Ease.OUT.apply((now - placeSince) / (double) REVEAL_TIME);
		float old = Paint.opacity(Paint.opacity() * reveal);
		float rise = (1 - reveal) * Tokens.REVEAL_RISE;
		float right = gridRight.target();
		int cols = Math.max(1, Math.min(3, (int) ((right - x0 + GAP) / (MIN_CARD_W + GAP))));
		float cardW = (right - x0 - (cols - 1) * GAP) / cols;
		float y = gridTop() + rise;
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
			float cy = gridTop() + (slot / cols) * (CARD_H + GAP) + rise;
			drawProfileCard(d, config, name, cx, cy, cardW, mx, my);
			slot++;
		}
		if (cols > 1 && slot % cols == 0) slot++;
		float cx = x0 + (slot % cols) * (cardW + GAP);
		float cy = gridTop() + (slot / cols) * (CARD_H + GAP) + rise;
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
		float x = RAIL_W + (WIN_W - RAIL_W - w) / 2f;
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

	private void switchSection(int index) {
		Place first = SECTIONS.get(index);
		switchPlace(first.pill() != null ? lastModsPlace : PLACES.indexOf(first));
	}

	private void switchPlace(int index) {
		if (index == place && search.isEmpty()) return;
		place = index;
		if (PLACES.get(index).pill() != null) lastModsPlace = index;
		placeSince = System.currentTimeMillis();
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
		if (sheet.isOpen() && sheet.mouseClicked(sheetX(mx), sheetY(my), button)) return true;
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
		return sheet.isOpen() && sheet.mouseDragged(sheetX(mx), sheetY(my));
	}

	public boolean mouseReleased() {
		return sheet.isOpen() && sheet.mouseReleased();
	}

	public boolean mouseScrolled(double x, double y, double amount) {
		return scrollLayout((float) (x / unit) - winX, (float) (y / unit) - winY, amount);
	}

	public boolean scrollLayout(float mx, float my, double amount) {
		if (sheet.isOpen() && sheet.over(sheetX(mx), sheetY(my))) return sheet.mouseScrolled(sheetX(mx), sheetY(my), amount);
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
				placeSince = System.currentTimeMillis();
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
			switchPlace(Math.floorMod(place + (back ? -1 : 1), PLACES.size()));
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
		float top = (index / columns) * (CARD_H + ROW_GAP);
		float view = WIN_H - 22 - gridTop();
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
			placeSince = System.currentTimeMillis();
		}
		return true;
	}

	/** Lets go of the keyboard (the search box and the new-profile name), as when you click away from the panel. */
	public void stopTyping() {
		searchFocused = false;
		typingProfile = false;
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
		final Anim lit;
		boolean placed = false;
		long drawnAt = -1;
		/** When it was last switched, for the ripple off its dot. */
		long toggledAt = 0;
		float cx;
		float cy;

		CardState(boolean on) {
			lit = new Anim(on ? 1 : 0, Tokens.T_SWITCH_FILL, Ease.OUT);
		}
	}
}
