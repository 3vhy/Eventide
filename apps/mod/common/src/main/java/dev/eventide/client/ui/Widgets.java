package dev.eventide.client.ui;

import dev.eventide.client.Draw;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.ui.Type.Weight;

/**
 * Eventide's controls: switches, icon tiles, round icon buttons, the accent pill, search and text fields,
 * key caps, segmented pickers, dropdowns, sliders, colour swatches and the colour picker, panels and
 * cards. Surfaces are translucent midnight a tone step apart with 1px edges; the accent goes only on
 * what's on or chosen, and only lit things carry a soft glow. Sizes are in layout units; callers scale
 * the whole screen. Animation state (hover, on/off) comes in as 0..1 values from {@link Anim}s the
 * caller keeps, so nothing here allocates state.
 */
public final class Widgets {
	private static final int CONTROL_FILL = 0x12FFFFFF;
	private static final int CONTROL_FILL_HOVER = 0x21FFFFFF;
	private static final int CONTROL_EDGE = 0x1CFFFFFF;
	private static final int CONTROL_EDGE_HOVER = 0x38FFFFFF;
	private static final int PANEL = 0xF20B0819;
	private static final int PANEL_EDGE = 0x24FFFFFF;
	private static final int CARD = 0x0DFFFFFF;
	private static final int CARD_HOVER = 0x17FFFFFF;
	private static final int CARD_EDGE = 0x14FFFFFF;
	private static final int CARD_EDGE_HOVER = 0x26FFFFFF;
	/** The six hues of the colour picker's hue bar, red round to red. */
	private static final int[] HUES = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};

	private Widgets() {
	}

	/** A switch, {@code w} x {@code h}: {@code knob} is how far the knob has slid and {@code lit} how far the track has turned to the accent. */
	public static void toggle(Draw d, float x, float y, float w, float h, float knob, float lit) {
		float r = h / 2f;
		int accent = Accent.get();
		if (lit > 0.01f) Paint.shadow(d, x, y, x + w, y + h, r, 0, 0, h * 0.9f, -h * 0.1f, Paint.fade(accent, 0.38f * lit));
		Paint.rect(d, x, y, x + w, y + h, r, mixColor(Tokens.SWITCH_OFF, accent, lit));
		Paint.stroke(d, x, y, x + w, y + h, r, 1, mixColor(0x24FFFFFF, 0x40FFFFFF, lit));
		float inset = h * 0.14f;
		float size = h - 2 * inset;
		float k = Math.max(0, Math.min(1, knob));
		float kx = x + inset + k * (w - h);
		Paint.shadow(d, kx, y + inset, kx + size, y + inset + size, size / 2f, 0, size * 0.08f, size * 0.35f, 0, 0x59000000);
		Paint.circle(d, kx + size / 2f, y + h / 2f, size / 2f, mixColor(0xFFD9D3EE, 0xFFFFFFFF, lit));
	}

	/** A module's icon in a {@code size} box with no tile: grey when it's off, the accent's light when it's on. */
	public static void iconTile(Draw d, float x, float y, float size, String icon, float on, float hover) {
		int color = mixColor(mixColor(Tokens.TEXT_3, Tokens.TEXT_2, hover), Accent.text(), on);
		Paint.icon(d, icon, x, y, size, color);
	}

	/**
	 * A module's icon on a rounded tile {@code size} square: a quiet tone step when it's off; when it's on, an
	 * accent-tinted tile with an accent edge, a lit icon and a soft glow under it.
	 */
	public static void tile(Draw d, float x, float y, float size, String icon, float on, float hover) {
		float r = size * 0.3f;
		int accent = Accent.get();
		if (on > 0.01f) Paint.shadow(d, x, y, x + size, y + size, r, 0, size * 0.1f, size * 0.7f, -size * 0.12f, Paint.fade(accent, 0.45f * on));
		Paint.rect(d, x, y, x + size, y + size, r, mixColor(mixColor(0x0FFFFFFF, 0x18FFFFFF, hover), Paint.fade(accent, 0.24f), on));
		Paint.stroke(d, x, y, x + size, y + size, r, 1, mixColor(0x14FFFFFF, Paint.fade(Accent.lighten(accent, 0.2f), 0.55f), on));
		float s = size * 0.5f;
		int color = mixColor(mixColor(Tokens.TEXT_2, Tokens.TEXT, hover), 0xFFFFFFFF, on);
		Paint.icon(d, icon, x + (size - s) / 2f, y + (size - s) / 2f, s, color);
	}

	/** A round icon button (a close button, a card's settings button): one tone step, lighter on hover. */
	public static void roundButton(Draw d, float cx, float cy, float radius, String icon, float hover, float iconScale) {
		Paint.circle(d, cx, cy, radius, mixColor(CONTROL_FILL, CONTROL_FILL_HOVER, hover));
		Paint.ring(d, cx, cy, radius, 1, mixColor(0x12FFFFFF, CONTROL_EDGE_HOVER, hover));
		float size = radius * iconScale;
		Paint.icon(d, icon, cx - size / 2f, cy - size / 2f, size, mixColor(Tokens.TEXT_2, Tokens.TEXT, hover));
	}

	/** A square icon button with round corners, like the window's toolbar buttons. */
	public static void iconButton(Draw d, float x1, float y1, float x2, float y2, String icon, float hover, boolean lit) {
		float r = (y2 - y1) * 0.3f;
		int accent = Accent.get();
		Paint.rect(d, x1, y1, x2, y2, r, lit ? Paint.fade(accent, 0.2f + 0.08f * hover) : mixColor(CONTROL_FILL, CONTROL_FILL_HOVER, hover));
		Paint.stroke(d, x1, y1, x2, y2, r, 1, lit ? Paint.fade(accent, 0.55f) : mixColor(CONTROL_EDGE, CONTROL_EDGE_HOVER, hover));
		float size = (y2 - y1) * 0.46f;
		Paint.icon(d, icon, (x1 + x2 - size) / 2f, (y1 + y2 - size) / 2f, size, lit ? 0xFFFFFFFF : mixColor(Tokens.TEXT_2, Tokens.TEXT, hover));
	}

	/** The accent pill: a primary button or a chosen option. {@code hover} lightens it a step. */
	public static void accentPill(Draw d, float x1, float y1, float x2, float y2, float hover) {
		float r = (y2 - y1) / 2f;
		int c = Accent.get();
		Paint.shadow(d, x1, y1, x2, y2, r, 0, (y2 - y1) * 0.12f, (y2 - y1) * 0.8f, -(y2 - y1) * 0.15f, Paint.fade(c, 0.35f + 0.15f * hover));
		Paint.rect(d, x1, y1, x2, y2, r, mixColor(c, Accent.lighten(c, 0.12f), hover));
		Paint.stroke(d, x1, y1, x2, y2, r, 1, 0x33FFFFFF);
	}

	/** A control (a button, a picker, a slot): a tone step with a 1px edge, both a step lighter on hover. */
	public static void control(Draw d, float x1, float y1, float x2, float y2, float r, float hover) {
		Paint.rect(d, x1, y1, x2, y2, r, mixColor(CONTROL_FILL, CONTROL_FILL_HOVER, hover));
		Paint.stroke(d, x1, y1, x2, y2, r, 1, mixColor(CONTROL_EDGE, CONTROL_EDGE_HOVER, hover));
	}

	/** A key cap: the label on a tone step with a 1px edge and a darker lip along the bottom. */
	public static float kbd(Draw d, float x, float y, String label, float cap) {
		float padX = cap * 1.0f;
		float h = cap * 2.4f;
		float w = Math.max(h, Type.width(label, cap, Weight.SEMIBOLD) + 2 * padX);
		float r = cap * 0.55f;
		Paint.rect(d, x, y, x + w, y + h, r, 0x1AFFFFFF);
		Paint.rect(d, x, y, x + w, y + h - cap * 0.18f, r, CONTROL_FILL);
		Paint.stroke(d, x, y, x + w, y + h, r, 1, CONTROL_EDGE);
		Type.center(d, label, x + w / 2f, y + (h - cap) / 2f - cap * 0.08f, cap, Weight.SEMIBOLD, Tokens.TEXT);
		return w;
	}

	public static float kbdWidth(String label, float cap) {
		return Math.max(cap * 2.4f, Type.width(label, cap, Weight.SEMIBOLD) + 2 * cap);
	}

	/** A small square key badge on a module card (C for Zoom), {@code size} tall. */
	public static float keyBadge(Draw d, float rightX, float y, float size, String label) {
		float cap = size * 0.34f;
		float w = Math.max(size, Type.width(label, cap, Weight.SEMIBOLD) + size * 0.6f);
		float x = rightX - w;
		float r = size * 0.27f;
		Paint.rect(d, x, y, x + w, y + size, r, Tokens.CONTROL);
		Paint.stroke(d, x, y, x + w, y + size, r, 1, Tokens.CONTROL_EDGE);
		Type.center(d, label, x + w / 2f, y + (size - cap) / 2f, cap, Weight.SEMIBOLD, Tokens.TEXT_2);
		return w;
	}

	/** The search field: a magnifier, the text (or placeholder) and a key hint at the end. */
	public static void search(Draw d, float x1, float y1, float x2, float y2, String text, String placeholder, String keyHint,
			boolean focused, float hover, long nowMs) {
		field(d, x1, y1, x2, y2, "search", text, placeholder, keyHint, focused, hover, nowMs);
	}

	/** A text field like the search field, with any icon at the start; focused, its edge takes the accent and glows softly. */
	public static void field(Draw d, float x1, float y1, float x2, float y2, String icon, String text, String placeholder, String keyHint,
			boolean focused, float hover, long nowMs) {
		fieldState(d, x1, y1, x2, y2, icon, text, placeholder, keyHint, focused, hover, nowMs, false);
	}

	/** A text field that can show it holds something that won't work ({@code invalid}): a pink edge instead of the accent. */
	public static void fieldState(Draw d, float x1, float y1, float x2, float y2, String icon, String text, String placeholder, String keyHint,
			boolean focused, float hover, long nowMs, boolean invalid) {
		float h = y2 - y1;
		float r = h * 0.32f;
		int accent = invalid ? Tokens.LOW : Accent.get();
		if (focused) Paint.shadow(d, x1, y1, x2, y2, r, 0, 0, h * 0.5f, 0, Paint.fade(accent, 0.3f));
		Paint.rect(d, x1, y1, x2, y2, r, focused ? 0x1AFFFFFF : mixColor(CONTROL_FILL, CONTROL_FILL_HOVER, hover));
		Paint.stroke(d, x1, y1, x2, y2, r, 1, focused || invalid ? Paint.fade(accent, focused ? 0.9f : 0.6f) : mixColor(CONTROL_EDGE, CONTROL_EDGE_HOVER, hover));
		float iconSize = h * 0.4f;
		float ix = x1 + h * 0.36f;
		Paint.icon(d, icon, ix, y1 + (h - iconSize) / 2f, iconSize, focused ? Accent.text() : Tokens.TEXT_2);
		float cap = h * 0.25f;
		float tx = ix + iconSize + h * 0.24f;
		float badge = h * 0.5f;
		float badgeX = x2 - h * 0.3f;
		boolean hint = keyHint != null && (text.isEmpty() || !focused);
		float maxW = (hint ? badgeX - badge : x2 - h * 0.3f) - tx - h * 0.12f;
		boolean empty = text.isEmpty();
		String shown = empty ? placeholder : text;
		while (!empty && shown.length() > 1 && Type.width(shown, cap, Weight.SEMIBOLD) > maxW) shown = shown.substring(1);
		Type.draw(d, Type.fit(shown, maxW, cap, Weight.SEMIBOLD), tx, y1 + (h - cap) / 2f, cap, Weight.SEMIBOLD, empty ? Tokens.TEXT_3 : Tokens.TEXT);
		if (focused && (nowMs / 530) % 2 == 0) {
			float cx = tx + (empty ? 0 : Type.width(shown, cap, Weight.SEMIBOLD) + cap * 0.15f);
			Paint.rect(d, cx, y1 + h * 0.28f, cx + Math.max(0.8f, cap * 0.11f), y2 - h * 0.28f, 0, Accent.text());
		}
		if (hint) keyBadge(d, badgeX, y1 + (h - badge) / 2f, badge, keyHint);
	}

	/**
	 * A segmented picker: options as icons (or labels), the chosen one on a sliding tinted pill. {@code at}
	 * is the pill's animated position (an option index, fractional mid-slide) and {@code hover} the option
	 * under the mouse or -1.
	 */
	public static void segmented(Draw d, float x1, float y1, float x2, float y2, String[] icons, String[] labels, float at, int hover) {
		int n = icons != null ? icons.length : labels.length;
		float h = y2 - y1;
		float r = h * 0.3f;
		Paint.rect(d, x1, y1, x2, y2, r, 0x0DFFFFFF);
		Paint.stroke(d, x1, y1, x2, y2, r, 1, 0x14FFFFFF);
		float pad = h * 0.1f;
		float segment = (x2 - x1 - 2 * pad) / n;
		float px = x1 + pad + at * segment;
		int accent = Accent.get();
		float pr = r - pad * 0.6f;
		Paint.shadow(d, px, y1 + pad, px + segment, y2 - pad, pr, 0, 0, h * 0.5f, -h * 0.1f, Paint.fade(accent, 0.25f));
		Paint.rect(d, px, y1 + pad, px + segment, y2 - pad, pr, Paint.fade(accent, 0.3f));
		Paint.stroke(d, px, y1 + pad, px + segment, y2 - pad, pr, 1, Paint.fade(Accent.lighten(accent, 0.25f), 0.7f));
		int nearest = Math.round(at);
		for (int i = 0; i < n; i++) {
			float cx = x1 + pad + segment * (i + 0.5f);
			boolean active = i == nearest;
			int color = active ? 0xFFFFFFFF : i == hover ? Tokens.TEXT : Tokens.TEXT_2;
			if (icons != null) {
				float size = h * 0.42f;
				Paint.icon(d, icons[i], cx - size / 2f, y1 + (h - size) / 2f, size, color);
			} else {
				float cap = h * 0.27f;
				Type.center(d, Type.fit(labels[i], segment - h * 0.3f, cap, Weight.SEMIBOLD), cx, y1 + (h - cap) / 2f, cap, Weight.SEMIBOLD, color);
			}
		}
	}

	/** A dropdown's button: the chosen option and a chevron that turns as the list opens ({@code open} 0..1). */
	public static void dropdown(Draw d, float x1, float y1, float x2, float y2, String value, float open, float hover) {
		float h = y2 - y1;
		float r = h * 0.3f;
		int accent = Accent.get();
		Paint.rect(d, x1, y1, x2, y2, r, mixColor(mixColor(CONTROL_FILL, CONTROL_FILL_HOVER, hover), 0x1AFFFFFF, open));
		Paint.stroke(d, x1, y1, x2, y2, r, 1, mixColor(mixColor(CONTROL_EDGE, CONTROL_EDGE_HOVER, hover), Paint.fade(accent, 0.8f), open));
		float cap = h * 0.28f;
		float icon = h * 0.42f;
		Type.draw(d, Type.fit(value, x2 - x1 - h * 0.6f - icon, cap, Weight.SEMIBOLD), x1 + h * 0.36f, y1 + (h - cap) / 2f, cap, Weight.SEMIBOLD, Tokens.TEXT);
		Paint.icon(d, open > 0.5f ? "chevron-up" : "chevron-down", x2 - h * 0.3f - icon, y1 + (h - icon) / 2f, icon,
				mixColor(Tokens.TEXT_2, Accent.text(), open));
	}

	/** A dropdown's list: a floating panel holding the options, the chosen one marked, one under the mouse lit. */
	public static void dropdownList(Draw d, float x1, float y1, float x2, float rowH, String[] options, int chosen, int hover, float reveal) {
		float h = rowH * options.length + rowH * 0.3f;
		float y2 = y1 + h * reveal;
		float r = rowH * 0.32f;
		float old = Paint.opacity(Paint.opacity() * Math.min(1, reveal * 1.4f));
		Paint.shadow(d, x1, y1, x2, y2, r, 0, rowH * 0.3f, rowH * 1.2f, -rowH * 0.2f, 0x99000000);
		Paint.rect(d, x1, y1, x2, y2, r, 0xFA110C22);
		Paint.stroke(d, x1, y1, x2, y2, r, 1, 0x29FFFFFF);
		float cap = rowH * 0.3f;
		float y = y1 + rowH * 0.15f;
		int accent = Accent.get();
		for (int i = 0; i < options.length; i++) {
			if (y + rowH > y2 + 0.5f) break;
			boolean picked = i == chosen;
			if (i == hover || picked) {
				Paint.rect(d, x1 + rowH * 0.15f, y, x2 - rowH * 0.15f, y + rowH, r * 0.7f, picked ? Paint.fade(accent, 0.22f) : 0x12FFFFFF);
			}
			Type.draw(d, Type.fit(options[i], x2 - x1 - rowH * 1.4f, cap, Weight.SEMIBOLD), x1 + rowH * 0.45f, y + (rowH - cap) / 2f, cap, Weight.SEMIBOLD,
					picked ? 0xFFFFFFFF : i == hover ? Tokens.TEXT : Tokens.TEXT_2);
			if (picked) {
				float s = rowH * 0.42f;
				Paint.icon(d, "check", x2 - rowH * 0.4f - s, y + (rowH - s) / 2f, s, Accent.text());
			}
			y += rowH;
		}
		Paint.opacity(old);
	}

	/**
	 * A slider: a thin track filled from the accent into the second light up to {@code fraction}, and a white
	 * knob with an accent ring that grows a little under the mouse ({@code hover} 0..1).
	 */
	public static void slider(Draw d, float x1, float cy, float x2, float fraction, float hover) {
		float t = 2.2f;
		Paint.rect(d, x1, cy - t, x2, cy + t, t, 0x1FFFFFFF);
		float fx = x1 + (x2 - x1) * Math.max(0, Math.min(1, fraction));
		int accent = Accent.get();
		if (fx > x1 + 0.5f) {
			Paint.shadow(d, x1, cy - t, fx, cy + t, t, 0, 0, 8, 0, Paint.fade(accent, 0.35f));
			Paint.rect(d, x1, cy - t, fx, cy + t, t, Grad.across(accent, Draw.mix(accent, Accent.secondary(), Math.max(0, Math.min(1, fraction)))));
		}
		float knob = 7f + hover * 1.2f;
		Paint.shadow(d, fx - knob, cy - knob, fx + knob, cy + knob, knob, 0, 1.5f, 6, 0, 0x66000000);
		Paint.circle(d, fx, cy, knob, 0xFFFFFFFF);
		Paint.ring(d, fx, cy, knob * 0.45f, knob * 0.45f, accent);
	}

	/** A round colour swatch, ringed when it's the one picked. */
	public static void swatch(Draw d, float cx, float cy, float r, int color, boolean picked, float hover) {
		if (picked) Paint.ring(d, cx, cy, r + 3.5f, 1.6f, 0xEBFFFFFF);
		Paint.circle(d, cx, cy, r - (picked ? 0.5f : 1 - hover), color | 0xFF000000);
		Paint.ring(d, cx, cy, r, 1, 0x33FFFFFF);
	}

	/**
	 * The colour picker's saturation and brightness square for {@code hue} (0..1), with a ring at the
	 * picked spot ({@code sat}, {@code val}).
	 */
	public static void colorSquare(Draw d, float x1, float y1, float x2, float y2, float hue, float sat, float val) {
		int pure = hsv(hue, 1, 1);
		float r = 6;
		Paint.shadow(d, x1, y1, x2, y2, r, 0, 2, 10, 0, 0x55000000);
		Paint.corners(d, x1, y1, x2, y2, 0xFFFFFFFF, pure, 0xFF000000, 0xFF000000);
		Paint.stroke(d, x1, y1, x2, y2, 0, 1, 0x26FFFFFF);
		float px = x1 + (x2 - x1) * sat;
		float py = y1 + (y2 - y1) * (1 - val);
		Paint.ring(d, px, py, 6, 2, 0xFFFFFFFF);
		Paint.ring(d, px, py, 7, 1, 0x80000000);
	}

	/** The colour picker's hue bar, with a handle at {@code hue} (0..1). */
	public static void hueBar(Draw d, float x1, float y1, float x2, float y2, float hue) {
		Paint.stops(d, x1, y1, x2, y2, HUES);
		Paint.stroke(d, x1, y1, x2, y2, 0, 1, 0x26FFFFFF);
		float hx = x1 + (x2 - x1) * hue;
		Paint.rect(d, hx - 3, y1 - 2, hx + 3, y2 + 2, 3, 0xFFFFFFFF);
		Paint.stroke(d, hx - 3, y1 - 2, hx + 3, y2 + 2, 3, 1, 0x80000000);
	}

	/** A floating panel (a settings panel, a toolbar): near-opaque midnight, a 1px edge, and the one shadow it earns by floating. */
	public static void glass(Draw d, float x1, float y1, float x2, float y2, float r) {
		Paint.shadow(d, x1, y1, x2, y2, r, 0, 14, 40, -10, 0x99000000);
		Paint.rect(d, x1, y1, x2, y2, r, PANEL);
		Paint.stroke(d, x1, y1, x2, y2, r, 1, PANEL_EDGE);
	}

	/** A module card's body: one tone step off the window with a 1px edge; {@code hover} lightens it a step. */
	public static void card(Draw d, float x1, float y1, float x2, float y2, float r, float hover) {
		card(d, x1, y1, x2, y2, r, hover, 0, 0);
	}

	/**
	 * A module card that can also be on ({@code on}, a faint accent wash) and chosen ({@code chosen}, an
	 * accent edge: the card whose settings are open or the one the keyboard is on).
	 */
	public static void card(Draw d, float x1, float y1, float x2, float y2, float r, float hover, float on, float chosen) {
		int accent = Accent.get();
		if (chosen > 0.01f) Paint.shadow(d, x1, y1, x2, y2, r, 0, 0, 18, -4, Paint.fade(accent, 0.28f * chosen));
		Paint.rect(d, x1, y1, x2, y2, r, mixColor(mixColor(CARD, CARD_HOVER, hover), Paint.fade(accent, 0.09f), on * 0.85f));
		int edge = mixColor(CARD_EDGE, CARD_EDGE_HOVER, hover);
		Paint.stroke(d, x1, y1, x2, y2, r, 1, mixColor(edge, Paint.fade(Accent.lighten(accent, 0.15f), 0.75f), chosen));
	}

	/** A small status dot (a connection that's up). */
	public static void pulseDot(Draw d, float cx, float cy, float r, int color, long nowMs) {
		float pulse = 0.5f + 0.5f * (float) Math.sin(nowMs / 1000.0 * Math.PI);
		Paint.glow(d, cx, cy, r, r * 3, Paint.fade(color, 0.35f * pulse));
		Paint.circle(d, cx, cy, r, color);
	}

	/** Blends two colours, alpha included. */
	public static int mixColor(int a, int b, float t) {
		return Draw.mix(a, b, Math.max(0, Math.min(1, t)));
	}

	/** An opaque colour from hue, saturation and value, all 0..1. */
	public static int hsv(float h, float s, float v) {
		float hh = (h - (float) Math.floor(h)) * 6f;
		int i = (int) Math.floor(hh);
		float f = hh - i;
		float p = v * (1 - s);
		float q = v * (1 - s * f);
		float t = v * (1 - s * (1 - f));
		float r;
		float g;
		float b;
		switch (i % 6) {
			case 0 -> {
				r = v;
				g = t;
				b = p;
			}
			case 1 -> {
				r = q;
				g = v;
				b = p;
			}
			case 2 -> {
				r = p;
				g = v;
				b = t;
			}
			case 3 -> {
				r = p;
				g = q;
				b = v;
			}
			case 4 -> {
				r = t;
				g = p;
				b = v;
			}
			default -> {
				r = v;
				g = p;
				b = q;
			}
		}
		return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
	}

	/** A colour's hue, saturation and value (0..1 each). */
	public static float[] toHsv(int argb) {
		float r = ((argb >> 16) & 255) / 255f;
		float g = ((argb >> 8) & 255) / 255f;
		float b = (argb & 255) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float h = 0;
		if (delta > 1e-6f) {
			if (max == r) h = ((g - b) / delta) % 6f;
			else if (max == g) h = (b - r) / delta + 2;
			else h = (r - g) / delta + 4;
			h /= 6f;
			if (h < 0) h += 1;
		}
		return new float[] {h, max <= 0 ? 0 : delta / max, max};
	}
}
