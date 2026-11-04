package dev.eventide.client.hud;

import dev.eventide.client.Draw;
import dev.eventide.client.gui.HudEditorScreen;
import dev.eventide.client.module.Modules;
import dev.eventide.client.module.Waypoints;
import dev.eventide.client.module.Zoom;
import net.minecraft.client.Minecraft;

/** Draws the waypoint markers and the enabled HUD widgets every frame (not while the menu floats in front of you). */
public final class HudRenderer {
	private HudRenderer() {
	}

	public static void render(Draw d) {
		Minecraft mc = Minecraft.getInstance();
		CpsTracker.poll(mc);
		Zoom.update(mc);
		if (mc.options.hideGui || mc.player == null) return;
		Waypoints.drawOnScreen(d, mc);
		if (mc.screen instanceof HudEditorScreen || dev.eventide.client.gui.menu.FloatingMenu.visible()) return;
		drawAll(d, mc);
	}

	private static void drawAll(Draw d, Minecraft mc) {
		for (HudWidget widget : HudWidgets.ALL) {
			if (!widget.shown() || !widget.visible(mc)) continue;
			float scale = scaleFor(widget);
			int w = Math.round(d.width() / scale);
			int h = Math.round(d.height() / scale);
			int[] pos = widget.position(mc, w, h);
			d.push();
			d.scale(scale);
			widget.draw(d, pos[0], pos[1], mc, false);
			d.pop();
		}
	}

	/** How big a widget is drawn: the HUD size setting (unless it opts out) times its own Size setting. */
	public static float scaleFor(HudWidget widget) {
		return (widget.scaled() ? scale() : 1f) * widget.sizeScale();
	}

	/** The HUD size setting, as a multiplier. */
	public static float scale() {
		return (float) (Modules.THEME_HUD_SCALE.get() / 100.0);
	}
}
