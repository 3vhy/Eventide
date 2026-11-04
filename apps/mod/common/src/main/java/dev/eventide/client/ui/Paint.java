package dev.eventide.client.ui;

import dev.eventide.client.Draw;
import dev.eventide.client.EventideClient;
import dev.eventide.client.compat.Compat;
import dev.eventide.client.gui.Smooth;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Eventide's own vector renderer, on top of Minecraft's GUI renderer. Shapes are built as meshes in the
 * current transform: smooth round boxes and circles, strokes, CSS-style soft shadows and glows, lines,
 * rounded pictures and Lucide icons, each with a solid colour or a {@link Grad}. Every edge is feathered
 * over exactly one screen pixel, so shapes stay sharp and smooth at any GUI scale and in mid-animation,
 * with no textures to bake. Each shape is a single element for Minecraft to draw, built in reused
 * buffers. Render thread only.
 */
public final class Paint {
	private static final int MAX_VERTICES = 1 << 14;
	private static final int MAX_SEGMENTS = 16;
	private static final int MAX_POINTS = 4 * (MAX_SEGMENTS + 1);
	private static final float[] XY = new float[MAX_VERTICES * 2];
	private static final float[] UV = new float[MAX_VERTICES * 2];
	private static final int[] COLORS = new int[MAX_VERTICES];
	private static final float[] AX = new float[MAX_POINTS];
	private static final float[] AY = new float[MAX_POINTS];
	private static final float[] BX = new float[MAX_POINTS];
	private static final float[] BY = new float[MAX_POINTS];
	/** Glow falloff: offsets from the edge in standard deviations, like a CSS blur. */
	private static final float[] GLOW_STEPS = {-2.2f, -1.4f, -0.8f, -0.35f, 0f, 0.35f, 0.8f, 1.4f, 2.2f, 3.2f};
	/** The same falloff in fewer rings, for small blurs (where the difference can't be seen) and Simple shadows. */
	private static final float[] GLOW_STEPS_LITE = {-1.6f, -0.6f, 0f, 0.6f, 1.5f, 2.6f};
	/** Unit circle points for each corner segment count, so outlines don't call cos and sin per point. */
	private static final float[][] CORNER_COS = new float[MAX_SEGMENTS + 1][];
	private static final float[][] CORNER_SIN = new float[MAX_SEGMENTS + 1][];
	private static final int[] CA = new int[MAX_POINTS];
	private static final int[] CB = new int[MAX_POINTS];
	private static int shadowQuality = 2;
	private static final Map<String, Identifier> ICONS = new HashMap<>();
	private static final float[] OFFSETS = new float[GLOW_STEPS.length];
	private static final float[] ALPHAS = new float[GLOW_STEPS.length];
	private static float[] miterX = new float[64];
	private static float[] miterY = new float[64];

	private static int vertices = 0;
	private static float opacity = 1f;
	private static Draw target;
	private static Identifier texture;
	private static boolean smoothTexture;

	// What the shape being built is filled with: a colour or a gradient over a box, and maybe a picture
	private static int solid;
	private static Grad grad;
	private static float boxX1;
	private static float boxY1;
	private static float boxX2;
	private static float boxY2;
	private static float gradDx;
	private static float gradDy;
	private static float gradLength;
	private static float texU1;
	private static float texV1;
	private static float texU2;
	private static float texV2;

	private Paint() {
	}

	/** Real screen pixels per unit where {@code d} draws right now. */
	public static float density(Draw d) {
		return d.poseScale() * (float) Minecraft.getInstance().getWindow().getGuiScale();
	}

	/** Rounds a position to the nearest screen pixel, so straight edges land sharp. */
	public static float snapX(Draw d, float x) {
		return snap(x, d.poseScale(), d.poseX());
	}

	public static float snapY(Draw d, float y) {
		return snap(y, d.poseScale(), d.poseY());
	}

	private static float snap(float v, float scale, float offset) {
		float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
		if (scale == 0 || gui == 0) return v;
		float pixel = (v * scale + offset) * gui;
		return (Math.round(pixel) / gui - offset) / scale;
	}

	/** {@code argb} with its alpha multiplied by {@code alpha}. */
	public static int fade(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 255) * Math.max(0f, Math.min(1f, alpha)));
		return a << 24 | (argb & 0xFFFFFF);
	}

	/** Sets how opaque everything drawn next is (0..1), for fading whole screens; returns the old value. */
	public static float opacity(float value) {
		float old = opacity;
		opacity = Math.max(0f, Math.min(1f, value));
		return old;
	}

	public static float opacity() {
		return opacity;
	}

	/** How shadows and glows are drawn: 2 full, 1 simpler (fewer rings), 0 not at all (Performance). */
	public static void shadowQuality(int quality) {
		shadowQuality = quality;
	}

	// Filled shapes

	/** A box with round corners of radius {@code r} (0 for square). */
	public static void rect(Draw d, float x1, float y1, float x2, float y2, float r, int color) {
		solid(color, x1, y1, x2, y2);
		fill(d, x1, y1, x2, y2, r);
	}

	public static void rect(Draw d, float x1, float y1, float x2, float y2, float r, Grad gradient) {
		gradient(gradient, x1, y1, x2, y2);
		fill(d, x1, y1, x2, y2, r);
	}

	public static void circle(Draw d, float cx, float cy, float radius, int color) {
		rect(d, cx - radius, cy - radius, cx + radius, cy + radius, radius, color);
	}

	public static void circle(Draw d, float cx, float cy, float radius, Grad gradient) {
		rect(d, cx - radius, cy - radius, cx + radius, cy + radius, radius, gradient);
	}

	/** A picture in a box with round corners; {@code u}/{@code v} pick the part of the texture (0..1). */
	public static void image(Draw d, Identifier image, float x1, float y1, float x2, float y2, float r, float u1, float v1, float u2,
			float v2, int tint, boolean smooth) {
		solid(tint, x1, y1, x2, y2);
		texU1 = u1;
		texV1 = v1;
		texU2 = u2;
		texV2 = v2;
		begin(d, image, smooth);
		shape(x1, y1, x2, y2, r);
		flush();
	}

	private static void fill(Draw d, float x1, float y1, float x2, float y2, float r) {
		begin(d, null, true);
		shape(x1, y1, x2, y2, r);
		flush();
	}

	private static void shape(float x1, float y1, float x2, float y2, float r) {
		float k = density(target);
		if (k <= 0 || x2 <= x1 || y2 <= y1) return;
		float h = 0.5f / k;
		int segments = segments(r + h, k);
		int n = outline(AX, AY, x1, y1, x2, y2, r, -h, segments);
		outline(BX, BY, x1, y1, x2, y2, r, h, segments);
		fan(AX, AY, n, (x1 + x2) / 2f, (y1 + y2) / 2f, 1f);
		strip(AX, AY, 1f, BX, BY, 0f, n);
	}

	/**
	 * A box shaded smoothly between a colour at each corner (top left, top right, bottom right, bottom left),
	 * split into a grid so the blend is even across it, for colour pickers.
	 */
	public static void corners(Draw d, float x1, float y1, float x2, float y2, int topLeft, int topRight, int bottomRight, int bottomLeft) {
		if (x2 <= x1 || y2 <= y1) return;
		solid(0xFFFFFFFF, x1, y1, x2, y2);
		begin(d, null, true);
		int n = 8;
		for (int j = 0; j < n; j++) {
			float v0 = j / (float) n;
			float v1 = (j + 1) / (float) n;
			for (int i = 0; i < n; i++) {
				float u0 = i / (float) n;
				float u1 = (i + 1) / (float) n;
				float ax = x1 + (x2 - x1) * u0;
				float bx = x1 + (x2 - x1) * u1;
				float ay = y1 + (y2 - y1) * v0;
				float by = y1 + (y2 - y1) * v1;
				quad(ax, ay, bilinear(topLeft, topRight, bottomRight, bottomLeft, u0, v0), ax, by, bilinear(topLeft, topRight, bottomRight, bottomLeft, u0, v1),
						bx, by, bilinear(topLeft, topRight, bottomRight, bottomLeft, u1, v1), bx, ay, bilinear(topLeft, topRight, bottomRight, bottomLeft, u1, v0));
			}
		}
		flush();
	}

	private static int bilinear(int tl, int tr, int br, int bl, float u, float v) {
		int top = Draw.mix(tl, tr, u);
		int bottom = Draw.mix(bl, br, u);
		return fade(Draw.mix(top, bottom, v), opacity);
	}

	/** A horizontal strip through {@code colors}, evenly spaced, for the hue bar. */
	public static void stops(Draw d, float x1, float y1, float x2, float y2, int[] colors) {
		if (x2 <= x1 || y2 <= y1 || colors.length < 2) return;
		solid(0xFFFFFFFF, x1, y1, x2, y2);
		begin(d, null, true);
		int n = colors.length - 1;
		for (int i = 0; i < n; i++) {
			float ax = x1 + (x2 - x1) * i / n;
			float bx = x1 + (x2 - x1) * (i + 1) / n;
			int a = fade(colors[i], opacity);
			int b = fade(colors[i + 1], opacity);
			quad(ax, y1, a, ax, y2, a, bx, y2, b, bx, y1, b);
		}
		flush();
	}

	// Strokes

	/** A border {@code width} units wide just inside the box's edge. */
	public static void stroke(Draw d, float x1, float y1, float x2, float y2, float r, float width, int color) {
		solid(color, x1, y1, x2, y2);
		border(d, x1, y1, x2, y2, r, -width, 0);
	}

	public static void stroke(Draw d, float x1, float y1, float x2, float y2, float r, float width, Grad gradient) {
		gradient(gradient, x1, y1, x2, y2);
		border(d, x1, y1, x2, y2, r, -width, 0);
	}

	/** A border centred on the box's edge (half in, half out), like an SVG stroke. */
	public static void strokeCentered(Draw d, float x1, float y1, float x2, float y2, float r, float width, int color) {
		solid(color, x1, y1, x2, y2);
		border(d, x1, y1, x2, y2, r, -width / 2f, width / 2f);
	}

	public static void ring(Draw d, float cx, float cy, float radius, float width, int color) {
		stroke(d, cx - radius, cy - radius, cx + radius, cy + radius, radius, width, color);
	}

	public static void ring(Draw d, float cx, float cy, float radius, float width, Grad gradient) {
		stroke(d, cx - radius, cy - radius, cx + radius, cy + radius, radius, width, gradient);
	}

	private static void border(Draw d, float x1, float y1, float x2, float y2, float r, float from, float to) {
		float k = density(d);
		if (k <= 0 || x2 <= x1 || y2 <= y1) return;
		float h = 0.5f / k;
		begin(d, null, true);
		int segments = segments(r + to + h, k);
		int levels = edges(from, to, h);
		band(x1, y1, x2, y2, r, segments, levels);
		flush();
	}

	/**
	 * Sets {@link #OFFSETS} and {@link #ALPHAS} to a band from {@code from} to {@code to} with feathered
	 * sides ({@code h} is half a pixel); a band thinner than a pixel becomes a faint one-pixel line.
	 * Returns how many levels it uses.
	 */
	private static int edges(float from, float to, float h) {
		if (to - from >= 2 * h) {
			OFFSETS[0] = from - h;
			OFFSETS[1] = from + h;
			OFFSETS[2] = to - h;
			OFFSETS[3] = to + h;
			ALPHAS[0] = 0;
			ALPHAS[1] = 1;
			ALPHAS[2] = 1;
			ALPHAS[3] = 0;
			return 4;
		}
		float mid = (from + to) / 2f;
		OFFSETS[0] = mid - h;
		OFFSETS[1] = mid;
		OFFSETS[2] = mid + h;
		ALPHAS[0] = 0;
		ALPHAS[1] = (to - from) / (2 * h);
		ALPHAS[2] = 0;
		return 3;
	}

	/** Strips between outlines of the box grown by each of the first {@code levels} {@link #OFFSETS}, faded by {@link #ALPHAS}. */
	private static void band(float x1, float y1, float x2, float y2, float r, int segments, int levels) {
		float[] offsets = OFFSETS;
		float[] alphas = ALPHAS;
		int n = outline(AX, AY, x1, y1, x2, y2, r, offsets[0], segments);
		for (int level = 1; level < levels; level++) {
			boolean odd = level % 2 == 1;
			float[] fromX = odd ? AX : BX;
			float[] fromY = odd ? AY : BY;
			float[] toX = odd ? BX : AX;
			float[] toY = odd ? BY : AY;
			outline(toX, toY, x1, y1, x2, y2, r, offsets[level], segments);
			strip(fromX, fromY, alphas[level - 1], toX, toY, alphas[level], n);
		}
	}

	// Shadows and glows

	/**
	 * A soft shadow or glow like CSS {@code box-shadow: dx dy blur spread color}, for a box with corner
	 * radius {@code r}. Draw it before the box.
	 */
	public static void shadow(Draw d, float x1, float y1, float x2, float y2, float r, float dx, float dy, float blur, float spread,
			int color) {
		float k = density(d);
		if (k <= 0 || ((color >>> 24) & 255) == 0) return;
		float sx1 = x1 - spread + dx;
		float sy1 = y1 - spread + dy;
		float sx2 = x2 + spread + dx;
		float sy2 = y2 + spread + dy;
		if (sx2 <= sx1 || sy2 <= sy1) return;
		float sr = Math.max(0, r + spread);
		float sigma = blur / 2f;
		if (shadowQuality <= 0) return;
		if (sigma * k < 0.75f) {
			rect(d, sx1, sy1, sx2, sy2, sr, color);
			return;
		}
		solid(color, sx1, sy1, sx2, sy2);
		float half = Math.min(sx2 - sx1, sy2 - sy1) / 2f;
		float[] steps = shadowQuality == 1 || sigma * k < 8f ? GLOW_STEPS_LITE : GLOW_STEPS;
		for (int i = 0; i < steps.length; i++) {
			float offset = Math.max(-half, steps[i] * sigma);
			OFFSETS[i] = offset;
			ALPHAS[i] = (float) (0.5 * erfc(offset / (sigma * Math.sqrt(2))));
		}
		begin(d, null, true);
		int segments = Math.max(2, Math.min(10, (int) Math.ceil((sr + OFFSETS[steps.length - 1]) * k / 5f)));
		int n = outline(AX, AY, sx1, sy1, sx2, sy2, sr, OFFSETS[0], segments);
		fan(AX, AY, n, (sx1 + sx2) / 2f, (sy1 + sy2) / 2f, ALPHAS[0]);
		band(sx1, sy1, sx2, sy2, sr, segments, steps.length);
		flush();
	}

	/** A soft round glow: a circle's {@link #shadow}. */
	public static void glow(Draw d, float cx, float cy, float radius, float blur, int color) {
		shadow(d, cx - radius, cy - radius, cx + radius, cy + radius, radius, 0, 0, blur, 0, color);
	}

	private static double erfc(double x) {
		double z = Math.abs(x);
		double t = 1 / (1 + 0.5 * z);
		double r = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 + t * (-0.18628806
				+ t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))));
		return x >= 0 ? r : 2 - r;
	}

	// Lines

	/** A smooth line through {@code count} points {x0, y0, x1, y1, ...}, joined with mitres. */
	public static void line(Draw d, float[] points, int count, boolean closed, float width, int color) {
		lineInternal(d, points, null, count, closed, width, color, null);
	}

	/** A line coloured point by point: {@code colors} has one ARGB colour for each point. */
	public static void line(Draw d, float[] points, int[] colors, int count, boolean closed, float width) {
		lineInternal(d, points, colors, count, closed, width, 0, null);
	}

	public static void line(Draw d, float[] points, int count, boolean closed, float width, Grad gradient) {
		lineInternal(d, points, null, count, closed, width, 0, gradient);
	}

	private static void lineInternal(Draw d, float[] points, int[] colors, int count, boolean closed, float width, int color,
			Grad gradient) {
		float k = density(d);
		if (k <= 0 || count < 2) return;
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			minX = Math.min(minX, points[2 * i]);
			maxX = Math.max(maxX, points[2 * i]);
			minY = Math.min(minY, points[2 * i + 1]);
			maxY = Math.max(maxY, points[2 * i + 1]);
		}
		if (gradient != null) gradient(gradient, minX, minY, maxX, maxY);
		else solid(color, minX, minY, maxX, maxY);
		float h = 0.5f / k;
		int levels = edges(-width / 2f, width / 2f, h);
		float[] offsets = OFFSETS;
		float[] alphas = ALPHAS;
		if (miterX.length < count) {
			miterX = new float[count * 2];
			miterY = new float[count * 2];
		}
		float[] mx = miterX;
		float[] my = miterY;
		for (int i = 0; i < count; i++) {
			int prev = i > 0 ? i - 1 : closed ? count - 1 : -1;
			int next = i < count - 1 ? i + 1 : closed ? 0 : -1;
			float n1x = 0;
			float n1y = 0;
			float n2x = 0;
			float n2y = 0;
			if (prev >= 0) {
				float dx = points[2 * i] - points[2 * prev];
				float dy = points[2 * i + 1] - points[2 * prev + 1];
				float len = (float) Math.sqrt(dx * dx + dy * dy);
				if (len > 0) {
					n1x = -dy / len;
					n1y = dx / len;
				}
			}
			if (next >= 0) {
				float dx = points[2 * next] - points[2 * i];
				float dy = points[2 * next + 1] - points[2 * i + 1];
				float len = (float) Math.sqrt(dx * dx + dy * dy);
				if (len > 0) {
					n2x = -dy / len;
					n2y = dx / len;
				}
			}
			float sx = n1x + n2x;
			float sy = n1y + n2y;
			float len = (float) Math.sqrt(sx * sx + sy * sy);
			if (len < 1e-4f) {
				mx[i] = n1x != 0 || n1y != 0 ? n1x : n2x;
				my[i] = n1x != 0 || n1y != 0 ? n1y : n2y;
				continue;
			}
			sx /= len;
			sy /= len;
			float reference = prev >= 0 ? sx * n1x + sy * n1y : sx * n2x + sy * n2y;
			float miter = 1f / Math.max(0.35f, reference);
			mx[i] = sx * miter;
			my[i] = sy * miter;
		}
		begin(d, null, true);
		int segmentsCount = closed ? count : count - 1;
		for (int s = 0; s < segmentsCount; s++) {
			int i = s;
			int j = (s + 1) % count;
			int ci = colors != null ? colors[i] : 0;
			int cj = colors != null ? colors[j] : 0;
			for (int level = 0; level + 1 < levels; level++) {
				float o1 = offsets[level];
				float o2 = offsets[level + 1];
				float ax = points[2 * i] + mx[i] * o1;
				float ay = points[2 * i + 1] + my[i] * o1;
				float bx = points[2 * i] + mx[i] * o2;
				float by = points[2 * i + 1] + my[i] * o2;
				float cx = points[2 * j] + mx[j] * o2;
				float cy = points[2 * j + 1] + my[j] * o2;
				float dx = points[2 * j] + mx[j] * o1;
				float dy = points[2 * j + 1] + my[j] * o1;
				if (colors != null) {
					quad(ax, ay, fade(ci, alphas[level]), bx, by, fade(ci, alphas[level + 1]), cx, cy, fade(cj, alphas[level + 1]), dx, dy,
							fade(cj, alphas[level]));
				} else {
					quad(ax, ay, color(ax, ay, alphas[level]), bx, by, color(bx, by, alphas[level + 1]), cx, cy,
							color(cx, cy, alphas[level + 1]), dx, dy, color(dx, dy, alphas[level]));
				}
			}
		}
		flush();
	}

	// Icons

	/** A Lucide icon {@code size} units square with its top left at (x, y). */
	public static void icon(Draw d, String name, float x, float y, float size, int color) {
		icon(d, name, x, y, size, 1f, false, color, null);
	}

	public static void icon(Draw d, String name, float x, float y, float size, Grad gradient) {
		icon(d, name, x, y, size, 1f, false, 0, gradient);
	}

	/** An icon with a heavier or lighter stroke ({@code weight}, 1 is normal), optionally filled in. */
	public static void icon(Draw d, String name, float x, float y, float size, float weight, boolean solidShape, int color, Grad gradient) {
		float k = density(d);
		if (k <= 0 || size <= 0) return;
		int pixels = Math.max(2, Math.round(size * k));
		Identifier mask = iconMask(name, pixels, weight, solidShape);
		if (mask == null) return;
		float x1 = snapX(d, x);
		float y1 = snapY(d, y);
		float x2 = x1 + pixels / k;
		float y2 = y1 + pixels / k;
		if (gradient != null) gradient(gradient, x1, y1, x2, y2);
		else solid(color, x1, y1, x2, y2);
		texU1 = 0;
		texV1 = 0;
		texU2 = 1;
		texV2 = 1;
		begin(d, mask, true);
		quad(x1, y1, color(x1, y1, 1f), x1, y2, color(x1, y2, 1f), x2, y2, color(x2, y2, 1f), x2, y1, color(x2, y1, 1f));
		flush();
	}

	/** A Lucide icon as a white texture {@code pixels} square (made once, then kept), or null for an unknown name. */
	public static Identifier iconTexture(String name, int pixels) {
		return iconMask(name, pixels, 1f, false);
	}

	private static Identifier iconMask(String name, int pixels, float weight, boolean solidShape) {
		String key = name + "_" + pixels + "_" + Math.round(weight * 100) + (solidShape ? "_solid" : "");
		if (ICONS.containsKey(key)) return ICONS.get(key);
		Identifier made = null;
		if (IconShapes.exists(name)) {
			Identifier id = Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, "icon/" + key.toLowerCase(Locale.ROOT));
			if (Compat.uploadTexture(id, Smooth.png(pixels, pixels, IconShapes.mask(name, pixels, weight, solidShape))) != null) made = id;
		}
		ICONS.put(key, made);
		return made;
	}

	// Mesh building

	private static void solid(int color, float x1, float y1, float x2, float y2) {
		solid = color;
		grad = null;
		boxX1 = x1;
		boxY1 = y1;
		boxX2 = x2;
		boxY2 = y2;
	}

	private static void gradient(Grad gradient, float x1, float y1, float x2, float y2) {
		grad = gradient;
		boxX1 = x1;
		boxY1 = y1;
		boxX2 = x2;
		boxY2 = y2;
		double angle = Math.toRadians(gradient.angle());
		gradDx = (float) Math.sin(angle);
		gradDy = (float) -Math.cos(angle);
		gradLength = Math.abs((x2 - x1) * gradDx) + Math.abs((y2 - y1) * gradDy);
	}

	private static int color(float x, float y, float alpha) {
		int base = solid;
		if (grad != null) {
			float t = gradLength <= 0 ? 0
					: ((x - (boxX1 + boxX2) / 2f) * gradDx + (y - (boxY1 + boxY2) / 2f) * gradDy) / gradLength + 0.5f;
			base = Draw.mix(grad.from(), grad.to(), t);
		}
		return fade(base, alpha * opacity);
	}

	private static int segments(float radius, float k) {
		return Math.max(1, Math.min(MAX_SEGMENTS, (int) Math.ceil(radius * k / 2.5f)));
	}

	/**
	 * Fills {@code px}/{@code py} with the outline of the box grown by {@code grow} (negative shrinks),
	 * clockwise from the top left corner, its corners rounded to {@code r + grow}. Returns the point count.
	 */
	private static int outline(float[] px, float[] py, float x1, float y1, float x2, float y2, float r, float grow, int segments) {
		float gx1 = x1 - grow;
		float gy1 = y1 - grow;
		float gx2 = x2 + grow;
		float gy2 = y2 + grow;
		if (gx2 < gx1) {
			gx1 = (x1 + x2) / 2f;
			gx2 = gx1;
		}
		if (gy2 < gy1) {
			gy1 = (y1 + y2) / 2f;
			gy2 = gy1;
		}
		float base = Math.min(r, Math.min(x2 - x1, y2 - y1) / 2f);
		float rr = Math.max(0, Math.min(base + grow, Math.min(gx2 - gx1, gy2 - gy1) / 2f));
		float[] cos = CORNER_COS[segments];
		float[] sin = CORNER_SIN[segments];
		if (cos == null) {
			cos = new float[4 * (segments + 1)];
			sin = new float[4 * (segments + 1)];
			for (int corner = 0, j = 0; corner < 4; corner++) {
				double start = Math.PI * (1 + corner * 0.5);
				for (int i = 0; i <= segments; i++, j++) {
					double a = start + Math.PI / 2 * i / segments;
					cos[j] = (float) Math.cos(a);
					sin[j] = (float) Math.sin(a);
				}
			}
			CORNER_COS[segments] = cos;
			CORNER_SIN[segments] = sin;
		}
		int n = 0;
		for (int corner = 0; corner < 4; corner++) {
			float cx = corner == 0 || corner == 3 ? gx1 + rr : gx2 - rr;
			float cy = corner < 2 ? gy1 + rr : gy2 - rr;
			for (int i = 0; i <= segments; i++) {
				px[n] = cx + cos[n] * rr;
				py[n] = cy + sin[n] * rr;
				n++;
			}
		}
		return n;
	}

	/** Fills a closed outline from its middle, at {@code alpha}. */
	private static void fan(float[] px, float[] py, int n, float cx, float cy, float alpha) {
		int center = color(cx, cy, alpha);
		int[] colors = colors(px, py, n, alpha, CA);
		for (int i = 0; i < n; i += 2) {
			int b = (i + 1) % n;
			int c = (i + 2) % n;
			quad(cx, cy, center, px[i], py[i], colors[i], px[b], py[b], colors[b], px[c], py[c], colors[c]);
		}
	}

	/** The colour at each of an outline's {@code n} points, at {@code alpha}, into {@code out} (one call per point, not per quad corner). */
	private static int[] colors(float[] px, float[] py, int n, float alpha, int[] out) {
		if (grad == null) {
			int c = fade(solid, alpha * opacity);
			for (int i = 0; i < n; i++) out[i] = c;
		} else {
			for (int i = 0; i < n; i++) out[i] = color(px[i], py[i], alpha);
		}
		return out;
	}

	/** Joins two outlines with the same number of points, fading from {@code alphaA} to {@code alphaB}. */
	private static void strip(float[] ax, float[] ay, float alphaA, float[] bx, float[] by, float alphaB, int n) {
		int[] ca = colors(ax, ay, n, alphaA, CA);
		int[] cb = colors(bx, by, n, alphaB, CB);
		for (int i = 0; i < n; i++) {
			int j = i + 1 == n ? 0 : i + 1;
			quad(ax[i], ay[i], ca[i], bx[i], by[i], cb[i], bx[j], by[j], cb[j], ax[j], ay[j], ca[j]);
		}
	}

	private static void begin(Draw d, Identifier image, boolean smooth) {
		target = d;
		texture = image;
		smoothTexture = smooth;
		vertices = 0;
	}

	private static void quad(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3, float x4, float y4,
			int c4) {
		if (((c1 | c2 | c3 | c4) >>> 24) == 0) return;
		if (vertices + 4 > MAX_VERTICES) flush();
		float area = (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y4 - x4 * y3) + (x4 * y1 - x1 * y4);
		if (area > 0) {
			vertex(x1, y1, c1);
			vertex(x4, y4, c4);
			vertex(x3, y3, c3);
			vertex(x2, y2, c2);
		} else {
			vertex(x1, y1, c1);
			vertex(x2, y2, c2);
			vertex(x3, y3, c3);
			vertex(x4, y4, c4);
		}
	}

	private static void vertex(float x, float y, int color) {
		XY[2 * vertices] = x;
		XY[2 * vertices + 1] = y;
		if (texture != null) {
			float w = boxX2 - boxX1;
			float h = boxY2 - boxY1;
			float u = w == 0 ? 0 : Math.max(0, Math.min(1, (x - boxX1) / w));
			float v = h == 0 ? 0 : Math.max(0, Math.min(1, (y - boxY1) / h));
			UV[2 * vertices] = texU1 + (texU2 - texU1) * u;
			UV[2 * vertices + 1] = texV1 + (texV2 - texV1) * v;
		}
		COLORS[vertices] = color;
		vertices++;
	}

	private static void flush() {
		if (vertices >= 4 && target != null) target.quads(XY, UV, COLORS, vertices, texture, smoothTexture);
		vertices = 0;
	}
}
