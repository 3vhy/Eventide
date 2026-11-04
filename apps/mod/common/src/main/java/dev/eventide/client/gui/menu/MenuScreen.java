package dev.eventide.client.gui.menu;

import dev.eventide.client.Draw;
import dev.eventide.client.module.Module;
import dev.eventide.client.module.Module.Category;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/** The Eventide mod menu on screen, in game and on the title screen: hosts {@link MenuView} over the blurred game. */
public final class MenuScreen extends MenuHost {
	public MenuScreen(Screen parent, Category start) {
		super(parent, start);
	}

	/** The menu at the Profiles page. */
	public static MenuScreen profiles(Screen parent) {
		MenuScreen screen = new MenuScreen(parent, null);
		screen.view.openProfiles();
		return screen;
	}

	/** The menu with one module's settings open. */
	public static MenuScreen settings(Screen parent, Module module) {
		MenuScreen screen = new MenuScreen(parent, module.category);
		screen.view.openSettings(module);
		return screen;
	}

	@Override
	protected boolean menuTexture() {
		return false;
	}

	@Override
	protected void drawBackdrop(Draw d) {
	}

	@Override
	protected void draw(Draw d, int mouseX, int mouseY, float delta) {
		view.draw(d, this.width, this.height, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (extras.pickingKey()) {
			extras.pickMouse(event.button());
			return true;
		}
		if (view.mouseClicked(event.x(), event.y(), event.button())) return true;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (view.mouseDragged(event.x(), event.y())) return true;
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (view.mouseReleased()) return true;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		return view.mouseScrolled(x, y, vertical);
	}
}
