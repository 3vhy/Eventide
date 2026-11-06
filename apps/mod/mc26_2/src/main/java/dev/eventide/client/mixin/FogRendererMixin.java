package dev.eventide.client.mixin;

import dev.eventide.client.sky.LivingSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Distant fog fades into the Skies module's horizon instead of Minecraft's sky colour. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "computeFogColor", at = @At("TAIL"))
	private void eventide$skyFog(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darken, Vector4f dest,
			CallbackInfo ci) {
		DimensionType.Skybox skybox = level.dimensionType().skybox();
		if (camera.getFluidInCamera() == FogType.NONE && skybox != DimensionType.Skybox.NONE && skybox != DimensionType.Skybox.END) {
			LivingSky.tintFog(dest);
		}
	}
}
