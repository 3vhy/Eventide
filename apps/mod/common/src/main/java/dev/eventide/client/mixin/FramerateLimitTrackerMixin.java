package dev.eventide.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.eventide.client.module.Modules;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FPS Cap: at most the chosen frames per second, and fewer while the game's in the background. */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void eventide$cap(CallbackInfoReturnable<Integer> cir) {
		if (!Modules.FPS_CAP.enabled()) return;
		Minecraft mc = Minecraft.getInstance();
		int cap = mc != null && !mc.isWindowActive() ? Modules.FPS_CAP_BACKGROUND.getInt() : Modules.FPS_CAP_LIMIT.getInt();
		if (cap < cir.getReturnValueI()) cir.setReturnValue(cap);
	}
}
