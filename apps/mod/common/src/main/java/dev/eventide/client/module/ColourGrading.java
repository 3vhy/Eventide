package dev.eventide.client.module;

import dev.eventide.client.EventideClient;
import dev.eventide.client.mixin.GameRendererInvoker;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Colour Grading: a look for the whole picture (the world and your hand, not the HUD), from presets: Eventide's
 * violet night, Vivid, Cinematic, Warm, Cold and Noir. Each is a post effect (assets/eventide/post_effect) run
 * by Minecraft's own post-processing; Minecraft's effects (spectating a creeper or a spider) win over it.
 */
public final class ColourGrading {
	private ColourGrading() {
	}

	/** The post effect for the chosen preset. */
	private static Identifier effect() {
		return Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, "grade_" + Modules.COLOUR_GRADING_PRESET.get().toLowerCase(Locale.ROOT));
	}

	public static void tick(Minecraft mc) {
		if (mc.gameRenderer == null) return;
		Identifier current = mc.gameRenderer.currentPostEffect();
		boolean ours = current != null && current.getNamespace().equals(EventideClient.MOD_ID);
		if (current != null && !ours) return;
		if (Modules.COLOUR_GRADING.enabled() && mc.level != null) {
			Identifier want = effect();
			if (!want.equals(current)) ((GameRendererInvoker) mc.gameRenderer).eventide$setPostEffect(want);
		} else if (ours) {
			mc.gameRenderer.clearPostEffect();
		}
	}
}
