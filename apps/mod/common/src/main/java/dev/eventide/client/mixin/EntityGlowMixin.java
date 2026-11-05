package dev.eventide.client.mixin;

import dev.eventide.client.module.HitGlow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hit Glow and Heaven: what you hit, and bodies being lifted, get Minecraft's glowing outline. */
@Mixin(Minecraft.class)
public abstract class EntityGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void eventide$hitGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (HitGlow.glows(entity)) cir.setReturnValue(true);
	}
}
