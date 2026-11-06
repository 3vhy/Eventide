package dev.eventide.client.module;

import dev.eventide.client.compat.Compat;
import dev.eventide.client.Draw;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** The custom crosshair. Replaces Minecraft's when the module is on. */
public final class Crosshair {
	private static final int OUTLINE = 0xB0000000;
	private static final int TARGET = 0xFFFF6B81;

	private Crosshair() {
	}

	public static void draw(Draw d, Runnable original) {
		Minecraft mc = Minecraft.getInstance();
		if (!Modules.CROSSHAIR.enabled() || mc.player == null || Compat.hudHidden()
				|| !mc.options.getCameraType().isFirstPerson() || mc.player.isSpectator()) {
			original.run();
			return;
		}
		dev.eventide.client.Hooks.crosshairRan = true;
		int cx = d.width() / 2;
		int cy = d.height() / 2;
		int size = Modules.CROSSHAIR_SIZE.getInt();
		int gap = Modules.CROSSHAIR_GAP.getInt();
		int thick = Modules.CROSSHAIR_THICKNESS.getInt();
		boolean outline = Modules.CROSSHAIR_OUTLINE.get();
		int color = Modules.CROSSHAIR_COLOR.get();
		if (Modules.CROSSHAIR_TARGET.get() && mc.crosshairPickEntity instanceof Player) color = TARGET;
		int half = thick / 2;
		String style = Modules.CROSSHAIR_STYLE.get();

		switch (style) {
			case "Dot" -> rect(d, cx - half - 1, cy - half - 1, cx - half + thick + 1, cy - half + thick + 1, color, outline);
			case "Circle" -> circle(d, cx, cy, gap + size, thick, color, outline);
			default -> {
				boolean top = !style.equals("T");
				// left, right, bottom, (top)
				rect(d, cx - gap - size, cy - half, cx - gap, cy - half + thick, color, outline);
				rect(d, cx + gap + 1, cy - half, cx + gap + 1 + size, cy - half + thick, color, outline);
				rect(d, cx - half, cy + gap + 1, cx - half + thick, cy + gap + 1 + size, color, outline);
				if (top) rect(d, cx - half, cy - gap - size, cx - half + thick, cy - gap, color, outline);
				if (style.equals("Cross + dot")) rect(d, cx - half, cy - half, cx - half + thick, cy - half + thick, color, outline);
			}
		}

		if (mc.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
			float strength = mc.player.getAttackStrengthScale(0f);
			if (strength < 1f) {
				int y = cy + gap + size + 4;
				d.fill(cx - 8, y, cx + 8, y + 2, 0x80000000);
				d.fill(cx - 8, y, cx - 8 + Math.round(16 * strength), y + 2, color);
			}
		}
	}

	private static void rect(Draw d, int x1, int y1, int x2, int y2, int color, boolean outline) {
		if (outline) d.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, OUTLINE);
		d.fill(x1, y1, x2, y2, color);
	}

	private static void circle(Draw d, int cx, int cy, int radius, int thick, int color, boolean outline) {
		int steps = Math.max(16, radius * 8);
		for (int pass = outline ? 0 : 1; pass < 2; pass++) {
			int c = pass == 0 ? OUTLINE : color;
			int grow = pass == 0 ? 1 : 0;
			for (int i = 0; i < steps; i++) {
				double angle = i * Math.PI * 2 / steps;
				int x = cx + (int) Math.round(Math.cos(angle) * radius);
				int y = cy + (int) Math.round(Math.sin(angle) * radius);
				d.fill(x - grow, y - grow, x + thick + grow, y + thick + grow, c);
			}
		}
	}
}
