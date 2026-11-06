package dev.eventide.client.hud;

import dev.eventide.client.compat.Compat;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Counts mouse clicks in the last second. Minecraft handles every press the window reports one by
 * one, so jitter and butterfly clicks shorter than a frame still count; MouseHandlerMixin passes
 * each press on here. Only clicks made in game count, not clicks in menus.
 */
public final class CpsTracker {
	private static final Deque<Long> LEFT = new ArrayDeque<>();
	private static final Deque<Long> RIGHT = new ArrayDeque<>();

	private CpsTracker() {
	}

	/** Called for every mouse button press Minecraft handles. */
	public static synchronized void onPress(int button) {
		if (Compat.screen() != null) return;
		long now = System.currentTimeMillis();
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) LEFT.addLast(now);
		else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) RIGHT.addLast(now);
	}

	/** Called every frame from the HUD. */
	public static synchronized void poll(Minecraft mc) {
		long now = System.currentTimeMillis();
		trim(LEFT, now);
		trim(RIGHT, now);
	}

	private static void trim(Deque<Long> clicks, long now) {
		while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) {
			clicks.pollFirst();
		}
	}

	public static synchronized int left() {
		trim(LEFT, System.currentTimeMillis());
		return LEFT.size();
	}

	public static synchronized int right() {
		trim(RIGHT, System.currentTimeMillis());
		return RIGHT.size();
	}
}
