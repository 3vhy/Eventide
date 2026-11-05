package dev.eventide.client.knife;

import dev.eventide.client.render.ShaderPacks;

/**
 * How knives tell Eventide's shader (shaders/core/knife) what they're made of. Every vertex carries its
 * material, the finish, the edge glow's hue and saturation, and how near the blade's outline it is in its
 * colour; the knife, the glint after a swing, the glow's strength and the light boost ride in its overlay slot.
 * The shader turns those into the finish, the reflections, the light and the luminous edge. The numbers here
 * match the ones in knife_surface.glsl. With an Iris shader pack on, the pack can't run Eventide's shader, so
 * everything is drawn with Minecraft's own entity shading and a plain colour per material instead.
 */
public final class KnifeShading {
	public static final int BLADE = 0;
	public static final int EDGE = 1;
	public static final int GUARD = 2;
	public static final int HARDWARE = 3;
	public static final int RUBBER = 4;
	public static final int POLYMER = 5;
	public static final int WOOD = 6;
	public static final int CORD = 7;
	public static final int CARBON = 8;
	public static final int RING = 9;
	public static final int ENERGY = 10;
	public static final int HANDLE_PAINT = 11;
	/** A set crystal: dark violet facets lit from inside by the glow colour. */
	public static final int GEM = 12;
	/** The glowing frame round a crystal, and glowing studs. */
	public static final int GEM_RIM = 13;

	private KnifeShading() {
	}

	/** Whether Eventide's own shader can draw (no Iris shader pack in the way). */
	public static boolean custom() {
		return !ShaderPacks.inUse();
	}

	/**
	 * The packed colour for a vertex: material, finish, the glow's hue (0..1) and saturation (0..1), and how
	 * near the blade's outline it is (0..1).
	 */
	static int color(int material, int finish, float hue, float saturation, float edge) {
		int sat = Math.round(Math.max(0, Math.min(1, saturation)) * 3);
		int g = (finish & 63) | sat << 6;
		int b = Math.round((hue - (float) Math.floor(hue)) * 255) & 255;
		int a = Math.round(Math.max(0, Math.min(1, edge)) * 255);
		return a << 24 | (material & 255) << 16 | g << 8 | b;
	}

	/**
	 * The packed overlay slot for a whole draw: which knife (for its blade length and holes), the glint after a
	 * swing (0..1 along the blade, 0 for none), how strong the edge glows (0..2) and the light boost (0..1).
	 */
	static int frame(int knife, float glint, float glow, float boost) {
		int g = Math.round(Math.max(0, Math.min(1, glint)) * 63);
		int x = (knife & 63) | g << 6;
		int y = Math.round(Math.max(0, Math.min(2, glow)) * 50) | Math.round(Math.max(0, Math.min(1, boost)) * 100) << 7;
		return (y & 0xFFFF) << 16 | (x & 0xFFFF);
	}

	/** A plain colour for a material in a finish, for drawing without Eventide's shader; the edge takes the glow's colour. */
	static int plain(int material, int finish, int glow) {
		return switch (material) {
			case BLADE, GUARD, RING, HANDLE_PAINT -> Finish.byId(finish).plain;
			case EDGE -> glow | 0xFF000000;
			case HARDWARE -> 0xFF3C3E46;
			case RUBBER, POLYMER -> 0xFF141418;
			case WOOD -> 0xFF4A2E1C;
			case CORD -> 0xFF1C1C22;
			case CARBON -> 0xFF1A1A1E;
			case ENERGY, GEM_RIM -> glow | 0xFF000000;
			case GEM -> 0xFF2A1450;
			default -> 0xFF808080;
		};
	}
}
