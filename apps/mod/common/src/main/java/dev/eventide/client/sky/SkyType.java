package dev.eventide.client.sky;

/**
 * The skies, each drawn per pixel by its own shader ({@code shaders/core/sky_<id>.fsh}). {@code horizon} is
 * the colour the sky meets the ground with, which distant fog is tinted toward so terrain fades into the
 * sky with no seam; {@code day} marks the daytime skies (shown in daylight in their thumbnail), and
 * {@code yaw}/{@code pitch} aim a thumbnail at the sky's best part.
 */
public enum SkyType {
	GALAXY("galaxy", "Galaxy", "Eventide's sky: a violet nebula band, an eclipse ring and falling starlight", 0x2A1050, false, 200, 16),
	NEBULA("nebula", "Nebula", "Billowing cyan and violet gas lit from within, with a white-hot heart", 0x16244A, false, 40, 14),
	AETHER("aether", "Aether", "Silver light flowing over dark anthracite", 0x2E3136, false, 90, 22),
	NOCTURNE("nocturne", "Nocturne", "Soft violet clouds with a lilac glow at their edges", 0x2A1A36, false, 180, 20),
	AMETHYST("amethyst", "Amethyst", "Deep violet marble with fine veins of light", 0x2A0E48, false, 180, 25),
	OPALINE("opaline", "Opaline", "Iridescent veils of lavender, pearl and cool cyan", 0x56636E, true, 90, 12),
	RAIN("rain", "Rain", "Heavy blue-grey clouds and distant veils of rain", 0x5E656F, true, 180, 10),
	SNOW("snow", "Snow", "A pale winter sky with soft layers of cloud", 0xCDD3DC, true, 180, 14),
	FIRE("fire", "Fire", "A fairytale sky of moving flame clouds and dark smoke", 0x8C2A08, false, 180, 20),
	HOLLOW("hollow", "Hollow", "Eventide's October dusk: a harvest moon and drifting wisps", 0xB0501E, false, 220, 6),
	AFTERGLOW("afterglow", "Afterglow", "Eventide's violet sunset with glowing streaks of cloud", 0xB860B8, false, 45, 8);

	public final String id;
	public final String label;
	public final String description;
	public final int horizon;
	public final boolean day;
	public final float yaw;
	public final float pitch;

	SkyType(String id, String label, String description, int horizon, boolean day, float yaw, float pitch) {
		this.id = id;
		this.label = label;
		this.description = description;
		this.horizon = horizon;
		this.day = day;
		this.yaw = yaw;
		this.pitch = pitch;
	}

	public static String[] labels() {
		SkyType[] all = values();
		String[] labels = new String[all.length];
		for (int i = 0; i < all.length; i++) labels[i] = all[i].label;
		return labels;
	}

	public static SkyType byLabel(String label) {
		for (SkyType sky : values()) {
			if (sky.label.equals(label)) return sky;
		}
		return GALAXY;
	}
}
