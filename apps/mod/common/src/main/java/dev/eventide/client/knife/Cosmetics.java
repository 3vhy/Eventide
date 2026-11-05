package dev.eventide.client.knife;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.eventide.client.compat.Compat;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.module.Modules;
import dev.eventide.client.ui.Widgets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Hats and wings for your player, and your knife in your hand in third person, all built from code and shaded
 * by the knife shader like the knives: a Glow Cone (a see-through cone of light), a Halo, a Crown, a Top Hat, a Witch
 * Hat, Horns and Cat Ears; Angel,
 * Demon and Energy wings that flap gently. Shapes are made the first time they're worn and kept. Units are
 * pixels, Y up; the callers turn Minecraft's model space (Y down) round.
 */
public final class Cosmetics {
	public static final String[] HATS = {"Glow Cone", "Halo", "Crown", "Top Hat", "Witch Hat", "Horns", "Cat Ears"};
	public static final String[] WINGS = {"Angel", "Demon", "Energy"};
	private static final Map<String, Shape> SHAPES = new HashMap<>();

	private Cosmetics() {
	}

	private static Shape shape(String key) {
		return SHAPES.computeIfAbsent(key, Cosmetics::build);
	}

	private static Shape build(String key) {
		Shape m = new Shape();
		switch (key) {
			case "Halo" -> m.torus(0, 0, 0, 3.6f, 0.32f, 0.32f, 40, 10, KnifeShading.ENERGY);
			case "Glow Cone" -> m.loft(new Shape.Ring[] {Shape.Ring.along(0, 0, 0, 7.2f, 7.2f), Shape.Ring.along(0, 3.0f, 0, 0.04f, 0.04f)}, 48, 0,
					KnifeShading.ENERGY, Shape.OPEN, Shape.OPEN, false);
			case "Glow Cone rim" -> hoop(m, 0, 7.2f, 0.18f, KnifeShading.ENERGY);
			case "Crown" -> crown(m);
			case "Top Hat" -> topHat(m);
			case "Witch Hat" -> witchHat(m);
			case "Horns" -> horns(m);
			case "Cat Ears" -> catEars(m);
			case "Angel" -> featherWing(m, KnifeShading.FEATHER);
			case "Energy" -> featherWing(m, KnifeShading.ENERGY);
			case "Demon" -> demonWing(m);
			default -> {
			}
		}
		return m;
	}

	/** A flat ring lying round the Y axis at height {@code y}, {@code radius} across to the middle of its tube. */
	private static void hoop(Shape m, float y, float radius, float tube, int material) {
		int around = 48;
		Shape.Ring[] rings = new Shape.Ring[around + 1];
		for (int i = 0; i <= around; i++) {
			double a = 2 * Math.PI * i / around;
			float c = (float) Math.cos(a);
			float sn = (float) Math.sin(a);
			rings[i] = new Shape.Ring(c * radius, y, sn * radius, -sn, 0, c, tube, tube, new float[] {c, 0, sn});
		}
		m.loft(rings, 8, 0, material, Shape.OPEN, Shape.OPEN, false);
	}

	/** A band of four zigzag-topped plates round the head, a jewel on each side. */
	private static void crown(Shape m) {
		Shape side = new Shape();
		float[] outline = new float[2 * 12];
		int n = 0;
		outline[n++] = -4.6f;
		outline[n++] = 0;
		outline[n++] = 4.6f;
		outline[n++] = 0;
		for (int i = 0; i <= 4; i++) {
			float x = 4.6f - i * 2.3f;
			outline[n++] = x;
			outline[n++] = i % 2 == 0 ? 3.4f : 1.6f;
		}
		outline = java.util.Arrays.copyOf(outline, n);
		side.plate(outline, -0.22f, 0.22f, 0.1f, KnifeShading.GUARD);
		side.plate(diamond(0, 1.1f, 0.55f, 0.65f), -0.34f, -0.18f, 0.05f, KnifeShading.GEM);
		side.plate(diamond(0, 1.1f, 0.7f, 0.82f), -0.27f, -0.2f, 0.03f, KnifeShading.GEM_RIM);
		for (int i = 0; i < 4; i++) {
			float a = i * 90;
			float r = 4.45f;
			m.addTurned(side, a, -(float) Math.sin(Math.toRadians(a)) * r, 0, -(float) Math.cos(Math.toRadians(a)) * r);
		}
	}

	private static float[] diamond(float x, float y, float hx, float hy) {
		return new float[] {x, y - hy, x + hx, y, x, y + hy, x - hx, y};
	}

	/** A wide brim and a tall crown with a glowing band. */
	private static void topHat(Shape m) {
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, 0, 0, 6.4f, 6.4f), Shape.Ring.along(0, 0.45f, 0, 6.4f, 6.4f)}, 32, 0, KnifeShading.POLYMER,
				Shape.CAP, Shape.CAP, false);
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, 0.4f, 0, 4.1f, 4.1f), Shape.Ring.along(0, 6.8f, 0, 4.3f, 4.3f)}, 32, 0, KnifeShading.POLYMER,
				Shape.OPEN, Shape.CAP, false);
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, 0.6f, 0, 4.18f, 4.18f), Shape.Ring.along(0, 1.7f, 0, 4.2f, 4.2f)}, 32, 0, KnifeShading.ENERGY,
				Shape.OPEN, Shape.OPEN, false);
	}

	/** A wide brim and a tall cone that bends over at its tip, with a glowing band. */
	private static void witchHat(Shape m) {
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, 0, 0, 7.4f, 7.4f), Shape.Ring.along(0, 0.35f, 0, 7.2f, 7.2f)}, 36, 0, KnifeShading.POLYMER,
				Shape.CAP, Shape.CAP, false);
		Shape.Ring[] cone = new Shape.Ring[14];
		for (int i = 0; i < cone.length; i++) {
			float f = i / (float) (cone.length - 1);
			float r = Math.max(0.05f, 4.5f * (1 - f));
			float bend = 2.6f * f * f * f;
			cone[i] = Shape.Ring.along(bend, 0.3f + 9.5f * f - 1.2f * f * f * f, 0, r, r);
		}
		m.loft(cone, 28, 0, KnifeShading.POLYMER, Shape.OPEN, Shape.OPEN, false);
		m.loft(new Shape.Ring[] {Shape.Ring.along(0, 0.5f, 0, 4.35f, 4.35f), Shape.Ring.along(0.01f, 1.4f, 0, 4.0f, 4.0f)}, 28, 0, KnifeShading.ENERGY,
				Shape.OPEN, Shape.OPEN, false);
	}

	/** Two horns curving out and up from the top of the head, dark with glowing tips. */
	private static void horns(Shape m) {
		for (int side = -1; side <= 1; side += 2) {
			Shape.Ring[] horn = new Shape.Ring[12];
			for (int i = 0; i < horn.length; i++) {
				float f = i / (float) (horn.length - 1);
				float x = side * (2.6f + 2.2f * f - 0.4f * f * f);
				float y = -0.2f + 4.4f * f * f;
				float r = Math.max(0.06f, 0.85f * (1 - f));
				horn[i] = new Shape.Ring(x, y, -0.5f, side * (2.2f - 0.8f * f), 8.8f * f + 0.6f, 0, r, r, null);
			}
			m.loft(horn, 14, 0, KnifeShading.GUARD, Shape.CAP, Shape.OPEN, false);
		}
	}

	/** Two pointed ears with a glowing inside. */
	private static void catEars(Shape m) {
		for (int side = -1; side <= 1; side += 2) {
			float x = side * 2.4f;
			m.plate(new float[] {x - 1.6f, 0, x + 1.6f, 0, x + side * 0.6f, 3.0f}, -0.35f, 0.35f, 0.15f, KnifeShading.POLYMER);
			m.plate(new float[] {x - 0.9f, 0.3f, x + 0.9f, 0.3f, x + side * 0.4f, 2.1f}, -0.45f, -0.3f, 0.05f, KnifeShading.HANDLE_PAINT);
		}
	}

	/**
	 * One wing (the right; the left is drawn mirrored), spread wide behind you: an arched leading edge rising from
	 * the shoulder to above your head and out to the tip, with three layers of pointed feathers hanging from it,
	 * short coverts over medium secondaries near the body and long primaries out at the tip. Y is up.
	 */
	private static void featherWing(Shape m, int material) {
		float[][] bone = {{0.6f, 0.6f}, {3.4f, 4.6f}, {7.0f, 7.0f}, {11.0f, 7.5f}, {15.0f, 6.4f}, {18.2f, 4.6f}};
		Shape.Ring[] edge = new Shape.Ring[bone.length];
		for (int i = 0; i < bone.length; i++) {
			int a = Math.max(0, i - 1);
			int c = Math.min(bone.length - 1, i + 1);
			float r = 0.62f - 0.34f * i / (bone.length - 1);
			edge[i] = new Shape.Ring(bone[i][0], bone[i][1], 0.3f, bone[c][0] - bone[a][0], bone[c][1] - bone[a][1], 0, r, r * 0.8f, null);
		}
		m.loft(edge, 10, 0, material, Shape.DOME, Shape.DOME, false);
		featherRow(m, bone, 10, 0.48f, 1.0f, 10.0f, 13.6f, 82, 34, 1.75f, -0.12f, material);
		featherRow(m, bone, 10, 0.0f, 0.56f, 7.6f, 10.2f, 100, 84, 1.8f, 0.02f, material);
		featherRow(m, bone, 14, 0.02f, 0.98f, 3.6f, 5.4f, 96, 50, 1.6f, 0.16f, material);
	}

	/** A row of {@code count} feathers hung from the wing's edge between {@code from} and {@code to} (0..1 along it). */
	private static void featherRow(Shape m, float[][] bone, int count, float from, float to, float lengthNear, float lengthFar, float angleNear,
			float angleFar, float width, float z, int material) {
		for (int i = 0; i < count; i++) {
			float f = count == 1 ? 0 : i / (float) (count - 1);
			float t = from + (to - from) * f;
			float[] at = along(bone, t);
			float length = lengthNear + (lengthFar - lengthNear) * f;
			float angle = angleNear + (angleFar - angleNear) * f;
			feather(m, at[0], at[1] - 0.2f, Math.toRadians(angle), length, width, z + 0.025f * (i % 2), material);
		}
	}

	/** The point {@code t} (0..1) of the way along a path of points. */
	private static float[] along(float[][] path, float t) {
		float total = 0;
		float[] lengths = new float[path.length - 1];
		for (int i = 0; i + 1 < path.length; i++) {
			lengths[i] = (float) Math.hypot(path[i + 1][0] - path[i][0], path[i + 1][1] - path[i][1]);
			total += lengths[i];
		}
		float want = t * total;
		for (int i = 0; i < lengths.length; i++) {
			if (want <= lengths[i] || i == lengths.length - 1) {
				float k = Math.min(1, want / Math.max(1e-4f, lengths[i]));
				return new float[] {path[i][0] + (path[i + 1][0] - path[i][0]) * k, path[i][1] + (path[i + 1][1] - path[i][1]) * k};
			}
			want -= lengths[i];
		}
		return path[path.length - 1];
	}

	/** One feather from (x, y), pointing {@code angle} below the horizontal outward line, {@code length} long: a pointed blade. */
	private static void feather(Shape m, float x, float y, double angle, float length, float width, float z, int material) {
		float dx = (float) Math.cos(angle);
		float dy = -(float) Math.sin(angle);
		float px = -dy;
		float py = dx;
		int steps = 6;
		float[] outline = new float[2 * (2 * steps + 1)];
		int n = 0;
		for (int i = 0; i < steps; i++) {
			float t = i / (float) steps;
			float half = width * 0.5f * (0.55f + 0.45f * (float) Math.sin(Math.PI * Math.min(1, t * 1.6f))) * (1 - 0.55f * t * t);
			outline[n++] = x + dx * length * t + px * half;
			outline[n++] = y + dy * length * t + py * half;
		}
		outline[n++] = x + dx * length;
		outline[n++] = y + dy * length;
		for (int i = steps - 1; i >= 0; i--) {
			float t = i / (float) steps;
			float half = width * 0.5f * (0.55f + 0.45f * (float) Math.sin(Math.PI * Math.min(1, t * 1.6f))) * (1 - 0.55f * t * t);
			outline[n++] = x + dx * length * t - px * half;
			outline[n++] = y + dy * length * t - py * half;
		}
		m.plate(outline, z - 0.07f, z + 0.07f, 0.04f, material);
	}

	/** A bat's wing: three long fingers and a membrane between them, dark with a glowing edge. */
	private static void demonWing(Shape m) {
		float[][] tips = {{9.5f, 6.5f}, {10.5f, 1.0f}, {7.5f, -3.5f}};
		float[] shoulder = {0.4f, 2.2f};
		float[] elbow = {4.2f, 4.6f};
		m.loft(new Shape.Ring[] {new Shape.Ring(shoulder[0], shoulder[1], 0, 1, 0.6f, 0, 0.45f, 0.4f, null),
				new Shape.Ring(elbow[0], elbow[1], 0, 1, 0.6f, 0, 0.35f, 0.3f, null)}, 10, 0, KnifeShading.POLYMER, Shape.DOME, Shape.DOME, false);
		float[] membrane = new float[2 * (tips.length + 2) + 2 * tips.length];
		int n = 0;
		membrane[n++] = shoulder[0];
		membrane[n++] = shoulder[1] - 0.4f;
		for (int i = tips.length - 1; i >= 0; i--) {
			float[] tip = tips[i];
			if (i < tips.length - 1) {
				float[] prev = tips[i + 1];
				membrane[n++] = (tip[0] + prev[0]) / 2 - 1.6f;
				membrane[n++] = (tip[1] + prev[1]) / 2 + 0.2f;
			} else {
				membrane[n++] = 3.5f;
				membrane[n++] = -2.0f;
			}
			membrane[n++] = tip[0];
			membrane[n++] = tip[1];
		}
		membrane[n++] = elbow[0];
		membrane[n++] = elbow[1] + 0.3f;
		m.plate(java.util.Arrays.copyOf(membrane, n), -0.05f, 0.05f, 0.03f, KnifeShading.POLYMER);
		for (float[] tip : tips) {
			float dx = tip[0] - elbow[0];
			float dy = tip[1] - elbow[1];
			m.loft(new Shape.Ring[] {new Shape.Ring(elbow[0], elbow[1], 0, dx, dy, 0, 0.26f, 0.22f, null),
					new Shape.Ring(tip[0], tip[1], 0, dx, dy, 0, 0.08f, 0.08f, null)}, 8, 0, KnifeShading.POLYMER, Shape.OPEN, Shape.DOME, false);
			m.loft(new Shape.Ring[] {new Shape.Ring(tip[0] - dx * 0.08f, tip[1] - dy * 0.08f, 0, dx, dy, 0, 0.12f, 0.12f, null),
					new Shape.Ring(tip[0] + dx * 0.04f, tip[1] + dy * 0.04f, 0, dx, dy, 0, 0.02f, 0.02f, null)}, 8, 0, KnifeShading.ENERGY,
					Shape.OPEN, Shape.OPEN, false);
		}
	}

	/** Draws a shape with the knife shader in the chosen finish, glowing in the theme's accent colour. */
	private static void emit(Shape mesh, PoseStack pose, SubmitNodeCollector collector, int light, Finish finish, boolean shell) {
		int glowRgb = Accent.get();
		float[] hsv = Widgets.toHsv(glowRgb);
		int frame = KnifeShading.frame(63, 0, 1.0f, 0.35f);
		if (KnifeShading.custom()) {
			collector.submitCustomGeometry(pose, Compat.knifeShaded(), (last, out) -> mesh.emit(last, out, light, finish.id, hsv[0], hsv[1], frame));
			if (shell) {
				int color = 0x66000000 | glowRgb & 0xFFFFFF;
				collector.submitCustomGeometry(pose, Compat.knifeGlow(), (last, out) -> mesh.emitGlow(last, out, color, 0.4f));
			}
		} else {
			collector.submitCustomGeometry(pose, Compat.knifeRenderType(KnifeAtlas.get(), false),
					(last, out) -> mesh.emitPlain(last, out, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, finish.id, glowRgb));
		}
	}

	/** The hat, on a head whose model frame {@code pose} is in (Minecraft's: Y down, the face towards -Z). */
	public static void drawHat(PoseStack pose, SubmitNodeCollector collector, int light, float ageTicks) {
		String hat = Modules.HATS_TYPE.get();
		pose.pushPose();
		pose.translate(0, -8.05f / 16f, 0);
		pose.scale(1 / 16f, -1 / 16f, 1 / 16f);
		Finish finish = Finish.ROSE;
		switch (hat) {
			case "Halo" -> {
				pose.translate(0, 3.6f + 0.35f * (float) Math.sin(ageTicks * 0.08f), 0);
				pose.mulPose(Axis.YP.rotationDegrees(ageTicks * 1.5f));
				pose.mulPose(Axis.XP.rotationDegrees(-8));
			}
			case "Horns" -> finish = Finish.VOID;
			case "Glow Cone" -> {
				pose.translate(0, 0.6f, 0);
				int color = 0xA0000000 | Accent.get() & 0xFFFFFF;
				Shape cone = shape("Glow Cone");
				if (KnifeShading.custom()) {
					collector.submitCustomGeometry(pose, Compat.knifeGlow(), (last, out) -> cone.emitGlow(last, out, color, 0));
					collector.submitCustomGeometry(pose, Compat.knifeLine(), (last, out) -> cone.emitGlow(last, out, color, 0.08f));
				}
				emit(shape("Glow Cone rim"), pose, collector, light, finish, true);
				pose.popPose();
				return;
			}
			case "Cat Ears" -> pose.translate(0, -0.2f, 1.0f);
			default -> {
			}
		}
		emit(shape(hat), pose, collector, light, finish, hat.equals("Halo"));
		pose.popPose();
	}

	/** The wings, on a body whose model frame {@code pose} is in: both, mirrored, spread wide from the upper back, beating slowly. */
	public static void drawWings(PoseStack pose, SubmitNodeCollector collector, int light, float ageTicks) {
		String type = Modules.WINGS_TYPE.get();
		Shape wing = shape(type);
		float flap = Modules.WINGS_FLAP.get() ? (float) Math.sin(ageTicks * 0.08f) * 9 : 0;
		float size = (float) (Modules.WINGS_SIZE.get() / 100.0);
		boolean feathered = !type.equals("Demon");
		for (int side = -1; side <= 1; side += 2) {
			pose.pushPose();
			pose.translate(side * 0.8f / 16f, 3.2f / 16f, 2.4f / 16f);
			pose.scale(side / 16f * size, -1 / 16f * size, 1 / 16f * size);
			pose.mulPose(Axis.YP.rotationDegrees((feathered ? -12 : -28) - flap));
			if (!feathered) pose.translate(0, -3.0f, 0);
			emit(wing, pose, collector, light, type.equals("Demon") ? Finish.VOID : Finish.FROST, type.equals("Energy"));
			pose.popPose();
		}
	}

	/**
	 * Your knife in your hand in third person. {@code pose} is in the hand's frame as Minecraft places a held
	 * item (after its turn to the hand); the knife is posed like a sword is.
	 */
	public static void drawHeldKnife(KnifeModel model, HumanoidArm arm, PoseStack pose, SubmitNodeCollector collector, int light) {
		boolean left = arm == HumanoidArm.LEFT;
		pose.pushPose();
		pose.translate(0, 4 / 16f, 0.5f / 16f);
		pose.mulPose(Axis.YP.rotationDegrees(left ? 90 : -90));
		pose.mulPose(Axis.ZP.rotationDegrees(left ? -55 : 55));
		pose.scale(0.85f, 0.85f, 0.85f);
		pose.translate(-3.5f / 16f, -3.5f / 16f, 0);
		pose.mulPose(Axis.ZP.rotationDegrees(-45));
		float unit = model.scale * 0.9f / 16f;
		pose.scale(unit, unit, unit);
		pose.translate(0, -model.grip, 0);
		KnifeRenderer.drawStill(model, pose, collector, light);
		pose.popPose();
	}
}
