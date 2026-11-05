package dev.eventide.client.module;

import dev.eventide.client.Hooks;
import dev.eventide.client.mixin.OverlayTextureAccessor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hit Glow: what you hit glows in your colour while it's hurt, through the outline Minecraft draws round
 * glowing entities, and the hurt flash (red in Minecraft) takes your colour too. Heaven's lifted bodies glow
 * white the same way. The flash is a tint in Minecraft's overlay texture, repainted when the colour changes.
 */
public final class HitGlow {
	/** How long after your hit something counts as yours to glow. */
	private static final long YOURS_MS = 1_500;
	/** Minecraft's hurt tint: red at 70%. */
	private static final int VANILLA_TINT = 0xB2FF0000;
	private static final Map<Integer, Long> HITS = new HashMap<>();
	private static int paintedTint = VANILLA_TINT;

	private HitGlow() {
	}

	/** You hit something. */
	public static void onAttack(Entity attacked) {
		HITS.put(attacked.getId(), System.currentTimeMillis());
	}

	/** Whether {@code entity} glows now: being lifted by Heaven, or hurt and (if so set) hit by you. */
	public static boolean glows(Entity entity) {
		if (KillEffects.ascending(entity)) return true;
		if (!Modules.HIT_GLOW.enabled() || !Modules.HIT_GLOW_OUTLINE.get() || !(entity instanceof LivingEntity living) || living.hurtTime <= 0) {
			return false;
		}
		if (entity == Minecraft.getInstance().player) return false;
		if (Modules.HIT_GLOW_WHO.is("Anything hurt")) return true;
		Long at = HITS.get(entity.getId());
		return at != null && System.currentTimeMillis() - at < YOURS_MS;
	}

	/** The outline colour for a glowing {@code entity}. */
	public static int color(Entity entity) {
		Hooks.hitGlowShown = true;
		return KillEffects.ascending(entity) ? KillEffects.HEAVEN_GLOW & 0xFFFFFF : Modules.HIT_GLOW_COLOR.get() & 0xFFFFFF;
	}

	/** The hurt flash's colour now: yours at Minecraft's strength, or Minecraft's red. */
	private static int tint() {
		if (!Modules.HIT_GLOW.enabled() || !Modules.HIT_GLOW_FLASH.get()) return VANILLA_TINT;
		return 0xB2000000 | Modules.HIT_GLOW_COLOR.get() & 0xFFFFFF;
	}

	/** Forgets old hits, and repaints the hurt flash when its colour changes. */
	public static void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		HITS.values().removeIf(at -> now - at > YOURS_MS * 4);
		int want = tint();
		if (want == paintedTint || mc.gameRenderer == null) return;
		DynamicTexture texture = ((OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).eventide$texture();
		var pixels = texture.getPixels();
		if (pixels == null) return;
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) pixels.setPixel(x, y, want);
		}
		texture.upload();
		paintedTint = want;
	}
}
