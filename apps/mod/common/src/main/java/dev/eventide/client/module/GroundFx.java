package dev.eventide.client.module;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.eventide.client.Hooks;
import dev.eventide.client.compat.Compat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Rings on the ground, drawn into the world on your screen only. Ripple: rain drops landing around you while
 * it rains, each a small ring spreading and fading like on a puddle. Jump Circles: a ring of your colour
 * opening out under you (and other players, if chosen) each time you jump. Rings are kept to a few dozen at
 * once and drawn in one batch.
 */
public final class GroundFx {
	private static final int SEGMENTS = 40;
	private static final int MAX = 160;
	private static final float RIPPLE_RADIUS = 12;

	/** One ring: where, when it started (nanoseconds), how long it lasts, how far it opens and its colour. */
	private record Ring(double x, double y, double z, long start, float life, float from, float to, float width, int color) {
	}

	private static final List<Ring> RINGS = new ArrayList<>();
	private static final Map<Integer, Boolean> GROUNDED = new HashMap<>();
	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

	private GroundFx() {
	}

	public static void init() {
		Compat.onWorldDraw(GroundFx::draw);
	}

	/** How many rings are showing, for the in-game test. */
	public static int rings() {
		return RINGS.size();
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			RINGS.clear();
			GROUNDED.clear();
			return;
		}
		if (mc.isPaused()) return;
		long now = System.nanoTime();
		RINGS.removeIf(ring -> (now - ring.start) / 1e9f > ring.life);
		if (Modules.JUMP_CIRCLES.enabled()) {
			for (Player player : level.players()) {
				if (player != mc.player && !Modules.JUMP_CIRCLES_EVERYONE.get()) continue;
				boolean grounded = player.onGround();
				Boolean was = GROUNDED.put(player.getId(), grounded);
				if (was != null && was && !grounded && player.getDeltaMovement().y > 0.2) jump(player.getX(), player.getY(), player.getZ(), now);
			}
		} else {
			GROUNDED.clear();
		}
		if (Modules.RIPPLE.enabled() && level.getRainLevel(1) > 0.2f && !Performance.fewerParticles()) {
			ThreadLocalRandom random = ThreadLocalRandom.current();
			int count = (int) Math.round(Modules.RIPPLE_AMOUNT.get() / 100.0 * 6);
			for (int i = 0; i < count && RINGS.size() < MAX; i++) {
				double x = mc.player.getX() + random.nextDouble(-RIPPLE_RADIUS, RIPPLE_RADIUS);
				double z = mc.player.getZ() + random.nextDouble(-RIPPLE_RADIUS, RIPPLE_RADIUS);
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
				POS.set(x, top, z);
				if (Math.abs(top - mc.player.getY()) > 8 || level.getBiome(POS).value().getPrecipitationAt(POS, level.getSeaLevel()) != Biome.Precipitation.RAIN) {
					continue;
				}
				float size = (float) (0.32 + random.nextDouble() * 0.2);
				RINGS.add(new Ring(x, top + 0.02, z, now - random.nextLong(0, 50_000_000L), 0.85f, 0.03f, size, 0.022f, 0x99E8E4FF));
			}
		}
	}

	private static void jump(double x, double y, double z, long now) {
		if (RINGS.size() >= MAX) return;
		int color = 0xE6000000 | Modules.JUMP_CIRCLES_COLOR.get() & 0xFFFFFF;
		RINGS.add(new Ring(x, y + 0.03, z, now, 0.7f, 0.15f, 1.15f, 0.05f, color));
		Hooks.jumpCircleShown = true;
	}

	/** Starts a jump circle at a spot (the in-game test uses this to picture one). */
	public static void jumpAt(double x, double y, double z) {
		jump(x, y, z, System.nanoTime());
	}

	private static void draw(PoseStack pose, SubmitNodeCollector collector) {
		if (RINGS.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		Vec3 camera = mc.gameRenderer.getMainCamera().position();
		long now = System.nanoTime();
		List<Ring> rings = List.copyOf(RINGS);
		collector.submitCustomGeometry(pose, Compat.worldFx(), (last, out) -> {
			for (Ring ring : rings) {
				float t = Math.min(1, Math.max(0, (now - ring.start) / 1e9f / ring.life));
				float open = 1 - (1 - t) * (1 - t) * (1 - t);
				float radius = ring.from + (ring.to - ring.from) * open;
				float fade = (float) Math.pow(1 - t, 1.4) * Math.min(1, t * 8);
				int alpha = Math.round(((ring.color >>> 24) & 255) * fade);
				if (alpha < 2) continue;
				int solid = alpha << 24 | ring.color & 0xFFFFFF;
				int clear = ring.color & 0xFFFFFF;
				float x = (float) (ring.x - camera.x);
				float y = (float) (ring.y - camera.y);
				float z = (float) (ring.z - camera.z);
				band(last, out, x, y, z, radius - ring.width, radius, clear, solid);
				band(last, out, x, y, z, radius, radius + ring.width, solid, clear);
			}
		});
	}

	/** A flat band round (x, y, z) from radius {@code r0} to {@code r1}, its colour running from {@code c0} to {@code c1}. */
	private static void band(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z, float r0, float r1, int c0, int c1) {
		r0 = Math.max(0, r0);
		for (int i = 0; i < SEGMENTS; i++) {
			double a0 = Math.PI * 2 * i / SEGMENTS;
			double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
			float cos0 = (float) Math.cos(a0);
			float sin0 = (float) Math.sin(a0);
			float cos1 = (float) Math.cos(a1);
			float sin1 = (float) Math.sin(a1);
			out.addVertex(pose, x + cos0 * r0, y, z + sin0 * r0).setColor(c0);
			out.addVertex(pose, x + cos0 * r1, y, z + sin0 * r1).setColor(c1);
			out.addVertex(pose, x + cos1 * r1, y, z + sin1 * r1).setColor(c1);
			out.addVertex(pose, x + cos1 * r0, y, z + sin1 * r0).setColor(c0);
		}
	}
}
