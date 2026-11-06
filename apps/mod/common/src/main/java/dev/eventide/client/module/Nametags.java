package dev.eventide.client.module;

import dev.eventide.client.compat.Compat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.eventide.client.ui.Tokens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * Nametags: your own tag in third person, and players' tags as their face, their name (with whatever the
 * server puts in it, rank badges included) and their health in hearts with a pink heart, floating with a
 * text shadow and no plate behind them. The tag is built where Minecraft would submit its own, in the same
 * place and facing the camera the same way; seen through walls like Minecraft's when the player isn't
 * sneaking, and only in plain sight when they are.
 */
public final class Nametags {
	/** Minecraft's own name tag sizes: a text pixel is 0.025 blocks. */
	private static final float SCALE = 0.025f;
	private static final float HEAD = 8;
	private static final float HEAD_GAP = 2.5f;
	private static final float HEALTH_GAP = 4;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final String HEART = "❤";

	private Nametags() {
	}

	public static boolean showOwn(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		boolean wanted = Modules.OWN_NAMETAG.enabled() || Modules.NAMETAGS.enabled() && Modules.NAMETAGS_OWN.get();
		return wanted && entity == mc.player && !mc.options.getCameraType().isFirstPerson() && !Compat.hudHidden();
	}

	/** Whether this player's tag is drawn in Eventide's style instead of Minecraft's. */
	public static boolean styled(AvatarRenderState state) {
		return Modules.NAMETAGS.enabled() && Modules.NAMETAGS_STYLE.is("Eventide") && state.nameTag != null && state.nameTagAttachment != null;
	}

	/**
	 * Submits the tag for {@code state} at Minecraft's name tag spot, {@code offset} text pixels down (as
	 * Minecraft's own call takes it), facing the camera at {@code orientation}.
	 */
	public static void submit(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, Quaternionf orientation, int offset) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		Component name = state.nameTag;
		String hearts = hearts(mc, state.id);
		float nameW = font.width(name);
		float heartsW = hearts == null ? 0 : font.width(hearts) + 1 + font.width(HEART);
		float w = HEAD + HEAD_GAP + nameW + (hearts == null ? 0 : HEALTH_GAP + heartsW);
		float x0 = -w / 2f;
		boolean seeThrough = !state.isDiscrete;
		int light = state.lightCoords;

		pose.pushPose();
		pose.translate(state.nameTagAttachment.x, state.nameTagAttachment.y + 0.5, state.nameTagAttachment.z);
		pose.mulPose(orientation);
		pose.scale(SCALE, -SCALE, SCALE);
		Identifier skin = state.skin.body().texturePath();
		float hy = offset + (font.lineHeight - 1 - HEAD) / 2f;
		collector.submitCustomGeometry(pose, seeThrough ? RenderTypes.textSeeThrough(skin) : RenderTypes.text(skin), (last, out) -> {
			face(last, out, x0, hy, HEAD, 8, 8, light, 0);
			face(last, out, x0 - 0.4f, hy - 0.4f, HEAD + 0.8f, 40, 8, light, 0.01f);
		});
		float tx = x0 + HEAD + HEAD_GAP;
		text(collector, pose, name, tx, offset, 0xFFFFFFFF, seeThrough, light);
		if (hearts != null) {
			float hx = tx + nameW + HEALTH_GAP;
			text(collector, pose, Component.literal(hearts), hx, offset, 0xFFFFFFFF, seeThrough, light);
			text(collector, pose, Component.literal(HEART), hx + font.width(hearts) + 1, offset, Tokens.PINK, seeThrough, light);
		}
		pose.popPose();
	}

	/** The player's health in hearts, to the half heart ("10", "7.5"), or null if they can't be found. */
	private static String hearts(Minecraft mc, int id) {
		if (mc.level == null) return null;
		if (!(mc.level.getEntity(id) instanceof LivingEntity living)) return null;
		float halves = Math.round(living.getHealth() + living.getAbsorptionAmount());
		float value = halves / 2f;
		return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
	}

	/**
	 * One piece of the tag's text with a shadow. A tag that shows through walls is drawn twice, as Minecraft's
	 * is: faint where it's hidden, then in full where it's in sight.
	 */
	private static void text(SubmitNodeCollector collector, PoseStack pose, Component text, float x, float y, int color, boolean seeThrough,
			int light) {
		if (seeThrough) {
			collector.submitText(pose, x, y, text.getVisualOrderText(), false, Font.DisplayMode.SEE_THROUGH, light, (color & 0xFFFFFF) | 0x80000000, 0, 0);
			collector.submitText(pose, x, y, text.getVisualOrderText(), true, Font.DisplayMode.NORMAL, FULL_BRIGHT, color, 0, 0);
		} else {
			collector.submitText(pose, x, y, text.getVisualOrderText(), true, Font.DisplayMode.NORMAL, light, color, 0, 0);
		}
	}

	/** One 8 x 8 square of the 64 x 64 skin (the face at 8,8 or the hat at 40,8) drawn {@code size} square. */
	private static void face(PoseStack.Pose pose, VertexConsumer out, float x, float y, float size, int u, int v, int light, float lift) {
		float u1 = u / 64f;
		float v1 = v / 64f;
		float u2 = (u + 8) / 64f;
		float v2 = (v + 8) / 64f;
		float z = lift;
		out.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u1, v1).setLight(light);
		out.addVertex(pose, x, y + size, z).setColor(0xFFFFFFFF).setUv(u1, v2).setLight(light);
		out.addVertex(pose, x + size, y + size, z).setColor(0xFFFFFFFF).setUv(u2, v2).setLight(light);
		out.addVertex(pose, x + size, y, z).setColor(0xFFFFFFFF).setUv(u2, v1).setLight(light);
	}
}
