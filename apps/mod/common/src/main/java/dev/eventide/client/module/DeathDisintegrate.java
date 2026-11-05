package dev.eventide.client.module;

import dev.eventide.client.Hooks;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Death Disintegrate: instead of falling over, whatever dies crumbles away, shrinking into the ground while
 * its body drifts off as motes of colour and ash. On your screen only.
 */
public final class DeathDisintegrate {
	private static final float SECONDS = 0.9f;
	private static final RandomSource RANDOM = RandomSource.create();

	/** A body crumbling: where it stood, its size and when it started. */
	private record Body(double x, double y, double z, float width, float height, long start) {
	}

	private static final Map<Integer, Body> BODIES = new HashMap<>();

	private DeathDisintegrate() {
	}

	/** Something died: it crumbles, if it counts (never you; mobs only when chosen; not a body Heaven is lifting). */
	public static void onDeath(LivingEntity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (!Modules.DEATH_DISINTEGRATE.enabled() || entity == mc.player || KillEffects.ascending(entity)) return;
		if (!(entity instanceof Player) && Modules.DEATH_DISINTEGRATE_WHO.is("Players")) return;
		BODIES.put(entity.getId(), new Body(entity.getX(), entity.getY(), entity.getZ(), entity.getBbWidth(), entity.getBbHeight(), System.nanoTime()));
	}

	private static float progress(Body body) {
		return Math.min(1, (System.nanoTime() - body.start) / 1e9f / SECONDS);
	}

	/** A crumbling body shrinks away, upright and without the red hurt flash. */
	public static void apply(Entity entity, LivingEntityRenderState state) {
		Body body = BODIES.get(entity.getId());
		if (body == null) return;
		float t = progress(body);
		state.scale *= Math.max(0.02f, 1 - t * t);
		state.deathTime = 0;
		state.hasRedOverlay = false;
		Hooks.disintegrated = true;
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			BODIES.clear();
			return;
		}
		int color = Modules.DEATH_DISINTEGRATE_COLOR.get() | 0xFF000000;
		Iterator<Body> each = BODIES.values().iterator();
		while (each.hasNext()) {
			Body body = each.next();
			float t = progress(body);
			if ((System.nanoTime() - body.start) / 1e9f > SECONDS + 2) {
				each.remove();
				continue;
			}
			if (t >= 1) continue;
			int count = Math.round(14 * (1 - t)) + 2;
			float top = body.height * (1 - t * t);
			ParticleFilter.ours(() -> {
				for (int i = 0; i < count; i++) {
					double x = body.x + (RANDOM.nextDouble() - 0.5) * body.width;
					double y = body.y + RANDOM.nextDouble() * Math.max(0.1, top);
					double z = body.z + (RANDOM.nextDouble() - 0.5) * body.width;
					level.addParticle(new DustColorTransitionOptions(color, 0xFF1A1424, 0.9f), true, true, x, y, z, (RANDOM.nextDouble() - 0.5) * 0.06,
							0.03 + RANDOM.nextDouble() * 0.05, (RANDOM.nextDouble() - 0.5) * 0.06);
					if (i % 4 == 0) level.addParticle(ParticleTypes.WHITE_ASH, true, true, x, y, z, 0, 0.02, 0);
				}
			});
		}
	}
}
