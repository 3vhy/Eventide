package dev.eventide.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Shulker Preview: the slot under the mouse, and where the inventory sits (for the test). */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("hoveredSlot")
	Slot eventide$hoveredSlot();

	@Accessor("leftPos")
	int eventide$left();

	@Accessor("topPos")
	int eventide$top();
}
