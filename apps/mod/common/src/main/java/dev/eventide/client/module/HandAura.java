package dev.eventide.client.module;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.eventide.client.compat.Compat;
import dev.eventide.client.knife.KnifeShading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * Hand Aura: a soft glow of colour round your arm in first person, empty-handed or holding a knife. It's a few
 * shells a little bigger than the arm, each drawn by its far faces only, so all that shows of each is the rim
 * past the arm's edges; stacked, they fade out from a bright line at the arm to nothing.
 */
public final class HandAura {
	/** Each shell: how far it stands off the arm (in its pixels) and how strong it is. */
	private static final float[][] SHELLS = {{0.16f, 0.9f}, {0.42f, 0.42f}, {0.75f, 0.2f}, {1.15f, 0.09f}};

	private HandAura() {
	}

	public static boolean shows() {
		return Modules.HAND_AURA.enabled() && KnifeShading.custom();
	}

	/**
	 * Draws the aura round the right or left arm. {@code pose} is the one handed to Minecraft's arm drawing
	 * (renderRightHand / renderLeftHand), before the arm's own place in the model.
	 */
	public static void draw(PoseStack pose, SubmitNodeCollector collector, boolean right) {
		if (!shows()) return;
		Minecraft mc = Minecraft.getInstance();
		boolean slim = mc.player != null && mc.player.getSkin().model() == PlayerModelType.SLIM;
		float f = right ? 1 : -1;
		pose.pushPose();
		pose.translate(f * -5 / 16f, 2 / 16f, 0);
		pose.mulPose(Axis.ZP.rotation(f * 0.1f));
		float w = slim ? 3 : 4;
		float x1 = right ? -(w - 1) : -1;
		float x2 = x1 + w;
		float size = (float) (Modules.HAND_AURA_SIZE.get() / 100.0);
		float strength = (float) (Modules.HAND_AURA_STRENGTH.get() / 100.0);
		if (Modules.HAND_AURA_PULSE.get()) strength *= 0.82f + 0.18f * (float) Math.sin(System.nanoTime() / 1e9 * 2.2);
		int rgb = Modules.HAND_AURA_COLOR.get() & 0xFFFFFF;
		for (float[] shell : SHELLS) {
			float g = shell[0] * size;
			int a = Math.round(Math.max(0, Math.min(1, shell[1] * strength)) * 255);
			if (a < 2) continue;
			int color = a << 24 | rgb;
			float[] box = {(x1 - g) / 16f, (-2 - g) / 16f, (-2 - g) / 16f, (x2 + g) / 16f, (10 + g) / 16f, (2 + g) / 16f};
			collector.submitCustomGeometry(pose, Compat.knifeLine(), (last, out) -> box(last, out, box, color));
		}
		pose.popPose();
		dev.eventide.client.Hooks.handAuraDrawn = true;
	}

	private static void box(PoseStack.Pose pose, VertexConsumer out, float[] b, int color) {
		float x1 = b[0];
		float y1 = b[1];
		float z1 = b[2];
		float x2 = b[3];
		float y2 = b[4];
		float z2 = b[5];
		face(pose, out, color, -1, 0, 0, x1, y1, z1, x1, y2, z1, x1, y2, z2, x1, y1, z2);
		face(pose, out, color, 1, 0, 0, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1);
		face(pose, out, color, 0, 0, -1, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1);
		face(pose, out, color, 0, 0, 1, x1, y1, z2, x1, y2, z2, x2, y2, z2, x2, y1, z2);
		face(pose, out, color, 0, -1, 0, x1, y1, z1, x1, y1, z2, x2, y1, z2, x2, y1, z1);
		face(pose, out, color, 0, 1, 0, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2);
	}

	/** One face of a shell, wound anticlockwise seen from outside, so the outline shader can tell its far faces. */
	private static void face(PoseStack.Pose pose, VertexConsumer out, int color, float nx, float ny, float nz, float ax, float ay, float az, float bx,
			float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
		float ux = bx - ax;
		float uy = by - ay;
		float uz = bz - az;
		float vx = cx - ax;
		float vy = cy - ay;
		float vz = cz - az;
		float outward = (uy * vz - uz * vy) * nx + (uz * vx - ux * vz) * ny + (ux * vy - uy * vx) * nz;
		if (outward >= 0) {
			out.addVertex(pose, ax, ay, az).setColor(color).setNormal(pose, nx, ny, nz);
			out.addVertex(pose, bx, by, bz).setColor(color).setNormal(pose, nx, ny, nz);
			out.addVertex(pose, cx, cy, cz).setColor(color).setNormal(pose, nx, ny, nz);
			out.addVertex(pose, dx, dy, dz).setColor(color).setNormal(pose, nx, ny, nz);
		} else {
			out.addVertex(pose, ax, ay, az).setColor(color).setNormal(pose, nx, ny, nz);
			out.addVertex(pose, dx, dy, dz).setColor(color).setNormal(pose, nx, ny, nz);
			out.addVertex(pose, cx, cy, cz).setColor(color).setNormal(pose, nx, ny, nz);
			out.addVertex(pose, bx, by, bz).setColor(color).setNormal(pose, nx, ny, nz);
		}
	}
}
