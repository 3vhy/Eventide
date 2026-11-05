package dev.eventide.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.eventide.client.module.ItemPhysics;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Physics: landed items held still, then laid flat on the ground where they're drawn. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
			at = @At("TAIL"))
	private void eventide$still(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
		ItemPhysics.extract(entity, state);
	}

	@Inject(method = "submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
			+ "Lnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V",
			at = @At("HEAD"))
	private static void eventide$flat(PoseStack pose, SubmitNodeCollector collector, int light, ItemClusterRenderState state, RandomSource random, AABB box,
			CallbackInfo ci) {
		if (!ItemPhysics.resting(state)) return;
		pose.translate(0, (float) box.minY - 0.0625f + 0.035f, 0);
		pose.mulPose(Axis.XP.rotationDegrees(90));
		dev.eventide.client.Hooks.itemPhysicsRan = true;
	}
}
