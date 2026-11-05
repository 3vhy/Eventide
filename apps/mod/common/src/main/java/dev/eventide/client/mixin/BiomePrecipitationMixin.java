package dev.eventide.client.mixin;

import dev.eventide.client.module.WeatherChanger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather Changer: snow drawn everywhere for Snow. */
@Mixin(Biome.class)
public abstract class BiomePrecipitationMixin {
	@Inject(method = "getPrecipitationAt", at = @At("RETURN"), cancellable = true)
	private void eventide$snow(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
		Biome.Precipitation shown = WeatherChanger.precipitation(cir.getReturnValue());
		if (shown != cir.getReturnValue()) cir.setReturnValue(shown);
	}
}
