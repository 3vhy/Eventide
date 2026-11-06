package dev.eventide.client.ui;

import dev.eventide.client.Draw;
import dev.eventide.client.gui.UiFont;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Text in Eventide's menus and HUD, sized by its capital height in layout units (the way the website's type
 * is measured) and drawn in Inter ({@link UiFont}), or Minecraft's font when Theme picks it. Components
 * are made once per string and kept, so drawing the same labels every frame allocates nothing.
 */
public final class Type {
	public enum Weight {
		MEDIUM,
		SEMIBOLD,
		BOLD
	}

	/** Capital height of the UI font at scale 1, in GUI pixels. */
	public static final float CAP = 6.8f;
	/** How far below the drawing position a capital's top sits, at scale 1. */
	private static final float CAP_TOP = 0.2f;
	private static final float MINECRAFT_CAP = 7f;
	private static final int CACHE_LIMIT = 2048;
	private static final Map<String, Component> MEDIUM = new HashMap<>();
	private static final Map<String, Component> SEMIBOLD = new HashMap<>();
	private static final Map<String, Component> BOLD = new HashMap<>();
	private static final Map<String, Component> PIXEL = new HashMap<>();
	private static final Map<String, Component> PIXEL_BOLD = new HashMap<>();
	@SuppressWarnings("unchecked")
	private static final Map<String, Sharp>[][] SHARP = new Map[3][UiFont.SHARPNESS.length];

	/** Text in one sharp copy of the font, and how much to stretch it across to match the layout's width. */
	private record Sharp(Component text, float stretch) {
	}

	private Type() {
	}

	public static Component component(String text, Weight weight) {
		boolean clean = UiFont.clean();
		Map<String, Component> cache = !clean ? (weight == Weight.MEDIUM ? PIXEL : PIXEL_BOLD)
				: weight == Weight.BOLD ? BOLD : weight == Weight.SEMIBOLD ? SEMIBOLD : MEDIUM;
		Component made = cache.get(text);
		if (made != null) return made;
		if (cache.size() > CACHE_LIMIT) cache.clear();
		if (!clean) {
			made = weight == Weight.MEDIUM ? Component.literal(text) : Component.literal(text).withStyle(ChatFormatting.BOLD);
		} else {
			made = switch (weight) {
				case BOLD -> UiFont.bold(text);
				case SEMIBOLD -> UiFont.semibold(text);
				case MEDIUM -> UiFont.medium(text);
			};
		}
		cache.put(text, made);
		return made;
	}

	/** {@code text} in the clean font's copy rasterised for {@code level} (see {@link UiFont#level}). */
	private static Sharp sharp(String text, Weight weight, int level) {
		int w = weight.ordinal();
		Map<String, Sharp> cache = SHARP[w][level];
		if (cache == null) SHARP[w][level] = cache = new HashMap<>();
		Sharp made = cache.get(text);
		if (made != null) return made;
		if (cache.size() > CACHE_LIMIT) cache.clear();
		Component copy = UiFont.sharp(text, w, level);
		var splitter = Minecraft.getInstance().font.getSplitter();
		float own = splitter.stringWidth(copy);
		float layout = splitter.stringWidth(component(text, weight));
		made = new Sharp(copy, own > 0 ? layout / own : 1);
		cache.put(text, made);
		return made;
	}

	/** How much a sharp copy of the font is stretched across to match the layout (for the test). */
	public static float stretch(String text, Weight weight, int level) {
		return sharp(text, weight, level).stretch();
	}

	private static float baseCap() {
		return UiFont.clean() ? CAP : MINECRAFT_CAP;
	}

	/** How wide {@code text} is with capitals {@code cap} units tall. */
	public static float width(String text, float cap, Weight weight) {
		if (text.isEmpty()) return 0;
		return Minecraft.getInstance().font.width(component(text, weight)) * cap / baseCap();
	}

	/** Draws {@code text} with its left edge at {@code x} and the top of its capitals at {@code capTop}. */
	public static void draw(Draw d, String text, float x, float capTop, float cap, Weight weight, int color) {
		int faded = Paint.fade(color, Paint.opacity());
		if (text.isEmpty() || ((faded >>> 24) & 255) < 5) return;
		float scale = cap / baseCap();
		boolean clean = UiFont.clean();
		d.push();
		d.translate(x, capTop - (clean ? CAP_TOP : 0) * scale);
		d.scale(scale);
		if (clean && d.sharpText()) {
			Sharp copy = sharp(text, weight, UiFont.level(Paint.density(d)));
			if (Math.abs(copy.stretch() - 1) > 0.002f) d.scale(copy.stretch(), 1);
			d.text(copy.text(), 0, 0, faded, false);
		} else {
			d.text(component(text, weight), 0, 0, faded, false);
		}
		d.pop();
	}

	public static void center(Draw d, String text, float cx, float capTop, float cap, Weight weight, int color) {
		draw(d, text, cx - width(text, cap, weight) / 2f, capTop, cap, weight, color);
	}

	public static void right(Draw d, String text, float rightX, float capTop, float cap, Weight weight, int color) {
		draw(d, text, rightX - width(text, cap, weight), capTop, cap, weight, color);
	}

	/** {@code text} cut short with an ellipsis so it fits in {@code maxWidth}. */
	public static String fit(String text, float maxWidth, float cap, Weight weight) {
		if (width(text, cap, weight) <= maxWidth) return text;
		String cut = text;
		while (!cut.isEmpty() && width(cut + "...", cap, weight) > maxWidth) cut = cut.substring(0, cut.length() - 1);
		return cut.stripTrailing() + "...";
	}

	/**
	 * Letter-spaced text, like the wordmark and the small uppercase labels: {@code tracking} is the extra
	 * room after each letter as a share of the capital height. Returns how wide it was.
	 */
	public static float spaced(Draw d, String text, float x, float capTop, float cap, float tracking, Weight weight, int color) {
		float cx = x;
		float gap = tracking * cap;
		for (int i = 0; i < text.length(); i++) {
			String letter = String.valueOf(text.charAt(i));
			if (d != null) draw(d, letter, cx, capTop, cap, weight, color);
			cx += width(letter, cap, weight) + gap;
		}
		return text.isEmpty() ? 0 : cx - gap - x;
	}

	public static float spacedWidth(String text, float cap, float tracking, Weight weight) {
		return spaced(null, text, 0, 0, cap, tracking, weight, 0);
	}
}
