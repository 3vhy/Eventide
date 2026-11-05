package dev.eventide.client.hud;

import dev.eventide.client.Config;
import dev.eventide.client.Draw;
import dev.eventide.client.gui.HudEditorScreen;
import dev.eventide.client.mixin.BossHealthOverlayAccessor;
import dev.eventide.client.module.Module;
import dev.eventide.client.module.Modules;
import dev.eventide.client.module.Setting;
import dev.eventide.client.ui.Tokens;
import net.minecraft.client.Minecraft;

/**
 * One movable piece of the HUD. Until the player drags it somewhere, it sits at its anchor,
 * stacked under the other widgets that share the anchor. A dragged position is stored as
 * 0..1 across the free space, so it survives window and GUI scale changes.
 */
public abstract class HudWidget {
	/** Where a widget sits before it's moved. */
	public enum Anchor {
		TOP_LEFT,
		TOP_RIGHT,
		TOP_CENTER,
		MIDDLE_RIGHT,
		BOTTOM_RIGHT,
		BELOW_CROSSHAIR,
		ABOVE_CROSSHAIR,
		ABOVE_HOTBAR
	}

	private static final int MARGIN = 4;
	private static final int GAP = 3;
	public final String id;
	public final Module module;
	public final Anchor anchor;

	protected HudWidget(String id, Module module, Anchor anchor) {
		this.id = id;
		this.module = module;
		this.anchor = anchor;
	}

	public abstract int width(Minecraft mc);

	public abstract int height(Minecraft mc);

	/** Draws at (x, y). In preview mode (the hub) widgets show sample values when empty. */
	public abstract void draw(Draw d, int x, int y, Minecraft mc, boolean preview);

	public boolean visible(Minecraft mc) {
		return true;
	}

	/** Whether this widget is a glass pill, which can stick to other pills into one bar (see {@link Pills}). */
	public boolean pill() {
		return false;
	}

	/** Draws only what's inside the widget, without its glass, for a bar of pills stuck together. */
	public void drawContent(Draw d, int x, int y, Minecraft mc, boolean preview) {
		draw(d, x, y, mc, preview);
	}

	/** Whether the widget is switched on: its mod is on (and, for some, one of the mod's settings). */
	public boolean shown() {
		return module.enabled();
	}

	/** Whether the HUD size setting applies. The scoreboard keeps Minecraft's own size. */
	public boolean scaled() {
		return true;
	}

	/** This widget's own Size setting as a multiplier, or 1 when it has none. */
	public float sizeScale() {
		Setting.Slider size = module.sliderSetting("size");
		return size == null ? 1f : (float) (size.get() / 100.0);
	}

	/** Whether this widget has its own Size setting (scroll over it in the hub to change it). */
	public boolean resizable() {
		return module.sliderSetting("size") != null;
	}

	/** The position the player dragged this widget to, or null if it's at its anchor. */
	public double[] storedPosition() {
		double[] stored = Config.get().hud.get(id);
		return stored == null || stored.length < 2 ? null : stored;
	}

	public void setPosition(double x, double y) {
		Config.get().hud.put(id, new double[] {clamp(x), clamp(y)});
		Config.saveSoon();
	}

	public void resetPosition() {
		Config.get().hud.remove(id);
		Config.saveSoon();
	}

	/** Top-left corner {x, y} on a screen of the given (already scaled) size. */
	public int[] position(Minecraft mc, int screenWidth, int screenHeight) {
		double[] stored = storedPosition();
		if (stored != null) {
			return new int[] {
					(int) Math.round(stored[0] * Math.max(0, screenWidth - width(mc))),
					(int) Math.round(stored[1] * Math.max(0, screenHeight - height(mc)))
			};
		}
		return defaultPosition(mc, screenWidth, screenHeight);
	}

	protected int[] defaultPosition(Minecraft mc, int screenWidth, int screenHeight) {
		int w = width(mc);
		int h = height(mc);
		return switch (anchor) {
			case TOP_LEFT, TOP_RIGHT -> {
				int y = MARGIN + stackedBefore(mc);
				yield new int[] {anchor == Anchor.TOP_LEFT ? MARGIN : screenWidth - w - MARGIN, y};
			}
			case TOP_CENTER -> {
				int y = bossBarsBottom(mc) + MARGIN + stackedBefore(mc);
				yield new int[] {clearOfTopRight(mc, (screenWidth - w) / 2, y, w, h, screenWidth, screenHeight), y};
			}
			case BOTTOM_RIGHT -> new int[] {screenWidth - w - MARGIN, screenHeight - h - MARGIN - stackedBefore(mc)};
			case ABOVE_HOTBAR -> new int[] {(screenWidth - w) / 2, screenHeight - 58 - h - stackedBefore(mc)};
			case MIDDLE_RIGHT -> new int[] {screenWidth - w - MARGIN, (screenHeight - h) / 2};
			case BELOW_CROSSHAIR -> new int[] {(screenWidth - w) / 2, screenHeight / 2 + 20};
			case ABOVE_CROSSHAIR -> new int[] {(screenWidth - w) / 2, screenHeight / 2 - 22 - h};
		};
	}

	/** {@code x}, moved left if a widget in the top right (the Music Player) would cover a widget at (x, y), w x h. */
	private static int clearOfTopRight(Minecraft mc, int x, int y, int w, int h, int screenWidth, int screenHeight) {
		for (HudWidget other : HudWidgets.ALL) {
			if (other.anchor != Anchor.TOP_RIGHT || !other.shown() || !other.visible(mc)) continue;
			int[] at = other.position(mc, screenWidth, screenHeight);
			boolean beside = y < at[1] + other.height(mc) && at[1] < y + h;
			if (beside && x + w > at[0] - MARGIN) x = Math.max(MARGIN, at[0] - MARGIN - w);
		}
		return x;
	}

	/** Where the server's boss bars end at the top of the screen (0 when there are none). */
	private static int bossBarsBottom(Minecraft mc) {
		try {
			int bars = ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).eventide$events().size();
			return bars == 0 ? 0 : 12 + (bars - 1) * 19 + 5;
		} catch (RuntimeException | LinkageError e) {
			return 0;
		}
	}

	/** Height taken by the earlier widgets stacked at the same anchor. */
	private int stackedBefore(Minecraft mc) {
		int total = 0;
		boolean editing = mc.screen instanceof HudEditorScreen;
		for (HudWidget other : HudWidgets.ALL) {
			if (other == this) break;
			if (other.anchor == anchor && other.shown() && other.storedPosition() == null
					&& other.usesAnchor() && (editing || other.visible(mc))) {
				total += other.height(mc) + GAP;
			}
		}
		return total;
	}

	/** False when the widget's current layout ignores its anchor (e.g. armor drawn as a row). */
	protected boolean usesAnchor() {
		return true;
	}

	private static double clamp(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}

	/** The box's opacity, 0..255, from the module's Background setting. */
	protected int backgroundAlpha() {
		Setting.Slider slider = module.sliderSetting("background");
		double percent = slider == null ? 60 : slider.get();
		return (int) Math.round(percent / 100.0 * 255);
	}

	/** How strong the glass is, 0..1, from the Background setting. */
	public float glassStrength() {
		return backgroundAlpha() / 255f;
	}

	/** Text gets a shadow once the glass is too faint to read it against the world. */
	protected boolean textShadow() {
		return backgroundAlpha() < 90;
	}

	protected boolean showLabel() {
		Setting.Toggle toggle = module.toggleSetting("label");
		return toggle == null || toggle.get();
	}

	protected int valueColor() {
		Setting.Color color = module.colorSetting("color");
		return color == null ? Tokens.TEXT : color.get();
	}

	/** The corner radius for HUD glass: soft with Theme > Rounded HUD, nearly square without. */
	protected static float radius(float r) {
		return Modules.THEME_ROUNDED_HUD.get() ? r : Math.min(r, 1.5f);
	}

	/** This widget's violet glass behind (x, y, w, h). */
	protected void glass(Draw d, float x, float y, float w, float h, float r) {
		Hud.glass(d, x, y, x + w, y + h, radius(r), glassStrength());
	}
}
