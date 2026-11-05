package dev.eventide.client;

import net.minecraft.client.Minecraft;

/**
 * Small shared helpers, plus flags the mixins set when they run. The in-game test checks the
 * flags to prove each hook works on every Minecraft version Eventide supports.
 */
public final class Hooks {
	public static volatile boolean titleHookRan = false;
	public static volatile boolean entityEventHookRan = false;
	public static volatile boolean tabPingRan = false;
	public static volatile boolean slotHighlightRan = false;
	public static volatile boolean freelookCameraRan = false;
	public static volatile boolean ownNametagRan = false;
	public static volatile boolean styledNametagRan = false;
	public static volatile boolean shinyPotRan = false;
	public static volatile boolean timePacketRan = false;
	public static volatile boolean crosshairRan = false;
	public static volatile boolean scoreboardRan = false;
	public static volatile boolean nameProtectRan = false;
	public static volatile boolean skinProtectRan = false;
	public static volatile boolean playerListTicked = false;
	public static volatile boolean playerListAskedServer = false;
	public static volatile boolean wrapProtectRan = false;
	public static volatile boolean swingSpeedRan = false;
	public static volatile boolean equipSpeedRan = false;
	/** Command suggestion answers that reached the client, and the id of the last one (for the in-game test). */
	public static volatile int suggestionPackets = 0;
	public static volatile int lastSuggestionId = -1;
	public static volatile boolean musicArtUploaded = false;
	public static volatile boolean musicIconUploaded = false;
	public static volatile boolean textRuleRan = false;
	public static volatile boolean customSkinLoaded = false;
	public static volatile boolean menuNameDrawn = false;
	public static volatile boolean badgeShown = false;
	public static volatile boolean handAuraDrawn = false;
	public static volatile boolean heavenLifted = false;
	public static volatile boolean hitGlowShown = false;
	public static volatile boolean jumpCircleShown = false;
	public static volatile boolean playerOutlined = false;
	public static volatile boolean disintegrated = false;
	public static volatile boolean knifeThirdPerson = false;
	public static volatile boolean cosmeticsDrawn = false;
	public static volatile boolean pillsStuck = false;
	public static volatile boolean knifeRendered = false;
	public static volatile boolean knifeDrawRan = false;
	public static volatile boolean knifeSlashRan = false;
	public static volatile boolean knifeInspectRan = false;
	public static volatile boolean lyricsDrawn = false;
	public static volatile boolean staffListDrawn = false;
	public static volatile boolean hotbarDrawn = false;
	public static volatile boolean serverAddressDrawn = false;

	private Hooks() {
	}

	/** The GLFW handle of the game window, or 0 before the window exists. */
	public static long windowHandle() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getWindow() == null) return 0;
		return mc.getWindow().handle();
	}

	/** Set when the Skies module draws the sky (SkyRenderer.renderSkyDisc). */
	public static volatile boolean skyDrawn = false;
	/** Set when Kill Effects starts an effect. */
	public static volatile boolean killEffectRan = false;
}
