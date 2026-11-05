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
 * knife shader: the Grim Reaper (a hooded robe, a skull with glowing eyes and his scythe), Timber (a carved log
 * with a big grin, on stick arms and legs, swinging a bat), the Void Walker (dark crystal with glowing seams) and
 * the Astronaut (a white suit, a helmet with a dark visor and a backpack). Each limb is built round its joint in
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
		m.loft(new Shape.Ring[] {Shape.Ring.along(x, y0, z, r0, r0), Shape.Ring.along(x, y1, z, r1, r1)}, 16, 0, material, Shape.CAP, Shape.CAP, false);
	}

	/** A ball. */
	private static void ball(Shape m, float x, float y, float z, float r, int material) {
		int n = 8;
		Shape.Ring[] rings = new Shape.Ring[n + 1];
		for (int i = 0; i <= n; i++) {
			double a = Math.PI * i / n;
			float rr = Math.max(0.01f, (float) Math.sin(a) * r);
			rings[i] = Shape.Ring.along(x, y - (float) Math.cos(a) * r, z, rr, rr);
		}
		m.loft(rings, 16, 0, material, Shape.OPEN, Shape.OPEN, false);
	}

	private static float[] rect(float x0, float y0, float x1, float y1) {
		return new float[] {x0, y0, x1, y0, x1, y1, x0, y1};
	}

	private static float[] diamond(float x, float y, float hx, float hy) {
		return new float[] {x, y - hy, x + hx, y, x, y + hy, x - hx, y};
	}

	/** A hooded robe with a glowing rope belt, a skull with violet eyes, wide sleeves and bony hands. */
	private static void reaper(EnumMap<Limb, Shape> s) {
		Shape head = s.get(Limb.HEAD);
		head.loft(new Shape.Ring[] {Shape.Ring.along(0, 0.6f, 0.5f, 4.5f, 4.5f), Shape.Ring.along(0, -4, 0.6f, 5.0f, 5.0f),
				Shape.Ring.along(0, -8, 0.9f, 4.6f, 4.7f), Shape.Ring.along(0, -9.3f, 1.6f, 3.0f, 3.3f), Shape.Ring.along(0, -9.9f, 2.6f, 0.6f, 0.8f)},
				24, 0.2f, KnifeShading.RUBBER, Shape.OPEN, Shape.DOME, false);
		head.plate(rect(-3.6f, -7.4f, 3.6f, -0.2f), -4.75f, -4.6f, 0.6f, KnifeShading.RUBBER);
		box(head, -2.7f, -6.9f, -4.95f, 2.7f, -1.1f, -3.4f, 0.75f, KnifeShading.FEATHER);
		for (int side = -1; side <= 1; side += 2) {
			head.plate(diamond(side * 1.15f, -4.3f, 0.9f, 0.8f), -5.05f, -4.9f, 0.1f, KnifeShading.RUBBER);
			head.pin(side * 1.15f, -4.3f, -5.15f, -5.0f, 0.36f, KnifeShading.GEM_RIM);
		}
		head.plate(new float[] {-0.4f, -3.0f, 0.4f, -3.0f, 0, -3.8f}, -5.05f, -4.9f, 0.05f, KnifeShading.RUBBER);
		for (int i = -2; i <= 2; i++) head.plate(rect(i * 0.9f - 0.3f, -2.0f, i * 0.9f + 0.3f, -1.5f), -5.05f, -4.9f, 0.05f, KnifeShading.RUBBER);

		Shape body = s.get(Limb.BODY);
		body.loft(new Shape.Ring[] {Shape.Ring.along(0, -0.3f, 0, 4.7f, 2.8f), Shape.Ring.along(0, 6, 0, 4.9f, 2.9f), Shape.Ring.along(0, 12.2f, 0, 5.3f, 3.2f)},
				24, 0.55f, KnifeShading.RUBBER, Shape.CAP, Shape.CAP, false);
		body.loft(new Shape.Ring[] {Shape.Ring.along(0, 7.1f, 0, 5.05f, 3.05f), Shape.Ring.along(0, 7.8f, 0, 5.08f, 3.08f)}, 24, 0.55f,
				KnifeShading.HANDLE_PAINT, Shape.OPEN, Shape.OPEN, false);
		body.plate(rect(-0.35f, 0.2f, 0.35f, 12f), -3.1f, -2.95f, 0.1f, KnifeShading.HANDLE_PAINT);

		for (Limb arm : new Limb[] {Limb.RIGHT_ARM, Limb.LEFT_ARM}) {
			Shape m = s.get(arm);
			float x = arm == Limb.RIGHT_ARM ? -1 : 1;
			m.loft(new Shape.Ring[] {Shape.Ring.along(x, -2.4f, 0, 2.5f, 2.5f), Shape.Ring.along(x, 6, 0, 2.7f, 2.7f), Shape.Ring.along(x, 8.6f, 0, 3.2f, 3.2f)},
					20, 0.35f, KnifeShading.RUBBER, Shape.CAP, Shape.OPEN, false);
			box(m, x - 1.3f, 8.2f, -1.3f, x + 1.3f, 10.8f, 1.3f, 0.6f, KnifeShading.FEATHER);
		}
		for (Limb leg : new Limb[] {Limb.RIGHT_LEG, Limb.LEFT_LEG}) {
			s.get(leg).loft(new Shape.Ring[] {Shape.Ring.along(0, -0.4f, 0, 2.5f, 2.5f), Shape.Ring.along(0, 12.2f, 0, 3.0f, 3.0f)}, 20, 0.45f,
					KnifeShading.RUBBER, Shape.CAP, Shape.CAP, false);
		}
	}

	/** A carved log for head and body, a big grin and round eyes, on stick arms and legs. */
	private static void timber(EnumMap<Limb, Shape> s) {
		Shape head = s.get(Limb.HEAD);
		tube(head, 0, 0, 0.4f, -8.4f, 4.4f, 4.3f, KnifeShading.WOOD);
		for (int side = -1; side <= 1; side += 2) {
			ball(head, side * 1.7f, -4.6f, -3.9f, 1.25f, KnifeShading.FEATHER);
			ball(head, side * 1.55f, -4.5f, -4.95f, 0.55f, KnifeShading.RUBBER);
			head.plate(new float[] {side * 0.7f, -6.5f, side * 2.9f, -6.9f, side * 2.8f, -6.3f, side * 0.7f, -6.0f}, -4.6f, -4.2f, 0.12f,
					KnifeShading.RUBBER);
		}
		head.plate(new float[] {-2.9f, -2.6f, 2.9f, -2.6f, 2.2f, -1.2f, 0, -0.7f, -2.2f, -1.2f}, -4.6f, -4.25f, 0.15f, KnifeShading.RUBBER);
		head.plate(rect(-2.2f, -2.45f, 2.2f, -1.85f), -4.75f, -4.55f, 0.08f, KnifeShading.FEATHER);
		tube(head, 0, -3.6f, -3.4f, -3.5f, 0.6f, 0.6f, KnifeShading.WOOD);
		tube(s.get(Limb.BODY), 0, 0, -0.2f, 12.2f, 4.4f, 4.5f, KnifeShading.WOOD);
		for (Limb arm : new Limb[] {Limb.RIGHT_ARM, Limb.LEFT_ARM}) {
			float x = arm == Limb.RIGHT_ARM ? -1.5f : 1.5f;
			tube(s.get(arm), x, 0, -1.2f, 9.2f, 0.95f, 0.85f, KnifeShading.WOOD);
			ball(s.get(arm), x, 9.6f, 0, 1.2f, KnifeShading.WOOD);
		}
		for (Limb leg : new Limb[] {Limb.RIGHT_LEG, Limb.LEFT_LEG}) {
			tube(s.get(leg), 0, 0, 0, 11.4f, 1.05f, 1.0f, KnifeShading.WOOD);
			box(s.get(leg), -1.4f, 11.0f, -2.2f, 1.4f, 12.0f, 1.2f, 0.6f, KnifeShading.WOOD);
		}
	}

	/** Dark crystal plates with glowing seams, a lit core and a glowing visor. */
	private static void voidWalker(EnumMap<Limb, Shape> s) {
		Shape head = s.get(Limb.HEAD);
		box(head, -4.1f, -8.2f, -4.1f, 4.1f, 0.1f, 4.1f, 0.7f, KnifeShading.GEM);
		box(head, -3.2f, -5.2f, -4.45f, 3.2f, -3.8f, -3.9f, 0.6f, KnifeShading.GEM_RIM);
		Shape body = s.get(Limb.BODY);
		box(body, -4.1f, -0.1f, -2.1f, 4.1f, 12.1f, 2.1f, 0.7f, KnifeShading.GEM);
		body.plate(diamond(0, 4.2f, 1.5f, 2.0f), -2.5f, -2.0f, 0.1f, KnifeShading.ENERGY);
		box(body, -4.2f, 7.4f, -2.2f, 4.2f, 8.0f, 2.2f, 0.7f, KnifeShading.GEM_RIM);
		for (Limb arm : new Limb[] {Limb.RIGHT_ARM, Limb.LEFT_ARM}) {
			float x = arm == Limb.RIGHT_ARM ? -1 : 1;
			box(s.get(arm), x - 2.1f, -2.1f, -2.1f, x + 2.1f, 10.1f, 2.1f, 0.7f, KnifeShading.GEM);
			box(s.get(arm), x - 2.2f, 3.6f, -2.2f, x + 2.2f, 4.2f, 2.2f, 0.7f, KnifeShading.GEM_RIM);
		}
		for (Limb leg : new Limb[] {Limb.RIGHT_LEG, Limb.LEFT_LEG}) {
			box(s.get(leg), -2.1f, -0.1f, -2.1f, 2.1f, 12.1f, 2.1f, 0.7f, KnifeShading.GEM);
			box(s.get(leg), -2.2f, 5.6f, -2.2f, 2.2f, 6.2f, 2.2f, 0.7f, KnifeShading.GEM_RIM);
		}
	}

	/** A white suit with a chest panel and a backpack, boots, and a round helmet with a dark visor. */
	private static void astronaut(EnumMap<Limb, Shape> s) {
		Shape head = s.get(Limb.HEAD);
		ball(head, 0, -4.2f, 0, 5.4f, KnifeShading.FEATHER);
		box(head, -3.6f, -6.6f, -5.6f, 3.6f, -2.2f, -4.2f, 0.85f, KnifeShading.GEM);
		tube(head, -4.2f, 0, -7.2f, -10.2f, 0.25f, 0.2f, KnifeShading.HARDWARE);
		ball(head, -4.2f, -10.4f, 0, 0.45f, KnifeShading.GEM_RIM);
		Shape body = s.get(Limb.BODY);
		box(body, -4.6f, -0.3f, -2.6f, 4.6f, 12.3f, 2.6f, 0.75f, KnifeShading.FEATHER);
		box(body, -2.4f, 2.0f, -3.0f, 2.4f, 6.0f, -2.4f, 0.8f, KnifeShading.HARDWARE);
		body.plate(rect(1.2f, 2.4f, 2.0f, 3.2f), -3.15f, -2.95f, 0.1f, KnifeShading.GEM_RIM);
		box(body, -3.8f, 0.6f, 2.4f, 3.8f, 10.2f, 5.2f, 0.8f, KnifeShading.HARDWARE);
		body.plate(rect(-4.6f, 1.2f, -3.6f, 2.6f), -2.75f, -2.6f, 0.1f, KnifeShading.HANDLE_PAINT);
		for (Limb arm : new Limb[] {Limb.RIGHT_ARM, Limb.LEFT_ARM}) {
			float x = arm == Limb.RIGHT_ARM ? -1 : 1;
			box(s.get(arm), x - 2.5f, -2.5f, -2.5f, x + 2.5f, 8.6f, 2.5f, 0.8f, KnifeShading.FEATHER);
			box(s.get(arm), x - 2.2f, 8.4f, -2.2f, x + 2.2f, 10.6f, 2.2f, 0.8f, KnifeShading.HARDWARE);
		}
		for (Limb leg : new Limb[] {Limb.RIGHT_LEG, Limb.LEFT_LEG}) {
			box(s.get(leg), -2.5f, -0.3f, -2.5f, 2.5f, 9.6f, 2.5f, 0.8f, KnifeShading.FEATHER);
			box(s.get(leg), -2.6f, 9.4f, -3.0f, 2.6f, 12.3f, 2.6f, 0.8f, KnifeShading.HARDWARE);
		}
	}

	/** A wooden bat: a thin grip with a knob widening to a round-ended barrel, along +Y in knife pixels. */
	private static Shape bat() {
		if (bat == null) {
			bat = new Shape();
			bat.loft(new Shape.Ring[] {Shape.Ring.along(0, -5.4f, 0, 0.7f, 0.7f), Shape.Ring.along(0, -4.9f, 0, 0.7f, 0.7f),
					Shape.Ring.along(0, -4.7f, 0, 0.42f, 0.42f), Shape.Ring.along(0, 0.5f, 0, 0.48f, 0.48f), Shape.Ring.along(0, 5.5f, 0, 1.05f, 1.05f),
					Shape.Ring.along(0, 10.2f, 0, 1.15f, 1.15f)}, 16, 0, KnifeShading.WOOD, Shape.CAP, Shape.DOME, false);
		}
		return bat;
	}

	private static void emit(Shape mesh, PoseStack pose, SubmitNodeCollector collector, int light) {
		int glowRgb = Accent.get();
		float[] hsv = Widgets.toHsv(glowRgb);
		int frame = KnifeShading.frame(63, 0, 1.0f, 0.3f);
		Finish finish = Modules.MODEL_SKIN_TYPE.is("Void Walker") ? Finish.CRYSTAL : Finish.VOID;
		if (KnifeShading.custom()) {
			collector.submitCustomGeometry(pose, Compat.knifeShaded(), (last, out) -> mesh.emit(last, out, light, finish.id, hsv[0], hsv[1], frame));
		} else {
			collector.submitCustomGeometry(pose, Compat.knifeRenderType(KnifeAtlas.get(), false),
					(last, out) -> mesh.emitPlain(last, out, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, finish.id, glowRgb));
		}
	}

	/** Draws your 3D skin on the player model's posed parts. */
	public static void draw(PlayerModel model, HumanoidArm mainArm, PoseStack pose, SubmitNodeCollector collector, int light) {
		EnumMap<Limb, Shape> parts = shapes(Modules.MODEL_SKIN_TYPE.get());
		draw(model.head, parts.get(Limb.HEAD), pose, collector, light);
		draw(model.body, parts.get(Limb.BODY), pose, collector, light);
		draw(model.rightArm, parts.get(Limb.RIGHT_ARM), pose, collector, light);
		draw(model.leftArm, parts.get(Limb.LEFT_ARM), pose, collector, light);
		draw(model.rightLeg, parts.get(Limb.RIGHT_LEG), pose, collector, light);
		draw(model.leftLeg, parts.get(Limb.LEFT_LEG), pose, collector, light);
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
			Cosmetics.drawHeldKnife(skin.equals("Void Walker") ? KnifeModel.PRISM : KnifeModel.REAPER, arm, pose, collector, light);
		}
	}
}
