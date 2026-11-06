package dev.eventide.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.eventide.client.module.Nametags;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nametags: a player's tag in Eventide's style (face, name and health), with the scoreboard line above it kept as Minecraft draws it. */
@Mixin(EntityRenderer.class)
public abstract class EntityNameTagMixin {
	@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V", at = @At("HEAD"),
			cancellable = true)
	private void eventide$nameTag(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, int offset,
			CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState avatar) || !Nametags.styled(avatar)) return;
		pose.pushPose();
		if (state.scoreText != null) {
			collector.submitNameTag(pose, state.nameTagAttachment, offset, state.scoreText, !state.isDiscrete, state.lightCoords, camera);
			pose.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F);
		}
		Nametags.submit(avatar, pose, collector, camera.orientation, offset);
		pose.popPose();
		dev.eventide.client.Hooks.styledNametagRan = true;
		ci.cancel();
	}
}
