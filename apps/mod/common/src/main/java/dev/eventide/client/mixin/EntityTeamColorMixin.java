package dev.eventide.client.mixin;

import dev.eventide.client.module.HitGlow;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hit Glow, Heaven and Player Outline: the glowing outline in your colour (or Heaven's white) instead of the team's. */
@Mixin(Entity.class)
public abstract class EntityTeamColorMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void eventide$glowColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self.level() == null || !self.level().isClientSide()) return;
		int color = HitGlow.color(self);
		if (color >= 0) cir.setReturnValue(color);
	}
}
