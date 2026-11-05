package dev.eventide.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.eventide.client.Hooks;
import dev.eventide.client.knife.Cosmetics;
import dev.eventide.client.knife.KnifeModel;
import dev.eventide.client.knife.ModelSkins;
import dev.eventide.client.module.Knives;
import dev.eventide.client.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import com.mojang.math.Axis;

/**
 * Your cosmetics on your own player, wherever it's drawn (third person, the inventory, F5): your 3D skin in place
 * of the flat one, your hat on your head, your wings on your back, and your knife (or your 3D skin's weapon) in
 * your hand in place of the item. Only you see them.
 */
public final class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	/** Whether {@code state} is your own player. */
	public static boolean yours(AvatarRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && state.id == mc.player.getId();
	}

	/** The knife your player holds in third person, or null. */
	public static KnifeModel thirdPersonKnife(AvatarRenderState state) {
		if (!yours(state) || !Modules.KNIVES_THIRD_PERSON.get()) return null;
		return Knives.held(Minecraft.getInstance());
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (!yours(state) || state.isInvisible) return;
		PlayerModel model = getParentModel();
		if (ModelSkins.worn()) ModelSkins.draw(model, state.mainArm, pose, collector, light);
		if (Modules.HATS.enabled()) {
			pose.pushPose();
			model.head.translateAndRotate(pose);
			Cosmetics.drawHat(pose, collector, light, state.ageInTicks);
			pose.popPose();
			Hooks.cosmeticsDrawn = true;
		}
		if (Modules.WINGS.enabled()) {
			pose.pushPose();
			model.body.translateAndRotate(pose);
			Cosmetics.drawWings(pose, collector, light, state.ageInTicks);
			pose.popPose();
			Hooks.cosmeticsDrawn = true;
		}
		KnifeModel knife = thirdPersonKnife(state);
		if (ModelSkins.holdsProp()) {
			pose.pushPose();
			model.translateToHand(state, state.mainArm, pose);
			pose.mulPose(Axis.XP.rotationDegrees(-90));
			pose.mulPose(Axis.YP.rotationDegrees(180));
			boolean left = state.mainArm == net.minecraft.world.entity.HumanoidArm.LEFT;
			pose.translate((left ? -1 : 1) / 16f, 0.125f, -0.625f);
			ModelSkins.drawProp(state.mainArm, pose, collector, light);
			pose.popPose();
		} else if (knife != null) {
			pose.pushPose();
			model.translateToHand(state, state.mainArm, pose);
			pose.mulPose(Axis.XP.rotationDegrees(-90));
			pose.mulPose(Axis.YP.rotationDegrees(180));
			boolean left = state.mainArm == net.minecraft.world.entity.HumanoidArm.LEFT;
			pose.translate((left ? -1 : 1) / 16f, 0.125f, -0.625f);
			Cosmetics.drawHeldKnife(knife, state.mainArm, pose, collector, light);
			pose.popPose();
		}
	}
}
