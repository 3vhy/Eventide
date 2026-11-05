package dev.eventide.client.module;

import dev.eventide.client.ui.Anim;
import dev.eventide.client.ui.Paint;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;

/**
 * The Performance panel: a preset (Quality, Balanced, Performance) that sets every option at once, and the
 * options themselves. Balanced is how Eventide comes; Quality draws the sky at full resolution; Performance
 * turns shadows, blur and particles down. Changing one by hand makes the preset Custom. A preset is only
 * applied when you pick it, so settings you never touched keep their defaults. Shadows and animations take effect in
 * Eventide's renderer straight away; menu blur and particles set Minecraft's own options, only when they're
 * changed here, so Minecraft's settings screen can still change them too. Presets also pick the Skies'
 * resolution.
 */
public final class Performance {
	private static final String[][] PRESETS = {
			{"Full", "High", "Full", "false", "Full"},
			{"Full", "High", "Full", "false", "Half"},
			{"Off", "Off", "Quick", "true", "Quarter"}};
	private static int applied = -1;
	private static String blurApplied = null;
	private static Boolean particlesApplied = null;

	private Performance() {
	}

	static void tick(Minecraft mc) {
		int preset = Modules.PERFORMANCE_PRESET.index();
		if (applied < 0) applied = preset;
		if (preset != applied) {
			if (preset < PRESETS.length) apply(PRESETS[preset]);
			applied = preset;
		} else if (preset < PRESETS.length && !matches(PRESETS[preset])) {
			Modules.PERFORMANCE_PRESET.set("Custom");
			applied = Modules.PERFORMANCE_PRESET.index();
		}
		Paint.shadowQuality(2 - Modules.PERFORMANCE_SHADOWS.index());
		float glide = switch (Modules.PERFORMANCE_ANIMATIONS.index()) {
			case 1 -> 2f;
			case 2 -> 0f;
			default -> 1f;
		};
		Anim.speed(Modules.UI_ANIMATIONS.enabled() ? glide * (float) (100.0 / Modules.UI_ANIMATIONS_SPEED.get()) : 0f);
		String blur = Modules.PERFORMANCE_BLUR.get();
		if (!blur.equals(blurApplied)) {
			if (blurApplied != null) mc.options.menuBackgroundBlurriness().set(blur.equals("High") ? 5 : blur.equals("Low") ? 2 : 0);
			blurApplied = blur;
		}
		boolean fewer = Modules.PERFORMANCE_PARTICLES.get();
		if (particlesApplied == null || fewer != particlesApplied) {
			if (particlesApplied != null) mc.options.particles().set(fewer ? ParticleStatus.DECREASED : ParticleStatus.ALL);
			particlesApplied = fewer;
		}
	}

	/** Whether Skies should leave out its weather particles. */
	public static boolean fewerParticles() {
		return Modules.PERFORMANCE_PARTICLES.get();
	}

	private static void apply(String[] values) {
		Modules.PERFORMANCE_SHADOWS.set(values[0]);
		Modules.PERFORMANCE_BLUR.set(values[1]);
		Modules.PERFORMANCE_ANIMATIONS.set(values[2]);
		Modules.PERFORMANCE_PARTICLES.set(Boolean.parseBoolean(values[3]));
		Modules.SKIES_RESOLUTION.set(values[4]);
	}

	private static boolean matches(String[] values) {
		return Modules.PERFORMANCE_SHADOWS.is(values[0]) && Modules.PERFORMANCE_BLUR.is(values[1]) && Modules.PERFORMANCE_ANIMATIONS.is(values[2])
				&& Modules.PERFORMANCE_PARTICLES.get() == Boolean.parseBoolean(values[3]) && Modules.SKIES_RESOLUTION.is(values[4]);
	}
}
