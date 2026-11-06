package dev.eventide.client.hud;

import dev.eventide.client.compat.Compat;
import dev.eventide.client.Draw;
import dev.eventide.client.gui.MenuNameTag;
import dev.eventide.client.module.Modules;
import net.minecraft.client.Minecraft;

/**
 * The menu logo on the HUD while you play, when Menu Logo's Show in game is on: at the top in the
 * middle, above the Music HUD, like 67's. It keeps the menu pill's real-pixel size, so the HUD size
 * doesn't change it.
 */
final class MenuLogoWidget extends HudWidget {
	MenuLogoWidget() {
		super("menu_logo", Modules.MENU_NAME, Anchor.TOP_CENTER);
	}

	@Override
	public boolean shown() {
		return super.shown() && Modules.MENU_NAME_HUD.get();
	}

	/** Not under a menu that shows the logo in its corner already. */
	@Override
	public boolean visible(Minecraft mc) {
		return Compat.screen() == null || !MenuNameTag.shows(Compat.screen());
	}

	@Override
	public boolean scaled() {
		return false;
	}

	private static float guiScale(Minecraft mc) {
		return (float) mc.getWindow().getGuiScale();
	}

	@Override
	public int width(Minecraft mc) {
		return (int) Math.ceil(MenuNameTag.size(mc)[0] / guiScale(mc));
	}

	@Override
	public int height(Minecraft mc) {
		return (int) Math.ceil(MenuNameTag.size(mc)[1] / guiScale(mc));
	}

	@Override
	public void draw(Draw d, int x, int y, Minecraft mc, boolean preview) {
		float k = dev.eventide.client.ui.Paint.density(d);
		if (k <= 0) return;
		int[] size = MenuNameTag.size(mc);
		d.push();
		d.scale(1f / k);
		int left = Math.round(x * k + (width(mc) * k - size[0]) / 2f);
		int top = Math.round(y * k + (height(mc) * k - size[1]) / 2f);
		MenuNameTag.drawPill(d, mc, left, top);
		d.pop();
	}
}
