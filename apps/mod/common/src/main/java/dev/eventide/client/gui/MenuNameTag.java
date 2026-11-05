package dev.eventide.client.gui;

import dev.eventide.client.Draw;
import dev.eventide.client.Hooks;
import dev.eventide.client.compat.Compat;
import dev.eventide.client.gui.menu.MenuScreen;
import dev.eventide.client.module.Modules;
import dev.eventide.client.ui.Grad;
import dev.eventide.client.ui.Logo;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Type.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * The Eventide logo in the corner of the pause menu, your inventory and chests: the Eventide mark, the
 * letter-spaced EVENTIDE wordmark and CLIENT on a violet glass pill. It can also show your head and name
 * (Name Protect and Skin Protect apply to them like everywhere else), or only those. With Show in game
 * on, the HUD shows it too. The main menu has its own version line instead.
 *
 * <p>It's sized in real screen pixels off the window's height, so it's the same size at any GUI scale.
 */
public final class MenuNameTag {
	/** Your own entry, for your skin when you're not in a world. */
	private static PlayerInfo offline = null;

	/** The pill's sizes in real pixels. */
	private record Layout(float cap, float pad, float h, float mark, float wordW, float face, float nameW,
			boolean logo, boolean name, boolean head, String player, float w) {
	}

	private MenuNameTag() {
	}

	/** The menus it shows on; other screens often have buttons in that corner. */
	public static boolean shows(Screen screen) {
		return screen instanceof AbstractContainerScreen<?>;
	}

	private static Layout layout(Minecraft mc) {
		float cap = Math.max(8, Math.round(mc.getWindow().getHeight() / 1080f * 11));
		float pad = Math.round(0.95f * cap);
		float h = Math.round(2.6f * cap);
		float mark = Math.round(1.75f * cap);
		boolean logo = !Modules.MENU_NAME_SHOW.is("Name");
		boolean name = !Modules.MENU_NAME_SHOW.is("Logo");
		boolean head = Modules.MENU_NAME_HEAD.get();
		String player = mc.getUser().getName();
		float wordW = Logo.wordmarkWidth(cap);
		float face = Math.round(1.45f * cap);
		float nameW = Type.width(player, cap, Weight.SEMIBOLD) + (head ? face + 0.5f * cap : 0);
		float logoW = mark + 0.5f * cap + wordW;
		float w = 2 * pad + (logo ? logoW : 0) + (logo && name ? 1.4f * cap : 0) + (name ? nameW : 0);
		return new Layout(cap, pad, h, mark, wordW, face, nameW, logo, name, head, player, Math.round(w));
	}

	/** The pill's {width, height} in real pixels. */
	public static int[] size(Minecraft mc) {
		Layout l = layout(mc);
		return new int[] {Math.round(l.w()), Math.round(l.h())};
	}

	public static void draw(Draw d, Screen screen) {
		if (!Modules.MENU_NAME.enabled() || !shows(screen)) return;
		Minecraft mc = Minecraft.getInstance();
		float guiScale = (float) mc.getWindow().getGuiScale();
		Layout l = layout(mc);
		int screenW = Math.round(d.width() * guiScale);
		int screenH = Math.round(d.height() * guiScale);
		int margin = Math.round(0.6f * l.cap());
		int x = Math.round(screenW - margin - l.w());
		int y = Math.round(screenH - margin - l.h());
		if (coversButton(screen, x / guiScale, y / guiScale, l.w() / guiScale, l.h() / guiScale)) {
			y = margin;
			if (coversButton(screen, x / guiScale, y / guiScale, l.w() / guiScale, l.h() / guiScale)) return;
		}
		d.push();
		d.scale(1f / guiScale);
		drawPill(d, mc, l, x, y);
		d.pop();
		Hooks.menuNameDrawn = true;
	}

	/** Draws the pill with its top left at (x, y); {@code d} must be drawing in real pixels. */
	public static void drawPill(Draw d, Minecraft mc, int x, int y) {
		drawPill(d, mc, layout(mc), x, y);
	}

	private static void drawPill(Draw d, Minecraft mc, Layout l, float x, float y) {
		float cap = l.cap();
		float h = l.h();
		float x2 = x + l.w();
		Paint.shadow(d, x, y, x2, y + h, h / 2f, 0, cap * 0.5f, cap * 1.6f, -cap * 0.3f, 0x8C000000);
		Paint.rect(d, x, y, x2, y + h, h / 2f, Tokens.GLASS_SOLID);
		Paint.stroke(d, x, y, x2, y + h, h / 2f, 1, Tokens.GLASS_EDGE);
		Paint.stroke(d, x + 1, y + 1, x2 - 1, y + h - 1, h / 2f - 1, 1, Grad.down(0x1AFFFFFF, 0x00FFFFFF));
		float capTop = y + (h - cap) / 2f;
		float cx = x + l.pad();
		if (l.logo()) {
			Logo.mark(d, cx, y + (h - l.mark()) / 2f, l.mark(), 1, System.currentTimeMillis());
			cx += l.mark() + 0.5f * cap;
			cx += Logo.wordmark(d, cx, capTop, cap, Tokens.BRAND_WORDMARK);
		}
		if (l.logo() && l.name()) {
			float lineH = 1.2f * cap;
			float lx = cx + 0.7f * cap;
			Paint.rect(d, lx, y + (h - lineH) / 2f, lx + 1, y + (h + lineH) / 2f, 0, 0x29FFFFFF);
			cx += 1.4f * cap;
		}
		if (l.name()) {
			if (l.head()) {
				MenuScreen.face(d, ownSkin(mc), cx, y + (h - l.face()) / 2f, l.face(), l.face() * 0.28f);
				cx += l.face() + 0.5f * cap;
			}
			Type.draw(d, l.player(), cx, capTop, cap, Weight.SEMIBOLD, 0xFFFFFFFF);
		}
	}

	/** Whether the pill at this spot (in GUI pixels) would sit on one of the screen's buttons (small windows put buttons in the corners). */
	private static boolean coversButton(Screen screen, float x, float y, float w, float h) {
		for (AbstractWidget widget : Compat.widgets(screen)) {
			if (widget.visible && widget.getX() < x + w && widget.getX() + widget.getWidth() > x && widget.getY() < y + h
					&& widget.getY() + widget.getHeight() > y) {
				return true;
			}
		}
		return false;
	}

	/** Your skin: from the server in a world, otherwise your account's. */
	public static PlayerSkin ownSkin(Minecraft mc) {
		ClientPacketListener connection = mc.getConnection();
		if (connection != null && mc.player != null) {
			PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
			if (info != null) return info.getSkin();
		}
		if (offline == null) offline = new PlayerInfo(mc.getGameProfile(), false);
		return offline.getSkin();
	}
}
