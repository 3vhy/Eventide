package dev.eventide.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.eventide.client.module.Modules;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Fire hides the flames over your view when you burn; Low Fire lowers them out of the way. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Unique
	private static boolean eventide$lowered = false;

	@Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
	private static void eventide$fire(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		if (Modules.NO_FIRE.enabled()) {
			ci.cancel();
			return;
		}
		eventide$lowered = Modules.LOW_FIRE.enabled();
		if (eventide$lowered) {
			pose.pushPose();
			pose.translate(0, -(float) (Modules.LOW_FIRE_HEIGHT.get() / 100.0) * 0.6f, 0);
		}
	}

	@Inject(method = "submitFire", at = @At("TAIL"))
	private static void eventide$fireDone(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		if (eventide$lowered) pose.popPose();
		eventide$lowered = false;
	}
}
