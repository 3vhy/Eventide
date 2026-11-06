package dev.eventide.client.module;

import dev.eventide.client.compat.Compat;
import dev.eventide.client.EventideClient;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Hold Left Alt (or press it, in toggle mode) to swing the camera around your player without
 * turning them, so you keep running the same way.
 */
public final class Freelook {
	private static boolean active = false;
	private static boolean toggled = false;
	private static float yaw;
	private static float pitch;
	private static CameraType previous = CameraType.FIRST_PERSON;

	private Freelook() {
	}

	public static boolean active() {
		return active;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	static void tick(Minecraft mc) {
		boolean toggleMode = Modules.FREELOOK_MODE.is("Toggle");
		boolean pressed = false;
		while (EventideClient.freelookKey != null && EventideClient.freelookKey.consumeClick()) pressed = true;
		if (toggleMode && pressed) toggled = !toggled;
		boolean held = EventideClient.freelookKey != null && EventideClient.freelookKey.isDown();
		boolean allowed = Modules.FREELOOK.enabled() && mc.player != null;
		if (!allowed || !toggleMode) toggled = false;
		boolean want = allowed && (toggleMode ? toggled : held && Compat.screen() == null);

		if (want && !active) {
			start(mc);
		} else if (!want && active) {
			stop(mc);
		}
	}

	private static void start(Minecraft mc) {
		LocalPlayer player = mc.player;
		active = true;
		yaw = player.getYRot();
		pitch = player.getXRot();
		previous = mc.options.getCameraType();
		if (previous.isFirstPerson()) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
	}

	public static void stop(Minecraft mc) {
		if (!active) return;
		active = false;
		toggled = false;
		mc.options.setCameraType(previous);
	}

	/** Mouse movement while freelooking turns the camera instead of the player. */
	public static boolean turn(double yRot, double xRot) {
		if (!active) return false;
		yaw += (float) yRot * 0.15f;
		pitch = Math.max(-90f, Math.min(90f, pitch + (float) xRot * 0.15f));
		return true;
	}
}
