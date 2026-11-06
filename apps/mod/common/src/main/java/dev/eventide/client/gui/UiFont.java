package dev.eventide.client.gui;

import dev.eventide.client.EventideClient;
import dev.eventide.client.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Eventide's clean sans-serif, Inter (SIL Open Font License), the same face as the website, in medium,
 * semibold and bold. It's in the mod's resources as TrueType fonts (assets/eventide/font) sized so a
 * capital is 6.8 GUI pixels tall, with Minecraft's own font behind it for any character it lacks.
 * Theme's Menu font setting can switch back to Minecraft's pixel font.
 */
public final class UiFont {
	private static final FontDescription BOLD = new FontDescription.Resource(
			Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, "ui_bold"));
	private static final FontDescription SEMIBOLD = new FontDescription.Resource(
			Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, "ui_semibold"));
	private static final FontDescription MEDIUM = new FontDescription.Resource(
			Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, "ui_medium"));
	/** The wordmark's face: Unbounded Bold (SIL Open Font License), sized so a capital is 6.8 GUI pixels tall like the UI font. */
	private static final FontDescription BRAND = new FontDescription.Resource(
			Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, "brand"));

	/** Pixels per GUI unit each sharp copy of the UI font is drawn at; {@link #sharp} picks the nearest. */
	public static final float[] SHARPNESS = {1, 1.5f, 2, 3, 4, 6, 8};
	private static final String[] SHARP_NAMES = {"1", "1_5", "2", "3", "4", "6", "8"};
	private static final FontDescription[][] SHARP = new FontDescription[3][SHARPNESS.length];

	static {
		String[] weights = {"medium", "semibold", "bold"};
		for (int w = 0; w < 3; w++) {
			for (int i = 0; i < SHARPNESS.length; i++) {
				SHARP[w][i] = new FontDescription.Resource(Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, "ui_" + weights[w] + "_" + SHARP_NAMES[i]));
			}
		}
	}

	private UiFont() {
	}

	/**
	 * Text in the clean font rasterised at {@code SHARPNESS[level]} pixels per GUI unit ({@code weight} 0 medium,
	 * 1 semibold, 2 bold): text drawn from a raster close to its size on screen stays crisp instead of turning
	 * jagged when a big raster is shrunk.
	 */
	public static Component sharp(String text, int weight, int level) {
		return Component.literal(text).withStyle(Style.EMPTY.withFont(SHARP[weight][level]));
	}

	/** The copy of the font to draw text at {@code pixels} screen pixels per GUI unit with. */
	public static int level(float pixels) {
		for (int i = 0; i < SHARPNESS.length; i++) {
			if (SHARPNESS[i] >= pixels * 0.9f) return i;
		}
		return SHARPNESS.length - 1;
	}

	/** Whether the clean font is picked (Theme > Menu font). */
	public static boolean clean() {
		return !Modules.THEME_FONT.is("Minecraft");
	}

	/** Bold text in the clean font. */
	public static Component bold(String text) {
		return Component.literal(text).withStyle(Style.EMPTY.withFont(BOLD));
	}

	/** Semibold text in the clean font, for labels and smaller text. */
	public static Component semibold(String text) {
		return Component.literal(text).withStyle(Style.EMPTY.withFont(SEMIBOLD));
	}

	/** Medium text in the clean font, for longer descriptions. */
	public static Component medium(String text) {
		return Component.literal(text).withStyle(Style.EMPTY.withFont(MEDIUM));
	}

	/** Text in the wordmark's face, whatever the Menu font setting. */
	public static Component brand(String text) {
		return Component.literal(text).withStyle(Style.EMPTY.withFont(BRAND));
	}

	public static int width(Component text) {
		return Minecraft.getInstance().font.width(text);
	}
}
