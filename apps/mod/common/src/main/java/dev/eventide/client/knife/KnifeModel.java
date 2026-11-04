package dev.eventide.client.knife;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Eventide's knives, each its own weapon built from {@link Shape} pieces (ground blades, lofted grips, plates,
 * pins and rings): dark steel with a luminous edge, in shapes made for Eventide. Every knife has its own draw,
 * inspect and slash animations ({@link Clip}). Shapes are made the first time a knife is drawn and kept. The
 * order is the order the shader numbers them in (knife_surface.glsl's BLADE_RANGE and HOLE), so new knives go
 * at the end. Old knife names (from before the redesign) find their nearest new knife through {@link #byLabel}.
 */
public enum KnifeModel {
	ECLIPSE("Eclipse", "A crescent karambit with a ring for your finger and a moon cut through the blade", 1.0f, 0, -5.9f, -4.7f, 0.95f, 0.5f,
			Family.CURVED),
	RIPTIDE("Riptide", "A double-edged dagger with a trident guard and a riptide ring", 0.86f, 0, -2.0f, -2.3f, 0.8f, 0.62f, Family.FIXED),
	NIGHTFALL("Nightfall", "An angular tanto, black as the sky before the stars", 0.9f, 0, -1.6f, -2.4f, 0.95f, 0.62f, Family.FIXED),
	VESPER("Vesper", "A slim flipper that snaps open with a flick", 1.0f, -0.25f, 0.45f, -2.25f, 1.0f, 0.6f, Family.FOLDING),
	WRAITH("Wraith", "A balisong with open rail handles, made for tricks", 0.95f, -0.45f, 0.5f, -1.55f, 0.62f, 0.66f, Family.BUTTERFLY),
	HALO("Halo", "A push dagger with a halo round its blade", 1.15f, 0, -0.6f, -0.6f, 0.62f, 0.52f, Family.PUSH),
	LUMEN("Lumen", "A blade of pure light from a ringed hilt", 0.85f, 0, -1.0f, -1.8f, 0.72f, 0.72f, Family.FIXED),
	NOCTURNE("Nocturne", "A long stiletto with a crescent guard and a jewel for a pommel", 0.9f, 0, -1.0f, -2.0f, 0.6f, 0.6f, Family.FIXED),
	SERAPH("Seraph", "A curved short sword with swept wings for a guard", 0.66f, 0, -2.0f, -2.9f, 0.62f, 0.48f, Family.FIXED),
	REAPER("Reaper", "A sickle on a banded haft, for the end of a fight", 0.72f, 0, -1.0f, -2.8f, 0.42f, 0.42f, Family.FIXED),
	SPECTER("Specter", "A skeleton knife with a straight-edged blade and a ring", 0.98f, 0, -1.5f, -1.9f, 0.85f, 0.3f, Family.FIXED),
	CORVID("Corvid", "A hooked blade with a toothed spine and a beaked grip", 0.96f, 0, -1.5f, -1.9f, 0.82f, 0.6f, Family.FIXED);

	/** How a knife is built and handled: fixed blades, folders whose blade swings out, and the odd ones. */
	enum Family {
		FIXED,
		FOLDING,
		BUTTERFLY,
		CURVED,
		PUSH
	}

	/** What a part of a knife does when the knife moves. */
	enum PartKind {
		/** Moves with the knife. */
		BODY,
		/** A butterfly knife's handles, turning about their pins. */
		HANDLE_A,
		HANDLE_B,
		/** A folding knife's blade, swinging out about its pivot as the knife opens. */
		FOLD,
		/** An energy blade, as long as the pose's {@link Clip#OPEN}. */
		ENERGY
	}

	record Part(Shape mesh, PartKind kind, float pivotX, float pivotY) {
	}

	public final String label;
	public final String description;
	/** How big this knife is drawn, so long blades don't fill the screen. */
	final float scale;
	/** The point it spins about in tricks, in pixels: a karambit's ring, a butterfly knife's pin. */
	final float spinX;
	final float spinY;
	/** Where your fist closes on it, in pixels along the handle: the middle of the four fingers. */
	final float grip;
	/** How wide (towards the edge) and thick the handle is where you hold it, half each way, in pixels. */
	final float handleA;
	final float handleB;
	final Family family;
	private List<Part> parts;
	private Clip draw;
	private Clip inspect;
	private Clip[] slashes;
	private Clip use;

	KnifeModel(String label, String description, float scale, float spinX, float spinY, float grip, float handleA, float handleB, Family family) {
		this.label = label;
		this.description = description;
		this.scale = scale;
		this.spinX = spinX;
		this.spinY = spinY;
		this.grip = grip;
		this.handleA = handleA;
		this.handleB = handleB;
		this.family = family;
	}

	/** Every knife's name, in menu order. */
	public static String[] labels() {
		KnifeModel[] all = values();
		String[] out = new String[all.length];
		for (int i = 0; i < all.length; i++) out[i] = all[i].label;
		return out;
	}

	/** The knife with this name or id (case doesn't matter), or the new knife an old name became; null if none. */
	public static KnifeModel byLabel(String label) {
		if (label == null) return null;
		String wanted = label.trim();
		for (KnifeModel model : values()) {
			if (model.label.equalsIgnoreCase(wanted) || model.name().equalsIgnoreCase(wanted) || model.id().equalsIgnoreCase(wanted)) return model;
		}
		return renamed(wanted.toLowerCase(Locale.ROOT).replace(' ', '_'));
	}

	/** The new knife for a knife from before the redesign, by its old id or name, or null. */
	static KnifeModel renamed(String old) {
		return switch (old) {
			case "karambit", "talon" -> ECLIPSE;
			case "m9_bayonet", "bayonet" -> RIPTIDE;
			case "huntsman", "survival", "bowie" -> NIGHTFALL;
			case "flip", "flip_knife", "falchion", "navaja", "stiletto", "ursus" -> VESPER;
			case "butterfly" -> WRAITH;
			case "shadow_daggers" -> HALO;
			case "phantom" -> LUMEN;
			case "dagger", "classic" -> NOCTURNE;
			case "katana" -> SERAPH;
			case "scythe" -> REAPER;
			case "skeleton", "paracord", "nomad" -> SPECTER;
			case "gut", "gut_knife" -> CORVID;
			default -> null;
		};
	}

	List<Part> parts() {
		if (parts == null) parts = build();
		return parts;
	}

	Clip draw() {
		clips();
		return draw;
	}

	Clip inspect() {
		clips();
		return inspect;
	}

	Clip slash(int variant) {
		clips();
		return slashes[Math.floorMod(variant, slashes.length)];
	}

	int slashCount() {
		clips();
		return slashes.length;
	}

	Clip use() {
		clips();
		return use;
	}

	private List<Part> build() {
		List<Part> out = new ArrayList<>();
		Shape body = new Shape();
		switch (this) {
			case ECLIPSE -> eclipse(body);
			case RIPTIDE -> riptide(body);
			case NIGHTFALL -> nightfall(body);
			case VESPER -> {
				vesperHandle(body);
				out.add(new Part(vesperBlade(), PartKind.FOLD, -0.25f, 0.45f));
			}
			case WRAITH -> {
				wraithBlade(body);
				out.add(new Part(wraithHandle(-1), PartKind.HANDLE_A, -0.45f, 0.5f));
				out.add(new Part(wraithHandle(1), PartKind.HANDLE_B, 0.45f, 0.5f));
			}
			case HALO -> halo(body);
			case LUMEN -> {
				lumenHilt(body);
				out.add(new Part(lumenBlade(), PartKind.ENERGY, 0, 1.4f));
			}
			case NOCTURNE -> nocturne(body);
			case SERAPH -> seraph(body);
			case REAPER -> reaper(body);
			case SPECTER -> specter(body);
			case CORVID -> corvid(body);
		}
		out.add(0, new Part(body, PartKind.BODY, 0, 0));
		return List.copyOf(out);
	}

	/** The spine and edge of a blade at {@code t} (0 at the base, 1 at the tip): {sx, sy, ex, ey, half thickness}. */
	private interface Profile {
		float[] at(float t);
	}

	private static Shape.Blade blade(int steps, Profile profile) {
		Shape.Blade b = new Shape.Blade();
		for (int i = 0; i <= steps; i++) {
			float[] s = profile.at(i / (float) steps);
			b.station(s[0], s[1], s[2], s[3], s[4]);
		}
		return b;
	}

	/**
	 * A straight blade from {@code y0} to its tip at {@code y1}: the spine at {@code sx} and the edge at
	 * {@code ex}, both meeting at {@code tipX}. The spine runs straight until {@code clip} (0..1 along the
	 * blade) and then falls to the tip, rounded ({@code concave} false, a drop point) or hollowed (true, a
	 * clip point); the edge stays straight until {@code belly} and then sweeps up to the tip.
	 */
	private static Profile straight(float y0, float y1, float sx, float ex, float tipX, float clip, boolean concave, float belly, float h0, float h1) {
		return t -> {
			float y = y0 + (y1 - y0) * t;
			float x;
			if (t <= clip) {
				x = sx;
			} else {
				float u = (t - clip) / (1 - clip);
				float shape = concave ? 1 - (1 - u) * (1 - u) : u * u;
				x = sx + (tipX - sx) * shape;
			}
			float e;
			if (t <= belly) {
				e = ex;
			} else {
				float u = (t - belly) / (1 - belly);
				e = ex + (tipX - ex) * (float) Math.pow(u, 1.7);
			}
			return new float[] {x, y, e, y, h0 + (h1 - h0) * t};
		};
	}

	/** A symmetric double-edged blade from {@code y0} to {@code y1}, {@code width} half wide, tapering by {@code taper(t)} (1 at the base, 0 at the tip). */
	private static Profile symmetric(float y0, float y1, float width, float h0, float h1, java.util.function.Function<Float, Float> taper) {
		return t -> {
			float y = y0 + (y1 - y0) * t;
			float w = width * Math.max(0.015f, taper.apply(t));
			return new float[] {-w, y, w, y, h0 + (h1 - h0) * t};
		};
	}

	/** Saw teeth along the spine between {@code from} and {@code to} (0..1 along the blade). */
	private static Profile teeth(Profile base, float from, float to, int count, float depth) {
		return t -> {
			float[] s = base.at(t);
			if (t > from && t < to) {
				float u = (t - from) / (to - from) * count;
				float tooth = u - (float) Math.floor(u);
				float shape = tooth < 0.6f ? tooth / 0.6f : (1 - tooth) / 0.4f;
				s[0] -= depth * shape;
			}
			return s;
		};
	}

	/** A grip lofted along Y from {@code top} down to {@code bottom}, ribbed every {@code ribs} pixels if above zero. */
	private static void grip(Shape m, float top, float bottom, float rx, float rz, float squareness, float ribs, float bulge, int material, int bottomEnd) {
		int rings = Math.max(6, Math.round((top - bottom) / 0.25f));
		Shape.Ring[] out = new Shape.Ring[rings + 1];
		for (int i = 0; i <= rings; i++) {
			float f = i / (float) rings;
			float y = top + (bottom - top) * f;
			float swell = 1 + bulge * (float) Math.sin(Math.PI * f);
			float rib = ribs > 0 ? 1 - 0.08f * (float) Math.pow(Math.abs(Math.sin(Math.PI * (top - y) / ribs)), 6) : 1;
			out[i] = Shape.Ring.along(0, y, 0, rx * swell * rib, rz * swell * rib);
		}
		m.loft(out, 16, squareness, material, Shape.CAP, bottomEnd, false);
	}

	/** A tube along a path of points in the knife's plane (z = 0), {@code r} thick, for guards and hooks. */
	private static void curve(Shape m, float[] xy, float r, float rz, int material) {
		int n = xy.length / 2;
		Shape.Ring[] rings = new Shape.Ring[n];
		for (int i = 0; i < n; i++) {
			int a = Math.max(0, i - 1);
			int b = Math.min(n - 1, i + 1);
			float tx = xy[2 * b] - xy[2 * a];
			float ty = xy[2 * b + 1] - xy[2 * a + 1];
			float taper = 0.55f + 0.45f * (float) Math.sin(Math.PI * Math.min(1, (i + 0.5f) / n * 1.15f));
			rings[i] = new Shape.Ring(xy[2 * i], xy[2 * i + 1], 0, tx, ty, 0, r * taper, rz * taper, new float[] {-ty, tx, 0});
		}
		m.loft(rings, 10, 0.2f, material, Shape.DOME, Shape.DOME, true);
	}

	private static float[] rect(float x0, float y0, float x1, float y1) {
		return new float[] {x0, y0, x1, y0, x1, y1, x0, y1};
	}

	private static float[] mirrorX(float[] outline) {
		float[] out = new float[outline.length];
		for (int i = 0; i < outline.length; i += 2) {
			out[i] = -outline[outline.length - 2 - i];
			out[i + 1] = outline[outline.length - 1 - i];
		}
		return out;
	}

	/** A crescent karambit: the blade curls over like a waning moon, with a round hole near its root. */
	private static void eclipse(Shape m) {
		Shape.Blade b = blade(30, t -> {
			double angle = Math.toRadians(196 - 168 * t);
			float cx = 3.0f;
			float cy = 0.9f;
			float spine = 4.6f + 0.2f * t;
			float width = 1.65f * (1 - (float) Math.pow(t, 1.4f)) + 0.03f;
			float edge = spine - width;
			return new float[] {cx + (float) Math.cos(angle) * spine, cy + (float) Math.sin(angle) * spine, cx + (float) Math.cos(angle) * edge,
					cy + (float) Math.sin(angle) * edge, 0.28f - 0.18f * t};
		});
		b.grind = 0.45f;
		b.swedgeFrom = 0.7f;
		b.swedgeWidth = 0.3f;
		m.blade(b);
		float[] scale = {-1.2f, 1.15f, 0.55f, 1.15f, 0.95f, 0.4f, 0.8f, -0.4f, 1.05f, -1.1f, 0.72f, -1.7f, 1.0f, -2.4f, 0.68f, -3.0f, 0.92f, -3.7f,
				0.7f, -4.5f, 0.5f, -4.9f, -0.5f, -4.9f, -0.9f, -3.9f, -1.05f, -2.2f, -1.15f, -0.4f};
		m.plate(scale, -0.5f, 0.5f, 0.24f, KnifeShading.POLYMER);
		m.plate(new float[] {-0.18f, 0.6f, 0.18f, 0.6f, 0.18f, -4.3f, -0.18f, -4.3f}, -0.53f, 0.53f, 0.06f, KnifeShading.HANDLE_PAINT);
		m.plate(new float[] {-0.25f, 1.25f, 0.35f, 1.25f, 0.5f, 0.6f, -0.45f, 0.6f}, -0.54f, 0.54f, 0.12f, KnifeShading.HARDWARE);
		m.pin(0.0f, -0.4f, -0.56f, 0.56f, 0.2f, KnifeShading.HARDWARE);
		m.pin(-0.05f, -3.4f, -0.56f, 0.56f, 0.2f, KnifeShading.HARDWARE);
		m.torus(0, -5.95f, 0, 1.2f, 0.38f, 0.44f, 26, 10, KnifeShading.RING);
		m.torus(0, -5.95f, 0, 1.2f, 0.12f, 0.5f, 26, 6, KnifeShading.EDGE);
	}

	/** A double-edged dagger with the Eventide trident's three prongs as its guard and a riptide ring for a pommel. */
	private static void riptide(Shape m) {
		Shape.Blade b = blade(48, symmetric(1.3f, 11.8f, 0.95f, 0.26f, 0.1f, t -> t < 0.62f ? 1 - 0.12f * t : (1 - 0.12f * 0.62f)
				* (1 - (float) Math.pow((t - 0.62f) / 0.38f, 1.6f))));
		b.doubleEdged = true;
		b.grind = 0.36f;
		b.fullerFrom = 0.05f;
		b.fullerTo = 0.6f;
		b.fullerCentre = 0.5f;
		b.fullerWidth = 0.18f;
		b.fullerDepth = 0.09f;
		m.blade(b);
		m.plate(new float[] {-1.25f, 0.6f, 1.25f, 0.6f, 1.05f, 1.35f, -1.05f, 1.35f}, -0.4f, 0.4f, 0.16f, KnifeShading.GUARD);
		float[] tine = {1.05f, 0.62f, 1.9f, 0.7f, 2.35f, 1.35f, 2.3f, 2.9f, 1.95f, 3.5f, 1.78f, 2.7f, 1.62f, 1.6f, 1.15f, 1.35f};
		m.plate(tine, -0.34f, 0.34f, 0.14f, KnifeShading.GUARD);
		m.plate(mirrorX(tine), -0.34f, 0.34f, 0.14f, KnifeShading.GUARD);
		m.plate(new float[] {-0.3f, 1.3f, 0.3f, 1.3f, 0.0f, 2.25f}, -0.42f, 0.42f, 0.1f, KnifeShading.GUARD);
		grip(m, 0.62f, -5.2f, 0.78f, 0.6f, 0.4f, 0.42f, 0.06f, KnifeShading.CORD, Shape.OPEN);
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, -5.15f, 0, 0.84f, 0.66f), Shape.Ring.along(0, -5.55f, 0, 0.7f, 0.55f)}, 16, 0.3f,
				KnifeShading.HARDWARE, Shape.OPEN, Shape.CAP, false);
		m.torus(0, -6.35f, 0, 0.78f, 0.22f, 0.26f, 22, 8, KnifeShading.RING);
		m.torus(0, -6.35f, 0, 0.42f, 0.1f, 0.3f, 16, 6, KnifeShading.RING);
	}

	/** An angular tanto: a straight spine to a high point, the edge breaking up to it at a sharp corner. */
	private static void nightfall(Shape m) {
		Shape.Blade b = blade(56, t -> {
			float y = 1.2f + 9.8f * t;
			float sx = -1.0f + 0.12f * t * t;
			float tip = -0.82f;
			float ex = t <= 0.8f ? 1.32f - 0.08f * t : 1.32f - 0.064f + (tip - 1.256f) * (float) Math.pow((t - 0.8f) / 0.2f, 0.9f);
			if (t >= 0.999f) sx = tip;
			return new float[] {sx, y, ex, y, 0.34f - 0.14f * t};
		});
		b.grind = 0.52f;
		b.fullerFrom = 0.05f;
		b.fullerTo = 0.66f;
		b.fullerCentre = 0.2f;
		b.fullerWidth = 0.11f;
		b.fullerDepth = 0.09f;
		m.blade(b);
		m.plate(new float[] {-1.5f, 0.55f, 1.8f, 0.55f, 2.0f, 0.9f, 1.7f, 1.25f, -1.4f, 1.25f, -1.65f, 0.9f}, -0.42f, 0.42f, 0.12f, KnifeShading.GUARD);
		float[] scale = {-0.95f, 0.6f, 0.95f, 0.6f, 1.05f, -0.6f, 0.85f, -1.3f, 1.0f, -2.4f, 0.9f, -4.4f, 0.55f, -5.3f, -0.7f, -5.3f, -1.0f, -4.4f,
				-1.05f, -1.2f};
		m.plate(scale, -0.62f, 0.62f, 0.2f, KnifeShading.POLYMER);
		m.plate(new float[] {-0.22f, -0.5f, 0.22f, -0.5f, 0.22f, -4.6f, -0.22f, -4.6f}, -0.65f, 0.65f, 0.05f, KnifeShading.HANDLE_PAINT);
		m.pin(0.0f, -0.2f, -0.66f, 0.66f, 0.18f, KnifeShading.HARDWARE);
		m.pin(0.0f, -2.4f, -0.66f, 0.66f, 0.18f, KnifeShading.HARDWARE);
		m.pin(0.0f, -4.8f, -0.66f, 0.66f, 0.18f, KnifeShading.HARDWARE);
		m.torus(0, -5.75f, 0, 0.42f, 0.13f, 0.16f, 14, 6, KnifeShading.HARDWARE);
	}

	/** The flipper's handle: two scales on a liner with an inlay down the middle, the pivot at the top and a flipper tab. */
	private static void vesperHandle(Shape m) {
		float length = 5.8f;
		float half = 0.56f;
		float[] scale = {-0.95f, 0.9f, 0.8f, 0.9f, 0.95f, -0.4f, 0.85f, -length * 0.55f, 0.95f, -length + 0.6f, 0.55f, -length, -0.6f, -length, -0.95f,
				-length + 0.8f, -1.05f, -length * 0.4f};
		m.plate(scale, 0.16f, half, 0.16f, KnifeShading.POLYMER);
		m.plate(scale, -half, -0.16f, 0.16f, KnifeShading.POLYMER);
		float[] inlay = {-0.18f, 0.1f, 0.18f, 0.1f, 0.18f, -length + 1.0f, -0.18f, -length + 1.0f};
		m.plate(inlay, half - 0.02f, half + 0.04f, 0.04f, KnifeShading.HANDLE_PAINT);
		m.plate(inlay, -half - 0.04f, -half + 0.02f, 0.04f, KnifeShading.HANDLE_PAINT);
		m.plate(new float[] {-0.85f, 0.75f, 0.7f, 0.75f, 0.85f, -length + 0.5f, -0.85f, -length + 0.4f}, -0.18f, 0.18f, 0.05f, KnifeShading.HARDWARE);
		m.plate(new float[] {-1.05f, 0.95f, -0.35f, 0.95f, -0.45f, 1.85f, -0.95f, 1.75f}, -0.14f, 0.14f, 0.1f, KnifeShading.HARDWARE);
		m.pin(-0.25f, 0.45f, -half - 0.06f, half + 0.06f, 0.3f, KnifeShading.HARDWARE);
		m.pin(-0.1f, -length + 0.6f, -half - 0.04f, half + 0.04f, 0.18f, KnifeShading.HARDWARE);
	}

	private static Shape vesperBlade() {
		Shape m = new Shape();
		Profile shape = straight(0.2f, 9.2f, -0.82f, 0.95f, 0.05f, 0.56f, false, 0.5f, 0.22f, 0.09f);
		Shape.Blade b = blade(40, teeth(shape, 0.03f, 0.13f, 5, 0.1f));
		b.grind = 0.5f;
		b.swedgeFrom = 0.6f;
		b.swedgeWidth = 0.32f;
		m.blade(b);
		return m;
	}

	private static void wraithBlade(Shape m) {
		m.plate(rect(-0.75f, -0.15f, 0.75f, 1.25f), -0.17f, 0.17f, 0.08f, KnifeShading.BLADE);
		Shape.Blade b = blade(36, straight(1.2f, 9.6f, -0.62f, 0.82f, -0.18f, 0.62f, true, 0.62f, 0.17f, 0.08f));
		b.grind = 0.5f;
		b.swedgeFrom = 0.62f;
		b.fullerFrom = 0.08f;
		b.fullerTo = 0.5f;
		b.fullerCentre = 0.35f;
		b.fullerWidth = 0.14f;
		b.fullerDepth = 0.05f;
		m.blade(b);
	}

	/** One balisong handle: two rails with cross bars, hanging from its pin, on one side of the blade ({@code side} -1 or 1). */
	private static Shape wraithHandle(int side) {
		Shape m = new Shape();
		float z0 = side < 0 ? -0.66f : 0.2f;
		float z1 = side < 0 ? -0.2f : 0.66f;
		m.plate(new float[] {-0.6f, 0.45f, -0.3f, 0.45f, -0.32f, -7.5f, -0.62f, -7.5f}, z0, z1, 0.1f, KnifeShading.HANDLE_PAINT);
		m.plate(new float[] {0.3f, 0.45f, 0.6f, 0.45f, 0.62f, -7.5f, 0.32f, -7.5f}, z0, z1, 0.1f, KnifeShading.HANDLE_PAINT);
		for (float y : new float[] {0.15f, -2.6f, -5.0f, -7.25f}) {
			m.plate(rect(-0.4f, y - 0.22f, 0.4f, y + 0.22f), z0 + 0.04f, z1 - 0.04f, 0.06f, KnifeShading.HARDWARE);
		}
		m.pin(0, 0, z0 - 0.04f, z1 + 0.04f, 0.2f, KnifeShading.HARDWARE);
		m.pin(0, -7.0f, z0 - 0.04f, z1 + 0.04f, 0.16f, KnifeShading.HARDWARE);
		if (side > 0) m.plate(rect(-0.55f, -8.2f, 0.55f, -7.45f), -0.66f, 0.66f, 0.12f, KnifeShading.RING);
		return m;
	}

	/** A push dagger: a T handle across the palm, a leaf blade standing out between the fingers, and a halo round its root. */
	private static void halo(Shape m) {
		Shape.Blade b = blade(30, symmetric(0.2f, 5.6f, 0.82f, 0.26f, 0.08f, t -> (float) Math.pow(Math.sin(Math.PI * (0.12f + 0.88f * t)), 0.7)
				* (1 - 0.15f * t)));
		b.doubleEdged = true;
		b.grind = 0.42f;
		m.blade(b);
		m.loft(new Shape.Ring[] {Shape.Ring.facing(-2.4f, -0.45f, 0, 1, 0, 0, 0.55f, 0.48f), Shape.Ring.facing(-1.0f, -0.6f, 0, 1, 0, 0, 0.62f, 0.52f),
				Shape.Ring.facing(1.0f, -0.6f, 0, 1, 0, 0, 0.62f, 0.52f), Shape.Ring.facing(2.4f, -0.45f, 0, 1, 0, 0, 0.55f, 0.48f)}, 14, 0.35f,
				KnifeShading.RUBBER, Shape.DOME, Shape.DOME, false);
		m.plate(new float[] {-0.85f, -0.1f, 0.85f, -0.1f, 0.75f, 0.35f, -0.75f, 0.35f}, -0.3f, 0.3f, 0.1f, KnifeShading.HARDWARE);
		m.torus(0, 1.5f, 0, 1.45f, 0.13f, 0.2f, 30, 8, KnifeShading.RING);
	}

	/** A long stiletto: a diamond-section blade, crescent quillons and a jewel at the pommel. */
	private static void nocturne(Shape m) {
		Shape.Blade b = blade(40, symmetric(1.0f, 11.6f, 0.64f, 0.3f, 0.12f, t -> (float) Math.pow(1 - t, 0.55)));
		b.doubleEdged = true;
		b.grind = 0.5f;
		m.blade(b);
		float[] quillons = new float[2 * 15];
		for (int i = 0; i < 15; i++) {
			float u = i / 14f * 2 - 1;
			float x = u * 2.7f;
			float y = 0.65f + 0.22f * u * u + 0.55f * (float) Math.pow(Math.abs(u), 6);
			quillons[2 * i] = x;
			quillons[2 * i + 1] = y;
		}
		curve(m, quillons, 0.26f, 0.3f, KnifeShading.GUARD);
		m.plate(new float[] {-0.7f, 0.45f, 0.7f, 0.45f, 0.5f, 1.15f, -0.5f, 1.15f}, -0.4f, 0.4f, 0.12f, KnifeShading.GUARD);
		grip(m, 0.5f, -4.4f, 0.55f, 0.55f, 0.1f, 0.38f, 0.1f, KnifeShading.CORD, Shape.OPEN);
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, -4.35f, 0, 0.62f, 0.62f), Shape.Ring.along(0, -4.65f, 0, 0.5f, 0.5f)}, 14, 0, KnifeShading.GUARD,
				Shape.OPEN, Shape.OPEN, false);
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, -4.6f, 0, 0.42f, 0.42f), Shape.Ring.along(0, -5.0f, 0, 0.68f, 0.68f),
				Shape.Ring.along(0, -5.4f, 0, 0.5f, 0.5f)}, 8, 0, KnifeShading.RING, Shape.DOME, Shape.DOME, false);
	}

	/** A curved short sword with swept-back wings for a guard and a long wrapped grip. */
	private static void seraph(Shape m) {
		Shape.Blade b = blade(52, t -> {
			float y = 1.3f + 12.4f * t;
			float bend = -1.0f * t * t;
			float tip = t > 0.88f ? (t - 0.88f) / 0.12f : 0;
			float sx = -0.52f + bend + 0.62f * tip * tip;
			float ex = 0.6f + bend - 0.95f * (float) Math.pow(tip, 0.7);
			return new float[] {sx, y, ex, y, 0.22f - 0.1f * t};
		});
		b.grind = 0.62f;
		m.blade(b);
		float[] wing = {0.55f, 0.35f, 1.6f, 0.15f, 2.8f, -0.35f, 3.35f, -0.05f, 3.0f, 0.55f, 2.2f, 1.0f, 1.2f, 1.3f, 0.55f, 1.3f};
		m.plate(wing, -0.26f, 0.26f, 0.1f, KnifeShading.GUARD);
		m.plate(mirrorX(wing), -0.26f, 0.26f, 0.1f, KnifeShading.GUARD);
		m.plate(rect(-0.62f, 0.3f, 0.68f, 1.35f), -0.32f, 0.32f, 0.08f, KnifeShading.HARDWARE);
		grip(m, 0.3f, -6.3f, 0.62f, 0.48f, 0.35f, 0.48f, 0, KnifeShading.CORD, Shape.OPEN);
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, -6.25f, 0, 0.68f, 0.52f), Shape.Ring.along(0, -6.8f, 0, 0.56f, 0.42f)}, 14, 0.35f,
				KnifeShading.RING, Shape.OPEN, Shape.DOME, false);
	}

	/** A sickle on a banded haft. */
	private static void reaper(Shape m) {
		Shape.Ring[] pole = new Shape.Ring[6];
		for (int i = 0; i < pole.length; i++) {
			float f = i / (float) (pole.length - 1);
			pole[i] = Shape.Ring.along(0.12f * (float) Math.sin(f * Math.PI), -6.4f + 10.0f * f, 0, 0.42f, 0.42f);
		}
		m.loft(pole, 12, 0, KnifeShading.POLYMER, Shape.DOME, Shape.DOME, false);
		for (float y : new float[] {-5.6f, -1.0f, 2.4f}) {
			m.loft(new Shape.Ring[] {Shape.Ring.along(0, y, 0, 0.5f, 0.5f), Shape.Ring.along(0, y + 0.55f, 0, 0.5f, 0.5f)}, 12, 0, KnifeShading.HARDWARE,
					Shape.CAP, Shape.CAP, false);
		}
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, -6.3f, 0, 0.35f, 0.35f), Shape.Ring.along(0, -7.3f, 0, 0.02f, 0.02f)}, 10, 0, KnifeShading.RING,
				Shape.OPEN, Shape.OPEN, false);
		Shape.Blade b = blade(32, t -> {
			double angle = Math.toRadians(128 - 116 * t);
			float cx = 4.2f;
			float cy = -2.2f;
			float rs = 6.2f;
			float width = 1.65f * (1 - (float) Math.pow(t, 1.3)) + 0.02f;
			return new float[] {cx + (float) Math.cos(angle) * rs, cy + (float) Math.sin(angle) * rs, cx + (float) Math.cos(angle) * (rs - width),
					cy + (float) Math.sin(angle) * (rs - width), 0.24f - 0.15f * t};
		});
		b.grind = 0.45f;
		m.blade(b);
	}

	/** A skeleton knife: a straight edge with the spine sweeping down to meet it, an open frame for a handle and a finger ring. */
	private static void specter(Shape m) {
		Shape.Blade b = blade(40, t -> {
			float y = 0.6f + 8.6f * t;
			float ex = 1.05f - 0.05f * t;
			float sx = t < 0.3f ? -0.9f : -0.9f + (ex + 0.9f) * (float) Math.pow((t - 0.3f) / 0.7f, 1.35f);
			return new float[] {sx, y, ex, y, 0.26f - 0.15f * t};
		});
		b.grind = 0.5f;
		m.blade(b);
		Shape.Ring[] frame = new Shape.Ring[29];
		for (int i = 0; i < frame.length; i++) {
			double a = 2 * Math.PI * i / (frame.length - 1);
			float x = (float) Math.sin(a) * 0.85f;
			float y = -2.0f + (float) Math.cos(a) * 2.5f;
			float tx = (float) Math.cos(a) * 0.85f;
			float ty = -(float) Math.sin(a) * 2.5f;
			frame[i] = new Shape.Ring(x, y, 0, tx, ty, 0, 0.26f, 0.24f, new float[] {(float) Math.sin(a), (float) Math.cos(a) * 0.3f, 0});
		}
		m.loft(frame, 10, 0.5f, KnifeShading.BLADE, Shape.OPEN, Shape.OPEN, true);
		m.plate(new float[] {-0.85f, 0.7f, 1.05f, 0.7f, 0.85f, 0.1f, -0.85f, 0.1f}, -0.24f, 0.24f, 0.08f, KnifeShading.BLADE);
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, -0.4f, 0, 0.16f, 0.12f), Shape.Ring.along(0, -3.6f, 0, 0.16f, 0.12f)}, 8, 0.3f,
				KnifeShading.HANDLE_PAINT, Shape.CAP, Shape.CAP, false);
		m.torus(0, -5.35f, 0, 0.85f, 0.26f, 0.26f, 22, 8, KnifeShading.RING);
	}

	/** A hooked blade: a gut hook cut into the spine, teeth near the root, and a grip that curls like a beak. */
	private static void corvid(Shape m) {
		Profile base = straight(1.0f, 9.4f, -0.88f, 1.1f, 0.25f, 0.6f, false, 0.52f, 0.26f, 0.1f);
		Profile hooked = t -> {
			float[] s = teeth(base, 0.08f, 0.3f, 6, 0.18f).at(t);
			if (t > 0.5f && t < 0.66f) {
				float u = (t - 0.5f) / 0.16f;
				s[0] += 0.95f * (float) Math.sin(Math.PI * Math.pow(u, 0.7));
			}
			return s;
		};
		Shape.Blade b = blade(48, hooked);
		b.grind = 0.5f;
		m.blade(b);
		m.plate(new float[] {-1.25f, 0.45f, 1.6f, 0.45f, 1.7f, 1.05f, -1.3f, 1.05f}, -0.38f, 0.38f, 0.14f, KnifeShading.GUARD);
		Shape.Ring[] handle = new Shape.Ring[14];
		for (int i = 0; i < handle.length; i++) {
			float f = i / (float) (handle.length - 1);
			float x = 0.9f * f * f * f;
			float y = 0.5f - 5.1f * f;
			float r = f > 0.85f ? 0.62f - (f - 0.85f) * 2.2f : 0.78f + 0.06f * (float) Math.sin(Math.PI * f);
			handle[i] = new Shape.Ring(x, y, 0, 2.7f * f * f, -5.1f, 0, Math.max(0.2f, r), Math.max(0.16f, r * 0.76f), new float[] {1, 0, 0});
		}
		m.loft(handle, 16, 0.45f, KnifeShading.POLYMER, Shape.CAP, Shape.DOME, false);
		m.pin(0.0f, -0.8f, -0.66f, 0.66f, 0.17f, KnifeShading.HARDWARE);
		m.pin(0.1f, -3.0f, -0.66f, 0.66f, 0.17f, KnifeShading.HARDWARE);
	}

	/** The energy blade's hilt: a ringed grip, a forked emitter at the top and a lit core showing between its rings. */
	private static void lumenHilt(Shape m) {
		Shape.Ring[] hilt = new Shape.Ring[16];
		for (int i = 0; i < hilt.length; i++) {
			float f = i / (float) (hilt.length - 1);
			float y = 1.2f - 6.4f * f;
			float r = f < 0.1f ? 0.9f : f < 0.18f ? 0.72f : f > 0.92f ? 0.88f : (i % 2 == 0 ? 0.72f : 0.62f);
			hilt[i] = Shape.Ring.along(0, y, 0, r, r);
		}
		m.loft(hilt, 16, 0, KnifeShading.HARDWARE, Shape.CAP, Shape.DOME, false);
		for (float y : new float[] {-1.4f, -2.6f, -3.8f}) {
			m.loft(new Shape.Ring[] {Shape.Ring.along(0, y, 0, 0.66f, 0.66f), Shape.Ring.along(0, y - 0.25f, 0, 0.66f, 0.66f)}, 16, 0, KnifeShading.ENERGY,
					Shape.OPEN, Shape.OPEN, false);
		}
		float[] prong = {0.55f, 1.0f, 1.05f, 1.15f, 1.15f, 2.0f, 0.95f, 2.3f, 0.75f, 1.6f, 0.5f, 1.4f};
		m.plate(prong, -0.22f, 0.22f, 0.08f, KnifeShading.GUARD);
		m.plate(mirrorX(prong), -0.22f, 0.22f, 0.08f, KnifeShading.GUARD);
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, -4.4f, 0, 0.9f, 0.9f), Shape.Ring.along(0, -4.7f, 0, 0.9f, 0.9f)}, 16, 0, KnifeShading.RING,
				Shape.OPEN, Shape.OPEN, false);
	}

	/** The energy blade, base at the origin, 9.6 pixels long at full length; scaled along its length as it ignites. */
	private static Shape lumenBlade() {
		Shape m = new Shape();
		Shape.Ring[] rings = new Shape.Ring[9];
		for (int i = 0; i < rings.length; i++) {
			float f = i / (float) (rings.length - 1);
			float r = 0.34f * (1 - 0.25f * f * f);
			rings[i] = Shape.Ring.along(0, f * 9.2f, 0, r, r);
		}
		m.loft(rings, 14, 0, KnifeShading.ENERGY, Shape.CAP, Shape.DOME, false);
		return m;
	}

	private void clips() {
		if (draw != null) return;
		float reach = this == SERAPH || this == REAPER ? 1.3f : this == LUMEN || this == NOCTURNE ? 1.15f : this == ECLIPSE ? 0.9f : 1f;
		slashes = new Clip[] {
				Clip.of("slash right to left", 430)
						.key(0)
						.key(0.14f, Clip.X, 0.06f, Clip.Y, 0.05f, Clip.YAW, -14, Clip.PITCH, 8, Clip.ROLL, -10, Clip.K_ROLL, 8)
						.key(0.34f, Clip.X, -0.22f * reach, Clip.Y, 0.08f, Clip.Z, -0.08f, Clip.YAW, 38 * reach, Clip.PITCH, -6, Clip.SWEEP, 12,
								Clip.ROLL, 22, Clip.K_ROLL, -22)
						.key(0.48f, Clip.X, -0.3f * reach, Clip.Y, 0.02f, Clip.Z, -0.06f, Clip.YAW, 50 * reach, Clip.PITCH, -10, Clip.SWEEP, 16,
								Clip.ROLL, 28, Clip.K_ROLL, -26)
						.key(1),
				Clip.of("slash left to right", 430)
						.key(0)
						.key(0.16f, Clip.X, -0.2f, Clip.Y, 0.08f, Clip.YAW, 34, Clip.PITCH, 4, Clip.ROLL, 24, Clip.K_ROLL, -18)
						.key(0.34f, Clip.X, 0.02f, Clip.Y, 0.06f, Clip.Z, -0.08f, Clip.YAW, -12 * reach, Clip.PITCH, -6, Clip.SWEEP, -10,
								Clip.ROLL, -12, Clip.K_ROLL, 14)
						.key(0.48f, Clip.X, 0.1f * reach, Clip.Y, 0.0f, Clip.Z, -0.05f, Clip.YAW, -24 * reach, Clip.PITCH, -9, Clip.SWEEP, -16,
								Clip.ROLL, -18, Clip.K_ROLL, 18)
						.key(1)};
		use = Clip.of("heavy stab", 900)
				.key(0)
				.key(0.28f, Clip.Y, 0.08f, Clip.Z, 0.12f, Clip.PITCH, 18, Clip.YAW, 6, Clip.K_PITCH, 12)
				.key(0.34f, Clip.Y, 0.09f, Clip.Z, 0.13f, Clip.PITCH, 20, Clip.YAW, 7, Clip.K_PITCH, 14)
				.key(0.46f, Clip.X, -0.1f, Clip.Y, 0.06f, Clip.Z, -0.32f * reach, Clip.PITCH, -10, Clip.YAW, 14, Clip.K_PITCH, -18)
				.key(0.6f, Clip.X, -0.09f, Clip.Y, 0.05f, Clip.Z, -0.29f * reach, Clip.PITCH, -8, Clip.YAW, 13, Clip.K_PITCH, -16)
				.key(1);
		draw = KnifeClips.draw(this);
		inspect = KnifeClips.inspect(this);
	}

	/** The id used in saved settings: the name in lower case. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}
