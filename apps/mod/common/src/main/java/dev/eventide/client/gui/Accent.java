package dev.eventide.client.gui;

import dev.eventide.client.module.Modules;
import dev.eventide.client.ui.Grad;
import dev.eventide.client.ui.Tokens;
import java.util.List;

/**
 * The client's accent colour, picked in Theme (Accent colour) with Rainbow as an option. Lit switches,
 * the chosen tab, icon tiles, bars and glows use it. Violet, the default, is Eventide's electric violet;
 * {@link #secondary()} is the cyan second light that bars and edges run into, and {@link #highlight()} the
 * pink kept for the rare highlight.
 */
public final class Accent {
	/** A named colour in the accent picker. */
	public record Preset(String name, int color) {
	}

	public static final int DEFAULT = Tokens.PRIMARY_MID;

	public static final List<Preset> PRESETS = List.of(
			new Preset("Violet", DEFAULT),
			new Preset("Cyan", Tokens.CYAN),
			new Preset("Pink", Tokens.PINK),
			new Preset("Blue", 0xFF5A8CFF),
			new Preset("Emerald", 0xFF3DDC97),
			new Preset("Red", 0xFFFF5A6E));

	private Accent() {
	}

	/** The colour right now (opaque). */
	public static int get() {
		return get(0f);
	}

	/** The colour right now, a little further round the rainbow when Rainbow is on, so a list of lines ripples. */
	public static int get(float offset) {
		if (Modules.THEME_RAINBOW.get()) return rainbow((System.currentTimeMillis() % 6000L) / 6000f + offset);
		return Modules.THEME_PANEL_COLOR.get() | 0xFF000000;
	}

	private static boolean isDefault(int color) {
		return (color | 0xFF000000) == DEFAULT;
	}

	/** The gradient's violet end. */
	public static int from() {
		int c = get();
		return isDefault(c) ? Tokens.PRIMARY_FROM : turn(c, -17);
	}

	/** The gradient's magenta end. */
	public static int to() {
		int c = get();
		return isDefault(c) ? Tokens.PRIMARY_TO : turn(c, 9);
	}

	/** Buttons and the active tab: the accent, flat. */
	public static Grad primary() {
		int c = get();
		return new Grad(c, c, 0);
	}

	/** An icon or swatch that's on: the accent, flat. */
	public static Grad tile() {
		return primary();
	}

	/** A switch that's on: the accent, flat. */
	public static Grad toggle() {
		return primary();
	}

	/** Progress bars and sliders: the accent, flat. */
	public static Grad bar() {
		return primary();
	}

	/** The second light: cyan, or the accent turned a little towards blue for other themes. */
	public static int secondary() {
		int c = get();
		return isDefault(c) ? Tokens.CYAN : turn(c, -40);
	}

	/** Pink, for the rare highlight (the sung word, a new badge). */
	public static int highlight() {
		return Tokens.PINK;
	}

	/** The accent running into the second light, for bars and progress. */
	public static Grad flow() {
		return Grad.across(get(), secondary());
	}

	/** The soft glow under lit things. */
	public static int glow(float alpha) {
		int c = get();
		int base = isDefault(c) ? Tokens.PURPLE : c;
		return Math.round(255 * Math.max(0, Math.min(1, alpha))) << 24 | (base & 0xFFFFFF);
	}

	/** A pale, readable tint of the accent for small text, like the website's lilac. */
	public static int text() {
		int c = get();
		return isDefault(c) ? Tokens.LILAC : lighten(c, 0.55f);
	}

	/** {@code color} with its hue turned by {@code degrees}, keeping saturation and lightness. */
	public static int turn(int color, float degrees) {
		float r = ((color >> 16) & 255) / 255f;
		float g = ((color >> 8) & 255) / 255f;
		float b = (color & 255) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float l = (max + min) / 2f;
		float s = 0;
		float h = 0;
		if (max != min) {
			float delta = max - min;
			s = l > 0.5f ? delta / (2 - max - min) : delta / (max + min);
			if (max == r) h = (g - b) / delta + (g < b ? 6 : 0);
			else if (max == g) h = (b - r) / delta + 2;
			else h = (r - g) / delta + 4;
			h /= 6f;
		}
		h = (h + degrees / 360f) % 1f;
		if (h < 0) h += 1;
		return (color & 0xFF000000) | hsl(h, s, l);
	}

	private static int hsl(float h, float s, float l) {
		if (s == 0) {
			int v = Math.round(l * 255);
			return v << 16 | v << 8 | v;
		}
		float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
		float p = 2 * l - q;
		int r = Math.round(channel(p, q, h + 1f / 3f) * 255);
		int g = Math.round(channel(p, q, h) * 255);
		int b = Math.round(channel(p, q, h - 1f / 3f) * 255);
		return r << 16 | g << 8 | b;
	}

	private static float channel(float p, float q, float t) {
		if (t < 0) t += 1;
		if (t > 1) t -= 1;
		if (t < 1f / 6f) return p + (q - p) * 6 * t;
		if (t < 1f / 2f) return q;
		if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
		return p;
	}

	/** {@code color} mixed toward white by {@code amount}. */
	public static int lighten(int color, float amount) {
		int r = (color >> 16) & 255;
		int g = (color >> 8) & 255;
		int b = color & 255;
		r += Math.round((255 - r) * amount);
		g += Math.round((255 - g) * amount);
		b += Math.round((255 - b) * amount);
		return (color & 0xFF000000) | r << 16 | g << 8 | b;
	}

	/** A soft rainbow colour at {@code hue} (0..1). */
	public static int rainbow(float hue) {
		return 0xFF000000 | hsl(hue - (float) Math.floor(hue), 0.85f, 0.68f);
	}
}
