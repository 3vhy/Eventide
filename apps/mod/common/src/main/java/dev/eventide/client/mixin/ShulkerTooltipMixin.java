package dev.eventide.client.mixin;

import dev.eventide.client.module.ShulkerPreview;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shulker Preview: a previewed box has no tooltip of its own, since the preview shows its name and contents. */
@Mixin(AbstractContainerScreen.class)
public abstract class ShulkerTooltipMixin {
	@Inject(method = "getTooltipFromContainerItem", at = @At("HEAD"), cancellable = true)
	private void eventide$previewInstead(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
		if (ShulkerPreview.previews(stack)) cir.setReturnValue(List.of());
	}
}
