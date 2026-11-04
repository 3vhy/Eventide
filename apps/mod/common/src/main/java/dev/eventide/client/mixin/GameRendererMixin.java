package dev.eventide.client.mixin;

import dev.eventide.client.gui.menu.FloatingMenu;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Floating mode: the menu's panel is drawn into the world once the world (and the hand) are drawn, before the HUD. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderLevel", at = @At("TAIL"))
	private void eventide$floatingMenu(DeltaTracker deltaTracker, CallbackInfo ci) {
		FloatingMenu.render();
	}
}
