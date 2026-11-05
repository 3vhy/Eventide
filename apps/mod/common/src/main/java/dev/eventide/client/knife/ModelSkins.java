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
 * knife shader: the Grim Reaper (a deep hood round a skull, a cape and a robe to the ground, his scythe and an
 * hourglass lantern), Timber (a tall golden log with cartoon eyes and a grin, on stick limbs, with a bat), the Void
 * Walker (a body made of the night sky) and the Astronaut (a bulky white suit, a gold visor and a backpack). Each limb is built round its joint in
 * Minecraft's player model (pixels, Y down), so it moves with your own walk, swing and sneak.
 */
public final class ModelSkins {
	public static final String[] SKINS = {"Grim Reaper", "Timber", "Void Walker", "Astronaut"};

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
			case "Void Walker" -> voidWalker(out);
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

	/** A tube along Y from y0 to y1, its radius running from r0 to r1. */
	private static void tube(Shape m, float x, float z, float y0, float y1, float r0, float r1, int material) {
		m.loft(new Shape.Ring[] {Shape.Ring.along(x, y0, z, r0, r0), Shape.Ring.along(x, y1, z, r1, r1)}, 18, 0, material, Shape.CAP, Shape.CAP, false);
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
		int n = 10;
		Shape.Ring[] rings = new Shape.Ring[n + 1];
		for (int i = 0; i <= n; i++) {
			double a = Math.PI * i / n;
			float rr = Math.max(0.01f, (float) Math.sin(a) * r);
			rings[i] = Shape.Ring.along(x, y - (float) Math.cos(a) * r, z, rr, rr);
		}
		m.loft(rings, 18, 0, material, Shape.OPEN, Shape.OPEN, false);
	}

	/** A smooth tube down Y through rings of {y, x radius, z radius, z centre}, closed at the bottom when {@code capped}. */
	private static void sleeve(Shape m, float x, float[][] rings, int material, boolean capped) {
		Shape.Ring[] out = new Shape.Ring[rings.length];
		for (int i = 0; i < rings.length; i++) out[i] = Shape.Ring.along(x, rings[i][0], rings[i][3], rings[i][1], rings[i][2]);
		m.loft(out, 24, 0.35f, material, Shape.OPEN, capped ? Shape.CAP : Shape.OPEN, false);
	}

	private static float[] rect(float x0, float y0, float x1, float y1) {
		return new float[] {x0, y0, x1, y0, x1, y1, x0, y1};
	}

	private static float[] diamond(float x, float y, float hx, float hy) {
		return new float[] {x, y - hy, x + hx, y, x, y + hy, x - hx, y};
	}

	/**
	 * The Grim Reaper: a deep pointed hood round a skull with violet eyes, a cape over the shoulders, a robe to
	 * the ground with wide sleeves and bony hands; his scythe and an hourglass lantern are in {@link #reaperProps}.
	 */
	private static void reaper(EnumMap<Limb, Shape> s) {
		Shape head = s.get(Limb.HEAD);
		head.loft(new Shape.Ring[] {Shape.Ring.along(0, 0.9f, 0.4f, 4.9f, 4.9f), Shape.Ring.along(0, -4, 0.6f, 5.4f, 5.3f),
				Shape.Ring.along(0, -8.4f, 1.0f, 4.9f, 5.0f), Shape.Ring.along(0, -9.9f, 2.0f, 3.2f, 3.6f), Shape.Ring.along(0, -10.7f, 3.5f, 0.6f, 0.8f)},
				28, 0.15f, KnifeShading.RUBBER, Shape.OPEN, Shape.DOME, false);
		head.plate(rect(-3.8f, -7.8f, 3.8f, 0.4f), -4.75f, -4.6f, 0.8f, KnifeShading.RUBBER);
		box(head, -2.6f, -7.0f, -4.95f, 2.6f, -1.2f, -3.3f, 0.78f, KnifeShading.FEATHER);
		for (int side = -1; side <= 1; side += 2) {
			head.plate(diamond(side * 1.1f, -4.4f, 0.85f, 0.8f), -5.05f, -4.9f, 0.1f, KnifeShading.RUBBER);
			head.pin(side * 1.1f, -4.4f, -5.15f, -5.0f, 0.34f, KnifeShading.GEM_RIM);
		}
		head.plate(new float[] {-0.4f, -3.0f, 0.4f, -3.0f, 0, -3.8f}, -5.05f, -4.9f, 0.05f, KnifeShading.RUBBER);
		for (int i = -2; i <= 2; i++) head.plate(rect(i * 0.85f - 0.28f, -2.0f, i * 0.85f + 0.28f, -1.5f), -5.05f, -4.9f, 0.05f, KnifeShading.RUBBER);

		Shape body = s.get(Limb.BODY);
		sleeve(body, 0, new float[][] {{-0.3f, 4.5f, 2.7f, 0}, {6, 4.8f, 3.0f, 0}, {12, 5.4f, 3.4f, 0.1f}, {18, 6.1f, 3.9f, 0.2f}, {23.6f, 6.7f, 4.4f, 0.3f}},
				KnifeShading.RUBBER, true);
		sleeve(body, 0, new float[][] {{-0.7f, 4.6f, 3.0f, 0}, {2.6f, 6.1f, 3.9f, 0.1f}, {5.6f, 6.8f, 4.4f, 0.2f}}, KnifeShading.RUBBER, false);
		body.pin(0, 1.6f, -3.35f, -2.9f, 0.32f, KnifeShading.HARDWARE);
		body.pin(0, 3.6f, -4.1f, -3.6f, 0.32f, KnifeShading.HARDWARE);

		for (Limb arm : new Limb[] {Limb.RIGHT_ARM, Limb.LEFT_ARM}) {
			Shape m = s.get(arm);
			float x = arm == Limb.RIGHT_ARM ? -1 : 1;
			sleeve(m, x, new float[][] {{-2.4f, 2.5f, 2.5f, 0}, {5, 2.8f, 2.8f, 0}, {9.2f, 3.7f, 3.7f, 0}}, KnifeShading.RUBBER, false);
			box(m, x - 1.2f, 8.6f, -1.2f, x + 1.2f, 10.8f, 1.2f, 0.65f, KnifeShading.FEATHER);
			for (int f = -1; f <= 1; f++) twig(m, x + f * 0.7f, 10.6f, -0.4f, x + f * 0.9f, 12.4f, -0.9f, 0.22f, 0.18f, KnifeShading.FEATHER);
		}
	}

	/** The Reaper's scythe, upright in his right hand with its blade sweeping forward, and an hourglass lantern hanging from his left. */
	private static void reaperProps(EnumMap<Limb, Shape> props) {
		Shape right = props.get(Limb.RIGHT_ARM);
		float x = -1;
		twig(right, x, -22, -0.6f, x, 21, -0.1f, 0.62f, 0.55f, KnifeShading.TAN_WOOD);
		twig(right, x, 1.8f, -0.5f, x, 1.8f, -4.2f, 0.42f, 0.42f, KnifeShading.TAN_WOOD);
		twig(right, x, 13.5f, -0.2f, x, 13.5f, -3.6f, 0.42f, 0.42f, KnifeShading.TAN_WOOD);
		Shape blade = new Shape();
		Shape.Blade b = new Shape.Blade();
		int steps = 48;
		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			double angle = Math.toRadians(-38 - 132 * t);
			float cx = -8.5f;
			float cy = -14.5f;
			float r = 10.6f;
			float width = 2.8f * (1 - (float) Math.pow(t, 1.25)) + 0.04f;
			float sx = cx + (float) Math.cos(angle) * r;
			float sy = cy + (float) Math.sin(angle) * r;
			float ex = cx + (float) Math.cos(angle) * (r - width);
			float ey = cy + (float) Math.sin(angle) * (r - width);
			b.station(sx, sy, ex, ey, 0.32f - 0.22f * t);
		}
		b.grind = 0.55f;
		b.flatColumns = 5;
		blade.blade(b);
		right.addTurned(blade, -90, x, 0, -0.6f);

		Shape left = props.get(Limb.LEFT_ARM);
		float lx = 1;
		left.torus(lx, 11.9f, 0, 0.75f, 0.16f, 0.16f, 20, 6, KnifeShading.HARDWARE);
		twig(left, lx, 12.6f, 0, lx, 13.6f, 0, 0.12f, 0.12f, KnifeShading.HARDWARE);
		tube(left, lx, 0, 13.6f, 14.1f, 1.45f, 1.45f, KnifeShading.HARDWARE);
		tube(left, lx, 0, 18.6f, 19.1f, 1.45f, 1.45f, KnifeShading.HARDWARE);
		for (int k = 0; k < 3; k++) {
			double a = Math.PI * 2 * k / 3;
			float px = lx + (float) Math.cos(a) * 1.15f;
			float pz = (float) Math.sin(a) * 1.15f;
			twig(left, px, 14.0f, pz, px, 18.7f, pz, 0.13f, 0.13f, KnifeShading.HARDWARE);
		}
		ball(left, lx, 15.2f, 0, 1.0f, KnifeShading.GEM);
		ball(left, lx, 17.4f, 0, 1.0f, KnifeShading.GEM);
		ball(left, lx, 17.9f, 0, 0.55f, KnifeShading.FEATHER);
	}

	/**
	 * Timber: a tall golden log for head and body with big cartoon eyes under heavy brows, a long nose and a wide
	 * grin, on thin stick arms with twig fingers and thin legs with big flat feet.
	 */
	private static void timber(EnumMap<Limb, Shape> s) {
		Shape head = s.get(Limb.HEAD);
		tube(head, 0, 0, 0.6f, -9.4f, 4.7f, 4.6f, KnifeShading.TAN_WOOD);
		for (int side = -1; side <= 1; side += 2) {
			ball(head, side * 1.8f, -5.0f, -4.15f, 1.4f, KnifeShading.FEATHER);
			ball(head, side * 1.62f, -4.95f, -5.2f, 0.78f, KnifeShading.RUBBER);
			ball(head, side * 1.35f, -5.3f, -5.9f, 0.22f, KnifeShading.FEATHER);
			head.plate(new float[] {side * 0.5f, -6.7f, side * 3.3f, -7.2f, side * 3.3f, -6.4f, side * 0.5f, -6.1f}, -5.0f, -4.35f, 0.2f,
					KnifeShading.TAN_WOOD);
		}
		tube(head, 0, -4.95f, -4.9f, -2.5f, 0.55f, 0.72f, KnifeShading.TAN_WOOD);
		ball(head, 0, -2.5f, -5.05f, 0.8f, KnifeShading.TAN_WOOD);
		head.plate(new float[] {-2.8f, -1.75f, 2.8f, -1.75f, 2.2f, -0.75f, 0, -0.35f, -2.2f, -0.75f}, -4.8f, -4.5f, 0.15f, KnifeShading.RUBBER);
		head.plate(rect(-2.1f, -1.72f, 2.1f, -1.3f), -4.92f, -4.75f, 0.08f, KnifeShading.FEATHER);
		head.plate(new float[] {-3.0f, -1.95f, 3.0f, -1.95f, 2.9f, -1.6f, -2.9f, -1.6f}, -4.95f, -4.6f, 0.12f, KnifeShading.TAN_WOOD);
		tube(s.get(Limb.BODY), 0, 0, -0.4f, 12.8f, 4.7f, 4.35f, KnifeShading.TAN_WOOD);
		for (Limb arm : new Limb[] {Limb.RIGHT_ARM, Limb.LEFT_ARM}) {
			Shape m = s.get(arm);
			float x = arm == Limb.RIGHT_ARM ? -1.6f : 1.6f;
			float out = arm == Limb.RIGHT_ARM ? -1 : 1;
			twig(m, x, -1.0f, 0, x, 9.6f, 0, 0.55f, 0.45f, KnifeShading.TAN_WOOD);
			twig(m, x, 9.6f, 0, x - out * 0.9f, 11.6f, -0.5f, 0.24f, 0.16f, KnifeShading.TAN_WOOD);
			twig(m, x, 9.6f, 0, x, 12.0f, -0.6f, 0.24f, 0.16f, KnifeShading.TAN_WOOD);
			twig(m, x, 9.6f, 0, x + out * 0.9f, 11.6f, -0.4f, 0.24f, 0.16f, KnifeShading.TAN_WOOD);
			twig(m, x, 9.4f, 0, x + out * 0.4f, 10.6f, -1.3f, 0.22f, 0.15f, KnifeShading.TAN_WOOD);
		}
		for (Limb leg : new Limb[] {Limb.RIGHT_LEG, Limb.LEFT_LEG}) {
			Shape m = s.get(leg);
			twig(m, 0, 0.2f, 0, 0, 11.0f, -0.2f, 0.62f, 0.55f, KnifeShading.TAN_WOOD);
			box(m, -1.7f, 10.8f, -3.6f, 1.7f, 12.0f, 1.2f, 0.85f, KnifeShading.TAN_WOOD);
			for (int t = 0; t < 4; t++) ball(m, -1.2f + t * 0.8f, 11.5f, -3.65f, 0.45f, KnifeShading.TAN_WOOD);
		}
	}

	/** A body made of the night sky: smooth, starry nebula all over, with two glowing eyes. */
	private static void voidWalker(EnumMap<Limb, Shape> s) {
		Shape head = s.get(Limb.HEAD);
		box(head, -4.0f, -8.2f, -4.0f, 4.0f, 0.1f, 4.0f, 0.55f, KnifeShading.GUARD);
		for (int side = -1; side <= 1; side += 2) {
			head.plate(new float[] {side * 0.7f, -4.9f, side * 2.6f, -5.2f, side * 2.5f, -4.4f, side * 0.7f, -4.3f}, -4.3f, -4.05f, 0.1f,
					KnifeShading.GEM_RIM);
		}
		Shape body = s.get(Limb.BODY);
		sleeve(body, 0, new float[][] {{-0.2f, 4.2f, 2.2f, 0}, {4, 4.4f, 2.4f, 0}, {8, 3.9f, 2.1f, 0}, {12.2f, 4.1f, 2.2f, 0}}, KnifeShading.GUARD, true);
		body.loft(new Shape.Ring[] {Shape.Ring.along(0, -0.25f, 0, 4.2f, 2.2f), Shape.Ring.along(0, -0.2f, 0, 0.01f, 0.01f)}, 24, 0.35f,
				KnifeShading.GUARD, Shape.OPEN, Shape.OPEN, false);
		for (Limb arm : new Limb[] {Limb.RIGHT_ARM, Limb.LEFT_ARM}) {
			float x = arm == Limb.RIGHT_ARM ? -1 : 1;
			sleeve(s.get(arm), x, new float[][] {{-2.3f, 2.1f, 2.1f, 0}, {1.0f, 2.3f, 2.2f, 0}, {4.0f, 1.9f, 1.9f, 0}, {7.0f, 2.1f, 2.0f, 0},
					{10.2f, 1.8f, 1.8f, 0}}, KnifeShading.GUARD, true);
		}
		for (Limb leg : new Limb[] {Limb.RIGHT_LEG, Limb.LEFT_LEG}) {
			sleeve(s.get(leg), 0, new float[][] {{-0.2f, 2.2f, 2.2f, 0}, {4.0f, 2.3f, 2.3f, 0}, {7.0f, 1.9f, 1.9f, 0}, {12.2f, 2.0f, 2.3f, -0.3f}},
					KnifeShading.GUARD, true);
		}
	}

	/**
	 * The Astronaut: a bulky white suit with rings at the elbows, a chest unit with dials and hoses, a backpack,
	 * grey gloves and big boots, and a round helmet with a gold visor.
	 */
	private static void astronaut(EnumMap<Limb, Shape> s) {
		Shape head = s.get(Limb.HEAD);
		ball(head, 0, -4.4f, 0, 5.7f, KnifeShading.FEATHER);
		box(head, -3.9f, -7.2f, -6.0f, 3.9f, -1.9f, -4.3f, 0.88f, KnifeShading.VISOR);
		tube(head, 0, 0, -0.2f, 1.0f, 4.6f, 4.6f, KnifeShading.HARDWARE);
		Shape body = s.get(Limb.BODY);
		box(body, -5.0f, -0.4f, -3.0f, 5.0f, 12.4f, 3.0f, 0.72f, KnifeShading.FEATHER);
		box(body, -2.7f, 2.0f, -3.8f, 2.7f, 6.4f, -2.8f, 0.8f, KnifeShading.HARDWARE);
		for (int i = 0; i < 4; i++) {
			float px = -1.4f + (i % 2) * 2.8f;
			float py = 3.2f + (i / 2) * 2.2f;
			body.pin(px, py, -4.25f, -3.7f, 0.5f, i % 3 == 0 ? KnifeShading.GEM_RIM : KnifeShading.HANDLE_PAINT);
		}
		twig(body, -2.7f, 4.2f, -3.3f, -4.4f, 6.6f, -3.4f, 0.38f, 0.38f, KnifeShading.FEATHER);
		twig(body, -4.4f, 6.6f, -3.4f, -5.0f, 9.2f, -2.2f, 0.38f, 0.38f, KnifeShading.FEATHER);
		twig(body, 2.7f, 4.2f, -3.3f, 4.4f, 6.6f, -3.4f, 0.38f, 0.38f, KnifeShading.FEATHER);
		twig(body, 4.4f, 6.6f, -3.4f, 5.0f, 9.2f, -2.2f, 0.38f, 0.38f, KnifeShading.FEATHER);
		box(body, -4.3f, -0.8f, 2.8f, 4.3f, 11.2f, 6.6f, 0.75f, KnifeShading.FEATHER);
		box(body, -3.0f, -1.6f, 3.4f, 3.0f, -0.6f, 6.0f, 0.75f, KnifeShading.FEATHER);
		body.plate(new float[] {-4.6f, 1.0f, -3.4f, 1.0f, -3.4f, 2.4f, -4.6f, 2.4f}, -3.15f, -2.95f, 0.3f, KnifeShading.HANDLE_PAINT);
		for (Limb arm : new Limb[] {Limb.RIGHT_ARM, Limb.LEFT_ARM}) {
			float x = arm == Limb.RIGHT_ARM ? -1.2f : 1.2f;
			Shape m = s.get(arm);
			box(m, x - 2.7f, -2.7f, -2.7f, x + 2.7f, 3.0f, 2.7f, 0.78f, KnifeShading.FEATHER);
			box(m, x - 2.9f, 3.0f, -2.9f, x + 2.9f, 4.0f, 2.9f, 0.8f, KnifeShading.FEATHER);
			box(m, x - 2.6f, 4.0f, -2.6f, x + 2.6f, 9.0f, 2.6f, 0.78f, KnifeShading.FEATHER);
			box(m, x - 2.3f, 8.8f, -2.3f, x + 2.3f, 11.2f, 2.3f, 0.8f, KnifeShading.HARDWARE);
		}
		for (Limb leg : new Limb[] {Limb.RIGHT_LEG, Limb.LEFT_LEG}) {
			Shape m = s.get(leg);
			box(m, -2.8f, -0.4f, -2.8f, 2.8f, 9.6f, 2.8f, 0.78f, KnifeShading.FEATHER);
			box(m, -2.4f, 4.0f, -3.3f, 2.4f, 7.4f, -2.6f, 0.8f, KnifeShading.FEATHER);
			box(m, -3.0f, 9.4f, -3.8f, 3.0f, 12.5f, 3.0f, 0.82f, KnifeShading.FEATHER);
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
		int frame = KnifeShading.frame(63, 0, 1.0f, 0.3f);
		Finish finish = switch (Modules.MODEL_SKIN_TYPE.get()) {
			case "Void Walker" -> Finish.NEBULA;
			case "Astronaut" -> Finish.ROSE;
			default -> Finish.VOID;
		};
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
