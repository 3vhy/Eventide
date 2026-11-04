package dev.eventide.client.hud;

import dev.eventide.client.Draw;
import dev.eventide.client.module.Modules;
import dev.eventide.client.ui.Paint;
import net.minecraft.world.BossEvent;

/**
 * Boss Bars: the server's boss bars (and the banners servers make from them, like "Join our Discord") as a
 * thin rounded line of light in the bar's own colour, softly glowing, instead of Minecraft's chunky bar. The
 * name above it is still Minecraft's, so servers' colours and icons stay.
 */
public final class BossBars {
	private static final int WIDTH = 182;

	private BossBars() {
	}

	public static boolean styled() {
		return Modules.BOSS_BARS.enabled();
	}

	/** The line for {@code event} with its left end at ({@code x}, {@code y}), where Minecraft would draw its bar. */
	public static void draw(Draw d, int x, int y, BossEvent event) {
		int color = color(event.getColor());
		float progress = Math.max(0, Math.min(1, event.getProgress()));
		float h = 2.2f;
		float cy = y + 2.5f;
		float x1 = x + 6;
		float x2 = x + WIDTH - 6;
		Paint.rect(d, x1, cy - h / 2f, x2, cy + h / 2f, h / 2f, 0x33FFFFFF);
		float fx = x1 + (x2 - x1) * progress;
		if (fx > x1 + h / 2f) {
			Paint.shadow(d, x1, cy - h / 2f, fx, cy + h / 2f, h / 2f, 0, 0, 6, 0, Paint.fade(color, 0.6f));
			Paint.rect(d, x1, cy - h / 2f, fx, cy + h / 2f, h / 2f, color);
		}
	}

	private static int color(BossEvent.BossBarColor color) {
		return switch (color) {
			case PINK -> 0xFFFF6AD5;
			case BLUE -> 0xFF35C4FF;
			case RED -> 0xFFFF5C6E;
			case GREEN -> 0xFF5CF29A;
			case YELLOW -> 0xFFFFD25C;
			case PURPLE -> 0xFFA06BFF;
			default -> 0xFFF0F0FA;
		};
	}
}
