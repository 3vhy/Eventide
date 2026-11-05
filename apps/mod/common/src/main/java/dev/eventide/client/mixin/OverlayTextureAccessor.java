package dev.eventide.client.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Hit Glow: the overlay texture whose top rows are the hurt flash's tint. */
@Mixin(OverlayTexture.class)
public interface OverlayTextureAccessor {
	@Accessor("texture")
	DynamicTexture eventide$texture();
}
