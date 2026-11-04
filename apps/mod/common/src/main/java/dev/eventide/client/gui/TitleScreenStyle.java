package dev.eventide.client.gui;

import dev.eventide.client.Draw;
import dev.eventide.client.Hooks;
import dev.eventide.client.EventideClient;
import dev.eventide.client.compat.Compat;
import dev.eventide.client.gui.menu.MenuHost;
import dev.eventide.client.gui.menu.MenuScreen;
import dev.eventide.client.module.Modules;
import dev.eventide.client.module.Setting;
import dev.eventide.client.ui.Anim;
import dev.eventide.client.ui.Ease;
import dev.eventide.client.ui.Grad;
import dev.eventide.client.ui.Logo;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Sky;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Type.Weight;
import dev.eventide.client.ui.Widgets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Eventide's main menu, over Minecraft's own title screen so buttons other mods add still show up: Eventide's
 * live Galaxy sky behind it (the same shader as in game, the camera drifting slowly), the Eventide logo in place
 * of Minecraft's with a soft violet glow (and the splash beside it), Minecraft's buttons as midnight pills, the
 * Eventide version over Minecraft's in the bottom left, and round buttons in the bottom right for the mod
 * menu, your screenshots and the Scene panel. The Scene panel picks the time of day (the light over the
 * panorama), the scene (the galaxy, Minecraft's panorama, a night sky or October) and how the camera moves.
 */
public final class TitleScreenStyle {
	private static final int MARGIN = 6;
	private static final int ROUND = 20;
	private static final int ROUND_GAP = 6;
	/** The copyright line along the bottom, which the round buttons sit above. */
	private static final int FOOTER = 12;
	private static final float PANEL_W = 176;
	private static final float PANEL_PAD = 12;
	private static final float ROW_LABEL = 15;
	private static final float SEGMENT_H = 22;
	private static final float ROW_GAP = 10;

	private enum Kind {
		ROUND, SEGMENT
	}

	/** One of Eventide's own title screen buttons: what it is and what it draws. */
	private record Part(Kind kind, String icon, Setting.Choice choice, int option) {
	}

	private record Row(String label, Setting.Choice choice, String[] icons) {
	}

	private static final List<Row> ROWS = List.of(
			new Row("Time of day", Modules.MAIN_MENU_TIME, new String[] {"sunrise", "sun", "sunset", "moon-star", "clock"}),
			new Row("Scene", Modules.MAIN_MENU_SCENE, new String[] {"orbit", "image", "sparkles", "ghost"}),
			new Row("Camera", Modules.MAIN_MENU_CAMERA, new String[] {"orbit", "wind", "pause"}));

	private static final Map<AbstractWidget, Part> PARTS = new WeakHashMap<>();
	private static final Map<AbstractWidget, Anim> HOVER = new WeakHashMap<>();
	private static final Map<Setting.Choice, Anim> PICKED = new WeakHashMap<>();
	private static final Anim PANEL = new Anim(0, Tokens.T_SHEET, Ease.OUT);
	private static boolean panelOpen = false;
	private static float panelX;
	private static float panelY;
	private static float driftSpin = 0;
	private static int lastMouseX = -1;
	private static int lastMouseY = -1;

	private TitleScreenStyle() {
	}

	public static boolean enabled() {
		return Modules.MAIN_MENU.enabled();
	}

	private static boolean onTitleScreen() {
		return enabled() && Minecraft.getInstance().screen instanceof TitleScreen;
	}

	/** Called once the title screen has made its buttons (and other mods have added theirs). */
	public static void prepare(Screen screen) {
		if (!enabled() || !(screen instanceof TitleScreen)) return;
		Minecraft mc = Minecraft.getInstance();
		int y = screen.height - FOOTER - MARGIN - ROUND;
		int x = screen.width - MARGIN - ROUND;
		round(screen, x, y, "sliders-horizontal", "Scene", () -> {
			panelOpen = !panelOpen;
			showSegments(screen);
		});
		x -= ROUND + ROUND_GAP;
		round(screen, x, y, "image", "Screenshots", TitleScreenStyle::openScreenshots);
		x -= ROUND + ROUND_GAP;
		round(screen, x, y, "orbit", "Eventide", () -> mc.setScreen(MenuHost.fromKey(screen)));

		panelX = screen.width - MARGIN - PANEL_W;
		panelY = y - 8 - panelHeight();
		float sy = panelY + PANEL_PAD;
		for (Row row : ROWS) {
			sy += ROW_LABEL;
			List<String> options = row.choice().options();
			float segment = (PANEL_W - 2 * PANEL_PAD) / options.size();
			for (int i = 0; i < options.size(); i++) {
				int sx = Math.round(panelX + PANEL_PAD + i * segment);
				int ex = Math.round(panelX + PANEL_PAD + (i + 1) * segment);
				String option = options.get(i);
				Button button = Button.builder(Component.literal(row.label() + ": " + option), pressed -> row.choice().set(option))
						.pos(sx, Math.round(sy)).size(ex - sx, Math.round(SEGMENT_H)).build();
				PARTS.put(button, new Part(Kind.SEGMENT, null, row.choice(), i));
				Compat.addWidget(screen, button);
			}
			sy += SEGMENT_H + ROW_GAP;
		}
		showSegments(screen);
		PANEL.snap(panelOpen ? 1 : 0);
		Compat.overlay(screen, (d, mouseX, mouseY) -> {
			lastMouseX = mouseX;
			lastMouseY = mouseY;
			drawOverlay(d, screen);
		});
		ScreenMouseEvents.allowMouseClick(screen).register((shown, event) -> !clickPanel(shown, event));
		ScreenKeyboardEvents.afterKeyPress(screen).register((shown, event) -> {
			if (EventideClient.openSettingsKey != null && EventideClient.openSettingsKey.matches(event) && mc.screen == shown) {
				mc.setScreen(MenuHost.fromKey(shown));
			}
		});
		Hooks.mainMenuShown = true;
	}

	private static boolean overPanel(double x, double y) {
		return panelOpen && x >= panelX && x < panelX + PANEL_W && y >= panelY && y < panelY + panelHeight();
	}

	/**
	 * A click on the open Scene panel goes to the panel only, never to the main menu's buttons under it:
	 * the segment under the mouse takes it, and anywhere else on the panel does nothing.
	 */
	private static boolean clickPanel(Screen screen, MouseButtonEvent event) {
		if (!onTitleScreen() || !overPanel(event.x(), event.y())) return false;
		for (AbstractWidget widget : Compat.widgets(screen)) {
			Part part = PARTS.get(widget);
			if (part != null && part.kind() == Kind.SEGMENT && widget.visible && widget.isMouseOver(event.x(), event.y())) {
				widget.mouseClicked(event, false);
				break;
			}
		}
		return true;
	}

	/** Whether the open Scene panel is over this widget where the mouse is, so it shouldn't light up. */
	public static boolean hidesHover(AbstractWidget widget) {
		return panelOpen && !PARTS.containsKey(widget) && onTitleScreen() && overPanel(lastMouseX, lastMouseY);
	}

	/** The Scene panel's segments can only be clicked (or reached with Tab) while it's open. */
	private static void showSegments(Screen screen) {
		for (AbstractWidget widget : Compat.widgets(screen)) {
			Part part = PARTS.get(widget);
			if (part == null || part.kind() != Kind.SEGMENT) continue;
			widget.visible = panelOpen;
			widget.active = panelOpen;
		}
	}

	private static float panelHeight() {
		return 2 * PANEL_PAD + ROWS.size() * (ROW_LABEL + SEGMENT_H + ROW_GAP) - ROW_GAP;
	}

	private static void round(Screen screen, int x, int y, String icon, String label, Runnable action) {
		Button button = Button.builder(Component.literal(label), pressed -> action.run()).pos(x, y).size(ROUND, ROUND).build();
		PARTS.put(button, new Part(Kind.ROUND, icon, null, -1));
		Compat.addWidget(screen, button);
	}

	private static void openScreenshots() {
		Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
		try {
			Files.createDirectories(folder);
			Util.getPlatform().openPath(folder);
		} catch (IOException e) {
			EventideClient.LOG.warn("Couldn't open the screenshots folder", e);
		}
	}

	/** Whether the title screen's mixin hands this widget to {@link #drawWidget}. */
	public static boolean styled(AbstractWidget widget) {
		return PARTS.containsKey(widget) && onTitleScreen();
	}

	/** The round buttons; the Scene panel's segments are drawn with the panel. */
	public static void drawWidget(Draw d, AbstractWidget widget) {
		Part part = PARTS.get(widget);
		Hooks.titleButtonsStyled = true;
		if (part == null || part.kind() != Kind.ROUND || !widget.visible) return;
		Anim anim = HOVER.computeIfAbsent(widget, w -> new Anim(0, Tokens.T_COLOR, Ease.OUT));
		boolean on = widget.isHoveredOrFocused() || "sliders-horizontal".equals(part.icon()) && panelOpen;
		anim.set(on);
		float r = widget.getWidth() / 2f;
		float cx = widget.getX() + r;
		float cy = widget.getY() + r;
		float old = Paint.opacity(Paint.opacity() * widget.getAlpha());
		ButtonStyle.pill(d, widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), anim.get(),
				widget.isFocused());
		float size = r * 1.0f;
		Paint.icon(d, part.icon(), cx - size / 2f, cy - size / 2f, size, 0xFFF4F0FF);
		Paint.opacity(old);
	}

	/** The Eventide logo where Minecraft's goes: {@code y} is the top of Minecraft's logo, {@code alpha} its fade. */
	public static void drawLogo(Draw d, int screenWidth, float alpha, int y) {
		long now = System.currentTimeMillis();
		float mark = 44;
		float cap = 24;
		float w = mark * 1.24f + Logo.wordmarkWidth(cap);
		float x = screenWidth / 2f - w / 2f;
		float old = Paint.opacity(Paint.opacity() * alpha);
		float breath = 0.85f + 0.15f * (float) Math.sin(now % 5000 / 5000.0 * Math.PI * 2);
		Paint.shadow(d, x - 10, y + 4, x + w + 10, y + mark - 4, mark / 2f, 0, 0, mark * 1.6f, 0, Paint.fade(Accent.get(), 0.22f * breath));
		Logo.lockup(d, x, y, mark, cap, now);
		Paint.opacity(old);
		Hooks.logoDrawn = true;
	}

	/** Whether the main menu draws its own scene in place of Minecraft's panorama. */
	public static boolean drawsBackground() {
		return enabled() && !Modules.MAIN_MENU_SCENE.is("Panorama");
	}

	/** The Galaxy, Night sky or October scene, in place of the panorama. */
	public static void drawBackground(Draw d, int w, int h) {
		if (Modules.MAIN_MENU_SCENE.is("October")) {
			October.drawBackdrop(d, w, h);
			return;
		}
		if (Modules.MAIN_MENU_SCENE.is("Galaxy") && drawGalaxy(d, w, h)) return;
		float unit = Math.min(w / 1600f, h / 900f);
		d.push();
		d.scale(unit);
		Sky.backdrop(d, w / unit, h / unit, false, 1);
		Sky.stars(d, w / unit, h / unit, 0.8f, 1, System.currentTimeMillis());
		d.pop();
		tint(d, w, h, true);
	}

	private static float galaxyYaw = 200;
	private static long galaxyFrame = 0;

	/**
	 * Eventide's live Galaxy sky behind the menu, the camera drifting slowly round it (or still, as Camera
	 * says), with a soft shade at the bottom so the buttons read. Returns false if the sky couldn't be drawn.
	 */
	private static boolean drawGalaxy(Draw d, int w, int h) {
		var window = Minecraft.getInstance().getWindow();
		long now = System.nanoTime();
		float dt = galaxyFrame == 0 ? 0 : Math.min(0.1f, (now - galaxyFrame) / 1e9f);
		galaxyFrame = now;
		float speed = Modules.MAIN_MENU_CAMERA.is("Still") ? 0 : Modules.MAIN_MENU_CAMERA.is("Drift") ? 0.6f : 1.6f;
		galaxyYaw = (galaxyYaw + dt * speed) % 360f;
		float pitch = 14 + (float) Math.sin(now / 1e9 * 0.07) * 3;
		var id = dev.eventide.client.sky.LivingSky.drawMenu(dev.eventide.client.sky.SkyType.GALAXY, Math.max(2, window.getWidth() * 2 / 3),
				Math.max(2, window.getHeight() * 2 / 3), galaxyYaw, pitch);
		if (id == null) return false;
		Paint.image(d, id, 0, 0, w, h, 0, 0, 1, 1, 0, 0xFFFFFFFF, true);
		Paint.rect(d, 0, h * 0.45f, w, h, 0, Grad.down(0x00000000, 0xB305030B));
		return true;
	}

	/** The time of day's light over the panorama, and a soft shade at the bottom so the buttons read. */
	public static void drawTint(Draw d, int w, int h) {
		if (!enabled() || Modules.MAIN_MENU_SCENE.is("Galaxy")) return;
		tint(d, w, h, false);
	}

	private static String timeOfDay() {
		if (!Modules.MAIN_MENU_TIME.is("Live")) return Modules.MAIN_MENU_TIME.get();
		int hour = LocalTime.now().getHour();
		return hour >= 5 && hour < 8 ? "Dawn" : hour >= 8 && hour < 17 ? "Day" : hour >= 17 && hour < 20 ? "Dusk" : "Night";
	}

	private static void tint(Draw d, int w, int h, boolean sky) {
		switch (timeOfDay()) {
			case "Dawn" -> Paint.rect(d, 0, 0, w, h, 0, Grad.down(0x4DFF8AC8, 0x40FFB36B));
			case "Dusk" -> Paint.rect(d, 0, 0, w, h, 0, Grad.down(0x66482A9C, 0x59FF7445));
			case "Night" -> {
				if (!sky) {
					Paint.rect(d, 0, 0, w, h, 0, Grad.down(0xB8050414, 0xA0120A2E));
					float unit = Math.min(w / 1600f, h / 900f);
					d.push();
					d.scale(unit);
					Sky.stars(d, w / unit, h * 0.6f / unit, 0.55f, 1, System.currentTimeMillis());
					d.pop();
				}
			}
			default -> Paint.rect(d, 0, 0, w, h, 0, Grad.down(0x1F6A2CFF, 0x00000000));
		}
		Paint.rect(d, 0, h * 0.55f, w, h, 0, Grad.down(0x00000000, 0x8C06040F));
	}

	/**
	 * Whether the panorama turns this frame: always for Orbit, never for Still, and every third frame
	 * or so for Drift, which turns it at a third of the speed.
	 */
	public static boolean panoramaSpins() {
		if (!enabled() || Modules.MAIN_MENU_CAMERA.is("Orbit")) return true;
		if (Modules.MAIN_MENU_CAMERA.is("Still")) return false;
		driftSpin += 0.34f;
		if (driftSpin < 1) return false;
		driftSpin -= 1;
		return true;
	}

	private static void drawOverlay(Draw d, Screen screen) {
		if (!onTitleScreen()) return;
		drawVersion(d, screen);
		PANEL.set(panelOpen);
		float t = PANEL.get();
		if (t > 0.01f) drawPanel(d, screen, t);
	}

	private static void drawVersion(Draw d, Screen screen) {
		float cap = 5.2f;
		float y = screen.height - 10 - 6 - cap;
		float x = 2;
		Widgets.pulseDot(d, x + 3, y + cap / 2f, 2, Tokens.MAGENTA, System.currentTimeMillis());
		x += 9;
		x += Type.width("Eventide ", cap, Weight.SEMIBOLD);
		Type.draw(d, "Eventide", 11, y, cap, Weight.SEMIBOLD, 0xFFFFFFFF);
		Type.draw(d, EventideClient.version(), x, y, cap, Weight.SEMIBOLD, Tokens.LILAC);
	}

	private static void drawPanel(Draw d, Screen screen, float t) {
		float old = Paint.opacity(Paint.opacity() * t);
		float x1 = panelX;
		float y1 = panelY + (1 - t) * 10;
		float x2 = x1 + PANEL_W;
		float y2 = y1 + panelHeight();
		Widgets.glass(d, x1, y1, x2, y2, 14);
		List<AbstractWidget> segments = new ArrayList<>();
		for (AbstractWidget widget : Compat.widgets(screen)) {
			Part part = PARTS.get(widget);
			if (part != null && part.kind() == Kind.SEGMENT) segments.add(widget);
		}
		float y = y1 + PANEL_PAD;
		for (Row row : ROWS) {
			Type.draw(d, row.label(), x1 + PANEL_PAD, y + 1.6f, 5, Weight.SEMIBOLD, Tokens.TEXT_2);
			String value = row.choice() == Modules.MAIN_MENU_TIME && row.choice().is("Live") ? "Live · " + timeOfDay() : row.choice().get();
			Type.right(d, value, x2 - PANEL_PAD, y + 1.6f, 5, Weight.SEMIBOLD, Tokens.TEXT);
			y += ROW_LABEL;
			int hover = -1;
			for (AbstractWidget widget : segments) {
				Part part = PARTS.get(widget);
				if (part.choice() == row.choice() && widget.isHoveredOrFocused()) hover = part.option();
			}
			Anim at = PICKED.computeIfAbsent(row.choice(), c -> new Anim(c.index(), Tokens.T_INDICATOR, Ease.OUT));
			at.set(row.choice().index());
			Widgets.segmented(d, x1 + PANEL_PAD - 2, y - 1, x2 - PANEL_PAD + 2, y + SEGMENT_H + 1, row.icons(), null, at.get(), hover);
			y += SEGMENT_H + ROW_GAP;
		}
		Paint.opacity(old);
	}
}
