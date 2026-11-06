package dev.eventide.client.mixin;

import dev.eventide.client.sky.LivingSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the Skies module where the sun is and how much rain hides it, as Minecraft works them out each frame. */
@Mixin(SkyRenderer.class)
public abstract class SkyStateMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void eventide$keepSun(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
		LivingSky.capture(state.sunAngle, state.rainBrightness);
	}
}
