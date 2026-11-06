package dev.eventide.client.mixin;

import dev.eventide.client.compat.DrawImpl;
import dev.eventide.client.module.ItemHighlighter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Highlighter: tint behind the PvP items in every inventory. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void eventide$highlight(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		dev.eventide.client.Hooks.slotHighlightRan = true;
		ItemHighlighter.drawSlot(new DrawImpl(graphics), slot);
	}
}
