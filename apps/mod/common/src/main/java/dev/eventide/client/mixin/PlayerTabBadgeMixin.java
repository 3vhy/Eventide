package dev.eventide.client.mixin;

import dev.eventide.client.module.EventideBadge;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Eventide Badge: the logo before Eventide players' names in the tab list. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabBadgeMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void eventide$badge(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		cir.setReturnValue(EventideBadge.tab(info, cir.getReturnValue()));
	}
}
