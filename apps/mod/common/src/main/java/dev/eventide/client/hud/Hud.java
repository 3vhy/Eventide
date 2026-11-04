package dev.eventide.client.hud;

import dev.eventide.client.Draw;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.ui.Grad;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Type.Weight;

/**
 * The HUD's shared look: dark midnight glass (a near-black violet fill and a light hairline), Inter
 * text, and the signal colours. Sizes are in GUI pixels at 100% HUD size.
 */
public final class Hud {
	/** The capital height of a HUD value. */
	public static final float CAP = 5.2f;
	/** Labels and small print. */
	public static final float SMALL = 4.1f;
	/** A pill's height. */
	public static final float PILL_H = 18;
	public static final float PAD_X = 6.5f;
	public static final float GAP = 4;
	public static final float RADIUS = 7;

	private Hud() {
	}

	/** The website's glass at {@code strength} (0..1, the Background setting); nothing at 0. */
	public static void glass(Draw d, float x1, float y1, float x2, float y2, float r, float strength) {
		if (strength <= 0.01f) return;
		float old = Paint.opacity(Paint.opacity() * Math.min(1, strength * 1.15f));
		float hair = hairline(d);
		Paint.rect(d, x1, y1, x2, y2, r, 0xCC0B0817);
		Paint.stroke(d, x1, y1, x2, y2, r, hair, 0x24FFFFFF);
		Paint.opacity(old);
	}

	/** One real pixel, in the units {@code d} draws in now. */
	public static float hairline(Draw d) {
		return 1f / Math.max(1, Paint.density(d));
	}

	/** Text with its capitals' top at {@code capTop}; with {@code shadow}, a soft dark copy under it for bare text over the world. */
	public static float text(Draw d, String text, float x, float capTop, float cap, Weight weight, int color, boolean shadow) {
		if (shadow) Type.draw(d, text, x + cap * 0.12f, capTop + cap * 0.12f, cap, weight, Paint.fade(0xFF000000, ((color >>> 24) / 255f) * 0.55f));
		Type.draw(d, text, x, capTop, cap, weight, color);
		return Type.width(text, cap, weight);
	}

	public static float width(String text, float cap, Weight weight) {
		return Type.width(text, cap, weight);
	}

	/** Mint when it's good, then gold, ember and pink as it gets worse (0..1). */
	public static int quality(double badness) {
		if (badness < 0.25) return Tokens.MINT;
		if (badness < 0.5) return Tokens.GOLD;
		if (badness < 0.75) return Tokens.EMBER;
		return Tokens.LOW;
	}

	/** A thin rounded bar filled to {@code fraction} with the accent gradient. */
	public static void bar(Draw d, float x1, float y, float x2, float h, float fraction, Grad fill) {
		Paint.rect(d, x1, y, x2, y + h, h / 2f, 0x1FFFFFFF);
		float fx = x1 + (x2 - x1) * Math.max(0, Math.min(1, fraction));
		if (fx > x1 + h * 0.5f) Paint.rect(d, x1, y, fx, y + h, h / 2f, fill == null ? Accent.bar() : fill);
	}

	/**
	 * One line of lyrics like karaoke: {@code lit} as far as it's been sung ({@code sung}, 0..1) and
	 * {@code dim} after that.
	 */
	public static void karaoke(Draw d, String text, float x, float capTop, float cap, Weight weight, float sung, int lit, int dim, boolean shadow) {
		float w = Type.width(text, cap, weight);
		float here = w * Math.max(0, Math.min(1, sung));
		if (here < w) text(d, text, x, capTop, cap, weight, dim, shadow);
		if (here >= w) {
			text(d, text, x, capTop, cap, weight, lit, shadow);
		} else if (here > 0) {
			d.enableScissor((int) Math.floor(x - cap), (int) Math.floor(capTop - cap), (int) Math.ceil(x + here), (int) Math.ceil(capTop + cap * 2));
			Type.draw(d, text, x, capTop, cap, weight, lit);
			d.disableScissor();
		}
	}
}
