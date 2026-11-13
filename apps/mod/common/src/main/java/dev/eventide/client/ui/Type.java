package dev.eventide.client.ui;

import dev.eventide.client.Draw;
import dev.eventide.client.compat.Compat;
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
	/** Measured widths at the font's own size, by font (clean, Minecraft's) and weight. */
	@SuppressWarnings("unchecked")
	private static final Map<String, Float>[][] WIDTHS = new Map[2][3];
	/** {@link #fit} results, by font and weight. */
	@SuppressWarnings("unchecked")
	private static final Map<String, Fitted>[][] FITTED = new Map[2][3];
	private static boolean reloading = false;

	/** Text in one sharp copy of the font, and how much to stretch it across to match the layout's width. */
	private record Sharp(Component text, float stretch) {
	}

	/** What {@link #fit} made of a text for one width and size. */
	private record Fitted(float maxWidth, float cap, String text) {
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
		return baseWidth(text, weight) * cap / baseCap();
	}

	/**
	 * {@code text}'s width at the font's own size. Measuring goes through Minecraft's text code, which allocates on every call, and
	 * the HUD and menus measure the same labels many times a frame, so each width is kept. A resource reload can change the fonts,
	 * so nothing is kept while one runs, and everything is forgotten once it's done.
	 */
	private static float baseWidth(String text, Weight weight) {
		if (cacheBlocked()) return Minecraft.getInstance().font.width(component(text, weight));
		Map<String, Float> cache = cache(WIDTHS, weight);
		Float known = cache.get(text);
		if (known != null) return known;
		if (cache.size() > CACHE_LIMIT) cache.clear();
		float measured = Minecraft.getInstance().font.width(component(text, weight));
		cache.put(text, measured);
		return measured;
	}

	/** Whether a resource reload is running (so measurements can't be kept); forgets every kept measurement once it has finished. */
	private static boolean cacheBlocked() {
		if (Compat.overlayShown()) {
			reloading = true;
			return true;
		}
		if (reloading) {
			reloading = false;
			for (Map<String, ?>[] font : WIDTHS) for (Map<String, ?> map : font) if (map != null) map.clear();
			for (Map<String, ?>[] font : FITTED) for (Map<String, ?> map : font) if (map != null) map.clear();
			for (Map<String, ?>[] weight : SHARP) for (Map<String, ?> map : weight) if (map != null) map.clear();
		}
		return false;
	}

	private static <T> Map<String, T> cache(Map<String, T>[][] caches, Weight weight) {
		int font = UiFont.clean() ? 0 : 1;
		Map<String, T> cache = caches[font][weight.ordinal()];
		if (cache == null) caches[font][weight.ordinal()] = cache = new HashMap<>();
		return cache;
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
		boolean keep = !cacheBlocked();
		Map<String, Fitted> cache = keep ? cache(FITTED, weight) : null;
		Fitted known = keep ? cache.get(text) : null;
		if (known != null && known.maxWidth() == maxWidth && known.cap() == cap) return known.text();
		String cut = text;
		while (!cut.isEmpty() && width(cut + "...", cap, weight) > maxWidth) cut = cut.substring(0, cut.length() - 1);
		String fitted = cut.stripTrailing() + "...";
		if (keep) {
			if (cache.size() > CACHE_LIMIT) cache.clear();
			cache.put(text, new Fitted(maxWidth, cap, fitted));
		}
		return fitted;
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
