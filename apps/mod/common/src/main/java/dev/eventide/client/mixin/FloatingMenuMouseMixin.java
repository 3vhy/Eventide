package dev.eventide.client.mixin;

import dev.eventide.client.gui.menu.FloatingMenu;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Floating mode: clicks and the wheel with the crosshair on the floating menu go to it instead of the game. */
@Mixin(MouseHandler.class)
public abstract class FloatingMenuMouseMixin {
	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void eventide$floatingClick(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (FloatingMenu.mouseButton(info.button(), action)) ci.cancel();
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void eventide$floatingScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (vertical != 0 && FloatingMenu.scroll(vertical)) ci.cancel();
	}
}
