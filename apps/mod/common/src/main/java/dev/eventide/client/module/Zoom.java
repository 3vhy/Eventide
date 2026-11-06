package dev.eventide.client.module;

import dev.eventide.client.compat.Compat;
import dev.eventide.client.EventideClient;
import dev.eventide.client.mixin.OptionInstanceAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/**
 * Hold the zoom key (C) to zoom in smoothly. Works by lowering the field of view setting while
 * the key is held and putting the player's own value back afterwards, which behaves the same
 * on every Minecraft version.
 */
public final class Zoom {
	private static boolean held = false;
	private static boolean savedSmoothCamera = false;
	/** The player's own FOV while a zoom is in progress, otherwise null. */
	private static Integer savedFov = null;
	private static double current = 1.0;
	private static long lastFrame = 0;
	/** Extra zoom from the mouse wheel while zooming, multiplied onto the zoom strength. */
	private static double scrollFactor = 1.0;

	private Zoom() {
	}

	static void tick(Minecraft mc) {
		boolean want = Modules.ZOOM.enabled() && Compat.screen() == null && mc.player != null
				&& EventideClient.zoomKey != null && EventideClient.zoomKey.isDown();
		if (want && !held) {
			held = true;
			if (savedFov == null) savedFov = mc.options.fov().get();
			savedSmoothCamera = mc.options.smoothCamera;
			scrollFactor = 1.0;
			if (Modules.ZOOM_SMOOTH.get()) mc.options.smoothCamera = true;
		} else if (!want && held) {
			held = false;
			mc.options.smoothCamera = savedSmoothCamera;
		}
		// A screen opening or leaving the world ends the zoom straight away
		if (savedFov != null && (Compat.screen() != null || mc.player == null)) {
			current = 1.0;
		}
		update(mc);
	}

	/** Called every frame (from the HUD) and every tick, so the zoom eases in smoothly. */
	public static void update(Minecraft mc) {
		if (savedFov == null) {
			lastFrame = 0;
			return;
		}
		long now = System.nanoTime();
		double seconds = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1_000_000_000.0);
		lastFrame = now;
		double target = held ? Math.max(1.0, Modules.ZOOM_STRENGTH.get() * scrollFactor) : 1.0;
		current += (target - current) * Math.min(1.0, seconds * 14.0);
		if (Math.abs(target - current) < 0.01) current = target;

		OptionInstance<Integer> fov = mc.options.fov();
		if (!held && current <= 1.0) {
			setFov(fov, savedFov);
			savedFov = null;
			return;
		}
		setFov(fov, Math.max(1, (int) Math.round(savedFov / current)));
	}

	/** Puts the player's FOV back, e.g. when the game closes mid-zoom. */
	public static void restore(Minecraft mc) {
		if (savedFov != null) {
			setFov(mc.options.fov(), savedFov);
			savedFov = null;
			current = 1.0;
		}
		if (held) {
			held = false;
			mc.options.smoothCamera = savedSmoothCamera;
		}
	}

	private static void setFov(OptionInstance<Integer> fov, int value) {
		if (fov.get() != value) {
			((OptionInstanceAccessor) (Object) fov).eventide$setValue(value);
		}
	}

	/** Mouse wheel while zooming zooms further in or out. Returns true when it used the scroll. */
	public static boolean onScroll(double amount) {
		if (!held || amount == 0 || !Modules.ZOOM_SCROLL.get()) return false;
		scrollFactor = Math.max(0.5, Math.min(4.0, scrollFactor * (amount > 0 ? 1.25 : 0.8)));
		return true;
	}

	public static boolean isZooming() {
		return savedFov != null;
	}
}
