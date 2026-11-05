package dev.eventide.client.knife;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Eventide's knives, each its own weapon built from {@link Shape} pieces (ground blades, lofted grips, plates,
 * pins and rings): dark steel with a luminous edge, in shapes made for Eventide. Every knife has its own draw,
 * inspect and slash animations ({@link Clip}) and its own signature finish, worn unless you pick another.
 * Shapes are made the first time a knife is drawn and kept. The order is the order the shader numbers them in
 * (knife_surface.glsl's BLADE_RANGE), so new knives go at the end. Old knife names (from before the redesign)
 * find their nearest new knife through {@link #byLabel}.
 */
public enum KnifeModel {
	ECLIPSE("Eclipse", "A claw-curved karambit with a finger ring and a rose engraved on its blade", 1.0f, 0, -5.95f, -4.6f, 0.95f, 0.5f,
			Family.CURVED, Finish.ENGRAVED),
	RIPTIDE("Riptide", "A double-edged dagger with a trident guard and a riptide ring", 0.9f, 0, -2.0f, -2.3f, 0.8f, 0.62f, Family.FIXED,
			Finish.MIDNIGHT),
	NIGHTFALL("Nightfall", "An angular tanto with cyan filigree, dark as the sky before the stars", 0.92f, 0, -1.6f, -2.4f, 0.95f, 0.62f,
			Family.FIXED, Finish.OBSIDIAN),
	VESPER("Vesper", "A slim flipper in rose gold that snaps open with a flick", 1.0f, -0.25f, 0.45f, -2.25f, 1.0f, 0.6f, Family.FOLDING,
			Finish.ROSE),
	WRAITH("Wraith", "A butterfly knife with a long dagger blade, a rose engraved near its point and open rail handles", 0.95f, -0.45f, 0.5f,
			-1.55f, 0.62f, 0.66f,
			Family.BUTTERFLY, Finish.ENGRAVED),
	HALO("Halo", "A leaf-bladed dagger with a halo for a guard", 0.95f, 0, -1.0f, -2.2f, 0.62f, 0.55f, Family.FIXED, Finish.AURORA),
	LUMEN("Lumen", "A blade of pure light from a ringed hilt", 0.88f, 0, -1.0f, -1.8f, 0.72f, 0.72f, Family.FIXED, Finish.CHROME),
	NOCTURNE("Nocturne", "A long stiletto with a crescent guard and a jewel for a pommel", 0.9f, 0, -1.0f, -2.0f, 0.6f, 0.6f, Family.FIXED,
			Finish.NEBULA),
	SERAPH("Seraph", "A katana: a long curved blade with a frosted temper line, a round guard and a wrapped grip", 0.92f, 0, -1.8f, -1.9f, 0.55f,
			0.44f, Family.FIXED, Finish.FROST),
	REAPER("Reaper", "A scythe: a great crescent blade on a long banded pole", 0.9f, 0, -1.0f, -1.2f, 0.4f, 0.4f, Family.FIXED, Finish.VOID),
	SPECTER("Specter", "A skeleton knife in a glowing crystal lattice, with a ring", 1.0f, 0, -1.5f, -1.9f, 0.85f, 0.3f, Family.FIXED,
			Finish.CRYSTAL),
	CORVID("Corvid", "A hooked blade with a toothed spine and a beaked grip", 0.98f, 0, -1.5f, -1.9f, 0.82f, 0.6f, Family.FIXED, Finish.MIDNIGHT),
	PRISM("Prism", "A broad onyx dagger set with a column of glowing crystals", 1.0f, 0, -1.5f, -2.2f, 0.8f, 0.6f, Family.FIXED, Finish.ONYX);

	/** How a knife is built and handled: fixed blades, folders whose blade swings out, and the odd ones. */
	enum Family {
		FIXED,
		FOLDING,
		BUTTERFLY,
		CURVED
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
	/** The finish this knife wears when the Finish setting is Signature. */
	public final Finish signature;
	private List<Part> parts;
	private float[][] reach;
	private Clip draw;
	private Clip inspect;
	private Clip[] slashes;
	private Clip use;

	KnifeModel(String label, String description, float scale, float spinX, float spinY, float grip, float handleA, float handleB, Family family,
			Finish signature) {
		this.label = label;
		this.description = description;
		this.scale = scale;
		this.spinX = spinX;
		this.spinY = spinY;
		this.grip = grip;
		this.handleA = handleA;
		this.handleB = handleB;
		this.family = family;
		this.signature = signature;
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

	/** The corners of the knife's outline in its own pixels, for keeping all of it in front of the camera. */
	float[][] reach() {
		if (reach == null) {
			float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
			for (Part part : parts()) part.mesh().bounds(box);
			reach = new float[][] {{box[0], box[1]}, {box[2], box[1]}, {box[0], box[3]}, {box[2], box[3]}};
		}
		return reach;
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
			case PRISM -> prism(body);
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

	/**
	 * A karambit: the blade rises from the handle and curls forward like a claw, the edge on its inside, with a
	 * false edge along the spine near the tip; a curved grip below with a finger ring at its end.
	 */
	private static void eclipse(Shape m) {
		Shape.Blade b = blade(56, t -> {
			double angle = Math.toRadians(180 - 124 * t);
			float cx = 2.2f;
			float cy = 1.2f;
			float cos = (float) Math.cos(angle);
			float sin = (float) Math.sin(angle);
			float width = 1.6f * (1 - (float) Math.pow(t, 1.5f)) + 0.03f;
			float sx = cx + cos * 3.4f;
			float sy = cy + sin * 5.0f;
			float nx = cos * 5.0f;
			float ny = sin * 3.4f;
			float nl = (float) Math.sqrt(nx * nx + ny * ny);
			return new float[] {sx, sy, sx - nx / nl * width, sy - ny / nl * width, 0.3f - 0.2f * t};
		});
		b.grind = 0.5f;
		b.swedgeFrom = 0.68f;
		b.swedgeWidth = 0.3f;
		b.flatColumns = 6;
		b.bevelColumns = 4;
		m.blade(b);
		float[] scale = new float[2 * 22];
		for (int i = 0; i < 11; i++) {
			float f = i / 10f;
			float y = 1.15f - 6.0f * f;
			float bend = 0.55f * f * f;
			float half = 0.95f - 0.25f * (float) Math.sin(Math.PI * f) * 0.6f - 0.2f * f;
			scale[2 * i] = -half + bend - 0.15f;
			scale[2 * i + 1] = y;
			scale[2 * (21 - i)] = half + bend - 0.15f;
			scale[2 * (21 - i) + 1] = y;
		}
		m.plate(scale, -0.5f, 0.5f, 0.24f, KnifeShading.POLYMER);
		m.plate(new float[] {-0.15f, 0.55f, 0.15f, 0.55f, 0.32f, -4.4f, 0.02f, -4.4f}, -0.53f, 0.53f, 0.06f, KnifeShading.HANDLE_PAINT);
		m.plate(new float[] {-1.2f, 1.45f, 0.5f, 1.45f, 0.65f, 0.75f, -1.1f, 0.75f}, -0.54f, 0.54f, 0.14f, KnifeShading.HARDWARE);
		m.pin(-0.1f, -0.3f, -0.56f, 0.56f, 0.2f, KnifeShading.HARDWARE);
		m.pin(0.15f, -3.3f, -0.56f, 0.56f, 0.2f, KnifeShading.HARDWARE);
		m.torus(0.4f, -5.95f, 0, 1.15f, 0.36f, 0.44f, 32, 12, KnifeShading.RING);
		m.torus(0.4f, -5.95f, 0, 1.15f, 0.1f, 0.5f, 32, 6, KnifeShading.EDGE);
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

	/**
	 * The balisong's blade, as in Eventide's reference: long and straight with a dagger point, a groove with
	 * bright rails up its middle to a rose engraved near the point, and at its root a tang that flares into
	 * latches either side, each with a glowing pivot pin.
	 */
	private static void wraithBlade(Shape m) {
		m.plate(new float[] {-0.95f, -0.2f, 0.95f, -0.2f, 0.95f, 0.55f, 1.35f, 0.75f, 1.35f, 1.55f, 0.95f, 1.75f, -0.95f, 1.75f, -1.35f, 1.55f,
				-1.35f, 0.75f, -0.95f, 0.55f}, -0.2f, 0.2f, 0.08f, KnifeShading.BLADE);
		m.pin(-0.8f, 1.15f, -0.27f, 0.27f, 0.2f, KnifeShading.GEM_RIM);
		m.pin(0.8f, 1.15f, -0.27f, 0.27f, 0.2f, KnifeShading.GEM_RIM);
		Shape.Blade b = blade(64, symmetric(1.7f, 13.0f, 0.92f, 0.2f, 0.1f, t -> t < 0.86f ? 1f : 1 - (t - 0.86f) / 0.14f));
		b.doubleEdged = true;
		b.grind = 0.24f;
		b.fullerFrom = 0.03f;
		b.fullerTo = 0.64f;
		b.fullerCentre = 0.5f;
		b.fullerWidth = 0.26f;
		b.fullerDepth = 0.06f;
		b.flatColumns = 10;
		b.bevelColumns = 3;
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

	/** A leaf-bladed dagger: the blade swells past the guard then tapers to its point, a halo ring for a guard and a ribbed grip. */
	private static void halo(Shape m) {
		Shape.Blade b = blade(48, symmetric(1.1f, 9.6f, 1.05f, 0.28f, 0.09f, t -> t < 0.3f ? 0.72f + 0.28f * (float) Math.sin(Math.PI / 2 * t / 0.3f)
				: (float) Math.cos(Math.PI / 2 * Math.pow((t - 0.3f) / 0.7f, 1.3))));
		b.doubleEdged = true;
		b.grind = 0.38f;
		b.flatColumns = 6;
		m.blade(b);
		m.torus(0, 0.85f, 0, 1.45f, 0.17f, 0.24f, 36, 10, KnifeShading.RING);
		m.plate(new float[] {-1.35f, 0.62f, 1.35f, 0.62f, 1.1f, 1.15f, -1.1f, 1.15f}, -0.3f, 0.3f, 0.12f, KnifeShading.GUARD);
		grip(m, 0.65f, -4.6f, 0.62f, 0.5f, 0.3f, 0.5f, 0.05f, KnifeShading.RUBBER, Shape.OPEN);
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, -4.55f, 0, 0.7f, 0.56f), Shape.Ring.along(0, -5.0f, 0, 0.58f, 0.46f)}, 16, 0.3f,
				KnifeShading.RING, Shape.OPEN, Shape.DOME, false);
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

	/**
	 * A katana: a long, slim blade that curves back towards its spine, its ridge line near the edge and an angled
	 * point; a collar, a round guard and a long wrapped grip with a cap at its end.
	 */
	private static void seraph(Shape m) {
		Shape.Blade b = blade(72, t -> {
			float y = 1.45f + 14.6f * t;
			float bend = -1.5f * t * t;
			float tip = t > 0.9f ? (t - 0.9f) / 0.1f : 0;
			float sx = -0.4f + bend + 0.08f * tip;
			float ex = 0.42f + bend - 0.78f * (float) Math.pow(tip, 0.75);
			return new float[] {sx, y, ex, y, 0.2f - 0.09f * t};
		});
		b.grind = 0.68f;
		b.flatColumns = 6;
		b.bevelColumns = 4;
		m.blade(b);
		m.plate(new float[] {-0.5f, 0.95f, 0.5f, 0.95f, 0.47f, 1.55f, -0.47f, 1.55f}, -0.26f, 0.26f, 0.08f, KnifeShading.GUARD);
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, 0.68f, 0, 1.4f, 1.12f), Shape.Ring.along(0, 0.95f, 0, 1.4f, 1.12f)}, 28, 0.25f,
				KnifeShading.HARDWARE, Shape.CAP, Shape.CAP, false);
		grip(m, 0.7f, -6.9f, 0.52f, 0.4f, 0.3f, 0.62f, 0.04f, KnifeShading.CORD, Shape.OPEN);
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, -6.85f, 0, 0.58f, 0.45f), Shape.Ring.along(0, -7.35f, 0, 0.5f, 0.39f)}, 16, 0.3f,
				KnifeShading.RING, Shape.OPEN, Shape.DOME, false);
	}

	/**
	 * A scythe: a long pole with an easy bend, banded and capped, and at its head a great crescent blade
	 * sweeping out and down, its edge on the inside of the curve.
	 */
	private static void reaper(Shape m) {
		Shape.Ring[] pole = new Shape.Ring[12];
		for (int i = 0; i < pole.length; i++) {
			float f = i / (float) (pole.length - 1);
			pole[i] = Shape.Ring.along(0.35f * (float) Math.sin(f * Math.PI) - 0.15f * f, -8.0f + 15.0f * f, 0, 0.4f, 0.4f);
		}
		m.loft(pole, 14, 0, KnifeShading.POLYMER, Shape.DOME, Shape.OPEN, false);
		for (float y : new float[] {-7.2f, -2.4f, 2.6f}) {
			float x = 0.35f * (float) Math.sin((y + 8.0f) / 15.0f * Math.PI) - 0.15f * (y + 8.0f) / 15.0f;
			m.loft(new Shape.Ring[] {Shape.Ring.along(x, y, 0, 0.48f, 0.48f), Shape.Ring.along(x, y + 0.5f, 0, 0.48f, 0.48f)}, 14, 0,
					KnifeShading.HARDWARE, Shape.CAP, Shape.CAP, false);
		}
		m.loft(new Shape.Ring[] {Shape.Ring.along(-0.15f, 6.4f, 0, 0.55f, 0.55f), Shape.Ring.along(-0.15f, 7.6f, 0, 0.55f, 0.55f)}, 14, 0.4f,
				KnifeShading.HARDWARE, Shape.CAP, Shape.DOME, false);
		Shape.Blade b = blade(64, t -> {
			double angle = Math.toRadians(42 + 122 * t);
			float cx = -5.3f;
			float cy = 1.8f;
			float rs = 6.9f;
			float width = 2.1f * (1 - (float) Math.pow(t, 1.25)) + 0.03f;
			return new float[] {cx + (float) Math.cos(angle) * rs, cy + (float) Math.sin(angle) * rs, cx + (float) Math.cos(angle) * (rs - width),
					cy + (float) Math.sin(angle) * (rs - width), 0.26f - 0.16f * t};
		});
		b.grind = 0.5f;
		b.flatColumns = 6;
		b.bevelColumns = 4;
		m.blade(b);
		m.plate(new float[] {0.6f, 6.3f, -1.1f, 6.6f, -1.4f, 7.7f, 0.35f, 7.8f}, -0.32f, 0.32f, 0.1f, KnifeShading.GUARD);
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

	/**
	 * A broad onyx dagger like the one in Eventide's reference: a leaf blade, widest a third of the way up, set
	 * down its middle with a column of crystals in glowing frames and a glowing stud either side of each, on a
	 * short guard and a wrapped grip.
	 */
	private static void prism(Shape m) {
		float y0 = 1.1f;
		float y1 = 10.6f;
		float h0 = 0.3f;
		float h1 = 0.1f;
		java.util.function.Function<Float, Float> taper = t -> t < 0.32f ? 0.74f + 0.26f * (float) Math.sin(Math.PI / 2 * t / 0.32f)
				: (float) Math.cos(Math.PI / 2 * Math.pow((t - 0.32f) / 0.68f, 1.2));
		Shape.Blade b = blade(56, symmetric(y0, y1, 1.5f, h0, h1, taper));
		b.doubleEdged = true;
		b.grind = 0.3f;
		b.flatColumns = 6;
		m.blade(b);
		float[][] gems = {{2.7f, 0.66f, 0.42f}, {4.3f, 0.6f, 0.38f}, {5.8f, 0.54f, 0.33f}, {7.2f, 0.46f, 0.28f}, {8.4f, 0.36f, 0.22f}};
		for (float[] gem : gems) {
			float t = (gem[0] - y0) / (y1 - y0);
			float h = h0 + (h1 - h0) * t;
			float[] frame = diamond(0, gem[0], gem[2], gem[1]);
			float[] stone = diamond(0, gem[0], gem[2] - 0.12f, gem[1] - 0.15f);
			for (int side = -1; side <= 1; side += 2) {
				float in = side * (h - 0.03f);
				float out = side * (h + 0.05f);
				m.plate(frame, Math.min(in, out), Math.max(in, out), 0.03f, KnifeShading.GEM_RIM);
				float top = side * (h + 0.1f);
				m.plate(stone, Math.min(in, top), Math.max(in, top), 0.05f, KnifeShading.GEM);
			}
			float stud = 1.5f * taper.apply(t) * 0.55f;
			if (gem[0] < 8f) {
				m.pin(-stud, gem[0] + gem[1] * 0.6f, -h - 0.04f, h + 0.04f, 0.09f, KnifeShading.GEM_RIM);
				m.pin(stud, gem[0] + gem[1] * 0.6f, -h - 0.04f, h + 0.04f, 0.09f, KnifeShading.GEM_RIM);
			}
		}
		m.plate(new float[] {-1.55f, 0.55f, 1.55f, 0.55f, 1.3f, 1.2f, -1.3f, 1.2f}, -0.4f, 0.4f, 0.14f, KnifeShading.GUARD);
		m.plate(diamond(0, 0.88f, 0.32f, 0.3f), -0.43f, 0.43f, 0.06f, KnifeShading.GEM_RIM);
		grip(m, 0.6f, -4.7f, 0.62f, 0.5f, 0.3f, 0.45f, 0.06f, KnifeShading.CORD, Shape.OPEN);
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, -4.65f, 0, 0.7f, 0.56f), Shape.Ring.along(0, -5.15f, 0, 0.52f, 0.42f)}, 16, 0.3f,
				KnifeShading.GUARD, Shape.OPEN, Shape.DOME, false);
	}

	/** A diamond outline round (x, y), {@code hx} half wide and {@code hy} half tall. */
	private static float[] diamond(float x, float y, float hx, float hy) {
		return new float[] {x, y - hy, x + hx, y, x, y + hy, x - hx, y};
	}

	/** The energy blade's hilt:	/** The energy blade's hilt: a ringed grip, a forked emitter at the top and a lit core showing between its rings. */
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
