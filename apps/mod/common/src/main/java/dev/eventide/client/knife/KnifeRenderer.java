package dev.eventide.client.knife;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.eventide.client.Hooks;
import dev.eventide.client.compat.Compat;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.module.Knives;
import dev.eventide.client.module.Modules;
import dev.eventide.client.ui.Widgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the knife in your hand, in place of the held item, the way Minecraft holds things: your own arm (your
 * skin, slim or wide, with its sleeve) comes in from the bottom corner exactly where Minecraft puts it, and the
 * knife's handle runs through the fist with the blade forward. {@link KnifeAnimator} and the Knives settings
 * move the arm about its shoulder, twist the wrist and turn the knife in the fingers. Around the knife (and,
 * if you like, the arm) a soft shell of light makes the luminous outline. The hand is drawn after the world with
 * the depth cleared, so it never sinks into walls; the knife is also kept clear of the camera's near plane, so
 * big flips never cut it off.
 */
public final class KnifeRenderer {
	/** Where Minecraft's first-person arm starts, in blocks from the eye: right, down, forward. */
	private static final float BASE_X = 0.64f;
	private static final float BASE_Y = -0.6f;
	private static final float BASE_Z = -0.72f;
	/** How far down the arm the middle of the fist is, in the arm model's pixels from its shoulder pivot. */
	private static final float FIST = 8.6f;
	/**
	 * Where the handle sits on the fist, in the arm model's pixels from its middle: out on the side the camera
	 * sees (the arm's outer side, top and front), so the knife crosses in front of the hand, as when you see
	 * someone hold one.
	 */
	private static final float[] SEAT = {-1.3f, -1.15f, -1.35f};
	/** One knife pixel, in the arm model's blocks: a knife is about twice as long as the arm is wide. */
	private static final float UNIT = 0.85f / 16f;
	/**
	 * The knife's frame (handle +Y, edge +X, near flat +Z) in the arm's, worked out so that as you see it the
	 * blade rises up and to the right out of the fist, level with the screen, its edge facing down and its flat
	 * turned towards you (a little past face-on, so its bevels catch the light).
	 */
	private static final Quaternionf HOLD = new Quaternionf().setFromNormalized(new Matrix3f(0.0728f, -0.4603f, 0.8848f, -0.4473f, 0.7779f, 0.4415f,
			-0.8914f, -0.4278f, -0.1493f));
	/** The viewmodel field of view the framing above is made for. */
	private static final float CLASSIC_FOV = 70;
	/** How far the animations' moves (in blocks) carry. */
	private static final float MOVE = 0.6f;
	private static final float NEAR = 0.12f;
	/** How far the glowing shell stands off the knife, in its pixels. */
	private static final float GLOW_WIDTH = 0.34f;
	/** How far the crisp outline stands off the knife, in its pixels. */
	private static final float LINE_WIDTH = 0.12f;

	private static final PoseStack PROBE = new PoseStack();
	private static final Vector3f POINT = new Vector3f();

	private static final KnifeAnimator ANIMATOR = new KnifeAnimator();
	private static final float[] POSE = Clip.rest();
	/** A pose (or a clip at a point in it) to show instead of the animation, for the in-game test's pictures. */
	private static float[] preview = null;
	private static int lastSlot = -1;
	private static KnifeModel lastModel = null;
	private static boolean shown = false;
	/** When the glint after the last swing started running up the blade, or 0. */
	private static long glintStarted = 0;
	/** When the last swing's camera kick peaks, and how far it turns the view (degrees). */
	private static long kickAt = 0;
	private static float kickYaw = 0;
	private static float kickPitch = 0;
	private static boolean mirrored = false;

	private KnifeRenderer() {
	}

	/** How fast a kind of animation plays: its slider, times the Animation style. */
	private static float speed(dev.eventide.client.module.Setting.Slider slider) {
		float style = switch (Modules.KNIVES_STYLE.index()) {
			case 1 -> 1.25f;
			case 2 -> 0.85f;
			default -> 1f;
		};
		return (float) (slider.get() / 100.0) * style;
	}

	/** How much the knife sways and bobs for the Animation style. */
	private static float weight() {
		return switch (Modules.KNIVES_STYLE.index()) {
			case 1 -> 0.7f;
			case 2 -> 1.3f;
			default -> 1f;
		};
	}

	public static void inspect(KnifeModel model) {
		ANIMATOR.play(model.inspect(), speed(Modules.KNIVES_INSPECT_SPEED), System.nanoTime());
		Hooks.knifeInspectRan = true;
	}

	public static void use(KnifeModel model) {
		float speed = speed(Modules.KNIVES_SWING_SPEED);
		ANIMATOR.play(model.use(), speed, System.nanoTime());
		glintStarted = System.nanoTime() + (long) (400_000_000L / speed);
		kick(420 / speed, 0.4f, 1.1f * weight());
		Hooks.knifeSlashRan = true;
	}

	public static void slash(KnifeModel model) {
		var gameMode = Minecraft.getInstance().gameMode;
		boolean mining = gameMode != null && gameMode.isDestroying();
		float speed = speed(Modules.KNIVES_SWING_SPEED);
		int which = ANIMATOR.slash(model, speed, System.nanoTime(), mining);
		if (which < 0) return;
		glintStarted = System.nanoTime() + (long) (100_000_000L / speed);
		kick(140 / speed, (which == 0 ? -0.6f : 0.6f) * weight(), 0.25f * weight());
		Hooks.knifeSlashRan = true;
	}

	/** A small turn of the view peaking {@code millis} from now, the way a hit punches the camera. */
	private static void kick(float millis, float yaw, float pitch) {
		kickAt = System.nanoTime() + (long) (millis * 1_000_000L);
		kickYaw = mirrored ? -yaw : yaw;
		kickPitch = pitch;
	}

	/** How far the camera kick turns the view right now (degrees, positive turns right). */
	public static float kickYaw() {
		return kickYaw * kickShape();
	}

	/** How far the camera kick tips the view right now (degrees, positive looks down). */
	public static float kickPitch() {
		return kickPitch * kickShape();
	}

	private static float kickShape() {
		if (kickAt == 0 || !shown || !Modules.KNIVES_KICK.get()) return 0;
		float t = (System.nanoTime() - kickAt) / 1e9f + 0.06f;
		if (t <= 0) return 0;
		if (t > 0.6f) {
			kickAt = 0;
			return 0;
		}
		float u = t / 0.06f;
		return u * (float) Math.exp(1 - u);
	}

	/**
	 * For the in-game test: holds the knife still at one point of an animation ("draw", "inspect", "use",
	 * "slash0" or "slash1", {@code t} 0..1 through it), or at rest with one pose channel set ("channel3" with
	 * {@code t} as the value). Null lets the animation play again.
	 */
	public static void preview(KnifeModel model, String which, float t) {
		if (which == null) {
			preview = null;
			return;
		}
		float[] pose = Clip.rest();
		if (which.startsWith("channel")) {
			pose[Integer.parseInt(which.substring(7))] = t;
		} else {
			Clip clip = switch (which) {
				case "draw" -> model.draw();
				case "inspect" -> model.inspect();
				case "use" -> model.use();
				default -> model.slash(which.startsWith("slash") ? Integer.parseInt(which.substring(5)) : 0);
			};
			clip.sample(t, pose);
		}
		preview = pose;
	}

	/**
	 * For the in-game test: how far the near-plane check moves this knife when it's at rest. Nothing at
	 * rest is near the camera, so anything but 0 means the check is measuring in the wrong space.
	 */
	public static float restPush(KnifeModel model) {
		float[] saved = POSE.clone();
		System.arraycopy(Clip.rest(), 0, POSE, 0, Clip.CHANNELS);
		float push = Math.abs(nearPush(model, true, false));
		System.arraycopy(saved, 0, POSE, 0, Clip.CHANNELS);
		return push;
	}

	/**
	 * For the in-game test: plays a draw, quick slashes, an inspect cut short by a slash and a stab on a
	 * made-up clock at 240 frames a second, and returns the biggest change in turning speed between two
	 * frames (degrees a second) over every turning channel, taking each turn the short way round (a whole
	 * turn looks the same as none). Smooth motion keeps it small; a knife that stops dead or starts at full
	 * speed shows up as a spike.
	 */
	public static float motionCheck(KnifeModel model) {
		KnifeAnimator animator = new KnifeAnimator();
		int[] angles = {Clip.PITCH, Clip.YAW, Clip.ROLL, Clip.K_PITCH, Clip.K_YAW, Clip.K_ROLL, Clip.HANDLE_A, Clip.HANDLE_B, Clip.SWEEP};
		long frame = 1_000_000_000L / 240;
		long start = 1_000_000_000L;
		float[] previous = Clip.rest();
		float[] current = Clip.rest();
		float[] lastSpeed = new float[Clip.CHANNELS];
		float worst = 0;
		for (int i = 0; i <= 240 * 4; i++) {
			long now = start + i * frame;
			int ms = Math.round(i * 1000f / 240);
			if (ms == 0) animator.play(model.draw(), 1, now);
			if (ms == 1000 || ms == 1150 || ms == 1300 || ms == 2300) animator.slash(model, 1, now, false);
			if (ms == 1600) animator.play(model.inspect(), 1, now);
			if (ms == 2700) animator.play(model.use(), 1, now);
			animator.pose(now, 0, 0, 0, 0, current);
			if (i > 0) {
				for (int c : angles) {
					float step = (current[c] - previous[c]) % 360f;
					if (step > 180) step -= 360;
					if (step < -180) step += 360;
					float speed = step * 240;
					if (i > 1) worst = Math.max(worst, Math.abs(speed - lastSpeed[c]));
					lastSpeed[c] = speed;
				}
			}
			System.arraycopy(current, 0, previous, 0, Clip.CHANNELS);
		}
		return worst;
	}

	/** Whether an inspect is playing, for the in-game test. */
	public static boolean inspecting() {
		KnifeModel model = lastModel;
		return model != null && ANIMATOR.playing(model.inspect());
	}

	/**
	 * Draws your main hand as a knife if it holds one; returns false to let Minecraft draw it as usual.
	 * Called where Minecraft draws the arm and item in first person.
	 */
	public static boolean render(AbstractClientPlayer player, HumanoidArm arm, ItemStack stack, PoseStack poseStack, SubmitNodeCollector collector,
			int light) {
		Minecraft mc = Minecraft.getInstance();
		int slot = player.getInventory().getSelectedSlot();
		KnifeModel model = Knives.animated() ? Knives.modelFor(player, stack, slot) : null;
		if (model == null) {
			shown = false;
			return false;
		}
		long now = System.nanoTime();
		if (!shown || slot != lastSlot || model != lastModel) {
			ANIMATOR.play(model.draw(), speed(Modules.KNIVES_DRAW_SPEED), now);
			Hooks.knifeDrawRan = true;
		}
		shown = true;
		lastSlot = slot;
		lastModel = model;
		if (player.isScoping()) return true;
		var camera = mc.gameRenderer.getMainCamera();
		var motion = player.getDeltaMovement();
		double yaw = Math.toRadians(player.getYRot());
		double forward = -Math.sin(yaw) * motion.x + Math.cos(yaw) * motion.z;
		double strafe = -Math.cos(yaw) * motion.x - Math.sin(yaw) * motion.z;
		ANIMATOR.moving(forward, strafe, motion.y, player.onGround(), player.isSprinting());
		float sway = weight();
		ANIMATOR.pose(now, camera.yRot(), camera.xRot(), speed(Modules.KNIVES_IDLE) * sway, preview != null ? 0 : speed(Modules.KNIVES_BOB) * sway, POSE);
		if (preview != null) System.arraycopy(preview, 0, POSE, 0, Clip.CHANNELS);
		int preset = Modules.KNIVES_PRESET.index();
		boolean right = preset == 2 ? false : arm == HumanoidArm.RIGHT;
		mirrored = !right;
		boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
		float push = nearPush(model, right, slim);

		poseStack.pushPose();
		float fov = fovScale();
		poseStack.scale(fov, fov, 1);
		armFrame(poseStack, right, push);
		boolean glow = Modules.KNIVES_GLOW.get() && KnifeShading.custom();
		if (Modules.KNIVES_ARM.get() && !player.isInvisible()) {
			drawArm(mc, player, right, poseStack, collector, light);
			dev.eventide.client.module.HandAura.draw(poseStack, collector, right);
		}
		fist(poseStack, right, slim);
		knife(poseStack, model);
		drawParts(model, poseStack, collector, light, glow);
		poseStack.popPose();
		Hooks.knifeRendered = true;
		return true;
	}

	/**
	 * How far to move the knife away so no part of it comes nearer than the near plane, worked out in the
	 * camera's own space (the hand's pose stack arrives already turned to the world, so its depth can't be
	 * read off it).
	 */
	private static float nearPush(KnifeModel model, boolean right, boolean slim) {
		PROBE.last().pose().identity();
		PROBE.last().normal().identity();
		PROBE.pushPose();
		float fov = fovScale();
		PROBE.scale(fov, fov, 1);
		armFrame(PROBE, right, 0);
		fist(PROBE, right, slim);
		knife(PROBE, model);
		float nearest = Float.NEGATIVE_INFINITY;
		for (float[] p : model.reach()) {
			nearest = Math.max(nearest, PROBE.last().pose().transformPosition(POINT.set(p[0], p[1], 0)).z);
		}
		PROBE.popPose();
		return nearest > -NEAR ? -NEAR - nearest : 0;
	}

	/**
	 * How much bigger (or smaller) the viewmodel field of view setting makes everything, against the 70 the
	 * framing is made for (Minecraft's own).
	 */
	private static float fovScale() {
		double base = Math.atan(Math.tan(Math.toRadians(CLASSIC_FOV / 2)) * 0.75);
		double now = Math.atan(Math.tan(Math.toRadians(Modules.KNIVES_FOV.get() / 2)) * 0.75);
		return (float) (Math.tan(base) / Math.tan(now));
	}

	/**
	 * Moves {@code pose} into the arm model's frame, the way Minecraft places its first-person arm, with the
	 * viewmodel settings and the animation on top: moves carry the whole arm, turns and tilts swing it about the
	 * shoulder, and a sweep turns it in the screen's plane.
	 */
	private static void armFrame(PoseStack pose, boolean right, float push) {
		float f = right ? 1 : -1;
		boolean center = Modules.KNIVES_PRESET.index() == 1;
		float x = Modules.KNIVES_X.getFloat() / 100f * 0.3f - (center ? 0.16f : 0);
		float y = Modules.KNIVES_Y.getFloat() / 100f * 0.3f + (center ? 0.04f : 0);
		float z = Modules.KNIVES_Z.getFloat() / 100f * 0.3f;
		pose.translate(f * (BASE_X + x + POSE[Clip.X] * MOVE), BASE_Y + y + POSE[Clip.Y] * MOVE, BASE_Z + z + POSE[Clip.Z] * MOVE + push);
		pose.mulPose(Axis.ZP.rotationDegrees(f * POSE[Clip.SWEEP]));
		pose.mulPose(Axis.YP.rotationDegrees(f * (45 + Modules.KNIVES_YAW.getFloat() + POSE[Clip.YAW])));
		pose.mulPose(Axis.XP.rotationDegrees(Modules.KNIVES_PITCH.getFloat() + POSE[Clip.PITCH]));
		pose.translate(f * -1.0f, 3.6f, 3.5f);
		pose.mulPose(Axis.ZP.rotationDegrees(f * 120));
		pose.mulPose(Axis.XP.rotationDegrees(200));
		pose.mulPose(Axis.YP.rotationDegrees(f * -135));
		pose.translate(f * 5.6f, 0, 0);
	}

	/**
	 * From the arm's frame to the fist: down the arm to the middle of the hand, the wrist's twist about the arm
	 * (inspects turn the blade's flat to you with it), then the knife's frame in the hand and its size.
	 */
	private static void fist(PoseStack pose, boolean right, boolean slim) {
		float f = right ? 1 : -1;
		pose.translate(f * -5 / 16f, 2 / 16f, 0);
		pose.mulPose(Axis.ZP.rotation(f * 0.1f));
		pose.translate((f * -(slim ? 0.5f : 1f) + f * SEAT[0]) / 16f, (FIST + SEAT[1]) / 16f, SEAT[2] / 16f);
		pose.mulPose(Axis.YP.rotationDegrees(f * -(Modules.KNIVES_ROLL.getFloat() + POSE[Clip.ROLL])));
		if (!right) pose.scale(-1, 1, 1);
		pose.mulPose(HOLD);
		float unit = UNIT * (float) (Modules.KNIVES_SCALE.get() / 100.0);
		pose.scale(unit, unit, unit);
	}

	/** From the hand to the knife's own frame: its size, then its turn in the fingers. */
	private static void knife(PoseStack pose, KnifeModel model) {
		pose.scale(model.scale, model.scale, model.scale);
		pose.translate(model.spinX, model.spinY - model.grip, 0);
		pose.mulPose(Axis.YP.rotationDegrees(POSE[Clip.K_YAW]));
		pose.mulPose(Axis.XP.rotationDegrees(POSE[Clip.K_PITCH]));
		pose.mulPose(Axis.ZP.rotationDegrees(POSE[Clip.K_ROLL]));
		pose.translate(-model.spinX, -model.spinY + POSE[Clip.SLIDE], 0);
	}

	/** Your own arm, drawn by Minecraft's player renderer exactly as in vanilla first person: your skin, slim or wide, and its sleeve. */
	private static void drawArm(Minecraft mc, AbstractClientPlayer player, boolean right, PoseStack pose, SubmitNodeCollector collector, int light) {
		var renderer = mc.getEntityRenderDispatcher().getPlayerRenderer(player);
		Identifier skin = player.getSkin().body().texturePath();
		pose.pushPose();
		if (right) {
			renderer.renderRightHand(pose, collector, light, skin, Modules.KNIVES_SLEEVE.get() && player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
		} else {
			renderer.renderLeftHand(pose, collector, light, skin, Modules.KNIVES_SLEEVE.get() && player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
		}
		pose.popPose();
	}

	/** The edge glow's colour with its strength (and the flash after a swing) in its alpha. */
	private static int glowColor(float scale) {
		float strength = (float) (Modules.KNIVES_GLOW_STRENGTH.get() / 100.0) * scale * (1 + 0.9f * glintPulse());
		int a = Math.round(Math.max(0, Math.min(1, strength * 0.6f)) * 255);
		return a << 24 | (Modules.KNIVES_GLOW_COLOR.get() & 0xFFFFFF);
	}

	/**
	 * The knife's parts, each moved for the pose: a balisong's handles about their pins, a folding blade about
	 * its pivot, an energy blade's length. All of them go through Eventide's knife shader (or plain entity
	 * shading under a shader pack) in one batch, then their glowing shells.
	 */
	private static void drawParts(KnifeModel model, PoseStack pose, SubmitNodeCollector collector, int light, boolean glow) {
		Finish finish = finish(model);
		boolean custom = KnifeShading.custom();
		var type = custom ? Compat.knifeShaded() : Compat.knifeRenderType(KnifeAtlas.get(), false);
		int glowRgb = Modules.KNIVES_GLOW_COLOR.get();
		float[] hsv = Widgets.toHsv(glowRgb);
		float strength = Modules.KNIVES_GLOW.get() ? (float) (Modules.KNIVES_GLOW_STRENGTH.get() / 100.0) * (1 + 0.6f * glintPulse()) : 0;
		int frame = KnifeShading.frame(model.ordinal(), glint(), strength, (float) (Modules.KNIVES_LIGHT.get() / 100.0));
		int shell = glowColor(1);
		int line = lineColor();
		float open = Math.max(0, POSE[Clip.OPEN]);
		for (KnifeModel.Part part : model.parts()) {
			pose.pushPose();
			switch (part.kind()) {
				case HANDLE_A, HANDLE_B -> {
					boolean a = part.kind() == KnifeModel.PartKind.HANDLE_A;
					float closed = a ? 180 : -180;
					float angle = closed * (1 - Math.min(1, open)) + (a ? POSE[Clip.HANDLE_A] : POSE[Clip.HANDLE_B]);
					pose.translate(part.pivotX(), part.pivotY(), 0);
					pose.mulPose(Axis.ZP.rotationDegrees(angle));
				}
				case FOLD -> {
					pose.translate(part.pivotX(), part.pivotY(), 0);
					pose.mulPose(Axis.ZP.rotationDegrees(-180 * (1 - open)));
					pose.translate(-part.pivotX(), -part.pivotY(), 0);
				}
				case ENERGY -> {
					if (open < 0.01f) {
						pose.popPose();
						continue;
					}
					pose.translate(part.pivotX(), part.pivotY(), 0);
					pose.scale(1, open, 1);
				}
				default -> {
				}
			}
			Shape mesh = part.mesh();
			int finishId = finish.id;
			if (custom) {
				collector.submitCustomGeometry(pose, type, (last, out) -> mesh.emit(last, out, light, finishId, hsv[0], hsv[1], frame));
				if (glow && strength > 0.01f) {
					float width = part.kind() == KnifeModel.PartKind.ENERGY ? GLOW_WIDTH * 2.2f : GLOW_WIDTH;
					collector.submitCustomGeometry(pose, Compat.knifeLine(), (last, out) -> mesh.emitGlow(last, out, line, LINE_WIDTH));
					collector.submitCustomGeometry(pose, Compat.knifeGlow(), (last, out) -> mesh.emitGlow(last, out, shell, width));
				}
			} else {
				collector.submitCustomGeometry(pose, type, (last, out) -> mesh.emitPlain(last, out, light, OverlayTexture.NO_OVERLAY, finishId, glowRgb));
			}
			pose.popPose();
		}
	}

	/** The finish {@code model} wears: the one picked in the settings, or its own signature finish. */
	static Finish finish(KnifeModel model) {
		Finish finish = Finish.byLabel(Modules.KNIVES_FINISH.get());
		return finish == null ? model.signature : finish;
	}

	/** The crisp outline's colour: the glow colour, nearly solid, a touch brighter after a swing. */
	private static int lineColor() {
		float strength = (float) (Modules.KNIVES_GLOW_STRENGTH.get() / 100.0) * (1 + 0.4f * glintPulse());
		int a = Math.round(Math.max(0, Math.min(1, 0.35f + 0.55f * strength)) * 255);
		return a << 24 | (Accent.lighten(Modules.KNIVES_GLOW_COLOR.get(), 0.12f) & 0xFFFFFF);
	}

	/** Where the glint after a swing is along the blade (0..1), or 0 when there isn't one. */
	private static float glint() {
		if (glintStarted == 0) return 0;
		float t = (System.nanoTime() - glintStarted) / 220_000_000f;
		if (t < 0) return 0;
		if (t >= 1) {
			glintStarted = 0;
			return 0;
		}
		return Math.max(0.01f, t);
	}

	/** How bright the edge flares after a swing: up as the glint runs and back down (0..1). */
	private static float glintPulse() {
		float t = glint();
		return t <= 0 ? 0 : (float) Math.sin(Math.PI * t);
	}
}
