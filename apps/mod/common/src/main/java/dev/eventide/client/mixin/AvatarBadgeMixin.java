package dev.eventide.client.mixin;

import dev.eventide.client.module.EventideBadge;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Eventide Badge: the logo before an Eventide player's name on their name tag, in Minecraft's style or Eventide's. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarBadgeMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void eventide$badge(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (state.nameTag != null && EventideBadge.shows(avatar)) {
			state.nameTag = EventideBadge.badged(state.nameTag);
			dev.eventide.client.Hooks.badgeShown = true;
		}
	}
}
