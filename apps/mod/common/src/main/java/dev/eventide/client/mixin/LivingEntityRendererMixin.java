package dev.eventide.client.mixin;

import dev.eventide.client.module.KillEffects;
import dev.eventide.client.module.Nametags;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nametags: your own nametag in third person. Kill Effects: Heaven lifting a body. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true)
	private void eventide$ownNametag(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && Nametags.showOwn(entity)) {
			dev.eventide.client.Hooks.ownNametagRan = true;
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void eventide$heaven(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		KillEffects.ascend(entity, state);
	}
}
