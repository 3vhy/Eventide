package dev.eventide.client.module;

import dev.eventide.client.Hooks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

/**
 * Kill Effects: when a player you were fighting falls (or any player, or mobs too, as you choose), a burst of
 * light marks the spot, in your colour. Five effects, all on your screen only: Nebula Burst (a sphere of
 * violet and cyan light with a ring rolling out along the ground), Soul Rise (a spiral of light lifting the
 * soul away), Starfall (stars raining down onto the spot), Thunder (a column of crackling light from the sky),
 * Shatter (the body breaking into amethyst shards) and Heaven (the body lifted up into a beam of light from the
 * sky, glowing white, with a halo and feathers of light). Deaths come from the server's death event, so they
 * work on any server. Effects play over a second or so, a few particles a tick, and are dropped when you
 * leave the world.
 */
public final class KillEffects {
	/** How long after your last hit a death still counts as yours. */
	private static final long YOURS_MS = 8_000;
	private static final byte DEATH = 3;

	/** How long Heaven lifts a body, and how high. */
	private static final float ASCEND_SECONDS = 1.6f;
	private static final float ASCEND_HEIGHT = 3.2f;
	public static final int HEAVEN_GLOW = 0xFFFFF4D6;

	private static final Map<Integer, Long> HITS = new HashMap<>();
	/** Bodies Heaven is lifting, by entity id, and when it started. */
	private static final Map<Integer, Long> ASCENDING = new HashMap<>();
	private static final List<Running> RUNNING = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();

	/** One effect playing at a spot. */
	private static final class Running {
		final int kind;
		final double x;
		final double y;
		final double z;
		final double height;
		final int color;
		final int second;
		final float size;
		int age = 0;

		Running(int kind, double x, double y, double z, double height, int color, int second, float size) {
			this.kind = kind;
			this.x = x;
			this.y = y;
			this.z = z;
			this.height = height;
			this.color = color;
			this.second = second;
			this.size = size;
		}
	}

	private KillEffects() {
	}

	/** You hit something: deaths soon after count as your kills. */
	public static void onAttack(Entity attacked) {
		HITS.put(attacked.getId(), System.currentTimeMillis());
	}

	/** An entity event from the server: event 3 is a death. */
	public static void onEntityEvent(Entity entity, byte event) {
		if (event != DEATH || !(entity instanceof LivingEntity living)) return;
		play(living);
	}

	private static boolean wanted(LivingEntity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (!Modules.KILL_EFFECTS.enabled() || entity == mc.player) return false;
		if (!(entity instanceof Player) && !Modules.KILL_EFFECTS_MOBS.get()) return false;
		if (Modules.KILL_EFFECTS_WHO.is("Any player") && entity instanceof Player) return true;
		Long hit = HITS.get(entity.getId());
		return hit != null && System.currentTimeMillis() - hit < YOURS_MS;
	}

	/** Starts the chosen effect where {@code entity} fell, if it counts. */
	public static void play(LivingEntity entity) {
		if (!wanted(entity)) return;
		int kind = Modules.KILL_EFFECTS_STYLE.index();
		if (kind == HEAVEN) ASCENDING.put(entity.getId(), System.currentTimeMillis());
		start(kind, entity.getX(), entity.getY(), entity.getZ(), Math.max(0.6, entity.getBbHeight()));
	}

	private static final int HEAVEN = 5;

	/** How far into its lift a body is (0..1), or -1 when Heaven isn't lifting it. */
	private static float ascent(Entity entity) {
		Long at = ASCENDING.get(entity.getId());
		if (at == null) return -1;
		return Math.min(1, (System.currentTimeMillis() - at) / 1000f / ASCEND_SECONDS);
	}

	/** Whether Heaven is lifting {@code entity}, for its white glow. */
	public static boolean ascending(Entity entity) {
		return ASCENDING.containsKey(entity.getId());
	}

	/**
	 * Heaven: a body being lifted rises (easing in, then gliding), stays upright instead of falling over, and
	 * loses the red hurt flash; it glows white through the outline Minecraft draws for glowing entities.
	 */
	public static void ascend(Entity entity, net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
		float t = ascent(entity);
		if (t < 0) return;
		float eased = t * t * (3 - 2 * t);
		state.y += eased * ASCEND_HEIGHT;
		state.deathTime = 0;
		state.hasRedOverlay = false;
		Hooks.heavenLifted = true;
	}

	/** Starts effect {@code kind} at a spot (the in-game test uses this to picture each one). */
	public static void start(int kind, double x, double y, double z, double height) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) return;
		int color = Modules.KILL_EFFECTS_COLOR.get() | 0xFF000000;
		int second = kind == 0 || kind == 2 ? 0xFF4FD8FF : 0xFFFF6AD5;
		float size = (float) (Modules.KILL_EFFECTS_SIZE.get() / 100.0);
		RUNNING.add(new Running(kind, x, y, z, height, color, second, size));
		if (Modules.KILL_EFFECTS_SOUND.get()) {
			var sound = switch (kind) {
				case 1 -> SoundEvents.SOUL_ESCAPE.value();
				case 2 -> SoundEvents.AMETHYST_BLOCK_CHIME;
				case 3 -> SoundEvents.TRIDENT_THUNDER.value();
				case 4 -> SoundEvents.AMETHYST_CLUSTER_BREAK;
				case 5 -> SoundEvents.BEACON_ACTIVATE;
				default -> SoundEvents.BEACON_DEACTIVATE;
			};
			level.playLocalSound(x, y + height / 2, z, sound, SoundSource.PLAYERS, kind == 3 ? 0.55f : 0.9f, kind == 0 ? 1.6f : 1.0f, false);
		}
		Hooks.killEffectRan = true;
	}

	/** How many effects are playing, for the in-game test. */
	public static int running() {
		return RUNNING.size();
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			RUNNING.clear();
			HITS.clear();
			ASCENDING.clear();
			return;
		}
		long now = System.currentTimeMillis();
		HITS.values().removeIf(at -> now - at > YOURS_MS * 2);
		ASCENDING.values().removeIf(at -> now - at > 4000);
		Iterator<Running> each = RUNNING.iterator();
		while (each.hasNext()) {
			Running effect = each.next();
			boolean done = switch (effect.kind) {
				case 1 -> soulRise(level, effect);
				case 2 -> starfall(level, effect);
				case 3 -> thunder(level, effect);
				case 4 -> shatter(level, effect);
				case 5 -> heaven(level, effect);
				default -> nebulaBurst(level, effect);
			};
			effect.age++;
			if (done) each.remove();
		}
	}

	private static void spawn(ClientLevel level, ParticleOptions options, double x, double y, double z, double dx, double dy, double dz) {
		level.addParticle(options, true, true, x, y, z, dx, dy, dz);
	}

	private static double spread() {
		return RANDOM.nextDouble() * 2 - 1;
	}

	/** A sphere of light bursting out, then a ring rolling out along the ground and a few sparks hanging in the air. */
	private static boolean nebulaBurst(ClientLevel level, Running e) {
		double cy = e.y + e.height * 0.55;
		if (e.age == 0) {
			int count = Math.round(70 * e.size);
			for (int i = 0; i < count; i++) {
				double u = spread();
				double t = RANDOM.nextDouble() * Math.PI * 2;
				double r = Math.sqrt(1 - u * u);
				double speed = (0.18 + RANDOM.nextDouble() * 0.16) * e.size;
				double dx = r * Math.cos(t) * speed;
				double dy = u * speed;
				double dz = r * Math.sin(t) * speed;
				int from = i % 3 == 0 ? e.second : e.color;
				spawn(level, new DustColorTransitionOptions(from, 0xFFFFFFFF, 1.4f * e.size), e.x, cy, e.z, dx, dy, dz);
				if (i % 4 == 0) spawn(level, ParticleTypes.END_ROD, e.x, cy, e.z, dx * 0.9, dy * 0.9, dz * 0.9);
			}
		}
		if (e.age >= 2 && e.age <= 8) {
			int ring = Math.round(28 * e.size);
			double radius = (e.age - 1) * 0.32 * e.size;
			for (int i = 0; i < ring; i++) {
				double a = Math.PI * 2 * i / ring;
				spawn(level, new DustParticleOptions(e.age % 2 == 0 ? e.color : e.second, 1.1f * e.size), e.x + Math.cos(a) * radius, e.y + 0.08,
						e.z + Math.sin(a) * radius, 0, 0.005, 0);
			}
		}
		if (e.age > 2 && e.age < 18 && e.age % 3 == 0) {
			spawn(level, ParticleTypes.ELECTRIC_SPARK, e.x + spread() * 0.6 * e.size, cy + spread() * 0.6, e.z + spread() * 0.6 * e.size, 0, 0.02, 0);
		}
		return e.age > 20;
	}

	/** A spiral of light climbing out of the body, with souls drifting up through it. */
	private static boolean soulRise(ClientLevel level, Running e) {
		int steps = 4;
		for (int s = 0; s < steps; s++) {
			double t = (e.age * steps + s) / (double) steps;
			double h = t * 0.12 * e.size;
			double radius = 0.55 * e.size * (1 - Math.min(1, t / 34.0));
			for (int strand = 0; strand < 2; strand++) {
				double a = t * 0.55 + strand * Math.PI;
				spawn(level, new DustColorTransitionOptions(strand == 0 ? e.color : e.second, 0xFFFFFFFF, 0.9f * e.size), e.x + Math.cos(a) * radius,
						e.y + h, e.z + Math.sin(a) * radius, 0, 0.01, 0);
			}
		}
		if (e.age % 2 == 0 && e.age < 26) spawn(level, ParticleTypes.SOUL, e.x + spread() * 0.3, e.y + 0.4 + RANDOM.nextDouble() * e.height,
				e.z + spread() * 0.3, 0, 0.06, 0);
		if (e.age % 3 == 0 && e.age < 30) spawn(level, ParticleTypes.END_ROD, e.x, e.y + e.age * 0.12 * e.size, e.z, spread() * 0.01, 0.04, spread() * 0.01);
		return e.age > 34;
	}

	/** Stars falling out of the sky onto the spot, flaring where they land. */
	private static boolean starfall(ClientLevel level, Running e) {
		if (e.age < 14) {
			int count = Math.round(3 * e.size) + 1;
			for (int i = 0; i < count; i++) {
				double ox = spread() * 1.2 * e.size;
				double oz = spread() * 1.2 * e.size;
				spawn(level, ParticleTypes.END_ROD, e.x + ox + 0.4, e.y + 3.6 + RANDOM.nextDouble(), e.z + oz, -0.03, -0.32, 0);
				spawn(level, new DustParticleOptions(i % 2 == 0 ? e.color : e.second, 1.3f * e.size), e.x + ox, e.y + 2.4 + RANDOM.nextDouble() * 1.2,
						e.z + oz, 0, -0.25, 0);
			}
		}
		if (e.age == 10) {
			for (int i = 0; i < Math.round(36 * e.size); i++) {
				double a = RANDOM.nextDouble() * Math.PI * 2;
				double speed = 0.08 + RANDOM.nextDouble() * 0.12;
				spawn(level, new DustColorTransitionOptions(e.color, e.second, 1.2f * e.size), e.x, e.y + 0.2, e.z, Math.cos(a) * speed, 0.08,
						Math.sin(a) * speed);
			}
			spawn(level, ParticleTypes.FIREWORK, e.x, e.y + 0.4, e.z, 0, 0.1, 0);
		}
		return e.age > 24;
	}

	/** A column of crackling light from high above, with sparks and a scorch of light on the ground. */
	private static boolean thunder(ClientLevel level, Running e) {
		if (e.age < 5) {
			double top = 14;
			for (double h = 0; h < top; h += 0.35) {
				double jitter = 0.12 * Math.sin(h * 3.1 + e.age * 2.3);
				spawn(level, new DustParticleOptions(h % 0.7 < 0.35 ? 0xFFFFFFFF : e.color, 1.6f * e.size), e.x + jitter, e.y + h, e.z + jitter * 0.6, 0, 0, 0);
				if (RANDOM.nextFloat() < 0.15f) spawn(level, ParticleTypes.ELECTRIC_SPARK, e.x, e.y + h, e.z, spread() * 0.2, spread() * 0.2, spread() * 0.2);
			}
		}
		if (e.age == 2) {
			for (int i = 0; i < Math.round(30 * e.size); i++) {
				double a = RANDOM.nextDouble() * Math.PI * 2;
				spawn(level, ParticleTypes.ELECTRIC_SPARK, e.x, e.y + 0.1, e.z, Math.cos(a) * 0.35, 0.15 + RANDOM.nextDouble() * 0.2, Math.sin(a) * 0.35);
				spawn(level, new DustParticleOptions(e.color, 1.2f * e.size), e.x + Math.cos(a) * 0.6, e.y + 0.05, e.z + Math.sin(a) * 0.6, 0, 0.01, 0);
			}
		}
		return e.age > 14;
	}

	/** The body breaking into amethyst shards that scatter, with a puff of light. */
	private static boolean shatter(ClientLevel level, Running e) {
		if (e.age == 0) {
			BlockParticleOption shard = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState());
			for (int i = 0; i < Math.round(60 * e.size); i++) {
				double h = RANDOM.nextDouble() * e.height;
				spawn(level, shard, e.x + spread() * 0.35, e.y + h, e.z + spread() * 0.35, spread() * 0.35, 0.1 + RANDOM.nextDouble() * 0.3, spread() * 0.35);
			}
			for (int i = 0; i < Math.round(24 * e.size); i++) {
				spawn(level, new DustColorTransitionOptions(e.color, e.second, 1.5f * e.size), e.x + spread() * 0.3, e.y + RANDOM.nextDouble() * e.height,
						e.z + spread() * 0.3, spread() * 0.08, 0.04, spread() * 0.08);
			}
		}
		if (e.age > 1 && e.age < 12 && e.age % 2 == 0) {
			spawn(level, ParticleTypes.END_ROD, e.x + spread() * 0.5, e.y + RANDOM.nextDouble() * e.height, e.z + spread() * 0.5, 0, 0.03, 0);
		}
		return e.age > 14;
	}

	/**
	 * Heaven: a beam of light pours down from the sky onto the body as it's lifted, motes of warm light drift up
	 * through it, a halo turns above where the head rises to, and feathers of light fall away to the sides.
	 */
	private static boolean heaven(ClientLevel level, Running e) {
		float lift = Math.min(1, e.age / (ASCEND_SECONDS * 20));
		double rise = lift * lift * (3 - 2 * lift) * ASCEND_HEIGHT;
		double head = e.y + rise + e.height + 0.35;
		if (e.age < 36) {
			for (int i = 0; i < Math.round(5 * e.size); i++) {
				double a = RANDOM.nextDouble() * Math.PI * 2;
				double r = RANDOM.nextDouble() * 0.45 * e.size;
				spawn(level, new DustParticleOptions(i % 3 == 0 ? HEAVEN_GLOW : 0xFFFFFFFF, 1.6f * e.size), e.x + Math.cos(a) * r,
						e.y + 1 + RANDOM.nextDouble() * 12, e.z + Math.sin(a) * r, 0, -0.05, 0);
			}
			if (e.age % 2 == 0) {
				spawn(level, ParticleTypes.END_ROD, e.x + spread() * 0.4, e.y + rise + RANDOM.nextDouble() * e.height, e.z + spread() * 0.4,
						spread() * 0.01, 0.07, spread() * 0.01);
			}
			int ring = Math.round(14 * e.size);
			double spin = e.age * 0.25;
			for (int i = 0; i < ring; i++) {
				double a = spin + Math.PI * 2 * i / ring;
				spawn(level, new DustParticleOptions(0xFFFFE9A8, 0.8f * e.size), e.x + Math.cos(a) * 0.38, head, e.z + Math.sin(a) * 0.38, 0, 0, 0);
			}
		}
		if (e.age > 4 && e.age < 30 && e.age % 3 == 0) {
			for (int side = -1; side <= 1; side += 2) {
				spawn(level, new DustColorTransitionOptions(0xFFFFFFFF, e.color, 1.2f * e.size), e.x + side * 0.5, e.y + rise + e.height * 0.7,
						e.z, side * 0.06, -0.02, spread() * 0.02);
			}
		}
		if (e.age == 0) {
			for (int i = 0; i < Math.round(26 * e.size); i++) {
				double a = RANDOM.nextDouble() * Math.PI * 2;
				spawn(level, new DustParticleOptions(0xFFFFFFFF, 1.3f * e.size), e.x + Math.cos(a) * 0.9, e.y + 0.05, e.z + Math.sin(a) * 0.9, 0, 0.02, 0);
			}
		}
		return e.age > 44;
	}
}
