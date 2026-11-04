package dev.eventide.client.hud;

import dev.eventide.client.Draw;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.module.Modules;
import dev.eventide.client.ui.Logo;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Type.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * The Watermark: Eventide's mark and wordmark in a dark pill in the top left, with your frames per second and
 * your ping beside them in small inner pills, each with a dot that turns from mint to gold to pink as things
 * get worse. The numbers keep room for their widest so the pill doesn't twitch.
 */
final class WatermarkWidget extends HudWidget {
	private static final float H = 18;
	private static final float PAD = 5;
	private static final float MARK = 11;
	private static final float WORD_CAP = 5.4f;
	private static final float VALUE_CAP = 5.0f;
	private static final float UNIT_CAP = 3.9f;
	private static final float CHIP_H = 11;

	WatermarkWidget() {
		super("watermark", Modules.WATERMARK, Anchor.TOP_LEFT);
	}

	private static int latency(Minecraft mc) {
		ClientPacketListener connection = mc.getConnection();
		if (connection == null || mc.player == null) return -1;
		PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
		return info == null ? -1 : info.getLatency();
	}

	private static float chipWidth(String value, String template, String unit) {
		float v = Math.max(Type.width(value, VALUE_CAP, Weight.BOLD), Type.width(template, VALUE_CAP, Weight.BOLD));
		return 5 + 3.2f + 3 + v + 2 + Type.width(unit, UNIT_CAP, Weight.MEDIUM) + 5;
	}

	private String fps(Minecraft mc, boolean preview) {
		return Integer.toString(mc.getFps());
	}

	private String ping(Minecraft mc, boolean preview) {
		int ms = latency(mc);
		return ms < 0 ? (preview ? "14" : "-") : Integer.toString(ms);
	}

	private float layout(Minecraft mc, boolean preview) {
		float w = PAD + MARK + 4 + Logo.wordmarkWidth(WORD_CAP);
		if (Modules.WATERMARK_FPS.get()) w += 6 + chipWidth(fps(mc, preview), "000", "fps");
		if (Modules.WATERMARK_PING.get()) w += 4 + chipWidth(ping(mc, preview), "00", "ms");
		return w + PAD;
	}

	@Override
	public int width(Minecraft mc) {
		return (int) Math.ceil(layout(mc, true));
	}

	@Override
	public int height(Minecraft mc) {
		return Math.round(H);
	}

	@Override
	public void draw(Draw d, int x, int y, Minecraft mc, boolean preview) {
		float w = width(mc);
		float r = radius(7);
		float strength = glassStrength();
		int accent = Accent.get();
		if (strength > 0.01f) {
			float old = Paint.opacity(Paint.opacity() * Math.min(1, strength * 1.15f));
			Paint.shadow(d, x, y, x + w, y + H, r, 0, 1, 6, 0, 0x55000000);
			Paint.rect(d, x, y, x + w, y + H, r, 0xD90B0817);
			Paint.shadow(d, x - 4, y - 6, x + 26, y + 10, 8, 0, 0, 16, 0, Paint.fade(accent, 0.22f));
			Paint.stroke(d, x, y, x + w, y + H, r, Hud.hairline(d), Paint.fade(Accent.lighten(accent, 0.2f), 0.45f));
			Paint.opacity(old);
		}
		float cy = y + H / 2f;
		float cx = x + PAD;
		Logo.mark(d, cx - 0.5f, cy - MARK / 2f, MARK, 1, System.currentTimeMillis());
		cx += MARK + 4;
		cx += Logo.wordmark(d, cx, cy - WORD_CAP / 2f - 0.6f, WORD_CAP, Tokens.BRAND_WORDMARK);
		if (Modules.WATERMARK_FPS.get()) {
			int fps = mc.getFps();
			int dot = fps >= 120 ? Tokens.MINT : fps >= 60 ? Tokens.GOLD : Tokens.LOW;
			cx += 6;
			cx += chip(d, cx, cy, fps(mc, preview), "000", "fps", dot);
		}
		if (Modules.WATERMARK_PING.get()) {
			int ms = latency(mc);
			int dot = ms < 0 || ms <= 80 ? Tokens.MINT : ms <= 160 ? Tokens.GOLD : Tokens.LOW;
			cx += 4;
			chip(d, cx, cy, ping(mc, preview), "00", "ms", dot);
		}
	}

	/** One inner pill: a status dot, the number and its unit. Returns how wide it was. */
	private static float chip(Draw d, float x, float cy, String value, String template, String unit, int dot) {
		float w = chipWidth(value, template, unit);
		float y1 = cy - CHIP_H / 2f;
		Paint.rect(d, x, y1, x + w, y1 + CHIP_H, CHIP_H / 2f, 0x14FFFFFF);
		Paint.glow(d, x + 5 + 1.6f, cy, 1.6f, 4, Paint.fade(dot, 0.35f));
		Paint.circle(d, x + 5 + 1.6f, cy, 1.6f, dot);
		float tx = x + 5 + 3.2f + 3;
		float vw = Math.max(Type.width(value, VALUE_CAP, Weight.BOLD), Type.width(template, VALUE_CAP, Weight.BOLD));
		Type.draw(d, value, tx + vw - Type.width(value, VALUE_CAP, Weight.BOLD), cy - VALUE_CAP / 2f, VALUE_CAP, Weight.BOLD, Tokens.TEXT);
		Type.draw(d, unit, tx + vw + 2, cy - VALUE_CAP / 2f + (VALUE_CAP - UNIT_CAP), UNIT_CAP, Weight.MEDIUM, Tokens.TEXT_2);
		return w;
	}
}
