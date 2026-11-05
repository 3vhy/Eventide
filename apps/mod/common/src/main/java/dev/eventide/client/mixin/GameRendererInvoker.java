package dev.eventide.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Colour Grading: puts a post effect on the game's picture, the way spectating a creeper does. */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
	@Invoker("setPostEffect")
	void eventide$setPostEffect(Identifier id);
}
