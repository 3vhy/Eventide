package dev.eventide.client.mixin;

import dev.eventide.client.module.EventideBadge;
import dev.eventide.client.render.CosmeticsLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Eventide Badge: the logo before an Eventide player's name on their name tag, in Minecraft's style or Eventide's.
 * Cosmetics: the player renderer gets Eventide's cosmetics layer, and your held item makes way for your knife.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarBadgeMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void eventide$cosmetics(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
		AvatarRenderer self = (AvatarRenderer) (Object) this;
		((LivingEntityRendererInvoker) self).eventide$addLayer(new CosmeticsLayer(self));
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void eventide$badge(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (CosmeticsLayer.thirdPersonKnife(state) != null) {
			if (state.mainArm == HumanoidArm.LEFT) state.leftHandItemState.clear();
			else state.rightHandItemState.clear();
		}
		if (state.nameTag != null && EventideBadge.shows(avatar)) {
			state.nameTag = EventideBadge.badged(state.nameTag);
			dev.eventide.client.Hooks.badgeShown = true;
		}
	}
}
