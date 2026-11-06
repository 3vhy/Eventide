package dev.eventide.client.mixin;

import dev.eventide.client.compat.DrawImpl;
import dev.eventide.client.module.TabTweaks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tab Editor: ping as numbers, and the server's header and footer can be hidden. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	@Shadow private Component header;
	@Shadow private Component footer;

	@Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true)
	private void eventide$pingNumber(GuiGraphicsExtractor graphics, int width, int x, int y, PlayerInfo info, CallbackInfo ci) {
		if (TabTweaks.drawPing(new DrawImpl(graphics), width, x, y, info)) {
			dev.eventide.client.Hooks.tabPingRan = true;
			ci.cancel();
		}
	}

	@Redirect(method = "extractRenderState", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;header:Lnet/minecraft/network/chat/Component;"))
	private Component eventide$header(PlayerTabOverlay overlay) {
		return TabTweaks.hideHeaderAndFooter() ? null : header;
	}

	@Redirect(method = "extractRenderState", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;footer:Lnet/minecraft/network/chat/Component;"))
	private Component eventide$footer(PlayerTabOverlay overlay) {
		return TabTweaks.hideHeaderAndFooter() ? null : footer;
	}
}
