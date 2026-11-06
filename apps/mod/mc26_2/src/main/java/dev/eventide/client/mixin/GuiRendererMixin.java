package dev.eventide.client.mixin;

import dev.eventide.client.ui.VertexArena;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Once the frame's GUI is drawn, Eventide's shapes are done with their vertices, so the block they used is reused. */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void eventide$reuseVertices(CallbackInfo ci) {
		VertexArena.reset();
	}
}
