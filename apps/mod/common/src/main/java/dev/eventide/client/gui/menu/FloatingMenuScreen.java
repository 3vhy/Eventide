package dev.eventide.client.gui.menu;

import dev.eventide.client.compat.Compat;
import dev.eventide.client.Draw;
import dev.eventide.client.module.Module.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * The mod menu in Floating mode. It's never put on screen: {@link FloatingMenu} keeps it and passes it your
 * crosshair's clicks on the panel and, while you type in one of its boxes, your keys, so the game keeps your
 * mouse and movement. Should it ever be shown as a screen, it still works with the mouse.
 */
public final class FloatingMenuScreen extends MenuHost {
	private int drag = 0;
	private double lastX;
	private double lastY;

	public FloatingMenuScreen(Screen parent, Category start) {
		super(parent, start);
	}

	@Override
	public boolean floating() {
		return true;
	}

	@Override
	protected void init() {
		super.init();
		FloatingMenu.show(view);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected boolean dimBackground() {
		return false;
	}

	@Override
	protected void drawBackdrop(Draw d) {
	}

	@Override
	protected void draw(Draw d, int mouseX, int mouseY, float delta) {
		FloatingMenu.hover(mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		lastX = event.x();
		lastY = event.y();
		if (extras.pickingKey()) {
			extras.pickMouse(event.button());
			return true;
		}
		float[] p = FloatingMenu.hover(event.x(), event.y());
		if (event.button() == 1 && (p == null || !view.clickLayout(p[0], p[1], 1))) {
			drag = 2;
			return true;
		}
		if (p == null) return true;
		if (event.button() == 0 && FloatingMenu.onRecenter(p[0], p[1])) {
			clickSound();
			FloatingMenu.recenter();
			return true;
		}
		if (event.button() == 0 && FloatingMenu.onBar(p[0], p[1])) {
			drag = 1;
			return true;
		}
		view.clickLayout(p[0], p[1], event.button());
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		double dx = event.x() - lastX;
		double dy = event.y() - lastY;
		lastX = event.x();
		lastY = event.y();
		if (drag == 1) {
			FloatingMenu.move(dx, dy);
			return true;
		}
		if (drag == 2) {
			var player = Minecraft.getInstance().player;
			double scale = Minecraft.getInstance().getWindow().getGuiScale();
			if (player != null) player.turn(dx * scale * 0.6, dy * scale * 0.6);
			return true;
		}
		float[] p = FloatingMenu.hover(event.x(), event.y());
		return p != null && view.dragLayout(p[0], p[1]);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		drag = 0;
		return view.mouseReleased();
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		float[] p = FloatingMenu.hover(x, y);
		return p != null && view.scrollLayout(p[0], p[1], vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 82 && !view.typing() && !extras.pickingBind() && !extras.pickingKey()) {
			FloatingMenu.recenter();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		Minecraft mc = Minecraft.getInstance();
		if (Compat.screen() == this) Compat.setScreen(null);
		FloatingMenu.close();
	}

	@Override
	public void openHudEditor() {
		FloatingMenu.close();
		Compat.setScreen(new dev.eventide.client.gui.HudEditorScreen(null));
	}
}
