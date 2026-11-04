package dev.eventide.client.ui;

/**
 * Eventide's design tokens: every colour, size and timing the menus, the HUD and the title screen share,
 * in one place. The palette is a midnight purple night: near-black violet surfaces a tone step apart,
 * electric violet as the accent, cyan and ice blue as the second light, and a little pink for highlights.
 * The same values drive the download page (website/index.html) and the launcher, so all three look alike.
 * Colours are ARGB.
 */
public final class Tokens {
	private Tokens() {
	}

	// Brand: the logo's tile, its trident and its riptide rings (assets/brand/eventide-icon.svg), and the wordmark
	public static final int BRAND_TILE = 0xFF120D1C;
	public static final int BRAND_TRIDENT = 0xFFF3ECFF;
	public static final int BRAND_SWIRL = 0xFFA855F7;
	public static final int BRAND_WORDMARK = 0xFFF5EFFF;

	// Midnight surfaces, darkest first
	public static final int BG_0 = 0xFF05030B;
	public static final int BG_1 = 0xFF0A0716;
	public static final int BG_2 = 0xFF110C22;
	public static final int SURFACE = 0xFF181130;
	public static final int SURFACE_HI = 0xFF221A42;

	// Text
	public static final int TEXT = 0xFFF5F2FF;
	public static final int TEXT_2 = 0xFFAFA7D0;
	public static final int TEXT_3 = 0xFF726A96;

	// Accent family: electric violet, with cyan and ice as the second light and pink for highlights
	public static final int INDIGO = 0xFF5B3BF5;
	public static final int VIOLET = 0xFF8B5CF6;
	public static final int PURPLE = 0xFFA855F7;
	public static final int MAGENTA = 0xFFC94BF0;
	public static final int PINK = 0xFFFF6AD5;
	public static final int CYAN = 0xFF4FD8FF;
	public static final int ICE = 0xFFB6EEFF;
	public static final int LILAC = 0xFFCDB8FF;
	public static final int LILAC_2 = 0xFFAA8BFF;
	public static final int LILAC_3 = 0xFFE9A8FF;

	// Signals
	public static final int GOLD = 0xFFFFD27A;
	public static final int MINT = 0xFF7CF2C8;
	public static final int LOW = 0xFFFF5C84;
	public static final int EMBER = 0xFFFF8A5C;

	// Hairlines
	public static final int STROKE = 0x14FFFFFF;
	public static final int STROKE_HI = 0x29FFFFFF;

	// Glass: HUD pieces and floating panels
	public static final int GLASS = 0xB00A0716;
	public static final int GLASS_SOLID = 0xEB0C0819;
	public static final int GLASS_EDGE = 0x1FFFFFFF;
	public static final int GLASS_SHINE = 0x12FFFFFF;
	/** The menu window and its sidebar: the window a tone darker than the cards on it. */
	public static final int WINDOW = 0xF00A0716;
	public static final int WINDOW_EDGE = 0x24FFFFFF;
	public static final int SIDEBAR = 0x66050310;
	/** Module cards. */
	public static final int CARD_TOP = 0x0FFFFFFF;
	public static final int CARD_BOTTOM = 0x0AFFFFFF;
	public static final int CARD_EDGE = 0x14FFFFFF;
	public static final int CARD_SHINE = 0x0DFFFFFF;
	/** Round buttons, the search field and other quiet controls. */
	public static final int CONTROL = 0x0FFFFFFF;
	public static final int CONTROL_HOVER = 0x1CFFFFFF;
	public static final int CONTROL_EDGE = 0x1CFFFFFF;
	/** An idle icon tile, an off switch's track. */
	public static final int TILE_OFF = 0x0FFFFFFF;
	public static final int SWITCH_OFF = 0xFF262038;
	public static final int KEY = 0x0DFFFFFF;

	// The accent ramp, deep violet to lilac (Accent derives other themes from the middle stop)
	public static final int PRIMARY_FROM = 0xFF7339F2;
	public static final int PRIMARY_MID = 0xFF9461FF;
	public static final int PRIMARY_TO = 0xFFC28BFF;
	public static final int TILE_FROM = 0xFF7C3AED;
	public static final int TILE_TO = 0xFFB37BFF;
	public static final int SWITCH_FROM = 0xFF8452FF;
	public static final int SWITCH_TO = 0xFFA97CFF;
	public static final int BAR_FROM = 0xFF9461FF;
	public static final int BAR_TO = 0xFF4FD8FF;
	/** The glow under lit things: violet at 90%. */
	public static final int GLOW = 0xE68B5CF6;

	// Corner radii, in layout units (a layout unit is one CSS pixel on the site)
	public static final float R_CARD = 14;
	public static final float R_TILE = 10;
	public static final float R_GLASS = 10;
	public static final float R_KEY = 7;
	public static final float R_PANEL = 18;
	public static final float R_PILL = 999;

	// Motion, in milliseconds. UI moves stay under 300ms and ease out (hover and press faster still), so
	// the menus answer at once and never hold up a click.
	public static final int T_SWITCH_KNOB = 200;
	public static final int T_SWITCH_FILL = 160;
	public static final int T_HOVER = 140;
	public static final int T_COLOR = 160;
	public static final int T_INDICATOR = 260;
	public static final int T_PRESS = 110;
	public static final int T_CAPTION = 200;
	public static final int T_REVEAL = 240;
	public static final int T_REVEAL_MOVE = 280;
	public static final int T_STAGGER = 22;
	public static final int T_SHEET = 260;
	public static final int T_OPEN = 220;
	public static final int T_CROSSFADE = 1200;
	/** How far things rise as they appear. */
	public static final float REVEAL_RISE = 8;
}
