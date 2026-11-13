package dev.eventide.client.hud;

import dev.eventide.client.compat.Compat;
import dev.eventide.client.Config;
import dev.eventide.client.Draw;
import dev.eventide.client.gui.HudEditorScreen;
import dev.eventide.client.mixin.BossHealthOverlayAccessor;
import dev.eventide.client.module.Module;
import dev.eventide.client.module.Modules;
import dev.eventide.client.module.Setting;
import dev.eventide.client.ui.Tokens;
import java.util.ArrayList;
import java.util.List;
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
		/** Side by side in one bar, centred at the top under the top-centre widgets. */
		TOP_BAR,
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
				int y = MARGIN + stackedBefore(mc) + bossBarClearance(mc, screenWidth);
				yield new int[] {anchor == Anchor.TOP_LEFT ? MARGIN : screenWidth - w - MARGIN, y};
			}
			case TOP_CENTER -> {
				int y = bossBarsBottom(mc) + MARGIN + stackedBefore(mc);
				yield new int[] {clearOfTopRight(mc, (screenWidth - w) / 2, y, w, h, screenWidth, screenHeight), y};
			}
			case TOP_BAR -> barPosition(mc, w, h, screenWidth, screenHeight);
			case BOTTOM_RIGHT -> {
				int y = screenHeight - h - MARGIN - stackedBefore(mc);
				yield new int[] {leftOfTopRight(mc, y, w, h), y};
			}
			case ABOVE_HOTBAR -> new int[] {(screenWidth - w) / 2, screenHeight - 58 - h - stackedBefore(mc)};
			case MIDDLE_RIGHT -> middleRight(mc);
			case BELOW_CROSSHAIR -> new int[] {(screenWidth - w) / 2, screenHeight / 2 + 20};
			case ABOVE_CROSSHAIR -> new int[] {(screenWidth - w) / 2, screenHeight / 2 - 22 - h};
		};
	}

	/**
	 * Where this widget goes in the top bar: the bar's widgets sit edge to edge (so their pills join into one),
	 * centred under the top-centre column and clear of the Music Player.
	 */
	private int[] barPosition(Minecraft mc, int w, int h, int screenWidth, int screenHeight) {
		boolean editing = Compat.screen() instanceof HudEditorScreen;
		int total = 0;
		int before = 0;
		boolean seen = false;
		for (HudWidget other : HudWidgets.ALL) {
			if (other.anchor != Anchor.TOP_BAR || other.storedPosition() != null || !other.shown() || !(editing || other.visible(mc))) continue;
			if (other == this) seen = true;
			int ow = other.width(mc);
			if (!seen) before += ow;
			total += ow;
		}
		if (!seen) {
			before = total;
			total += w;
		}
		int column = 0;
		for (HudWidget other : HudWidgets.ALL) {
			if (other.anchor == Anchor.TOP_CENTER && other.shown() && other.storedPosition() == null && other.usesAnchor()
					&& (editing || other.visible(mc))) {
				column += other.height(mc) + GAP;
			}
		}
		int y = bossBarsBottom(mc) + MARGIN + column;
		int start = clearOfTopRight(mc, (screenWidth - total) / 2, y, total, h, screenWidth, screenHeight);
		return new int[] {start + before, y};
	}

	/** For a middle-right widget: where its top would like to be on a screen this many units tall, before it makes room for the others. */
	protected int preferredTop(Minecraft mc, int screenHeight) {
		return (screenHeight - height(mc)) / 2;
	}

	/** For a middle-right widget: its gap to the right edge of the screen, in GUI pixels. */
	protected int edgeGap() {
		return MARGIN;
	}

	/**
	 * Where a middle-right widget goes. The middle-right widgets (the scoreboard, potions) stack in one group, centred where they
	 * would like to be and kept between the top-right and bottom-right widgets above and below them. When the gap there is too small,
	 * the group moves left of whatever covers its spot and looks again. Worked out in GUI pixels, since each widget has its own scale.
	 */
	private int[] middleRight(Minecraft mc) {
		boolean editing = Compat.screen() instanceof HudEditorScreen;
		int screenW = mc.getWindow().getGuiScaledWidth();
		int screenH = mc.getWindow().getGuiScaledHeight();
		float groupW = 0;
		float groupH = 0;
		float centres = 0;
		float offset = 0;
		int edge = 0;
		int members = 0;
		for (HudWidget other : HudWidgets.ALL) {
			if (other != this && (other.anchor != Anchor.MIDDLE_RIGHT || !other.placedAtAnchor(mc, editing))) continue;
			float scale = HudRenderer.scaleFor(other);
			float h = other.height(mc) * scale;
			if (other == this) offset = groupH + (members > 0 ? GAP : 0);
			groupH += h + (members > 0 ? GAP : 0);
			groupW = Math.max(groupW, other.width(mc) * scale);
			centres += other.preferredTop(mc, Math.round(screenH / scale)) * scale + h / 2f;
			edge = Math.max(edge, other.edgeGap());
			members++;
		}
		float wanted = Math.max(MARGIN, Math.min(screenH - MARGIN - groupH, centres / members - groupH / 2f));
		List<float[]> above = new ArrayList<>();
		List<float[]> below = new ArrayList<>();
		for (HudWidget other : HudWidgets.ALL) {
			if (other.anchor != Anchor.TOP_RIGHT && other.anchor != Anchor.BOTTOM_RIGHT || !other.placedAtAnchor(mc, editing)) continue;
			(other.anchor == Anchor.TOP_RIGHT ? above : below).add(other.guiBox(mc, screenW, screenH));
		}
		float right = screenW - edge;
		float top = wanted;
		for (int pass = 0; pass < 8; pass++) {
			float left = right - groupW;
			float clearTop = MARGIN;
			float clearBottom = screenH - MARGIN;
			float blockedAt = right;
			for (int side = 0; side < 2; side++) {
				for (float[] box : side == 0 ? above : below) {
					if (box[0] >= right || box[2] <= left) continue;
					if (side == 0) clearTop = Math.max(clearTop, box[3] + GAP);
					else clearBottom = Math.min(clearBottom, box[1] - GAP);
					if (box[1] < wanted + groupH && wanted < box[3]) blockedAt = Math.min(blockedAt, box[0] - GAP);
				}
			}
			if (groupH <= clearBottom - clearTop) {
				top = Math.max(clearTop, Math.min(clearBottom - groupH, wanted));
				break;
			}
			if (blockedAt >= right) break;
			right = blockedAt;
		}
		float scale = HudRenderer.scaleFor(this);
		return new int[] {Math.round(right / scale) - width(mc), Math.round((top + offset) / scale)};
	}

	/** x for a bottom-right widget at y: against the right edge, or left of the top-right widgets it would cover (a long Module List). */
	private int leftOfTopRight(Minecraft mc, int y, int w, int h) {
		boolean editing = Compat.screen() instanceof HudEditorScreen;
		int screenW = mc.getWindow().getGuiScaledWidth();
		int screenH = mc.getWindow().getGuiScaledHeight();
		float scale = HudRenderer.scaleFor(this);
		float y1 = y * scale;
		float y2 = (y + h) * scale;
		float right = screenW - MARGIN;
		for (HudWidget other : HudWidgets.ALL) {
			if (other.anchor != Anchor.TOP_RIGHT || !other.placedAtAnchor(mc, editing)) continue;
			float[] box = other.guiBox(mc, screenW, screenH);
			if (box[1] < y2 && y1 < box[3]) right = Math.min(right, box[0] - GAP);
		}
		return Math.round(right / scale) - w;
	}

	/** Where this widget is on a screen this big (GUI pixels), as {x1, y1, x2, y2}. */
	private float[] guiBox(Minecraft mc, int screenW, int screenH) {
		float scale = HudRenderer.scaleFor(this);
		int[] at = position(mc, Math.round(screenW / scale), Math.round(screenH / scale));
		return new float[] {at[0] * scale, at[1] * scale, (at[0] + width(mc)) * scale, (at[1] + height(mc)) * scale};
	}

	/** Whether this widget is on screen at its anchor (not dragged away, not hidden), so the widgets around it make room for it. */
	private boolean placedAtAnchor(Minecraft mc, boolean editing) {
		return shown() && storedPosition() == null && usesAnchor() && (editing || visible(mc));
	}

	/** How far this top corner's column moves down to clear the boss bars, when one of its widgets would reach under them. */
	private int bossBarClearance(Minecraft mc, int screenWidth) {
		int bottom = bossBarsBottom(mc);
		if (bottom == 0) return 0;
		int left = (screenWidth - 182) / 2 - MARGIN;
		int right = (screenWidth + 182) / 2 + MARGIN;
		for (HudWidget other : HudWidgets.ALL) {
			if (other.anchor != anchor || other.storedPosition() != null || !other.shown() || !other.visible(mc)) continue;
			int w = other.width(mc);
			if (anchor == Anchor.TOP_LEFT ? MARGIN + w > left : screenWidth - MARGIN - w < right) return bottom;
		}
		return 0;
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
			int bars = ((BossHealthOverlayAccessor) Compat.bossOverlay()).eventide$events().size();
			return bars == 0 ? 0 : 12 + (bars - 1) * 19 + 5;
		} catch (RuntimeException | LinkageError e) {
			return 0;
		}
	}

	/** Height taken by the earlier widgets stacked at the same anchor. */
	private int stackedBefore(Minecraft mc) {
		int total = 0;
		boolean editing = Compat.screen() instanceof HudEditorScreen;
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
