package dev.eventide.client.module;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleType;

/**
 * No Particles and No Explosion Particles: particles kept off your screen, all of them or just explosions'
 * flashes and smoke clouds. Eventide's own effects (Kill Effects) still show: they're spawned inside
 * {@link #ours}.
 */
public final class ParticleFilter {
	private static int ours = 0;

	private ParticleFilter() {
	}

	/** Runs {@code spawn} with Eventide's own particles let through. */
	public static void ours(Runnable spawn) {
		ours++;
		try {
			spawn.run();
		} finally {
			ours--;
		}
	}

	/** Whether a particle of this kind is kept off the screen. */
	public static boolean blocks(ParticleOptions options) {
		if (ours > 0) return false;
		if (Modules.NO_PARTICLES.enabled()) return true;
		if (!Modules.NO_EXPLOSION_PARTICLES.enabled()) return false;
		ParticleType<?> type = options.getType();
		return type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER;
	}

	/** Whether every particle is kept off the screen, including ones made directly (block cracks and breaks). */
	public static boolean blocksAll() {
		return ours == 0 && Modules.NO_PARTICLES.enabled();
	}
}
