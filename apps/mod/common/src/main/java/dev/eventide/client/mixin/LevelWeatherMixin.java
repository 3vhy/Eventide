package dev.eventide.client.mixin;

import dev.eventide.client.module.WeatherChanger;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather Changer: the rain and thunder the client sees (a client level's only). */
@Mixin(Level.class)
public abstract class LevelWeatherMixin {
	@Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
	private void eventide$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
		float rain = WeatherChanger.rain((Level) (Object) this, cir.getReturnValueF());
		if (rain != cir.getReturnValueF()) cir.setReturnValue(rain);
	}

	@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
	private void eventide$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
		float thunder = WeatherChanger.thunder((Level) (Object) this, cir.getReturnValueF());
		if (thunder != cir.getReturnValueF()) cir.setReturnValue(thunder);
	}
}
