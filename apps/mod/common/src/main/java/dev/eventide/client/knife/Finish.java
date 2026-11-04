package dev.eventide.client.knife;

import java.util.Locale;

/**
 * The finishes a knife can wear, all Eventide's own: dark steels with a luminous edge, a crystal lattice, a
 * nebula, and a few brighter metals. Each is drawn by Eventide's knife shader from its number (see
 * knife_surface.glsl), so they're crisp at any size and cost no textures; {@link #plain} is the colour used
 * when a shader pack is drawing instead. {@link #swatch} is a few colours for the finish picker.
 */
public enum Finish {
	OBSIDIAN(0, "Obsidian", "Dark gunmetal with cyan filigree near the guard", 0xFF26252C, 0xFF4A4955, 0xFF0E0E12),
	CRYSTAL(1, "Crystal", "Dark violet facets in a glowing lattice", 0xFF2A1450, 0xFF8B5CF6, 0xFF0C0618),
	MIDNIGHT(2, "Midnight", "Deep blued steel with silver filigree", 0xFF1A2448, 0xFF5A6CB4, 0xFF0A0E22),
	NEBULA(3, "Nebula", "Violet, cyan and pink clouds with a few stars", 0xFF5A2A9E, 0xFF4FD8FF, 0xFF1A0838),
	AURORA(4, "Aurora", "Cyan running into violet along the blade", 0xFF2C8CB8, 0xFF9461FF, 0xFF102A3A),
	FROST(5, "Frost", "Polished ice-silver with a cold blue light", 0xFFC8D6E6, 0xFFEFF6FF, 0xFF7C8CA4),
	ROSE(6, "Rose", "Pink gold, warm and bright", 0xFFD88AA8, 0xFFFFC4D8, 0xFF8A4A62),
	VOID(7, "Void", "Black as space, with a scatter of stars", 0xFF0A0A0E, 0xFF2A2A34, 0xFF000000),
	CHROME(8, "Chrome", "A mirror polish", 0xFFD8DCE2, 0xFFFFFFFF, 0xFF8C9096);

	/** The number the shader knows it by. */
	public final int id;
	public final String label;
	public final String description;
	final int plain;
	/** Three colours that sum the finish up, for its swatch. */
	public final int[] swatch;

	Finish(int id, String label, String description, int plain, int light, int dark) {
		this.id = id;
		this.label = label;
		this.description = description;
		this.plain = plain;
		this.swatch = new int[] {plain, light, dark};
	}

	public static String[] labels() {
		Finish[] all = values();
		String[] out = new String[all.length];
		for (int i = 0; i < all.length; i++) out[i] = all[i].label;
		return out;
	}

	public static Finish byLabel(String label) {
		for (Finish finish : values()) {
			if (finish.label.equalsIgnoreCase(label) || finish.name().equalsIgnoreCase(label)) return finish;
		}
		return null;
	}

	static Finish byId(int id) {
		for (Finish finish : values()) {
			if (finish.id == id) return finish;
		}
		return OBSIDIAN;
	}

	/** The finish a finish from before the redesign became, by its old name, or null when it's still a finish today. */
	public static String renamed(String old) {
		if (old == null || byLabel(old) != null) return null;
		return switch (old.toLowerCase(Locale.ROOT)) {
			case "eventide violet", "doppler (black pearl)", "crimson web" -> CRYSTAL.label;
			case "doppler (blue)", "blue steel" -> MIDNIGHT.label;
			case "doppler (pink)", "slaughter", "marble fade" -> ROSE.label;
			case "fade", "gamma", "emerald" -> AURORA.label;
			case "stainless", "stock" -> CHROME.label;
			case "night", "black laminate", "scorched" -> VOID.label;
			case "case hardened", "tiger tooth" -> NEBULA.label;
			default -> OBSIDIAN.label;
		};
	}
}
