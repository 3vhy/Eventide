package dev.eventide.client.knife;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.eventide.client.compat.Compat;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.module.Modules;
import dev.eventide.client.ui.Widgets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;

/**
 * 3D skins: your player drawn as a whole 3D character instead of a flat skin, built from code and shaded by the
 * knife shader: the Grim Reaper (a skull in a deep hood, a cowl and a robe to the ground, his scythe and an
 * hourglass lantern), Timber (a tall round golden log with cartoon eyes and a grin, on stick limbs, with a bat),
 * Galaxy (a sleek body made of the night sky) and the Astronaut (a puffy white suit, a gold visor and a backpack).
 * Each limb is built round its joint in Minecraft's player model (pixels, Y down), so it moves with your own walk,
 * swing and sneak.
 */
public final class ModelSkins {
	public static final String[] SKINS = {"Grim Reaper", "Timber", "Galaxy", "Astronaut"};

	/** The parts of the player model a skin dresses. */
	private enum Limb {
		HEAD,
		BODY,
		RIGHT_ARM,
		LEFT_ARM,
		RIGHT_LEG,
		LEFT_LEG
	}

	private static final Map<String, EnumMap<Limb, Shape>> SHAPES = new HashMap<>();
	/** What a skin carries, built round the same joints: drawn when Hold their weapon is on. */
	private static final Map<String, EnumMap<Limb, Shape>> PROPS = new HashMap<>();
	private static Shape bat;

	private ModelSkins() {
	}

	/** Whether you're wearing a 3D skin. */
	public static boolean worn() {
		return Modules.MODEL_SKIN.enabled();
	}

	/** Whether your 3D skin holds its own weapon in place of what's in your hand. */
	public static boolean holdsProp() {
		return worn() && Modules.MODEL_SKIN_PROP.get() && !Modules.MODEL_SKIN_TYPE.is("Astronaut");
	}

	private static EnumMap<Limb, Shape> shapes(String skin) {
		return SHAPES.computeIfAbsent(skin, ModelSkins::build);
	}

	private static EnumMap<Limb, Shape> props(String skin) {
		return PROPS.computeIfAbsent(skin, name -> {
			EnumMap<Limb, Shape> out = new EnumMap<>(Limb.class);
			for (Limb limb : Limb.values()) out.put(limb, new Shape());
			if (name.equals("Grim Reaper")) reaperProps(out);
			return out;
		});
	}

	private static EnumMap<Limb, Shape> build(String skin) {
		EnumMap<Limb, Shape> out = new EnumMap<>(Limb.class);
		for (Limb limb : Limb.values()) out.put(limb, new Shape());
		switch (skin) {
			case "Timber" -> timber(out);
			case "Galaxy" -> galaxy(out);
			case "Astronaut" -> astronaut(out);
			default -> reaper(out);
		}
		return out;
	}

	/** A box with soft round edges, from (x0, y0, z0) to (x1, y1, z1). */
	private static void box(Shape m, float x0, float y0, float z0, float x1, float y1, float z1, float squareness, int material) {
		float cx = (x0 + x1) / 2;
		float cz = (z0 + z1) / 2;
		m.loft(new Shape.Ring[] {Shape.Ring.along(cx, y0, cz, (x1 - x0) / 2, (z1 - z0) / 2), Shape.Ring.along(cx, y1, cz, (x1 - x0) / 2, (z1 - z0) / 2)},
				20, squareness, material, Shape.CAP, Shape.CAP, false);
	}

	/** A thin rod from one point to another, rounded at both ends, its radius running from r0 to r1. */
	private static void twig(Shape m, float x0, float y0, float z0, float x1, float y1, float z1, float r0, float r1, int material) {
		float ax = x1 - x0;
		float ay = y1 - y0;
		float az = z1 - z0;
		m.loft(new Shape.Ring[] {new Shape.Ring(x0, y0, z0, ax, ay, az, r0, r0, null), new Shape.Ring(x1, y1, z1, ax, ay, az, r1, r1, null)}, 10, 0,
				material, Shape.DOME, Shape.DOME, false);
	}

	/** A ball. */
	private static void ball(Shape m, float x, float y, float z, float r, int material) {
		blob(m, x, y, z, r, r, r, material);
	}

	/** An ellipsoid with radii rx, ry and rz. */
	private static void blob(Shape m, float x, float y, float z, float rx, float ry, float rz, int material) {
		int n = 10;
		Shape.Ring[] rings = new Shape.Ring[n + 1];
		for (int i = 0; i <= n; i++) {
			double a = Math.PI * i / n;
			float k = Math.max(0.004f, (float) Math.sin(a));
			rings[i] = Shape.Ring.along(x, y - (float) Math.cos(a) * ry, z, rx * k, rz * k);
		}
		m.loft(rings, 18, 0, material, Shape.OPEN, Shape.OPEN, false);
	}

	/** A tube through points of {x, y, z, radius}, rounded at both ends. */
	private static void path(Shape m, float[][] points, int material) {
		Shape.Ring[] rings = new Shape.Ring[points.length];
		for (int i = 0; i < points.length; i++) {
			float[] a = points[Math.max(0, i - 1)];
			float[] c = points[Math.min(points.length - 1, i + 1)];
			rings[i] = new Shape.Ring(points[i][0], points[i][1], points[i][2], c[0] - a[0], c[1] - a[1], c[2] - a[2], points[i][3], points[i][3], null);
		}
		m.loft(rings, 10, 0, material, Shape.DOME, Shape.DOME, false);
	}

	/** A smooth body down Y through rings of {y, x radius, z radius, z centre}. */
	private static void column(Shape m, float x, float[][] rings, float squareness, int material, int top, int bottom) {
		Shape.Ring[] out = new Shape.Ring[rings.length];
		for (int i = 0; i < rings.length; i++) out[i] = Shape.Ring.along(x, rings[i][0], rings[i][3], rings[i][1], rings[i][2]);
		m.loft(out, 24, squareness, material, top, bottom, false);
	}

	/** The ring of {@code rings} (as {@link #column} takes them) at height y: {x radius, z radius, z centre}. */
	private static float[] ringAt(float[][] rings, float y) {
		if (y <= rings[0][0]) return new float[] {rings[0][1], rings[0][2], rings[0][3]};
		for (int i = 0; i + 1 < rings.length; i++) {
			if (y <= rings[i + 1][0]) {
				float t = (y - rings[i][0]) / (rings[i + 1][0] - rings[i][0]);
				return new float[] {rings[i][1] + (rings[i + 1][1] - rings[i][1]) * t, rings[i][2] + (rings[i + 1][2] - rings[i][2]) * t,
						rings[i][3] + (rings[i + 1][3] - rings[i][3]) * t};
			}
		}
		float[] last = rings[rings.length - 1];
		return new float[] {last[1], last[2], last[3]};
	}

	/** Where the front of a body made of {@code rings} is at (x, y): its z. */
	private static float front(float[][] rings, float x, float y) {
		float[] r = ringAt(rings, y);
		float k = Math.min(0.98f, Math.abs(x) / r[0]);
		return r[2] - r[1] * (float) Math.sqrt(1 - k * k);
	}

	/**
	 * A flat shape laid on the front of a body made of {@code rings}, turned to face out from it:
	 * {@code outline} (x, y pairs round (0, 0)) centred at (x, y), standing {@code lift} out of the surface and
	 * sinking {@code sink} into it.
	 */
	private static void decal(Shape m, float[][] rings, float[] outline, float x, float y, float lift, float sink, int material) {
		float[] r = ringAt(rings, y);
		float k = Math.min(0.98f, Math.abs(x) / r[0]);
		float z = r[2] - r[1] * (float) Math.sqrt(1 - k * k);
		float nx = x / (r[0] * r[0]);
		float nz = (z - r[2]) / (r[1] * r[1]);
		float angle = -(float) Math.toDegrees(Math.atan2(nx, -nz));
		float[] placed = outline.clone();
		for (int i = 1; i < placed.length; i += 2) placed[i] += y;
		Shape piece = new Shape();
		piece.plate(placed, -lift, sink, Math.min(0.05f, (lift + sink) / 2), material);
		m.addTurned(piece, angle, x, 0, z);
	}

	private static float[] ellipse(float rx, float ry, int n) {
		float[] out = new float[n * 2];
		for (int i = 0; i < n; i++) {
			double a = 2 * Math.PI * i / n;
			out[i * 2] = (float) Math.cos(a) * rx;
			out[i * 2 + 1] = (float) Math.sin(a) * ry;
		}
		return out;
	}

	private static float[] rect(float x0, float y0, float x1, float y1) {
		return new float[] {x0, y0, x1, y0, x1, y1, x0, y1};
	}

	private static final float[][] REAPER_SKULL = {{-8.15f, 1.0f, 1.2f, 0.2f}, {-7.7f, 2.4f, 2.8f, 0.2f}, {-6.8f, 3.0f, 3.4f, 0.25f},
			{-5.4f, 3.1f, 3.5f, 0.25f}, {-4.2f, 2.9f, 3.4f, 0.15f}, {-3.2f, 2.45f, 3.25f, -0.05f}, {-2.2f, 1.95f, 2.85f, -0.25f},
			{-1.2f, 1.75f, 2.45f, -0.3f}, {-0.45f, 1.3f, 1.9f, -0.25f}};
	private static final float[][] REAPER_HOOD = {{-10.5f, 0.9f, 0.9f, 1.0f}, {-10.1f, 2.8f, 3.0f, 1.0f}, {-9.1f, 4.0f, 4.1f, 0.9f},
			{-7.4f, 4.5f, 4.4f, 0.95f}, {-5.6f, 4.6f, 4.5f, 1.35f}, {-3.6f, 4.5f, 4.45f, 1.9f}, {-1.6f, 4.3f, 4.3f, 2.3f}, {0.4f, 4.2f, 4.2f, 2.4f},
			{1.4f, 4.3f, 4.3f, 2.4f}};

	/**
	 * The Grim Reaper: an ivory skull (deep sockets, a nose hole and a row of teeth) looking out of a deep charcoal
	 * hood, a cowl over the shoulders closed with toggles, a long robe pooling on the ground, wide bell sleeves and
	 * bony hands; his scythe and an hourglass lantern are in {@link #reaperProps}.
	 */
	private static void reaper(EnumMap<Limb, Shape> s) {
		Shape head = s.get(Limb.HEAD);
		column(head, 0, REAPER_SKULL, 0, KnifeShading.BONE, Shape.DOME, Shape.DOME);
		for (int side = -1; side <= 1; side += 2) {
			decal(head, REAPER_SKULL, ellipse(0.98f, 1.08f, 16), side * 1.28f, -4.55f, 0.1f, 0.4f, KnifeShading.DARK);
			decal(head, REAPER_SKULL, ellipse(0.55f, 0.32f, 12), side * 2.1f, -3.35f, 0.04f, 0.3f, KnifeShading.CLOTH);
		}
		decal(head, REAPER_SKULL, new float[] {0, -0.6f, 0.28f, -0.1f, 0.5f, 0.3f, 0.3f, 0.5f, 0, 0.32f, -0.3f, 0.5f, -0.5f, 0.3f, -0.28f, -0.1f}, 0,
				-3.15f, 0.08f, 0.4f, KnifeShading.DARK);
		for (int i = -3; i <= 3; i++) {
			decal(head, REAPER_SKULL, rect(-0.27f, -0.09f, 0.27f, 0.09f), i * 0.5f, -1.72f, 0.07f, 0.3f, KnifeShading.DARK);
		}
		for (int i = -2; i <= 3; i++) {
			decal(head, REAPER_SKULL, rect(-0.05f, -0.42f, 0.05f, 0.42f), (i - 0.5f) * 0.5f, -1.72f, 0.06f, 0.3f, KnifeShading.DARK);
		}
		column(head, 0, REAPER_HOOD, 0, KnifeShading.CLOTH, Shape.DOME, Shape.OPEN);
		twig(head, 0, -10.0f, 1.0f, 0, -11.0f, 2.2f, 1.5f, 0.35f, KnifeShading.CLOTH);
		int n = 26;
		float[][] rim = new float[n + 1][];
		for (int i = 0; i <= n; i++) {
			double a = Math.toRadians(120 + 300.0 * i / n);
			float x = (float) Math.cos(a) * 3.45f;
			float y = -4.1f + (float) Math.sin(a) * 4.75f;
			rim[i] = new float[] {x, y, front(REAPER_HOOD, x, y) - 0.08f, 0.52f};
		}
		path(head, rim, KnifeShading.CLOTH);

		Shape body = s.get(Limb.BODY);
		column(body, 0, new float[][] {{-0.6f, 3.6f, 2.6f, 0.2f}, {1.5f, 4.4f, 2.7f, 0.2f}, {6, 4.6f, 2.9f, 0.2f}, {12, 5.3f, 3.4f, 0.3f},
				{18, 6.1f, 3.9f, 0.4f}, {22.6f, 6.9f, 4.5f, 0.5f}, {23.9f, 7.2f, 4.8f, 0.5f}}, 0.1f, KnifeShading.CLOTH, Shape.OPEN, Shape.CAP);
		float[][] cowl = {{-1.4f, 3.3f, 3.0f, 0.4f}, {-0.6f, 4.8f, 3.4f, 0.3f}, {0.6f, 6.8f, 3.8f, 0.2f}, {2.0f, 8.0f, 4.1f, 0.2f},
				{4.2f, 8.4f, 4.3f, 0.2f}, {6.3f, 8.4f, 4.4f, 0.2f}, {6.7f, 8.0f, 4.1f, 0.2f}};
		column(body, 0, cowl, 0, KnifeShading.CLOTH, Shape.OPEN, Shape.OPEN);
		for (int i = 0; i < 3; i++) {
			float y = 1.4f + i * 1.9f;
			float z = front(cowl, 0, y) - 0.15f;
			twig(body, -0.65f, y, z, 0.65f, y, z, 0.2f, 0.2f, KnifeShading.WOOD);
			twig(body, 0, y - 0.5f, z + 0.1f, 0, y + 0.5f, z + 0.1f, 0.09f, 0.09f, KnifeShading.CORD);
		}

		for (Limb arm : new Limb[] {Limb.RIGHT_ARM, Limb.LEFT_ARM}) {
			Shape m = s.get(arm);
			float x = arm == Limb.RIGHT_ARM ? -1 : 1;
			float in = -x;
			float sx = x * 0.6f;
			column(m, sx, new float[][] {{-0.6f, 1.9f, 1.9f, 0}, {2, 2.3f, 2.3f, 0}, {6, 3.0f, 3.0f, 0}, {9.4f, 3.7f, 3.6f, -0.2f}, {10.2f, 3.9f, 3.8f, -0.2f}},
					0.1f, KnifeShading.CLOTH, Shape.DOME, Shape.OPEN);
			column(m, sx, new float[][] {{9.2f, 3.5f, 3.4f, -0.2f}, {10.0f, 3.6f, 3.5f, -0.2f}}, 0, KnifeShading.DARK, Shape.OPEN, Shape.CAP);
			blob(m, x, 10.9f, -0.3f, 0.85f, 0.95f, 0.45f, KnifeShading.BONE);
			for (int f = 0; f < 4; f++) {
				float fx = x - 0.72f + f * 0.48f;
				path(m, new float[][] {{fx, 11.4f, -0.4f, 0.17f}, {fx, 12.6f, -0.75f, 0.15f}, {fx + in * 0.05f, 13.5f, -0.45f, 0.12f}}, KnifeShading.BONE);
				ball(m, fx, 12.6f, -0.75f, 0.2f, KnifeShading.BONE);
			}
			path(m, new float[][] {{x + in * 0.85f, 11.0f, -0.6f, 0.18f}, {x + in * 1.1f, 11.9f, -1.1f, 0.15f}, {x + in * 0.9f, 12.6f, -1.4f, 0.12f}},
					KnifeShading.BONE);
		}
	}

	/** The Reaper's scythe, upright in his right hand with its long curved blade over his head, and an hourglass lantern hanging from his left. */
	private static void reaperProps(EnumMap<Limb, Shape> props) {
		Shape right = props.get(Limb.RIGHT_ARM);
		float x = -1;
		float z = -0.7f;
		path(right, new float[][] {{x + 0.6f, -12.6f, z, 0.42f}, {x + 0.25f, -4, z, 0.47f}, {x, 4, z, 0.48f}, {x - 0.25f, 13, z, 0.46f},
				{x - 0.4f, 22, z, 0.42f}}, KnifeShading.TAN_WOOD);
		path(right, new float[][] {{x - 0.3f, 16.6f, z, 0.32f}, {x + 1.1f, 16.3f, z - 0.6f, 0.3f}, {x + 2.0f, 16.0f, z - 1.0f, 0.3f}}, KnifeShading.TAN_WOOD);
		blob(right, x + 0.6f, -12.3f, z, 0.75f, 1.0f, 0.75f, KnifeShading.HARDWARE);
		Shape blade = new Shape();
		Shape.Blade b = new Shape.Blade();
		int steps = 48;
		float rootX = x + 0.6f;
		float rootY = -12.9f;
		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			float sx = rootX + 12.5f * t;
			float sy = rootY - 2.7f * (float) Math.sin(Math.PI * t) + 4.4f * t * t * t;
			float t2 = Math.min(1, t + 0.01f);
			float tx = 12.5f * (t2 - t);
			float ty = -2.7f * (float) (Math.sin(Math.PI * t2) - Math.sin(Math.PI * t)) + 4.4f * (t2 * t2 * t2 - t * t * t);
			float len = (float) Math.max(1e-5, Math.hypot(tx, ty));
			float nx = -ty / len;
			float ny = tx / len;
			float width = 2.7f * (1 - (float) Math.pow(t, 1.35)) + 0.05f;
			b.station(sx, sy, sx + nx * width, sy + ny * width, 0.3f - 0.2f * t);
		}
		b.grind = 0.5f;
		b.flatColumns = 5;
		blade.blade(b);
		blade.recolor(KnifeShading.BLADE, KnifeShading.STEEL);
		blade.recolor(KnifeShading.EDGE, KnifeShading.STEEL);
		right.add(blade, 0, 0, z);

		Shape left = props.get(Limb.LEFT_ARM);
		float lx = 1;
		left.torus(lx, 12.3f, -0.4f, 0.75f, 0.16f, 0.16f, 20, 6, KnifeShading.HARDWARE);
		twig(left, lx, 13.0f, -0.4f, lx, 13.7f, -0.4f, 0.12f, 0.12f, KnifeShading.HARDWARE);
		column(left, lx, new float[][] {{13.6f, 1.45f, 1.45f, -0.4f}, {14.1f, 1.45f, 1.45f, -0.4f}}, 0.3f, KnifeShading.HARDWARE, Shape.CAP, Shape.CAP);
		column(left, lx, new float[][] {{18.6f, 1.45f, 1.45f, -0.4f}, {19.1f, 1.45f, 1.45f, -0.4f}}, 0.3f, KnifeShading.HARDWARE, Shape.CAP, Shape.CAP);
		for (int k = 0; k < 3; k++) {
			double a = Math.PI * 2 * k / 3 + Math.PI / 2;
			float px = lx + (float) Math.cos(a) * 1.18f;
			float pz = -0.4f + (float) Math.sin(a) * 1.18f;
			twig(left, px, 14.0f, pz, px, 18.7f, pz, 0.12f, 0.12f, KnifeShading.HARDWARE);
		}
		column(left, lx, new float[][] {{14.1f, 0.3f, 0.3f, -0.4f}, {14.6f, 0.95f, 0.95f, -0.4f}, {15.4f, 1.0f, 1.0f, -0.4f}, {16.1f, 0.5f, 0.5f, -0.4f},
				{16.45f, 0.17f, 0.17f, -0.4f}, {16.8f, 0.5f, 0.5f, -0.4f}, {17.5f, 1.0f, 1.0f, -0.4f}, {18.3f, 0.95f, 0.95f, -0.4f},
				{18.6f, 0.3f, 0.3f, -0.4f}}, 0, KnifeShading.GLASS, Shape.OPEN, Shape.OPEN);
		column(left, lx, new float[][] {{17.3f, 0.96f, 0.96f, -0.4f}, {18.25f, 0.99f, 0.99f, -0.4f}, {18.58f, 0.34f, 0.34f, -0.4f}}, 0,
				KnifeShading.SAND, Shape.OPEN, Shape.CAP);
		column(left, lx, new float[][] {{15.85f, 0.66f, 0.66f, -0.4f}, {16.15f, 0.48f, 0.48f, -0.4f}, {16.42f, 0.2f, 0.2f, -0.4f}}, 0, KnifeShading.SAND,
				Shape.CAP, Shape.OPEN);
	}

	private static final float[][] LOG = {{-10.9f, 3.75f, 3.75f, 0}, {-10.4f, 3.95f, 3.95f, 0}, {-5, 4.2f, 4.2f, 0}, {3, 4.35f, 4.35f, 0},
			{10, 4.3f, 4.3f, 0}, {14.0f, 4.15f, 4.15f, 0}, {14.5f, 3.9f, 3.9f, 0}};

	/**
	 * Timber: one tall, wide round log of light golden wood for head and body, its face in the top half (big
	 * bulging cartoon eyes under heavy lids and brows, a long round nose, full cheeks and a wide grin), thin stick
	 * arms coming out low on its sides with long twig fingers, and short thin legs with big bare feet.
	 */
	private static void timber(EnumMap<Limb, Shape> s) {
		Shape body = s.get(Limb.BODY);
		column(body, 0, LOG, 0, KnifeShading.LOG, Shape.CAP, Shape.CAP);
		for (int side = -1; side <= 1; side += 2) {
			float ex = side * 1.85f;
			ball(body, ex, -4.6f, -3.22f, 1.45f, KnifeShading.GLOSS);
			blob(body, side * 1.74f, -4.5f, -4.6f, 0.72f, 0.78f, 0.25f, KnifeShading.HARDWARE);
			blob(body, side * 1.74f, -4.5f, -4.86f, 0.36f, 0.38f, 0.12f, KnifeShading.DARK);
			ball(body, side * 1.5f, -4.82f, -4.93f, 0.15f, KnifeShading.GLOSS);
			blob(body, ex, -5.25f, -3.3f, 1.58f, 0.75f, 1.42f, KnifeShading.LOG);
			blob(body, ex, -3.4f, -3.45f, 1.3f, 0.45f, 1.0f, KnifeShading.LOG);
			blob(body, side * 1.95f, -6.6f, -3.5f, 1.75f, 0.55f, 0.75f, KnifeShading.LOG);
			blob(body, side * 2.45f, -1.5f, -2.85f, 1.2f, 1.05f, 0.95f, KnifeShading.LOG);
			blob(body, side * 0.7f, -1.35f, -4.45f, 0.55f, 0.5f, 0.55f, KnifeShading.LOG);
		}
		twig(body, 0, -5.3f, -4.0f, 0, -2.2f, -4.85f, 0.55f, 0.75f, KnifeShading.LOG);
		blob(body, 0, -1.65f, -4.95f, 0.95f, 0.85f, 0.8f, KnifeShading.LOG);
		int n = 13;
		float[][] mouth = new float[n][];
		float[][] upper = new float[n][];
		float[][] lower = new float[n][];
		for (int i = 0; i < n; i++) {
			float x = -2.7f + 5.4f * i / (n - 1);
			float k = x / 2.7f;
			float y = 0.55f + 0.95f * (1 - k * k);
			float z = -(float) Math.sqrt(4.3f * 4.3f - x * x) - 0.25f;
			float lip = 0.4f * (1 - 0.55f * k * k);
			mouth[i] = new float[] {x, y, z, 0.24f};
			upper[i] = new float[] {x, y - 0.36f, z - 0.07f, lip};
			lower[i] = new float[] {x * 0.94f, y + 0.44f, z - 0.05f, lip * 1.1f};
		}
		path(body, mouth, KnifeShading.DARK);
		path(body, upper, KnifeShading.LOG);
		path(body, lower, KnifeShading.LOG);
		blob(body, 0, 2.95f, -3.85f, 1.4f, 0.85f, 0.65f, KnifeShading.LOG);

		for (Limb arm : new Limb[] {Limb.RIGHT_ARM, Limb.LEFT_ARM}) {
			Shape m = s.get(arm);
			float out = arm == Limb.RIGHT_ARM ? -1 : 1;
			float ax = -out * 0.8f;
			float ex = ax + out * 0.9f;
			float wx = ax + out * 1.3f;
			ball(m, ax, 5.4f, 0, 0.6f, KnifeShading.LOG);
			path(m, new float[][] {{ax, 5.4f, 0, 0.5f}, {ex, 9.8f, 0.2f, 0.38f}, {wx, 13.8f, -0.1f, 0.32f}}, KnifeShading.LOG);
			ball(m, ex, 9.8f, 0.2f, 0.44f, KnifeShading.LOG);
			blob(m, wx, 14.4f, -0.15f, 0.48f, 0.6f, 0.32f, KnifeShading.LOG);
			for (int f = 0; f < 4; f++) {
				float fx = wx + (f - 1.5f) * 0.24f;
				float tip = wx + (f - 1.5f) * 0.62f;
				path(m, new float[][] {{fx, 14.7f, -0.2f, 0.14f}, {(fx + tip) / 2, 15.7f, -0.4f, 0.12f}, {tip, 16.5f, -0.45f, 0.09f}}, KnifeShading.LOG);
			}
			path(m, new float[][] {{wx - out * 0.38f, 14.4f, -0.35f, 0.14f}, {wx - out * 0.78f, 15.0f, -0.7f, 0.11f}, {wx - out * 0.95f, 15.5f, -0.95f, 0.09f}},
					KnifeShading.LOG);
		}
		for (Limb leg : new Limb[] {Limb.RIGHT_LEG, Limb.LEFT_LEG}) {
			Shape m = s.get(leg);
			float in = leg == Limb.RIGHT_LEG ? 1 : -1;
			path(m, new float[][] {{0, 1.6f, 0, 0.52f}, {0, 6.6f, -0.25f, 0.44f}, {0, 11.0f, 0.1f, 0.4f}}, KnifeShading.LOG);
			ball(m, 0, 6.6f, -0.25f, 0.52f, KnifeShading.LOG);
			blob(m, 0, 11.45f, -1.5f, 1.35f, 0.62f, 2.4f, KnifeShading.LOG);
			blob(m, 0, 11.25f, 0.35f, 0.85f, 0.7f, 0.9f, KnifeShading.LOG);
			for (int t = 0; t < 5; t++) {
				float tx = in * (1.0f - t * 0.5f);
				float r = t == 0 ? 0.44f : 0.35f - t * 0.02f;
				ball(m, tx, 11.65f, -3.65f + t * 0.15f, r, KnifeShading.LOG);
			}
		}
	}

	/**
	 * Galaxy: a sleek, muscular figure whose whole body is the night sky (violet and pink clouds over deep indigo,
	 * scattered with stars), with a smooth head and two glowing white eyes.
	 */
	private static void galaxy(EnumMap<Limb, Shape> s) {
		Shape head = s.get(Limb.HEAD);
		float[][] skull = {{-6, 2.98f, 3.28f, 0.15f}, {-2, 2.98f, 3.28f, 0.15f}};
		int rings = 14;
		Shape.Ring[] egg = new Shape.Ring[rings + 1];
		for (int i = 0; i <= rings; i++) {
			float t = i / (float) rings;
			float round = (float) Math.pow(Math.max(0.0001, Math.sin(Math.PI * t)), 0.62);
			float taper = 1 - 0.3f * t * t;
			egg[i] = Shape.Ring.along(0, -7.3f + 6.4f * t, 0.2f - 0.55f * t, Math.max(0.01f, 3.0f * round * taper), Math.max(0.01f, 3.3f * round * taper));
		}
		head.loft(egg, 24, 0, KnifeShading.GALAXY, Shape.OPEN, Shape.OPEN, false);
		column(head, 0, new float[][] {{-2.4f, 1.4f, 1.35f, 0.2f}, {0.8f, 1.55f, 1.45f, 0.2f}}, 0, KnifeShading.GALAXY, Shape.OPEN, Shape.OPEN);
		for (int side = -1; side <= 1; side += 2) {
			decal(head, skull, new float[] {-0.75f * side, 0.12f, -0.1f * side, -0.26f, 0.75f * side, -0.08f, 0.05f * side, 0.22f}, side * 1.15f, -4.25f,
					0.06f, 0.3f, KnifeShading.STAR);
		}
		Shape body = s.get(Limb.BODY);
		column(body, 0, new float[][] {{-0.4f, 2.0f, 1.5f, 0.1f}, {0.5f, 4.0f, 2.2f, 0}, {2.6f, 4.3f, 2.5f, -0.15f}, {5.0f, 3.8f, 2.25f, -0.05f},
				{7.4f, 3.2f, 1.9f, 0.05f}, {9.6f, 3.4f, 2.0f, 0.05f}, {12.3f, 3.6f, 2.0f, 0}}, 0.15f, KnifeShading.GALAXY, Shape.DOME, Shape.CAP);
		for (int side = -1; side <= 1; side += 2) {
			blob(body, side * 2.2f, 0.1f, 0.3f, 1.6f, 0.8f, 1.3f, KnifeShading.GALAXY);
		}
		for (Limb arm : new Limb[] {Limb.RIGHT_ARM, Limb.LEFT_ARM}) {
			Shape m = s.get(arm);
			float x = arm == Limb.RIGHT_ARM ? -1 : 1;
			float in = -x;
			blob(m, x, -0.8f, 0, 1.95f, 1.7f, 1.9f, KnifeShading.GALAXY);
			column(m, x, new float[][] {{-0.6f, 1.6f, 1.65f, 0}, {2.0f, 1.65f, 1.6f, 0.05f}, {4.4f, 1.3f, 1.35f, 0}, {5.4f, 1.2f, 1.25f, 0},
					{7.2f, 1.35f, 1.35f, -0.05f}, {9.4f, 0.95f, 1.0f, 0}}, 0, KnifeShading.GALAXY, Shape.OPEN, Shape.CAP);
			blob(m, x, 10.5f, -0.1f, 0.85f, 1.25f, 0.55f, KnifeShading.GALAXY);
			blob(m, x + in * 0.7f, 10.0f, -0.5f, 0.35f, 0.7f, 0.35f, KnifeShading.GALAXY);
		}
		for (Limb leg : new Limb[] {Limb.RIGHT_LEG, Limb.LEFT_LEG}) {
			Shape m = s.get(leg);
			column(m, 0, new float[][] {{-1.0f, 1.9f, 1.9f, 0}, {0.6f, 2.0f, 2.0f, 0}, {3.0f, 1.85f, 1.85f, -0.05f}, {5.6f, 1.35f, 1.4f, 0},
					{6.4f, 1.3f, 1.35f, 0}, {8.4f, 1.5f, 1.55f, 0.1f}, {10.6f, 1.05f, 1.1f, 0.05f}, {11.2f, 1.15f, 1.2f, 0}}, 0, KnifeShading.GALAXY, Shape.DOME,
					Shape.CAP);
			blob(m, 0, 11.35f, -0.9f, 1.2f, 0.75f, 2.1f, KnifeShading.GALAXY);
		}
	}

	/**
	 * The Astronaut: a round white helmet with a big dark gold visor, a puffy off-white suit with a chest unit,
	 * blue and red hose connectors and white hoses, a big backpack, grey gloves, knee pads and chunky white boots.
	 */
	private static void astronaut(EnumMap<Limb, Shape> s) {
		Shape head = s.get(Limb.HEAD);
		ball(head, 0, -4.6f, 0.3f, 5.2f, KnifeShading.GLOSS);
		blob(head, 0, -4.4f, -0.1f, 4.8f, 4.2f, 5.2f, KnifeShading.VISOR);
		column(head, 0, new float[][] {{-0.6f, 3.7f, 3.7f, 0.3f}, {1.0f, 3.8f, 3.8f, 0.3f}}, 0.1f, KnifeShading.GLOVE, Shape.OPEN, Shape.OPEN);

		Shape body = s.get(Limb.BODY);
		float[][] suit = {{-0.8f, 3.8f, 2.8f, 0.2f}, {0.6f, 4.9f, 3.1f, 0.1f}, {4, 5.0f, 3.3f, 0}, {8, 4.7f, 3.1f, 0}, {11, 4.8f, 3.1f, 0},
				{12.8f, 4.5f, 2.9f, 0}};
		column(body, 0, suit, 0.35f, KnifeShading.SUIT, Shape.DOME, Shape.CAP);
		box(body, -2.0f, 2.2f, -3.95f, 2.0f, 5.3f, -3.0f, 0.7f, KnifeShading.GLOSS);
		body.plate(rect(-1.4f, 2.7f, -0.2f, 3.6f), -4.05f, -3.9f, 0.05f, KnifeShading.DARK);
		body.plate(rect(0.3f, 2.7f, 1.4f, 3.6f), -4.05f, -3.9f, 0.05f, KnifeShading.DARK);
		for (int i = 0; i < 3; i++) body.pin(-1.0f + i, 4.5f, -4.15f, -3.9f, 0.22f, KnifeShading.GLOVE);
		for (int side = -1; side <= 1; side += 2) {
			float bz = front(suit, side * 1.4f, 6.6f);
			float rz = front(suit, side * 1.4f, 8.7f);
			body.pin(side * 1.4f, 6.6f, bz - 0.75f, bz + 0.3f, 0.62f, KnifeShading.BLUE);
			body.pin(side * 1.4f, 6.6f, bz - 0.95f, bz - 0.6f, 0.32f, KnifeShading.GLOSS);
			body.pin(side * 1.4f, 8.7f, rz - 0.75f, rz + 0.3f, 0.62f, KnifeShading.RED);
			body.pin(side * 1.4f, 8.7f, rz - 0.95f, rz - 0.6f, 0.32f, KnifeShading.GLOSS);
			path(body, new float[][] {{side * 1.4f, 6.6f, bz - 0.9f, 0.42f}, {side * 2.9f, 7.5f, bz - 1.05f, 0.42f}, {side * 4.3f, 9.1f, -3.3f, 0.42f},
					{side * 5.0f, 10.6f, -2.0f, 0.42f}}, KnifeShading.SUIT);
			path(body, new float[][] {{side * 1.4f, 8.7f, rz - 0.9f, 0.4f}, {side * 2.4f, 10.0f, rz - 0.95f, 0.4f}, {side * 3.6f, 11.0f, -3.3f, 0.4f},
					{side * 4.6f, 11.6f, -2.2f, 0.4f}}, KnifeShading.SUIT);
		}
		column(body, 0, new float[][] {{9.3f, 4.85f, 3.2f, 0}, {9.9f, 4.85f, 3.2f, 0}}, 0.35f, KnifeShading.GLOVE, Shape.OPEN, Shape.OPEN);
		box(body, -4.4f, -1.6f, 2.6f, 4.4f, 10.2f, 7.0f, 0.75f, KnifeShading.GLOSS);
		box(body, -3.4f, -2.4f, 3.4f, 3.4f, -1.4f, 6.2f, 0.75f, KnifeShading.GLOSS);
		for (Limb arm : new Limb[] {Limb.RIGHT_ARM, Limb.LEFT_ARM}) {
			Shape m = s.get(arm);
			float x = arm == Limb.RIGHT_ARM ? -1.2f : 1.2f;
			float in = arm == Limb.RIGHT_ARM ? 1 : -1;
			blob(m, x, -0.6f, 0, 2.6f, 2.3f, 2.6f, KnifeShading.SUIT);
			column(m, x, new float[][] {{-1.0f, 2.45f, 2.45f, 0}, {2.8f, 2.4f, 2.4f, 0}, {4.0f, 2.2f, 2.2f, 0}, {5.4f, 2.35f, 2.35f, 0},
					{8.4f, 2.15f, 2.15f, 0}, {9.0f, 2.05f, 2.05f, 0}}, 0, KnifeShading.SUIT, Shape.OPEN, Shape.OPEN);
			column(m, x, new float[][] {{3.7f, 2.45f, 2.45f, 0}, {4.3f, 2.45f, 2.45f, 0}}, 0, KnifeShading.SUIT, Shape.OPEN, Shape.OPEN);
			column(m, x, new float[][] {{8.7f, 2.3f, 2.3f, 0}, {9.5f, 2.3f, 2.3f, 0}}, 0.1f, arm == Limb.RIGHT_ARM ? KnifeShading.BLUE : KnifeShading.RED,
					Shape.OPEN, Shape.CAP);
			blob(m, x, 10.6f, -0.2f, 1.35f, 1.5f, 0.95f, KnifeShading.GLOVE);
			blob(m, x + in * 1.0f, 10.2f, -0.9f, 0.45f, 0.8f, 0.45f, KnifeShading.GLOVE);
			blob(m, x, 11.7f, -0.35f, 1.2f, 0.9f, 0.8f, KnifeShading.GLOVE);
		}
		for (Limb leg : new Limb[] {Limb.RIGHT_LEG, Limb.LEFT_LEG}) {
			Shape m = s.get(leg);
			column(m, 0, new float[][] {{-0.2f, 2.5f, 2.5f, 0}, {3.5f, 2.45f, 2.5f, 0}, {5.5f, 2.25f, 2.3f, 0}, {7.0f, 2.35f, 2.4f, 0},
					{9.6f, 2.25f, 2.3f, 0}}, 0, KnifeShading.SUIT, Shape.OPEN, Shape.OPEN);
			blob(m, 0, 5.4f, -2.0f, 1.7f, 1.9f, 0.8f, KnifeShading.SUIT);
			box(m, -1.4f, 6.8f, -2.75f, 1.4f, 9.2f, -2.15f, 0.7f, KnifeShading.SUIT);
			column(m, 0, new float[][] {{9.2f, 2.5f, 2.6f, 0}, {10.6f, 2.6f, 2.8f, -0.3f}, {11.8f, 2.6f, 3.0f, -0.6f}}, 0.3f, KnifeShading.GLOSS, Shape.OPEN,
					Shape.CAP);
			blob(m, 0, 11.1f, -2.3f, 2.3f, 1.0f, 1.6f, KnifeShading.GLOSS);
			column(m, 0, new float[][] {{11.7f, 2.7f, 3.2f, -0.7f}, {12.2f, 2.7f, 3.2f, -0.7f}}, 0.35f, KnifeShading.GLOVE, Shape.CAP, Shape.CAP);
		}
	}

	/** A wooden bat: a thin grip with a knob widening to a round-ended barrel, along +Y in knife pixels. */
	private static Shape bat() {
		if (bat == null) {
			bat = new Shape();
			bat.loft(new Shape.Ring[] {Shape.Ring.along(0, -5.4f, 0, 0.7f, 0.7f), Shape.Ring.along(0, -4.9f, 0, 0.7f, 0.7f),
					Shape.Ring.along(0, -4.7f, 0, 0.42f, 0.42f), Shape.Ring.along(0, 0.5f, 0, 0.48f, 0.48f), Shape.Ring.along(0, 5.5f, 0, 1.05f, 1.05f),
					Shape.Ring.along(0, 10.2f, 0, 1.15f, 1.15f)}, 16, 0, KnifeShading.TAN_WOOD, Shape.CAP, Shape.DOME, false);
		}
		return bat;
	}

	private static void emit(Shape mesh, PoseStack pose, SubmitNodeCollector collector, int light) {
		int glowRgb = Accent.get();
		float[] hsv = Widgets.toHsv(glowRgb);
		int frame = KnifeShading.frame(62, 0, 0, 0.3f);
		Finish finish = Finish.VOID;
		if (KnifeShading.custom()) {
			collector.submitCustomGeometry(pose, Compat.knifeShaded(), (last, out) -> mesh.emit(last, out, light, finish.id, hsv[0], hsv[1], frame));
		} else {
			collector.submitCustomGeometry(pose, Compat.knifeRenderType(KnifeAtlas.get(), false),
					(last, out) -> mesh.emitPlain(last, out, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, finish.id, glowRgb));
		}
	}

	/** Draws your 3D skin on the player model's posed parts. */
	public static void draw(PlayerModel model, HumanoidArm mainArm, PoseStack pose, SubmitNodeCollector collector, int light) {
		String skin = Modules.MODEL_SKIN_TYPE.get();
		EnumMap<Limb, Shape> parts = shapes(skin);
		draw(model.head, parts.get(Limb.HEAD), pose, collector, light);
		draw(model.body, parts.get(Limb.BODY), pose, collector, light);
		draw(model.rightArm, parts.get(Limb.RIGHT_ARM), pose, collector, light);
		draw(model.leftArm, parts.get(Limb.LEFT_ARM), pose, collector, light);
		draw(model.rightLeg, parts.get(Limb.RIGHT_LEG), pose, collector, light);
		draw(model.leftLeg, parts.get(Limb.LEFT_LEG), pose, collector, light);
		if (holdsProp()) {
			EnumMap<Limb, Shape> props = props(skin);
			draw(model.rightArm, props.get(Limb.RIGHT_ARM), pose, collector, light);
			draw(model.leftArm, props.get(Limb.LEFT_ARM), pose, collector, light);
		}
		dev.eventide.client.Hooks.modelSkinDrawn = true;
	}

	private static void draw(ModelPart part, Shape mesh, PoseStack pose, SubmitNodeCollector collector, int light) {
		pose.pushPose();
		part.translateAndRotate(pose);
		pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
		emit(mesh, pose, collector, light);
		pose.popPose();
	}

	/**
	 * Your skin's weapon in your hand, posed like a held sword: the Reaper's scythe, Timber's bat, the Void
	 * Walker's crystal dagger. {@code pose} is in the hand's frame as Minecraft places a held item.
	 */
	public static void drawProp(HumanoidArm arm, PoseStack pose, SubmitNodeCollector collector, int light) {
		String skin = Modules.MODEL_SKIN_TYPE.get();
		if (skin.equals("Grim Reaper")) return;
		if (skin.equals("Timber")) {
			boolean left = arm == HumanoidArm.LEFT;
			pose.pushPose();
			pose.translate(0, 0, -4.2f / 16f);
			pose.translate(0, 4 / 16f, 0.5f / 16f);
			pose.mulPose(Axis.YP.rotationDegrees(left ? 90 : -90));
			pose.mulPose(Axis.ZP.rotationDegrees(left ? -55 : 55));
			pose.scale(0.85f, 0.85f, 0.85f);
			pose.translate(-3.5f / 16f, -3.5f / 16f, 0);
			pose.mulPose(Axis.ZP.rotationDegrees(-45));
			pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
			pose.translate(0, 3.6f, 0);
			emit(bat(), pose, collector, light);
			pose.popPose();
		} else {
			Cosmetics.drawHeldKnife(KnifeModel.PRISM, arm, pose, collector, light);
		}
	}
}
