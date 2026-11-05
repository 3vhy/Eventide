package dev.eventide.client.module;

import dev.eventide.client.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/** Stay Open: the chat opens again, empty, right after you send a message, for fast back-and-forth. */
public final class StayOpen {
	private static boolean pending = false;

	private StayOpen() {
	}

	/** A message was sent from the chat; it reopens on the next tick, once the chat has closed itself. */
	public static void sent(String message) {
		if (Modules.STAY_OPEN.enabled() && !message.isBlank()) pending = true;
	}

	static void tick(Minecraft mc) {
		if (!pending) return;
		pending = false;
		if (mc.screen != null || mc.player == null) return;
		mc.setScreen(new ChatScreen("", false));
		Hooks.stayOpenRan = true;
	}
}
