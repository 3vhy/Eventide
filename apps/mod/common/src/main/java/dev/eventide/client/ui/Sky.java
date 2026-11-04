package dev.eventide.client.ui;

import dev.eventide.client.Draw;

/**
 * The night behind Eventide's menus: a midnight tint over the blurred game (or a deep night sky when
 * there's no world yet) with soft violet, cyan and pink nebula light at the edges, and a field of stars
 * that twinkle at their own pace, like the website's hero. Star positions are fixed, so they never jump between frames.
 */
public final class Sky {
	private static final int MAX_STARS = 260;
	private static final float[] STARS = new float[MAX_STARS * 6];

	static {
		long seed = 7;
		for (int i = 0; i < MAX_STARS; i++) {
			seed = next(seed);
			STARS[i * 6] = unit(seed);
			seed = next(seed);
			STARS[i * 6 + 1] = unit(seed);
			seed = next(seed);
			float size = unit(seed);
			STARS[i * 6 + 2] = 0.35f + size * size * size * size * 1.35f;
			seed = next(seed);
			STARS[i * 6 + 3] = 0.22f + unit(seed) * 0.62f;
			seed = next(seed);
			STARS[i * 6 + 4] = 0.4f + unit(seed) * 1.8f;
			seed = next(seed);
			STARS[i * 6 + 5] = unit(seed) * 6.2832f;
		}
	}

	private Sky() {
	}

	private static long next(long seed) {
		return seed * 6364136223846793005L + 1442695040888963407L;
	}

	private static float unit(long seed) {
		return ((seed >>> 40) & 0xFFFFFF) / (float) 0x1000000;
	}

	/**
	 * Covers a {@code w} x {@code h} screen: over a world, a deep midnight tint that lets the blurred game
	 * show through; without one, the full night sky. Either way soft nebula light gathers at the edges:
	 * violet high on the left, cyan low on the right and a touch of pink. {@code fade} (0..1) fades it in.
	 */
	public static void backdrop(Draw d, float w, float h, boolean world, float fade) {
		float old = Paint.opacity(Paint.opacity() * fade);
		if (world) {
			Paint.rect(d, 0, 0, w, h, 0, Grad.down(0xB805030B, 0xD80A0716));
		} else {
			Paint.rect(d, 0, 0, w, h, 0, Grad.down(0xFF0C0820, Tokens.BG_0));
		}
		nebula(d, w, h, world ? 0.75f : 1f);
		Paint.opacity(old);
	}

	/** The soft nebula light behind the menus and the title screen, at {@code strength} (0..1). */
	public static void nebula(Draw d, float w, float h, float strength) {
		Paint.shadow(d, -w * 0.12f, -h * 0.3f, w * 0.42f, h * 0.28f, h * 0.3f, 0, 0, h * 0.75f, 0, Paint.fade(0xFF7C3AED, 0.32f * strength));
		Paint.shadow(d, w * 0.62f, h * 0.7f, w * 1.15f, h * 1.25f, h * 0.3f, 0, 0, h * 0.7f, 0, Paint.fade(0xFF22B8E6, 0.2f * strength));
		Paint.shadow(d, w * 0.72f, -h * 0.25f, w * 1.05f, h * 0.1f, h * 0.2f, 0, 0, h * 0.5f, 0, Paint.fade(0xFFE04BC8, 0.12f * strength));
		Paint.shadow(d, w * 0.2f, h * 0.85f, w * 0.55f, h * 1.2f, h * 0.2f, 0, 0, h * 0.55f, 0, Paint.fade(0xFF5B3BF5, 0.16f * strength));
	}

	/** Twinkling stars over a {@code w} x {@code h} area; {@code amount} is 0..1 of the most there can be. */
	public static void stars(Draw d, float w, float h, float amount, float scale, long nowMs) {
		int count = Math.round(MAX_STARS * Math.max(0, Math.min(1, amount)) * Math.min(1f, w * h / (1600f * 900f) * 1.4f + 0.3f));
		float t = nowMs / 1000f;
		for (int i = 0; i < count; i++) {
			float x = STARS[i * 6] * w;
			float y = STARS[i * 6 + 1] * h;
			float r = STARS[i * 6 + 2] * scale;
			float twinkle = 0.62f + 0.38f * (float) Math.sin(t * STARS[i * 6 + 4] + STARS[i * 6 + 5]);
			int alpha = Math.round(255 * STARS[i * 6 + 3] * twinkle);
			int tint = i % 4 == 0 ? 0xD8F4FF : i % 4 == 1 ? 0xE9DEFF : 0xFFFFFF;
			if (STARS[i * 6 + 2] > 1.05f) Paint.glow(d, x, y, r, r * 5f, (Math.round(alpha * 0.4f) << 24) | (i % 2 == 0 ? 0xA98BFF : 0x7FD8FF));
			Paint.circle(d, x, y, Math.max(0.3f, r), alpha << 24 | tint);
		}
	}
}
