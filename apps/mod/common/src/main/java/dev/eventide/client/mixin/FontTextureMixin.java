package dev.eventide.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.function.Supplier;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Eventide's own fonts (Inter, the wordmark's face and the badge) are drawn scaled to any size, so their glyph
 * sheets are sampled smoothly instead of Minecraft's nearest-pixel way, which made them look jagged. Minecraft's
 * pixel font keeps its sharp pixels.
 */
@Mixin(FontTexture.class)
public abstract class FontTextureMixin extends AbstractTexture {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void eventide$smooth(Supplier<String> name, GlyphRenderTypes renderTypes, boolean colored, CallbackInfo ci) {
		String label = name.get();
		if (label != null && label.startsWith("eventide:")) {
			this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
			dev.eventide.client.Hooks.smoothFont = true;
		}
	}
}
