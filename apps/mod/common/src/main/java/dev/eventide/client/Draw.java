package dev.eventide.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

/**
 * The few drawing calls Eventide needs. Minecraft renamed its GUI drawing class between
 * 1.21.11 (GuiGraphics) and 26.1 (GuiGraphicsExtractor), so each version has its own
 * DrawImpl in compat/ and everything else draws through this interface.
 */
public interface Draw {
	/** Something drawn over a screen after it, knowing where the mouse is. */
	interface Overlay {
		void draw(Draw d, int mouseX, int mouseY);
	}

	int width();

	int height();

	void fill(int x1, int y1, int x2, int y2, int argb);

	void text(String text, int x, int y, int argb, boolean shadow);

	/** Styled text, e.g. bold titles. */
	void text(Component text, int x, int y, int argb, boolean shadow);

	void item(ItemStack stack, int x, int y);

	/**
	 * Draws a living entity standing in the box from (x0, y0) to (x1, y1), {@code scale} pixels a block, turned
	 * to look at (lookX, lookY), as the inventory draws your player.
	 */
	void entity(net.minecraft.world.entity.LivingEntity entity, int x0, int y0, int x1, int y1, int scale, float lookX, float lookY);

	/** Durability bar and count label; pass null to use the stack's own count. */
	void itemOverlay(ItemStack stack, int x, int y, String countLabel);

	/** Draws a sprite from Minecraft's GUI atlas, like a status effect's icon, at w x h. */
	void sprite(Identifier sprite, int x, int y, int w, int h);

	/** Draws a whole texture of the given size, stretched to w x h. */
	void texture(Identifier texture, int x, int y, int w, int h, int textureWidth, int textureHeight);

	/**
	 * Draws a w x h piece of a texture, from (u, v), pixel for pixel and tinted with {@code argb}.
	 * Used with white masks to draw smooth shapes in any colour.
	 */
	void mask(Identifier texture, int x, int y, int u, int v, int w, int h, int textureWidth, int textureHeight, int argb);

	/** How much the current transform scales things up, in GUI units per unit. */
	float poseScale();

	/** Where the current transform puts the origin, in GUI units. */
	float poseX();

	float poseY();

	/**
	 * Draws quads in the current transform, for Eventide's smooth shapes ({@link dev.eventide.client.ui.Paint}).
	 * {@code xy} holds two floats a vertex and four vertices a quad; {@code colors} holds one ARGB colour a
	 * vertex, blended across the quad. With a {@code texture}, {@code uv} holds its coordinates (0..1) a
	 * vertex, multiplied by the colours, and sampled smoothly when {@code smooth} (sharp for pixel art);
	 * without one, {@code uv} is ignored.
	 */
	void quads(float[] xy, float[] uv, int[] colors, int vertices, Identifier texture, boolean smooth);

	/** A player's face (with the hat layer), size x size. */
	void face(PlayerSkin skin, int x, int y, int size);

	void push();

	void pop();

	void translate(float x, float y);

	void scale(float scale);

	/** Only draw inside this rectangle until disableScissor(). */
	void enableScissor(int x1, int y1, int x2, int y2);

	void disableScissor();

	default void outline(int x1, int y1, int x2, int y2, int argb) {
		fill(x1, y1, x2, y1 + 1, argb);
		fill(x1, y2 - 1, x2, y2, argb);
		fill(x1, y1, x1 + 1, y2, argb);
		fill(x2 - 1, y1, x2, y2, argb);
	}

	/** A filled rectangle with pixel-rounded corners. Rows never overlap, so see-through colours stay even. */
	default void roundRect(int x1, int y1, int x2, int y2, int radius, int argb) {
		int r = Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
		if (r == 0) {
			fill(x1, y1, x2, y2, argb);
			return;
		}
		fill(x1, y1 + r, x2, y2 - r, argb);
		for (int i = 0; i < r; i++) {
			int inset = cornerInset(r, i);
			fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, argb);
			fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, argb);
		}
	}

	/** A one pixel rounded border, matching roundRect. */
	default void roundOutline(int x1, int y1, int x2, int y2, int radius, int argb) {
		int r = Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
		if (r == 0) {
			outline(x1, y1, x2, y2, argb);
			return;
		}
		int top = cornerInset(r, 0);
		fill(x1 + top, y1, x2 - top, y1 + 1, argb);
		fill(x1 + top, y2 - 1, x2 - top, y2, argb);
		for (int i = 1; i < r; i++) {
			int inset = cornerInset(r, i);
			int span = Math.max(1, cornerInset(r, i - 1) - inset);
			fill(x1 + inset, y1 + i, x1 + inset + span, y1 + i + 1, argb);
			fill(x2 - inset - span, y1 + i, x2 - inset, y1 + i + 1, argb);
			fill(x1 + inset, y2 - i - 1, x1 + inset + span, y2 - i, argb);
			fill(x2 - inset - span, y2 - i - 1, x2 - inset, y2 - i, argb);
		}
		fill(x1, y1 + r, x1 + 1, y2 - r, argb);
		fill(x2 - 1, y1 + r, x2, y2 - r, argb);
	}

	/** A rounded rectangle shaded from {@code top} at the top edge to {@code bottom} at the bottom edge. */
	default void roundGradient(int x1, int y1, int x2, int y2, int radius, int top, int bottom) {
		int h = y2 - y1;
		if (h <= 0 || x2 <= x1) return;
		int r = Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, h / 2)));
		for (int row = 0; row < h; row++) {
			int inset = row < r ? cornerInset(r, row) : row >= h - r ? cornerInset(r, h - 1 - row) : 0;
			fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, mix(top, bottom, h == 1 ? 0 : row / (double) (h - 1)));
		}
	}

	/** Blends two ARGB colours, alpha included; t = 0 gives {@code a}. */
	static int mix(int a, int b, double t) {
		double u = Math.max(0, Math.min(1, t));
		int out = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = (a >>> shift) & 255;
			int cb = (b >>> shift) & 255;
			out |= ((int) Math.round(ca + (cb - ca) * u) & 255) << shift;
		}
		return out;
	}

	private static int cornerInset(int r, int row) {
		double dy = r - row - 0.5;
		return (int) Math.round(r - Math.sqrt(r * r - dy * dy));
	}
}
