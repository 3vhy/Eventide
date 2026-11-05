package dev.eventide.client.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Weather Changer: the weather you see, on your screen only: clear skies, rain, a thunderstorm or snow,
 * whatever the server's weather. It changes the rain and thunder levels the client reads (never the
 * server's, in singleplayer too), and for Snow, the kind of precipitation the client draws.
 */
public final class WeatherChanger {
	private WeatherChanger() {
	}

	private static boolean on(Level level) {
		return Modules.WEATHER.enabled() && level.isClientSide();
	}

	/** The rain level the client sees, or {@code real} when the weather isn't being changed. */
	public static float rain(Level level, float real) {
		if (!on(level)) return real;
		return Modules.WEATHER_TYPE.is("Clear") ? 0 : (float) (Modules.WEATHER_STRENGTH.get() / 100.0);
	}

	/** The thunder level the client sees, or {@code real} when the weather isn't being changed. */
	public static float thunder(Level level, float real) {
		if (!on(level)) return real;
		return Modules.WEATHER_TYPE.is("Thunder") ? (float) (Modules.WEATHER_STRENGTH.get() / 100.0) : 0;
	}

	/**
	 * The precipitation drawn at a place: snow everywhere for Snow (only on the game's own thread, so a
	 * singleplayer world's server still uses the real biomes), or {@code real}.
	 */
	public static Biome.Precipitation precipitation(Biome.Precipitation real) {
		if (!Modules.WEATHER.enabled() || !Modules.WEATHER_TYPE.is("Snow") || real == Biome.Precipitation.NONE) return real;
		Minecraft mc = Minecraft.getInstance();
		return mc != null && mc.isSameThread() ? Biome.Precipitation.SNOW : real;
	}
}
