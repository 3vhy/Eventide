package dev.eventide.client.mixin;

import dev.eventide.client.compat.DrawImpl;
import dev.eventide.client.hud.BossBars;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Boss Bars: each server boss bar drawn as Eventide's thin glowing line in its colour. */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
	@Inject(method = "extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V", at = @At("HEAD"), cancellable = true)
	private void eventide$bossBar(GuiGraphicsExtractor graphics, int x, int y, BossEvent event, CallbackInfo ci) {
		if (!BossBars.styled()) return;
		BossBars.draw(new DrawImpl(graphics), x, y, event);
		ci.cancel();
	}
}
