package dev.eventide.client.module;

import dev.eventide.client.Hooks;
import dev.eventide.client.mixin.OverlayTextureAccessor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Hit Glow: what you hit glows in your colour while it's hurt, through the outline Minecraft draws round
 * glowing entities, and the hurt flash (red in Minecraft) takes your colour too. Heaven's lifted bodies glow
 * white the same way, and Player Outline puts the outline round every other player. The flash is a tint in
 * Minecraft's overlay texture, repainted when the colour changes.
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

	/** Why an entity glows: lifted by Heaven, hit, an outlined player, or not at all. */
	private enum Why {
		NONE,
		HEAVEN,
		HIT,
		PLAYER
	}

	private static Why why(Entity entity) {
		if (KillEffects.ascending(entity)) return Why.HEAVEN;
		Minecraft mc = Minecraft.getInstance();
		if (entity == mc.player) return Why.NONE;
		if (Modules.HIT_GLOW.enabled() && Modules.HIT_GLOW_OUTLINE.get() && entity instanceof LivingEntity living && living.hurtTime > 0) {
			Long at = HITS.get(entity.getId());
			if (Modules.HIT_GLOW_WHO.is("Anything hurt") || at != null && System.currentTimeMillis() - at < YOURS_MS) return Why.HIT;
		}
		if (Modules.PLAYER_OUTLINE.enabled() && entity instanceof Player && !entity.isInvisible()) return Why.PLAYER;
		return Why.NONE;
	}

	/** Whether {@code entity} has a glowing outline now. */
	public static boolean glows(Entity entity) {
		return why(entity) != Why.NONE;
	}

	/** The outline colour for a glowing {@code entity}, or -1 to keep its team's colour. */
	public static int color(Entity entity) {
		return switch (why(entity)) {
			case HEAVEN -> KillEffects.HEAVEN_GLOW & 0xFFFFFF;
			case HIT -> {
				Hooks.hitGlowShown = true;
				yield Modules.HIT_GLOW_COLOR.get() & 0xFFFFFF;
			}
			case PLAYER -> {
				Hooks.playerOutlined = true;
				yield Modules.PLAYER_OUTLINE_TEAM.get() ? -1 : Modules.PLAYER_OUTLINE_COLOR.get() & 0xFFFFFF;
			}
			case NONE -> -1;
		};
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
