package dev.eventide.client.gui;

import dev.eventide.client.Draw;
import dev.eventide.client.mixin.AbstractSliderButtonAccessor;
import dev.eventide.client.ui.Anim;
import dev.eventide.client.ui.Ease;
import dev.eventide.client.ui.Grad;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Type.Weight;
import dev.eventide.client.ui.Widgets;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Minecraft's buttons and sliders as Eventide's violet glass pills (Theme > Glass buttons): a translucent
 * lavender pill with a light top edge, white Inter text, and a soft accent glow on hover. It hooks the
 * button's own background and label, not its class, so any mod's button that draws Minecraft's button
 * background turns glass too and keeps whatever it draws on top (like Mod Menu's update badge). Buttons
 * with art of their own keep it; the language and accessibility icons become line icons.
 */
public final class ButtonStyle {
	private static final Map<AbstractWidget, Anim> HOVER = new WeakHashMap<>();
	private static final int FILL = 0xC70C0919;
	private static final int FILL_HOVER = 0xE61C1538;
	private static final int EDGE = 0x29FFFFFF;
	private static final int EDGE_HOVER = 0x99A06BFF;
	private static final int TRACK = 0xCC120E22;

	private ButtonStyle() {
	}

	/** The line icon for Minecraft's icon buttons Eventide knows: language and accessibility. */
	private static String iconFor(AbstractWidget widget) {
		if (!(widget.getMessage().getContents() instanceof TranslatableContents contents)) return null;
		return switch (contents.getKey()) {
			case "options.language" -> "languages";
			case "options.accessibility" -> "accessibility";
			default -> null;
		};
	}

	private static float hover(AbstractWidget widget) {
		Anim anim = HOVER.computeIfAbsent(widget, w -> new Anim(0, Tokens.T_COLOR, Ease.OUT));
		anim.set(widget.active && widget.isHoveredOrFocused() && !TitleScreenStyle.hidesHover(widget));
		return anim.get();
	}

	private static float fade(AbstractWidget widget) {
		return Paint.opacity(Paint.opacity() * widget.getAlpha() * (widget.active ? 1 : 0.5f));
	}

	/** A button's background, in place of Minecraft's: returns whether it was drawn as glass. */
	public static boolean background(Draw d, AbstractWidget widget) {
		if (!Glass.on()) return false;
		Glass.mark(widget);
		float old = fade(widget);
		pill(d, widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), hover(widget), widget.isFocused());
		Paint.opacity(old);
		return true;
	}

	/** A button's label in Inter, in place of Minecraft's font: returns whether it was drawn. */
	public static boolean label(Draw d, AbstractWidget widget) {
		if (!Glass.on()) return false;
		float old = fade(widget);
		label(d, widget.getMessage().getString(), widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight());
		Paint.opacity(old);
		return true;
	}

	/** An icon button's picture: a line icon for the ones Eventide knows, or false to draw Minecraft's. */
	public static boolean icon(Draw d, AbstractWidget widget) {
		String icon = Glass.on() ? iconFor(widget) : null;
		if (icon == null) return false;
		float old = fade(widget);
		float size = Math.min(widget.getWidth(), widget.getHeight()) * 0.5f;
		Paint.icon(d, icon, widget.getX() + (widget.getWidth() - size) / 2f, widget.getY() + (widget.getHeight() - size) / 2f, size, 0xFFF4F0FF);
		Paint.opacity(old);
		return true;
	}

	/** A slider, in place of Minecraft's: returns whether it was drawn as glass. */
	public static boolean slider(Draw d, AbstractSliderButton slider) {
		if (!Glass.on() || !slider.visible) return false;
		Glass.mark(slider);
		float old = fade(slider);
		drawSlider(d, slider, slider.getX(), slider.getY(), slider.getX() + slider.getWidth(), slider.getY() + slider.getHeight(), hover(slider));
		Paint.opacity(old);
		return true;
	}

	/** The pill itself, also used by Eventide's own title screen buttons: one flat tone with a 1px edge, a step lighter on hover. */
	public static void pill(Draw d, float x1, float y1, float x2, float y2, float hover, boolean focused) {
		float r = (y2 - y1) / 2f;
		float hair = 1f / Math.max(1, Paint.density(d));
		Paint.rect(d, x1, y1, x2, y2, r, Widgets.mixColor(FILL, FILL_HOVER, hover));
		Paint.stroke(d, x1, y1, x2, y2, r, Math.max(hair, 0.5f), Widgets.mixColor(EDGE, EDGE_HOVER, hover));
		if (focused) Paint.stroke(d, x1 - 1, y1 - 1, x2 + 1, y2 + 1, r + 1, Math.max(hair, 0.5f), Paint.fade(Accent.lighten(Accent.get(), 0.3f), 0.9f));
	}

	private static void label(Draw d, String text, float x1, float y1, float x2, float y2) {
		float h = y2 - y1;
		float cap = Math.min(6.4f, h * 0.31f);
		String shown = Type.fit(text, x2 - x1 - h * 0.6f, cap, Weight.SEMIBOLD);
		Type.center(d, shown, (x1 + x2) / 2f, y1 + (h - cap) / 2f, cap, Weight.SEMIBOLD, 0xFFF4F0FF);
	}

	private static void drawSlider(Draw d, AbstractSliderButton slider, float x1, float y1, float x2, float y2, float hover) {
		float h = y2 - y1;
		float r = h / 2f;
		double value = ((AbstractSliderButtonAccessor) slider).eventide$value();
		float hair = 1f / Math.max(1, Paint.density(d));
		Paint.rect(d, x1, y1, x2, y2, r, TRACK);
		float inset = h * 0.16f;
		float fx = x1 + inset + (float) Math.max(0, Math.min(1, value)) * (x2 - x1 - 2 * inset - (h - 2 * inset)) + (h - 2 * inset);
		float old = Paint.opacity(Paint.opacity() * (0.55f + 0.45f * hover));
		Paint.rect(d, x1 + inset, y1 + inset, fx, y2 - inset, r - inset, Accent.bar());
		Paint.opacity(old);
		Paint.stroke(d, x1, y1, x2, y2, r, Math.max(hair, 0.5f), Widgets.mixColor(EDGE, EDGE_HOVER, hover));
		float handleW = Math.max(1.5f, h * 0.12f);
		float hx = Math.max(x1 + inset + handleW / 2f, fx - handleW / 2f - inset * 0.5f);
		Paint.rect(d, hx - handleW / 2f, y1 + inset * 0.6f, hx + handleW / 2f, y2 - inset * 0.6f, handleW / 2f, 0xF2FFFFFF);
		label(d, slider.getMessage().getString(), x1, y1, x2, y2);
	}
}
