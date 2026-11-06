package dev.eventide.client.gui;

import dev.eventide.client.compat.Compat;
import dev.eventide.client.gui.menu.MenuHost;
import dev.eventide.client.ui.Logo;
import dev.eventide.client.module.ConfirmDisconnect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The pause menu with Eventide in it: a full-width Eventide button with the Eventide mark right under
 * Advancements and Statistics, where Minecraft's feedback links were (they're left out). Everything else
 * is Minecraft's own; buttons other mods put in the column move along with their row.
 */
public final class PauseMenuButton {
	public static final String LABEL = "Eventide";
	/** Minecraft's pause menu leaves 4 pixels between rows and 8 between the halves of a row. */
	private static final int ROW_GAP = 4;
	private static final int HALF_GAP = 8;
	private static final Set<String> FEEDBACK = Set.of("menu.sendFeedback", "menu.reportBugs", "menu.feedback");
	private static final Set<AbstractWidget> GEMS = Collections.newSetFromMap(new WeakHashMap<>());

	private PauseMenuButton() {
	}

	public static void add(Minecraft mc, Screen screen) {
		List<AbstractWidget> widgets = new ArrayList<>(Compat.widgets(screen));
		AbstractWidget options = find(widgets, "menu.options");
		AbstractWidget disconnect = null;
		for (AbstractWidget widget : widgets) {
			if (widget.visible && ConfirmDisconnect.isDisconnect(widget.getMessage())) {
				disconnect = widget;
				break;
			}
		}
		String label = LABEL;
		Button mods = Button.builder(Component.literal(label), button -> MenuHost.show(screen, null)).build();
		GEMS.add(mods);
		Compat.afterScreen(screen, d -> {
			for (AbstractWidget widget : Compat.widgets(screen)) {
				if (GEMS.contains(widget) && widget.visible) {
					float size = Math.min(14, widget.getHeight() - 6);
					Logo.mark(d, widget.getX() + widget.getWidth() - 5 - size, widget.getY() + (widget.getHeight() - size) / 2f, size,
							widget.getAlpha(), System.currentTimeMillis());
				}
			}
		});
		if (options == null || disconnect == null || !inColumn(options, disconnect)) {
			mods.setX(6);
			mods.setY(6);
			mods.setWidth(90);
			Compat.addWidget(screen, mods);
			return;
		}
		int left = disconnect.getX();
		int full = disconnect.getWidth();
		int half = (full - HALF_GAP) / 2;

		Map<Integer, List<AbstractWidget>> byY = new TreeMap<>();
		for (AbstractWidget widget : widgets) {
			if (widget.visible && inColumn(widget, disconnect)) byY.computeIfAbsent(widget.getY(), y -> new ArrayList<>()).add(widget);
		}
		List<List<AbstractWidget>> rows = new ArrayList<>(byY.values());
		int top = rows.get(0).get(0).getY();
		int optionsRow = -1;
		int feedbackRow = -1;
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).contains(options)) optionsRow = i;
			if (feedbackRow < 0 && rows.get(i).stream().anyMatch(widget -> FEEDBACK.contains(key(widget)))) feedbackRow = i;
		}
		if (feedbackRow < 0 || feedbackRow > optionsRow) feedbackRow = -1;

		List<AbstractWidget> moved = new ArrayList<>();
		if (feedbackRow >= 0) {
			for (AbstractWidget widget : rows.get(feedbackRow)) {
				if (FEEDBACK.contains(key(widget))) {
					widget.visible = false;
					widget.active = false;
				} else {
					moved.add(widget);
				}
			}
		}
		mods.setX(left);
		mods.setWidth(full);
		mods.setHeight(disconnect.getHeight());
		List<List<AbstractWidget>> layout = new ArrayList<>();
		int modsAt = feedbackRow >= 0 ? feedbackRow : optionsRow;
		for (int i = 0; i < rows.size(); i++) {
			if (i == modsAt) {
				layout.add(List.<AbstractWidget>of(mods));
				for (int start = 0; start < moved.size(); start += 2) {
					List<AbstractWidget> pair = moved.subList(start, Math.min(moved.size(), start + 2));
					for (int j = 0; j < pair.size(); j++) {
						AbstractWidget widget = pair.get(j);
						widget.setWidth(pair.size() == 1 ? full : half);
						widget.setX(left + j * (half + HALF_GAP));
					}
					layout.add(new ArrayList<>(pair));
				}
			}
			if (i == feedbackRow) continue;
			layout.add(rows.get(i));
		}
		int y = top;
		for (List<AbstractWidget> row : layout) {
			int h = 0;
			for (AbstractWidget widget : row) {
				widget.setY(y);
				h = Math.max(h, widget.getHeight());
			}
			y += h + ROW_GAP;
		}
		Compat.addWidget(screen, mods);
	}

	/** Whether a widget is in the pause menu's column (or just beside it), going by Disconnect's place. */
	private static boolean inColumn(AbstractWidget widget, AbstractWidget disconnect) {
		return widget.getX() + widget.getWidth() >= disconnect.getX() - 30 && widget.getX() <= disconnect.getX() + disconnect.getWidth() + 30;
	}

	private static AbstractWidget find(List<AbstractWidget> widgets, String key) {
		for (AbstractWidget widget : widgets) {
			if (widget.visible && key.equals(key(widget))) return widget;
		}
		return null;
	}

	private static String key(AbstractWidget widget) {
		return widget.getMessage().getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
	}
}
