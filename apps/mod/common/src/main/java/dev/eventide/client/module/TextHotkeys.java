package dev.eventide.client.module;

import dev.eventide.client.compat.Compat;
import com.mojang.blaze3d.platform.InputConstants;
import dev.eventide.client.Config;
import dev.eventide.client.Config.TextHotkey;
import dev.eventide.client.Hooks;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Text Hotkeys: keys (or extra mouse buttons) that send a chat message, or run a command when the
 * text starts with /. They're set up on the mod's page and fire once per press, only in game with
 * no menu or chat open, so typing never sets one off. A new hotkey counts as held until its key is
 * first seen up, so one set up mid-press doesn't fire straight away.
 */
public final class TextHotkeys {
	/** Minecraft's own limit for one chat message or command. */
	public static final int MAX_LENGTH = 256;
	private static final Map<TextHotkey, Boolean> held = new IdentityHashMap<>();

	private TextHotkeys() {
	}

	static void tick(Minecraft mc) {
		long window = Hooks.windowHandle();
		update(mc, hotkey -> window != 0 && isDown(window, hotkey));
	}

	/** Fires the hotkeys whose key just went down, given which keys are down now (the game test passes its own). */
	public static void update(Minecraft mc, Predicate<TextHotkey> isDown) {
		List<TextHotkey> hotkeys = Config.get().textHotkeys;
		if (hotkeys.isEmpty()) {
			held.clear();
			return;
		}
		boolean active = Modules.TEXT_HOTKEYS.enabled() && mc.player != null && Compat.screen() == null;
		Map<TextHotkey, Boolean> now = new IdentityHashMap<>();
		for (TextHotkey hotkey : List.copyOf(hotkeys)) {
			boolean down = isDown.test(hotkey);
			boolean was = held.getOrDefault(hotkey, true);
			if (active && down && !was) send(mc, hotkey.text);
			now.put(hotkey, down);
		}
		held.clear();
		held.putAll(now);
	}

	private static boolean isDown(long window, TextHotkey hotkey) {
		if (hotkey.key < 0) return false;
		if (hotkey.mouse) return GLFW.glfwGetMouseButton(window, hotkey.key) == GLFW.GLFW_PRESS;
		return GLFW.glfwGetKey(window, hotkey.key) == GLFW.GLFW_PRESS;
	}

	/** Sends {@code text} as the player: text starting with / runs as a command, anything else goes to chat. */
	public static boolean send(Minecraft mc, String text) {
		if (mc.player == null || text == null) return false;
		String message = text.trim();
		if (message.isEmpty()) return false;
		if (message.length() > MAX_LENGTH) message = message.substring(0, MAX_LENGTH);
		if (message.startsWith("/")) {
			String command = message.substring(1).trim();
			if (command.isEmpty()) return false;
			mc.player.connection.sendCommand(command);
		} else {
			mc.player.connection.sendChat(message);
		}
		return true;
	}

	/** The key's name as Minecraft shows it in Controls, like "R", "F6" or "Mouse 4". */
	public static String keyName(int key, boolean mouse) {
		if (key < 0) return "None";
		if (mouse) return "Mouse " + (key + 1);
		return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
	}

	public static String keyName(TextHotkey hotkey) {
		return keyName(hotkey.key, hotkey.mouse);
	}

	/** Binds {@code text} to a key, replacing what that key sent before, and turns Text Hotkeys on. */
	public static TextHotkey add(int key, boolean mouse, String text) {
		String message = text.trim();
		if (message.length() > MAX_LENGTH) message = message.substring(0, MAX_LENGTH);
		List<TextHotkey> hotkeys = Config.get().textHotkeys;
		for (TextHotkey existing : hotkeys) {
			if (existing.key == key && existing.mouse == mouse) {
				existing.text = message;
				enable();
				return existing;
			}
		}
		TextHotkey hotkey = new TextHotkey();
		hotkey.key = key;
		hotkey.mouse = mouse;
		hotkey.text = message;
		hotkeys.add(hotkey);
		enable();
		return hotkey;
	}

	public static void remove(TextHotkey hotkey) {
		Config.get().textHotkeys.remove(hotkey);
		held.remove(hotkey);
		Config.saveSoon();
	}

	private static void enable() {
		if (!Modules.TEXT_HOTKEYS.enabled()) Modules.TEXT_HOTKEYS.toggle();
		Config.saveSoon();
	}
}
