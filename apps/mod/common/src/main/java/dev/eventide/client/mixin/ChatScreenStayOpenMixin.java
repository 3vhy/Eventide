package dev.eventide.client.mixin;

import dev.eventide.client.module.StayOpen;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stay Open: notes each message sent from the chat, so it can open again after. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenStayOpenMixin {
	@Inject(method = "handleChatInput", at = @At("TAIL"))
	private void eventide$stayOpen(String message, boolean addToHistory, CallbackInfo ci) {
		StayOpen.sent(message);
	}
}
