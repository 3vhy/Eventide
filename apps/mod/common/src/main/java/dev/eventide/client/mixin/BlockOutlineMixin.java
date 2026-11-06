package dev.eventide.client.mixin;

import dev.eventide.client.module.Modules;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Block Outline: the outline round the block you look at in your colour and thickness. */
@Mixin(LevelRenderer.class)
public abstract class BlockOutlineMixin {
	@ModifyVariable(method = {"renderHitOutline", "submitHitOutline"}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int eventide$color(int color) {
		return Modules.BLOCK_OUTLINE.enabled() ? Modules.BLOCK_OUTLINE_COLOR.get() : color;
	}

	@ModifyVariable(method = {"renderHitOutline", "submitHitOutline"}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float eventide$width(float width) {
		return Modules.BLOCK_OUTLINE.enabled() && width < 7 ? (float) Modules.BLOCK_OUTLINE_WIDTH.get() : width;
	}
}
