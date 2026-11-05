package dev.eventide.client.module;

import dev.eventide.client.Draw;
import dev.eventide.client.gui.Accent;
import dev.eventide.client.mixin.AbstractContainerScreenAccessor;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.ui.Tokens;
import dev.eventide.client.ui.Type;
import dev.eventide.client.ui.Type.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Shulker Preview: hover a shulker box in any inventory and its 27 slots show above the cursor, items and counts,
 * on a dark panel edged in the box's own colour, so you see what's inside without placing it.
 */
public final class ShulkerPreview {
	private static final int COLUMNS = 9;
	private static final int ROWS = 3;
	private static final float CELL = 18;
	private static final float PAD = 5;
	private static final float HEADER = 13;

	private ShulkerPreview() {
	}

	/** Whether {@code stack} is a shulker box this mod previews (in place of its tooltip). */
	public static boolean previews(ItemStack stack) {
		return Modules.SHULKER_PREVIEW.enabled() && stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock;
	}

	/** Draws the preview for the shulker box under the mouse in {@code screen}, if there is one. */
	public static void draw(Draw d, AbstractContainerScreen<?> screen) {
		Slot slot = ((AbstractContainerScreenAccessor) screen).eventide$hoveredSlot();
		if (slot == null || !slot.hasItem()) return;
		ItemStack box = slot.getItem();
		if (!previews(box)) return;
		ShulkerBoxBlock shulker = (ShulkerBoxBlock) ((BlockItem) box.getItem()).getBlock();
		ItemContainerContents contents = box.get(DataComponents.CONTAINER);
		NonNullList<ItemStack> items = NonNullList.withSize(COLUMNS * ROWS, ItemStack.EMPTY);
		if (contents != null) contents.copyInto(items);
		Minecraft mc = Minecraft.getInstance();
		double scale = mc.getWindow().getGuiScale();
		float mouseX = (float) (mc.mouseHandler.xpos() / scale);
		float mouseY = (float) (mc.mouseHandler.ypos() / scale);
		float w = COLUMNS * CELL + 2 * PAD;
		float h = HEADER + ROWS * CELL + 2 * PAD;
		float x = Math.max(4, Math.min(screen.width - w - 4, mouseX + 8));
		float y = mouseY - h - 12 < 4 ? mouseY + 20 : mouseY - h - 12;
		int edge = shulker.getColor() == null ? Accent.get() : 0xFF000000 | shulker.getColor().getTextureDiffuseColor();
		Paint.shadow(d, x, y, x + w, y + h, 7, 0, 2, 10, 0, 0x66000000);
		Paint.rect(d, x, y, x + w, y + h, 7, 0xF00B0817);
		Paint.stroke(d, x, y, x + w, y + h, 7, 1, Paint.fade(edge, 0.85f));
		Type.draw(d, box.getHoverName().getString(), x + PAD + 1, y + PAD + 1.5f, 5.2f, Weight.SEMIBOLD, Tokens.TEXT);
		for (int i = 0; i < items.size(); i++) {
			float cx = x + PAD + (i % COLUMNS) * CELL;
			float cy = y + PAD + HEADER + (i / COLUMNS) * CELL;
			Paint.rect(d, cx + 0.5f, cy + 0.5f, cx + CELL - 0.5f, cy + CELL - 0.5f, 3, 0x12FFFFFF);
			ItemStack stack = items.get(i);
			if (stack.isEmpty()) continue;
			d.item(stack, Math.round(cx + 1), Math.round(cy + 1));
			if (stack.getCount() > 1) {
				String count = Integer.toString(stack.getCount());
				Type.draw(d, count, cx + CELL - 1.5f - Type.width(count, 4.6f, Weight.BOLD), cy + CELL - 6.5f, 4.6f, Weight.BOLD, Tokens.TEXT);
			}
		}
		dev.eventide.client.Hooks.shulkerPreviewDrawn = true;
	}
}
