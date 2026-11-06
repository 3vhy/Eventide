package dev.eventide.client.gui;

import dev.eventide.client.compat.Compat;
import dev.eventide.client.Config;
import dev.eventide.client.Draw;
import dev.eventide.client.EventideClient;
import dev.eventide.client.compat.CompatScreen;
import dev.eventide.client.gui.menu.MenuScreen;
import dev.eventide.client.hud.HudRenderer;
import dev.eventide.client.hud.HudWidget;
import dev.eventide.client.hud.HudWidgets;
import dev.eventide.client.hud.Pills;
import dev.eventide.client.module.Setting;
import dev.eventide.client.ui.Anim;
import dev.eventide.client.ui.Ease;
import dev.eventide.client.ui.Hits;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type.Weight;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Widgets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The HUD layout editor: the game stays sharp behind a faint violet wash and every HUD widget sits in a
 * dashed-violet frame to drag around. Widgets snap to the middle and the edges, with accent guide
 * lines. Hover one for its toolbar (settings, hide) and scroll over it to resize it. A glass bar at the
 * top has the hints, Reset and Done.
 */
public final class HudEditorScreen extends CompatScreen {
	private static final int SNAP = 4;
	private static final int MARGIN = 4;
	private static final float BAR_H = 22;

	private final Screen parent;
	private final Hits hits = new Hits();
	private final Map<HudWidget, Anim> hover = new HashMap<>();
	private final long openedAt = System.currentTimeMillis();
	private HudWidget dragging = null;
	private double grabX;
	private double grabY;
	private int guideX = -1;
	private int guideY = -1;
	private HudWidget toolbarFor = null;
	private final float[] toolbarBox = new float[4];

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Edit HUD layout"));
		this.parent = parent;
	}

	@Override
	protected boolean blurBackground() {
		return false;
	}

	@Override
	protected void drawBackdrop(Draw d) {
		Paint.rect(d, 0, 0, this.width, this.height, 0, 0x2E120A2A);
	}

	@Override
	protected void draw(Draw d, int mouseX, int mouseY, float delta) {
		hits.clear();
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		float intro = Ease.OUT.apply((now - openedAt) / 360.0);
		float old = Paint.opacity(intro);

		if (dragging != null) {
			if (guideX >= 0) Paint.rect(d, guideX - 0.25f, 0, guideX + 0.25f, this.height, 0, Paint.fade(Accent.get(), 0.85f));
			if (guideY >= 0) Paint.rect(d, 0, guideY - 0.25f, this.width, guideY + 0.25f, 0, Paint.fade(Accent.get(), 0.85f));
		}

		HudWidget hovered = null;
		boolean overBar = mouseY < BAR_H + 10;
		Pills.draw(d, mc, HudRenderer.placed(mc, this.width, this.height, true), true);
		for (HudWidget widget : HudWidgets.ALL) {
			if (!widget.shown()) continue;
			int[] box = box(mc, widget);
			if (dragging == null && !overBar && mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3]) hovered = widget;
			hits.add(box[0], box[1], box[2], box[3], () -> startDrag(widget, mouseX, mouseY));
		}
		if (hovered == null && dragging == null && toolbarFor != null && toolbarFor.shown() && mouseX >= toolbarBox[0] - 2
				&& mouseX < toolbarBox[2] + 2 && mouseY >= toolbarBox[1] - 4 && mouseY < toolbarBox[3] + 4) {
			hovered = toolbarFor;
		}
		toolbarFor = hovered;
		for (HudWidget widget : HudWidgets.ALL) {
			if (!widget.shown()) continue;
			Anim anim = hover.computeIfAbsent(widget, w -> new Anim(0, Tokens.T_COLOR, Ease.OUT));
			anim.set(widget == hovered || widget == dragging);
			frame(d, box(mc, widget), anim.get(), now);
		}
		if (hovered != null) toolbar(d, mc, hovered, mouseX, mouseY);
		drawBar(d, mouseX, mouseY, hovered);
		Paint.opacity(old);
	}

	/** A widget's frame: a faint violet edge, lit up with a glow while it's hovered or dragged. */
	private static void frame(Draw d, int[] box, float lit, long now) {
		float x1 = box[0] - 2;
		float y1 = box[1] - 2;
		float x2 = box[2] + 2;
		float y2 = box[3] + 2;
		float hair = 1f / Math.max(1, Paint.density(d));
		if (lit > 0.01f) {
			Paint.shadow(d, x1, y1, x2, y2, 4, 0, 0, 8, -3, Paint.fade(Accent.glow(0.45f), lit));
			Paint.rect(d, x1, y1, x2, y2, 4, Paint.fade(0x14880FF9, lit));
		}
		Paint.stroke(d, x1, y1, x2, y2, 4, Math.max(hair, 0.5f), Widgets.mixColor(0x5ACDB6FF, Accent.lighten(Accent.get(), 0.25f), lit));
	}

	private void toolbar(Draw d, Minecraft mc, HudWidget widget, int mouseX, int mouseY) {
		int[] box = box(mc, widget);
		float size = 13;
		float gap = 3;
		float w = 2 * size + gap + 6;
		float y = box[1] - 2 - size - 4 >= BAR_H + 6 ? box[1] - 2 - size - 4 : box[3] + 6;
		float x = Math.max(2, Math.min(this.width - w - 2, box[0] - 2));
		toolbarBox[0] = x;
		toolbarBox[1] = Math.min(y - 3, box[1]);
		toolbarBox[2] = x + w;
		toolbarBox[3] = Math.max(y + size + 3, box[1]);
		Widgets.glass(d, x, y - 3, x + w, y + size + 3, (size + 6) / 2f);
		toolButton(d, x + 3, y, size, "sliders-horizontal", false, mouseX, mouseY,
				() -> Compat.setScreen(MenuScreen.settings(this, widget.module)));
		toolButton(d, x + 3 + size + gap, y, size, "eye-off", true, mouseX, mouseY, () -> widget.module.set(false));
	}

	private void toolButton(Draw d, float x, float y, float size, String icon, boolean danger, int mouseX, int mouseY, Runnable action) {
		boolean over = mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
		Paint.circle(d, x + size / 2f, y + size / 2f, size / 2f, over ? danger ? 0x40FF5C84 : 0x26FFFFFF : 0x0FFFFFFF);
		float icon2 = size * 0.56f;
		Paint.icon(d, icon, x + (size - icon2) / 2f, y + (size - icon2) / 2f, icon2, over ? danger ? Tokens.LOW : 0xFFFFFFFF : Tokens.TEXT_2);
		hits.add(x - 1, y - 1, x + size + 1, y + size + 1, action);
	}

	private void drawBar(Draw d, int mouseX, int mouseY, HudWidget hovered) {
		String hint = hovered != null && hovered.resizable()
				? "Scroll to resize  ·  " + Math.round(hovered.sizeScale() * 100) + "%"
				: "Drag widgets to move them  ·  drop a pill against another to stick them together";
		float cap = 5.2f;
		float hintW = Type.width(hint, cap, Weight.MEDIUM);
		float titleW = Type.width("Edit HUD layout", cap, Weight.SEMIBOLD);
		float resetW = 8 + 8 + 4 + Type.width("Reset", cap, Weight.SEMIBOLD) + 8;
		float doneW = 12 + Type.width("Done", cap, Weight.SEMIBOLD) + 12;
		float w = 8 + 8 + 5 + titleW + 12 + hintW + 12 + resetW + 4 + doneW + 4;
		float x = (this.width - w) / 2f;
		float y = 6;
		Widgets.glass(d, x, y, x + w, y + BAR_H, BAR_H / 2f);
		float cx = x + 8;
		Paint.icon(d, "move", cx, y + (BAR_H - 8) / 2f, 8, Tokens.LILAC);
		cx += 8 + 5;
		Type.draw(d, "Edit HUD layout", cx, y + (BAR_H - cap) / 2f, cap, Weight.SEMIBOLD, Tokens.TEXT);
		cx += titleW + 12;
		Type.draw(d, hint, cx, y + (BAR_H - cap) / 2f, cap, Weight.MEDIUM, Tokens.TEXT_2);
		cx += hintW + 12;
		float by = y + 3;
		float bh = BAR_H - 6;
		boolean resetHover = mouseX >= cx && mouseX < cx + resetW && mouseY >= by && mouseY < by + bh;
		Paint.rect(d, cx, by, cx + resetW, by + bh, bh / 2f, resetHover ? 0x1FFFFFFF : 0x0DFFFFFF);
		Paint.icon(d, "rotate-ccw", cx + 8, by + (bh - 8) / 2f, 8, resetHover ? 0xFFFFFFFF : Tokens.TEXT_2);
		Type.draw(d, "Reset", cx + 20, by + (bh - cap) / 2f, cap, Weight.SEMIBOLD, Tokens.TEXT);
		float rx = cx;
		hits.add(rx, by, rx + resetW, by + bh, () -> {
			for (HudWidget each : HudWidgets.ALL) each.resetPosition();
		});
		cx += resetW + 4;
		boolean doneHover = mouseX >= cx && mouseX < cx + doneW && mouseY >= by && mouseY < by + bh;
		Widgets.accentPill(d, cx, by, cx + doneW, by + bh, doneHover ? 1 : 0.6f);
		Type.center(d, "Done", cx + doneW / 2f, by + (bh - cap) / 2f, cap, Weight.SEMIBOLD, 0xFFFFFFFF);
		hits.add(cx, by, cx + doneW, by + bh, this::onClose);
	}

	/** A widget's box on screen (GUI pixels): {x1, y1, x2, y2}. */
	private int[] box(Minecraft mc, HudWidget widget) {
		float scale = HudRenderer.scaleFor(widget);
		int[] pos = widget.position(mc, Math.round(this.width / scale), Math.round(this.height / scale));
		return new int[] {Math.round(pos[0] * scale), Math.round(pos[1] * scale), Math.round((pos[0] + widget.width(mc)) * scale),
				Math.round((pos[1] + widget.height(mc)) * scale)};
	}

	private void startDrag(HudWidget widget, double mouseX, double mouseY) {
		Minecraft mc = Minecraft.getInstance();
		float scale = HudRenderer.scaleFor(widget);
		int[] pos = widget.position(mc, Math.round(this.width / scale), Math.round(this.height / scale));
		dragging = widget;
		grabX = mouseX / scale - pos[0];
		grabY = mouseY / scale - pos[1];
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			Runnable action = hits.at(event.x(), event.y(), 0);
			if (action != null) {
				action.run();
				if (dragging == null) {
					Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging == null) return super.mouseDragged(event, dragX, dragY);
		Minecraft mc = Minecraft.getInstance();
		float scale = HudRenderer.scaleFor(dragging);
		int sw = Math.round(this.width / scale);
		int sh = Math.round(this.height / scale);
		int w = dragging.width(mc);
		int h = dragging.height(mc);
		double left = event.x() / scale - grabX;
		double top = event.y() / scale - grabY;
		guideX = -1;
		guideY = -1;
		if (Math.abs(left + w / 2.0 - sw / 2.0) < SNAP) {
			left = sw / 2.0 - w / 2.0;
			guideX = this.width / 2;
		} else if (Math.abs(left - MARGIN) < SNAP) {
			left = MARGIN;
		} else if (Math.abs(left + w - (sw - MARGIN)) < SNAP) {
			left = sw - MARGIN - w;
		}
		if (Math.abs(top + h / 2.0 - sh / 2.0) < SNAP) {
			top = sh / 2.0 - h / 2.0;
			guideY = this.height / 2;
		} else if (Math.abs(top - MARGIN) < SNAP) {
			top = MARGIN;
		} else if (Math.abs(top + h - (sh - MARGIN)) < SNAP) {
			top = sh - MARGIN - h;
		}
		int[] stuck = Pills.stick(dragging, left, top, w, h, scale, HudRenderer.placed(mc, this.width, this.height, true));
		if (stuck != null) {
			left = stuck[0];
			top = stuck[1];
		}
		dragging.setPosition(left / Math.max(1, sw - w), top / Math.max(1, sh - h));
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			guideX = -1;
			guideY = -1;
			Config.saveSoon();
			return true;
		}
		return super.mouseReleased(event);
	}

	/** Scrolling over a widget that has its own Size setting makes it bigger or smaller. */
	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		if (dragging == null && vertical != 0) {
			Minecraft mc = Minecraft.getInstance();
			for (int i = HudWidgets.ALL.size() - 1; i >= 0; i--) {
				HudWidget widget = HudWidgets.ALL.get(i);
				if (!widget.shown() || !widget.resizable()) continue;
				int[] box = box(mc, widget);
				if (x < box[0] || x >= box[2] || y < box[1] || y >= box[3]) continue;
				Setting.Slider size = widget.module.sliderSetting("size");
				size.set(size.get() + Math.signum(vertical) * size.step);
				return true;
			}
		}
		return super.mouseScrolled(x, y, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (EventideClient.openSettingsKey != null && EventideClient.openSettingsKey.matches(event)) {
			Config.save();
			Compat.setScreen(null);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		Config.save();
		Compat.setScreen(parent);
	}
}
