package dev.eventide.client.mixin;

import dev.eventide.client.module.HitGlow;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hit Glow and Heaven: the glowing outline in your colour (or Heaven's white) instead of the team's. */
@Mixin(Entity.class)
public abstract class EntityTeamColorMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void eventide$glowColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self.level() != null && self.level().isClientSide() && HitGlow.glows(self)) cir.setReturnValue(HitGlow.color(self));
	}
}
