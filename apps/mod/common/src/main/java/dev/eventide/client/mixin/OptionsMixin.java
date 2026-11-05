package dev.eventide.client.mixin;

import dev.eventide.client.module.EventideBadge;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Eventide Badge: Eventide's bit added to the skin-layer settings sent to the server. */
@Mixin(Options.class)
public abstract class OptionsMixin {
	@ModifyArg(method = "buildPlayerInformation", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ClientInformation;<init>"
			+ "(Ljava/lang/String;ILnet/minecraft/world/entity/player/ChatVisiblity;ZILnet/minecraft/world/entity/HumanoidArm;ZZ"
			+ "Lnet/minecraft/server/level/ParticleStatus;)V"), index = 4)
	private int eventide$badge(int customisation) {
		return EventideBadge.mark(customisation);
	}
}
