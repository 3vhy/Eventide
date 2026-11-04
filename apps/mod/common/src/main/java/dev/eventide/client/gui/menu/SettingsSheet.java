package dev.eventide.client.gui.menu;

import dev.eventide.client.Draw;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.module.Module;
import dev.eventide.client.module.Setting;
import dev.eventide.client.ui.Anim;
import dev.eventide.client.ui.Ease;
import dev.eventide.client.ui.Hits;
import dev.eventide.client.ui.Keys;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Type.Weight;
import dev.eventide.client.ui.Widgets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A module's settings, in a panel built into the right of the menu window: its tile, name, description and
 * switch at the top, then its settings under their headings (Advanced folded away until you open it).
 * Switches, sliders, segmented pickers for a few short options and dropdowns for longer lists, text fields
 * that say when what you typed won't work, and colours with a full picker (saturation and brightness,
 * hue, Eventide's swatches and the hex code). Whatever else the module needs, like the Player List's
 * names or the Knives slots, comes from {@link Extras}. Up and Down move between settings, Left and Right
 * change one, Enter or Space switch it; right-click a setting to put it back to its default.
 */
public final class SettingsSheet {
	/** Settings that aren't plain {@link Setting}s: lists, files, status lines, key binds and buttons. */
	public interface Extras {
		/** Whether this module has any. */
		boolean has(Module module);

		/** How tall they are, {@code width} wide. */
		float height(Module module, float width);

		/** Whether they come before the module's plain settings rather than after them. */
		default boolean first(Module module) {
			return false;
		}

		void draw(Draw d, Module module, float x, float y, float width, float mouseX, float mouseY, Hits hits, long nowMs);

		boolean keyPressed(int key);

		boolean charTyped(String typed);

		boolean typing();

		void stopTyping();
	}

	public static final float WIDTH = 460;
	private static final float PAD = 24;
	private static final float HEAD = 104;
	private static final float SECTION = 34;
	private static final float DESC_LINE = 13;
	private static final float PICKER = 186;
	private static final float DROP_ROW = 30;
	private static final String ADVANCED = "Advanced";

	private final Extras extras;
	private final Runnable clickSound;
	private final Hits hits = new Hits();
	private final Anim open = new Anim(0, Tokens.T_SHEET, Ease.OUT);
	private final Map<Setting, Anim[]> toggles = new HashMap<>();
	private final Map<Setting, Anim> segments = new HashMap<>();
	private final Map<Setting, Anim> hovers = new HashMap<>();
	private final Map<String, List<String>> wraps = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
			return size() > 256;
		}
	};
	private final Set<String> unfolded = new HashSet<>();
	private final Anim headKnob = new Anim(0, Tokens.T_SWITCH_KNOB, Ease.OUT);
	private final Anim headLit = new Anim(0, Tokens.T_SWITCH_FILL, Ease.OUT);
	private final Anim dropReveal = new Anim(0, 160, Ease.OUT);
	private final List<Setting> shownRows = new ArrayList<>();
	private Module module;
	private Module closing;
	private double scroll = 0;
	private double shownScroll = 0;
	private double maxScroll = 0;
	private Setting.Slider dragging = null;
	private float dragX;
	private float dragW;
	private Setting.Text editing = null;
	private Setting.Color picking = null;
	private String hexText = null;
	private int pickDrag = 0;
	private float pickX;
	private float pickY;
	private float pickW;
	private float pickH;
	private float[] hsv = {0, 0, 1};
	private Setting.Choice dropdown = null;
	private float dropX1;
	private float dropY;
	private float dropX2;
	private int focusRow = -1;
	private float sheetX1;
	private float sheetY1;
	private float sheetX2;
	private float sheetY2;
	private float contentX;
	private float contentY;
	private float lastX;
	private float lastY;
	private float mouseX;
	private float mouseY;

	public SettingsSheet(Extras extras, Runnable clickSound) {
		this.extras = extras;
		this.clickSound = clickSound;
	}

	public Extras extras() {
		return extras;
	}

	/** Whether a module has anything to show on its settings page. */
	public static boolean hasPage(Module module, Extras extras) {
		return !module.settings().isEmpty() || !module.toggleable() || extras != null && extras.has(module);
	}

	public void open(Module next) {
		if (next != module) {
			scroll = 0;
			shownScroll = 0;
			focusRow = -1;
			picking = null;
			dropdown = null;
		}
		module = next;
		closing = null;
		headKnob.snap(next.enabled() ? 1 : 0);
		headLit.snap(next.enabled() ? 1 : 0);
		open.set(1);
	}

	public void close() {
		if (module == null) return;
		closing = module;
		module = null;
		editing = null;
		dragging = null;
		picking = null;
		dropdown = null;
		if (extras != null) extras.stopTyping();
		open.set(0);
	}

	/** How far open the panel is (0..1), for the menu to make room for it. */
	public float openness() {
		return open.get();
	}

	/** Where the panel's rows started last frame (the left edge and top of the first row), in layout units. */
	public float[] contentOrigin() {
		return new float[] {contentX, contentY};
	}

	public boolean isOpen() {
		return module != null;
	}

	public Module module() {
		return module;
	}

	public boolean typing() {
		return editing != null || hexText != null && picking != null || extras != null && module != null && extras.typing();
	}

	/** Draws the panel into the box (x1, y1)-(x2, y2), sliding in from the right as it opens. */
	public void draw(Draw d, float x1, float y1, float x2, float y2, float mx, float my, long now) {
		float t = open.get();
		Module shown = module != null ? module : closing;
		if (shown == null || t <= 0.001f) {
			if (module == null) closing = null;
			return;
		}
		mouseX = mx;
		mouseY = my;
		hits.clear();
		float old = Paint.opacity(Paint.opacity() * t);
		float slide = (1 - t) * 28;
		float x = x1 + slide;
		float w = x2 - x1;
		sheetX1 = x;
		sheetY1 = y1;
		sheetX2 = x + w;
		sheetY2 = y2;
		Paint.shadow(d, x, y1, x + w, y2, Tokens.R_PANEL, 0, 10, 36, -12, 0x80000000);
		Paint.rect(d, x, y1, x + w, y2, Tokens.R_PANEL, 0xF2100B20);
		Paint.stroke(d, x, y1, x + w, y2, Tokens.R_PANEL, 1, 0x24FFFFFF);
		Paint.rect(d, x + Tokens.R_PANEL, y1, x + w - Tokens.R_PANEL, y1 + 1, 0, Paint.fade(Accent.get(), 0.5f));
		drawHead(d, shown, x, y1, w, mx, my);

		float listTop = y1 + HEAD;
		float listBottom = y2 - 58;
		float inner = w - 2 * PAD;
		layoutRows(shown);
		float contentH = 0;
		String group = null;
		for (Setting setting : shownRows) {
			if (setting.group() != null && !setting.group().equals(group)) {
				contentH += SECTION;
				group = setting.group();
			}
			if (!folded(shown, setting)) contentH += rowHeight(setting, inner);
		}
		float extra = extras != null && extras.has(shown) ? extras.height(shown, inner) : 0;
		contentH += extra + 16;
		maxScroll = Math.max(0, contentH - (listBottom - listTop));
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		shownScroll += (scroll - shownScroll) * 0.35;
		if (Math.abs(scroll - shownScroll) < 0.3) shownScroll = scroll;

		d.enableScissor(Math.round(x), Math.round(listTop), Math.round(x + w), Math.round(listBottom));
		hits.clip(x, listTop, x + w, listBottom);
		float y = listTop + 6 - (float) shownScroll;
		contentX = x + PAD;
		contentY = y;
		if (extra > 0 && extras.first(shown)) {
			extras.draw(d, shown, x + PAD, y + 12, inner, mx, my, hits, now);
			y += extra;
		}
		group = null;
		int index = 0;
		for (Setting setting : shownRows) {
			String g = setting.group();
			if (g != null && !g.equals(group)) {
				group = g;
				if (y + SECTION > listTop - 2 && y < listBottom + 2) drawSection(d, shown, g, x + PAD, y, inner, mx, my);
				y += SECTION;
			}
			if (folded(shown, setting)) {
				index++;
				continue;
			}
			float h = rowHeight(setting, inner);
			if (y + h > listTop - 2 && y < listBottom + 2) drawRow(d, setting, x + PAD, y, inner, h, mx, my, now, index == focusRow);
			y += h;
			index++;
		}
		if (extra > 0 && !extras.first(shown)) {
			extras.draw(d, shown, x + PAD, y + 12, inner, mx, my, hits, now);
		}
		hits.unclip();
		d.disableScissor();
		if (maxScroll > 0) {
			float barH = listBottom - listTop;
			float thumb = Math.max(36, barH * barH / (barH + (float) maxScroll));
			float ty = listTop + (barH - thumb) * (float) (shownScroll / maxScroll);
			Paint.rect(d, x + w - 9, ty, x + w - 6, ty + thumb, 1.5f, 0x40FFFFFF);
		}
		drawFooter(d, shown, x, w, y2, mx, my);
		drawDropdown(d, mx, my);
		Paint.opacity(old);
	}

	private void layoutRows(Module shown) {
		shownRows.clear();
		for (Setting setting : shown.settings()) {
			if (!setting.hidden()) shownRows.add(setting);
		}
		if (focusRow >= shownRows.size()) focusRow = shownRows.size() - 1;
	}

	private boolean folded(Module shown, Setting setting) {
		return ADVANCED.equals(setting.group()) && !unfolded.contains(shown.id + "/" + ADVANCED);
	}

	private void drawHead(Draw d, Module shown, float x, float y, float w, float mx, float my) {
		boolean on = shown.enabled();
		headKnob.set(on);
		headLit.set(on);
		float tile = 46;
		Widgets.tile(d, x + PAD, y + PAD, tile, shown.icon(), shown.toggleable() ? headLit.get() : 1, 0);
		float tx = x + PAD + tile + 14;
		float right = x + w - PAD;
		float size = 30;
		float cx1 = right - size;
		float cy1 = y + PAD;
		boolean closeHover = within(mx, my, cx1, cy1, right, cy1 + size);
		Widgets.iconButton(d, cx1, cy1, right, cy1 + size, "x", closeHover ? 1 : 0, false);
		hits.add(cx1, cy1, right, cy1 + size, this::close);
		float textRight = cx1 - 12;
		if (shown.toggleable()) {
			float sx = textRight - 42;
			Widgets.toggle(d, sx, cy1 + 4, 42, 22, headKnob.get(), headLit.get());
			hits.add(sx - 4, cy1, sx + 46, cy1 + 30, shown::toggle);
			textRight = sx - 12;
		}
		Type.draw(d, Type.fit(shown.name, textRight - tx, 15, Weight.BOLD), tx, y + PAD + 5, 15, Weight.BOLD, Tokens.TEXT);
		String state = !shown.toggleable() ? "Always on" : on ? "On" : "Off";
		int stateColor = !shown.toggleable() ? Tokens.TEXT_3 : on ? Accent.text() : Tokens.TEXT_3;
		Type.draw(d, state, tx, y + PAD + 28, 9.5f, Weight.SEMIBOLD, stateColor);
		List<String> lines = wrap(shown.description, w - 2 * PAD, 10, 2);
		for (int i = 0; i < lines.size(); i++) {
			Type.draw(d, lines.get(i), x + PAD, y + PAD + tile + 12 + i * 14, 10, Weight.MEDIUM, Tokens.TEXT_2);
		}
		Paint.rect(d, x + PAD, y + HEAD - 1 + (lines.size() > 1 ? 10 : 0), x + w - PAD, y + HEAD + (lines.size() > 1 ? 10 : 0), 0, 0x14FFFFFF);
	}

	private void drawSection(Draw d, Module shown, String name, float x, float y, float w, float mx, float my) {
		boolean advanced = ADVANCED.equals(name);
		float cap = 9.5f;
		float ty = y + 16;
		Type.draw(d, name, x, ty, cap, Weight.SEMIBOLD, Tokens.TEXT_3);
		float lx = x + Type.width(name, cap, Weight.SEMIBOLD) + 10;
		if (advanced) {
			boolean open = unfolded.contains(shown.id + "/" + ADVANCED);
			boolean hover = within(mx, my, x - 4, y + 6, x + w + 4, y + SECTION - 2);
			Paint.icon(d, open ? "chevron-up" : "chevron-down", x + w - 14, ty - 2.5f, 14, hover ? Tokens.TEXT : Tokens.TEXT_3);
			Paint.rect(d, lx, ty + cap / 2f, x + w - 22, ty + cap / 2f + 1, 0, 0x12FFFFFF);
			String key = shown.id + "/" + ADVANCED;
			hits.add(x - 4, y + 6, x + w + 4, y + SECTION - 2, () -> {
				if (!unfolded.remove(key)) unfolded.add(key);
			});
		} else {
			Paint.rect(d, lx, ty + cap / 2f, x + w, ty + cap / 2f + 1, 0, 0x12FFFFFF);
		}
	}

	private void drawFooter(Draw d, Module shown, float x, float w, float y2, float mx, float my) {
		float y = y2 - 46;
		Paint.rect(d, x + PAD, y - 10, x + w - PAD, y - 9, 0, 0x12FFFFFF);
		boolean any = false;
		for (Setting setting : shown.settings()) {
			if (!setting.hidden()) any = true;
		}
		String hint = "Right-click a setting to reset it";
		Type.draw(d, any ? hint : "Nothing to set here", x + PAD, y + 12, 9, Weight.MEDIUM, Tokens.TEXT_3);
		if (!any) return;
		String label = "Reset all";
		float cap = 9.5f;
		float bw = 18 + 12 + 6 + Type.width(label, cap, Weight.SEMIBOLD);
		float bx = x + w - PAD - bw;
		boolean hover = within(mx, my, bx, y + 2, bx + bw, y + 30);
		Widgets.control(d, bx, y + 2, bx + bw, y + 30, 9, hover ? 1 : 0);
		Paint.icon(d, "rotate-ccw", bx + 10, y + 10, 12, hover ? Tokens.TEXT : Tokens.TEXT_2);
		Type.draw(d, label, bx + 28, y + 2 + (28 - cap) / 2f, cap, Weight.SEMIBOLD, Tokens.TEXT);
		hits.add(bx, y + 2, bx + bw, y + 30, () -> {
			for (Setting setting : shown.settings()) {
				if (!setting.hidden()) setting.reset();
			}
		});
	}

	private static boolean segmentedFits(Setting.Choice choice, float width) {
		List<String> options = choice.options();
		if (options.size() > 4) return false;
		float segment = (width - 8) / options.size();
		for (String option : options) {
			if (Type.width(option, 10f, Weight.SEMIBOLD) > segment - 16) return false;
		}
		return true;
	}

	private float controlSpace(Setting setting, float w) {
		if (setting instanceof Setting.Toggle) return 62;
		if (setting instanceof Setting.Text) return 192;
		if (setting instanceof Setting.Choice c && !segmentedFits(c, w)) return 186;
		if (setting instanceof Setting.Slider) return 76;
		if (setting instanceof Setting.Color) return 112;
		return 0;
	}

	private int descLines(Setting setting, float w) {
		return wrap(setting.description, w - controlSpace(setting, w), 9.5f, 2).size();
	}

	private float rowHeight(Setting setting, float width) {
		float extra = Math.max(0, descLines(setting, width) - 1) * DESC_LINE;
		if (setting instanceof Setting.Slider) return 78 + extra;
		if (setting instanceof Setting.Color) return 60 + extra + (picking == setting ? PICKER : 0);
		if (setting instanceof Setting.Choice choice && segmentedFits(choice, width)) return 98 + extra;
		if (setting instanceof Setting.Text text && text.problem() != null) return 76 + extra;
		return 60 + extra;
	}

	private void drawRow(Draw d, Setting setting, float x, float y, float w, float h, float mx, float my, long now, boolean focused) {
		boolean rowHover = within(mx, my, x - 10, y + 2, x + w + 10, y + h - 2);
		Anim hover = hovers.computeIfAbsent(setting, s -> new Anim(0, Tokens.T_HOVER, Ease.OUT));
		hover.set(rowHover || focused);
		float hv = hover.get();
		if (hv > 0.01f) Paint.rect(d, x - 10, y + 3, x + w + 10, y + h - 3, 10, Paint.fade(0x0AFFFFFF, hv));
		if (focused) Paint.stroke(d, x - 10, y + 3, x + w + 10, y + h - 3, 10, 1, Paint.fade(Accent.get(), 0.7f));
		float labelTop = y + 15;
		float descTop = y + 33;
		float space = controlSpace(setting, w);
		Type.draw(d, Type.fit(setting.name, w - space, 11.5f, Weight.SEMIBOLD), x, labelTop, 11.5f, Weight.SEMIBOLD, Tokens.TEXT);
		List<String> desc = wrap(setting.description, w - space, 9.5f, 2);
		for (int i = 0; i < desc.size(); i++) Type.draw(d, desc.get(i), x, descTop + i * DESC_LINE, 9.5f, Weight.MEDIUM, Tokens.TEXT_3);
		float below = descTop + desc.size() * DESC_LINE;
		float right = x + w;
		hits.add(x - 10, y + 3, x + w + 10, y + h - 3, null, setting::reset);
		if (setting instanceof Setting.Toggle toggle) {
			Anim[] anims = toggles.computeIfAbsent(setting, s -> new Anim[] {
					new Anim(toggle.get() ? 1 : 0, Tokens.T_SWITCH_KNOB, Ease.OUT), new Anim(toggle.get() ? 1 : 0, Tokens.T_SWITCH_FILL, Ease.OUT)});
			anims[0].set(toggle.get());
			anims[1].set(toggle.get());
			Widgets.toggle(d, right - 40, y + 16, 40, 22, anims[0].get(), anims[1].get());
			hits.add(x - 10, y + 3, right + 10, y + h - 3, toggle::toggle, setting::reset);
		} else if (setting instanceof Setting.Slider slider) {
			String value = slider.format();
			float cap = 10f;
			float pw = Type.width(value, cap, Weight.SEMIBOLD) + 16;
			Paint.rect(d, right - pw, labelTop - 5, right, labelTop + cap + 5, 7, 0x12FFFFFF);
			Type.right(d, value, right - 8, labelTop, cap, Weight.SEMIBOLD, Accent.text());
			float ty = below + 14;
			boolean over = within(mx, my, x - 6, ty - 12, right + 6, ty + 12) || dragging == slider;
			Widgets.slider(d, x + 7, ty, right - 7, (float) slider.fraction(), over ? 1 : 0);
			hits.add(x - 6, ty - 12, right + 6, ty + 12, () -> {
				dragging = slider;
				dragX = x + 7;
				dragW = w - 14;
				slider.setFraction((lastX - dragX) / dragW);
			}, setting::reset);
		} else if (setting instanceof Setting.Choice choice) {
			List<String> options = choice.options();
			if (segmentedFits(choice, w)) {
				Anim at = segments.computeIfAbsent(setting, s -> new Anim(choice.index(), Tokens.T_INDICATOR, Ease.OUT));
				at.set(choice.index());
				float sy = below + 6;
				float segment = (w - 8) / options.size();
				int over = -1;
				for (int i = 0; i < options.size(); i++) {
					float sx = x + 4 + i * segment;
					if (within(mx, my, sx, sy, sx + segment, sy + 34)) over = i;
					String option = options.get(i);
					hits.add(sx, sy, sx + segment, sy + 34, () -> choice.set(option), setting::reset);
				}
				Widgets.segmented(d, x, sy, right, sy + 34, null, options.toArray(new String[0]), at.get(), over);
			} else {
				float bw = 176;
				float bx = right - bw;
				float by = y + 13;
				boolean over = within(mx, my, bx, by, right, by + 30);
				Widgets.dropdown(d, bx, by, right, by + 30, choice.get(), dropdown == choice ? dropReveal.get() : 0, over ? 1 : 0);
				hits.add(bx, by, right, by + 30, () -> {
					if (dropdown == choice) {
						dropdown = null;
					} else {
						dropdown = choice;
						dropX1 = bx;
						dropX2 = right;
						dropY = by + 34;
						dropReveal.snap(0);
						dropReveal.set(1);
					}
				}, setting::reset);
			}
		} else if (setting instanceof Setting.Text text) {
			float fw = 182;
			float fx = right - fw;
			boolean focusedField = editing == text;
			String problem = text.problem();
			Widgets.fieldState(d, fx, y + 13, right, y + 45, "pencil", text.get(), "Type here", null, focusedField, 0, now, problem != null);
			hits.add(fx, y + 13, right, y + 45, () -> editing = text, setting::reset);
			if (focusedField) {
				String count = text.get().length() + "/" + text.maxLength;
				Type.right(d, count, right, y + 50, 8.5f, Weight.MEDIUM, Tokens.TEXT_3);
			}
			if (problem != null) {
				Paint.icon(d, "circle-alert", x, below + 4, 11, Tokens.LOW);
				Type.draw(d, Type.fit(problem, w - 16, 9.5f, Weight.SEMIBOLD), x + 16, below + 5.5f, 9.5f, Weight.SEMIBOLD, Tokens.LOW);
			}
		} else if (setting instanceof Setting.Color color) {
			drawColor(d, color, x, y, w, below, mx, my, now);
		}
	}

	private void drawColor(Draw d, Setting.Color color, float x, float y, float w, float below, float mx, float my, long now) {
		float right = x + w;
		int value = color.get() | 0xFF000000;
		String hex = String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF);
		boolean expanded = picking == color;
		float bw = 104;
		float bx = right - bw;
		float by = y + 13;
		boolean over = within(mx, my, bx, by, right, by + 30);
		Widgets.dropdown(d, bx, by, right, by + 30, "", expanded ? 1 : 0, over ? 1 : 0);
		Paint.circle(d, bx + 17, by + 15, 8, value);
		Paint.ring(d, bx + 17, by + 15, 8, 1, 0x40FFFFFF);
		Type.draw(d, hex, bx + 31, by + (30 - 9.5f) / 2f, 9.5f, Weight.SEMIBOLD, Tokens.TEXT);
		hits.add(bx, by, right, by + 30, () -> {
			if (picking == color) {
				picking = null;
				hexText = null;
			} else {
				picking = color;
				hsv = Widgets.toHsv(color.get());
				hexText = null;
			}
		}, color::reset);
		if (!expanded) return;
		if ((Widgets.hsv(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF) != (value & 0xFFFFFF)) {
			float[] now2 = Widgets.toHsv(value);
			if (now2[1] > 0.01f && now2[2] > 0.01f) hsv[0] = now2[0];
			if (now2[2] > 0.01f) hsv[1] = now2[1];
			hsv[2] = now2[2];
		}
		float top = below + 10;
		float sqH = 108;
		Widgets.colorSquare(d, x, top, right, top + sqH, hsv[0], hsv[1], hsv[2]);
		pickX = x;
		pickY = top;
		pickW = w;
		pickH = sqH;
		hits.add(x, top, right, top + sqH, () -> {
			pickDrag = 1;
			dragColor(lastX, lastY);
		});
		float hy = top + sqH + 12;
		Widgets.hueBar(d, x, hy, right, hy + 10, hsv[0]);
		hits.add(x, hy - 4, right, hy + 14, () -> {
			pickDrag = 2;
			dragColor(lastX, lastY);
		});
		float sy = hy + 22;
		int[] swatches = Setting.Color.SWATCHES;
		float size = 20;
		float hexW = 104;
		float room = w - hexW - 12;
		int count = Math.min(swatches.length + 1, (int) ((room + 6) / (size + 6)));
		for (int i = 0; i < count; i++) {
			int swatch = i == 0 ? Accent.get() : swatches[i - 1];
			float cx = x + size / 2f + i * (size + 6);
			boolean picked = (swatch | 0xFF000000) == value;
			boolean hoverSwatch = within(mx, my, cx - size / 2f, sy, cx + size / 2f, sy + size);
			Widgets.swatch(d, cx, sy + size / 2f, size / 2f, swatch, picked, hoverSwatch ? 1 : 0);
			int pick = swatch | 0xFF000000;
			hits.add(cx - size / 2f - 2, sy - 2, cx + size / 2f + 2, sy + size + 2, () -> {
				color.set(pick);
				hsv = Widgets.toHsv(pick);
				hexText = null;
			});
		}
		boolean typingHex = hexText != null;
		String shown = typingHex ? hexText : hex.substring(1);
		boolean invalid = typingHex && !hexText.matches("[0-9a-fA-F]{6}");
		Widgets.fieldState(d, right - hexW, sy - 5, right, sy + 25, "hash", shown, "RRGGBB", null, typingHex, 0, now, invalid);
		hits.add(right - hexW, sy - 5, right, sy + 25, () -> hexText = hex.substring(1));
	}

	private void dragColor(float x, float y) {
		if (picking == null) return;
		if (pickDrag == 1) {
			hsv[1] = Math.max(0, Math.min(1, (x - pickX) / pickW));
			hsv[2] = Math.max(0, Math.min(1, 1 - (y - pickY) / pickH));
		} else if (pickDrag == 2) {
			hsv[0] = Math.max(0, Math.min(0.999f, (x - pickX) / pickW));
		}
		picking.set(Widgets.hsv(hsv[0], hsv[1], hsv[2]));
		hexText = null;
	}

	private void drawDropdown(Draw d, float mx, float my) {
		if (dropdown == null) return;
		String[] options = dropdown.options().toArray(new String[0]);
		int hover = -1;
		float y = dropY + DROP_ROW * 0.15f;
		float listH = DROP_ROW * options.length + DROP_ROW * 0.3f;
		float top = dropY;
		if (top + listH > sheetY2 - 8) top = Math.max(sheetY1 + 8, sheetY2 - 8 - listH);
		y = top + DROP_ROW * 0.15f;
		for (int i = 0; i < options.length; i++) {
			if (within(mx, my, dropX1, y, dropX2, y + DROP_ROW)) hover = i;
			y += DROP_ROW;
		}
		Widgets.dropdownList(d, dropX1, top, dropX2, DROP_ROW, options, dropdown.index(), hover, dropReveal.get());
		dropTop = top;
	}

	private float dropTop;

	/** The dropdown option under (x, y), or -1 (-2 when the dropdown isn't open). */
	private int dropdownAt(float x, float y) {
		if (dropdown == null) return -2;
		int n = dropdown.options().size();
		float y1 = dropTop;
		float y2 = y1 + DROP_ROW * n + DROP_ROW * 0.3f;
		if (x < dropX1 || x >= dropX2 || y < y1 || y >= y2) return -1;
		int i = (int) ((y - y1 - DROP_ROW * 0.15f) / DROP_ROW);
		return i >= 0 && i < n ? i : -1;
	}

	private List<String> wrap(String text, float width, float cap, int maxLines) {
		String key = Math.round(width) + "|" + cap + "|" + maxLines + "|" + text;
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
			if (lines.size() == maxLines - 1) {
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

	private static boolean within(float x, float y, float x1, float y1, float x2, float y2) {
		return x >= x1 && x < x2 && y >= y1 && y < y2;
	}

	/** A click in layout units; returns whether it landed on the panel. */
	public boolean mouseClicked(float x, float y, int button) {
		lastX = x;
		lastY = y;
		int option = dropdownAt(x, y);
		if (option != -2) {
			Setting.Choice choice = dropdown;
			dropdown = null;
			if (option >= 0) {
				clickSound.run();
				choice.set(choice.options().get(option));
			}
			return true;
		}
		boolean inside = x >= sheetX1 && x < sheetX2 && y >= sheetY1 && y < sheetY2;
		Runnable action = hits.at(x, y, button);
		editing = null;
		if (picking != null && hexText != null) commitHex();
		if (extras != null) extras.stopTyping();
		if (action == null) return inside;
		clickSound.run();
		action.run();
		return true;
	}

	private void commitHex() {
		if (picking != null && hexText != null && hexText.matches("[0-9a-fA-F]{6}")) {
			int value = 0xFF000000 | Integer.parseInt(hexText, 16);
			picking.set(value);
			hsv = Widgets.toHsv(value);
		}
		hexText = null;
	}

	public boolean mouseDragged(float x, float y) {
		lastX = x;
		lastY = y;
		if (pickDrag != 0) {
			dragColor(x, y);
			return true;
		}
		if (dragging == null) return false;
		dragging.setFraction((x - dragX) / dragW);
		return true;
	}

	public boolean mouseReleased() {
		boolean was = dragging != null || pickDrag != 0;
		dragging = null;
		pickDrag = 0;
		return was;
	}

	public boolean mouseScrolled(float x, float y, double amount) {
		if (dropdown != null) return true;
		scroll = Math.max(0, Math.min(maxScroll, scroll - amount * 60));
		return true;
	}

	public boolean keyPressed(int key) {
		if (editing != null) {
			if (key == Keys.BACKSPACE) {
				String value = editing.get();
				if (!value.isEmpty()) editing.set(value.substring(0, value.length() - 1));
				return true;
			}
			if (Keys.enter(key) || key == Keys.ESCAPE) {
				editing = null;
				return true;
			}
			return true;
		}
		if (picking != null && hexText != null) {
			if (key == Keys.BACKSPACE) {
				if (!hexText.isEmpty()) hexText = hexText.substring(0, hexText.length() - 1);
				return true;
			}
			if (Keys.enter(key)) {
				commitHex();
				return true;
			}
			if (key == Keys.ESCAPE) {
				hexText = null;
				return true;
			}
			return true;
		}
		if (extras != null && extras.typing()) return extras.keyPressed(key);
		if (dropdown != null) {
			if (key == Keys.ESCAPE) {
				dropdown = null;
				return true;
			}
			if (key == Keys.UP || key == Keys.DOWN) {
				if (key == Keys.DOWN) dropdown.next();
				else dropdown.previous();
				return true;
			}
			if (Keys.enter(key) || key == Keys.SPACE) {
				dropdown = null;
				return true;
			}
		}
		return keyNavigate(key);
	}

	/** Up and Down move between settings, Left and Right change one, Enter and Space switch or open it. */
	private boolean keyNavigate(int key) {
		if (module == null || shownRows.isEmpty()) return false;
		List<Integer> visible = new ArrayList<>();
		for (int i = 0; i < shownRows.size(); i++) {
			if (!folded(module, shownRows.get(i))) visible.add(i);
		}
		if (visible.isEmpty()) return false;
		int at = visible.indexOf(focusRow);
		if (key == Keys.DOWN || key == Keys.UP) {
			at = at < 0 ? (key == Keys.DOWN ? 0 : visible.size() - 1) : Math.max(0, Math.min(visible.size() - 1, at + (key == Keys.DOWN ? 1 : -1)));
			focusRow = visible.get(at);
			scrollToFocus();
			return true;
		}
		if (at < 0) return false;
		Setting setting = shownRows.get(focusRow);
		if (key == Keys.LEFT || key == Keys.RIGHT) {
			int step = key == Keys.RIGHT ? 1 : -1;
			if (setting instanceof Setting.Slider slider) {
				slider.set(slider.get() + step * slider.step);
			} else if (setting instanceof Setting.Choice choice) {
				if (step > 0) choice.next();
				else choice.previous();
			} else if (setting instanceof Setting.Toggle toggle) {
				toggle.set(step > 0);
			}
			return true;
		}
		if (Keys.enter(key) || key == Keys.SPACE) {
			if (setting instanceof Setting.Toggle toggle) {
				toggle.toggle();
			} else if (setting instanceof Setting.Choice choice) {
				choice.next();
			} else if (setting instanceof Setting.Text text) {
				editing = text;
			} else if (setting instanceof Setting.Color color) {
				picking = picking == color ? null : color;
				if (picking != null) hsv = Widgets.toHsv(color.get());
			}
			clickSound.run();
			return true;
		}
		return false;
	}

	private void scrollToFocus() {
		if (module == null || focusRow < 0) return;
		float inner = WIDTH - 2 * PAD;
		float y = 0;
		String group = null;
		float extra = extras != null && extras.has(module) && extras.first(module) ? extras.height(module, inner) : 0;
		y += extra;
		for (int i = 0; i < shownRows.size(); i++) {
			Setting setting = shownRows.get(i);
			if (setting.group() != null && !setting.group().equals(group)) {
				group = setting.group();
				y += SECTION;
			}
			if (folded(module, setting)) continue;
			float h = rowHeight(setting, inner);
			if (i == focusRow) {
				float view = sheetY2 - 58 - (sheetY1 + HEAD) - 12;
				if (y < scroll) scroll = y;
				else if (y + h > scroll + view) scroll = y + h - view;
				return;
			}
			y += h;
		}
	}

	public boolean charTyped(String typed) {
		if (editing != null) {
			if (editing.get().length() < editing.maxLength) editing.set(editing.get() + typed);
			return true;
		}
		if (picking != null && hexText != null) {
			if (hexText.length() < 6 && typed.matches("[0-9a-fA-F]")) hexText += typed.toUpperCase(Locale.ROOT);
			return true;
		}
		return extras != null && extras.typing() && extras.charTyped(typed);
	}

	/** Whether the mouse is over the panel (for the menu not to light cards under it). */
	public boolean over(float x, float y) {
		return isOpen() && x >= sheetX1 && x < sheetX2 && y >= sheetY1 && y < sheetY2;
	}

	/** The mouse position the panel last drew with, in layout units. */
	float[] mouse() {
		return new float[] {mouseX, mouseY};
	}
}
