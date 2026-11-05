package dev.eventide.client.mixin;

import dev.eventide.client.module.ParticleFilter;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No Particles and No Explosion Particles: the particles they hide are never made. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void eventide$filter(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz,
			CallbackInfoReturnable<Particle> cir) {
		if (ParticleFilter.blocks(options)) cir.setReturnValue(null);
	}

	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void eventide$filterAll(Particle particle, CallbackInfo ci) {
		if (ParticleFilter.blocksAll()) ci.cancel();
	}
}
