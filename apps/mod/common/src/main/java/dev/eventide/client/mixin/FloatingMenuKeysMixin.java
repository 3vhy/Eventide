package dev.eventide.client.mixin;

import dev.eventide.client.gui.menu.FloatingMenu;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Floating mode: keys reach the floating menu only while you type in it (and for Esc, the menu key and R). */
@Mixin(KeyboardHandler.class)
public abstract class FloatingMenuKeysMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void eventide$floatingKey(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (FloatingMenu.key(action, event)) ci.cancel();
	}

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
	private void eventide$floatingChar(long window, CharacterEvent event, CallbackInfo ci) {
		if (FloatingMenu.charTyped(event)) ci.cancel();
	}
}
