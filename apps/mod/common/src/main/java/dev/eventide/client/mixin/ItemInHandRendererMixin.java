package dev.eventide.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.eventide.client.knife.KnifeRenderer;
import dev.eventide.client.module.HandAura;
import dev.eventide.client.module.SwingSpeed;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swing Speed: how fast the item in your hand comes up when you switch to it (pulling out a sword).
 * Knives: your main hand drawn as a knife with its own animations, in place of the held item.
 * Hand Aura: the glow round your empty hand.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@ModifyConstant(method = "tick", constant = {@Constant(floatValue = 0.4F), @Constant(floatValue = -0.4F)})
	private float eventide$equipSpeed(float step) {
		return SwingSpeed.equipStep(step);
	}

	@Inject(method = {"renderArmWithItem", "submitArmWithItem"}, at = @At("HEAD"), cancellable = true)
	private void eventide$knife(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
			float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND) return;
		if (KnifeRenderer.render(player, player.getMainArm(), stack, pose, collector, light)) ci.cancel();
	}

	@Inject(method = "renderPlayerArm", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand"
			+ "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"))
	private void eventide$auraRight(PoseStack pose, SubmitNodeCollector collector, int light, float equip, float swing, HumanoidArm arm, CallbackInfo ci) {
		HandAura.draw(pose, collector, true);
	}

	@Inject(method = "renderPlayerArm", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand"
			+ "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"))
	private void eventide$auraLeft(PoseStack pose, SubmitNodeCollector collector, int light, float equip, float swing, HumanoidArm arm, CallbackInfo ci) {
		HandAura.draw(pose, collector, false);
	}
}
