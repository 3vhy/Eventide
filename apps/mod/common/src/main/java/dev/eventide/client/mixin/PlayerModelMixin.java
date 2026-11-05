package dev.eventide.client.mixin;

import dev.eventide.client.knife.ModelSkins;
import dev.eventide.client.render.CosmeticsLayer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 3D skins: your player's own flat-skinned body is hidden while a 3D skin is drawn in its place. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void eventide$hideForSkin(AvatarRenderState state, CallbackInfo ci) {
		PlayerModel self = (PlayerModel) (Object) this;
		boolean hide = ModelSkins.worn() && CosmeticsLayer.yours(state);
		self.head.visible = !hide;
		self.body.visible = !hide;
		self.rightArm.visible = !hide;
		self.leftArm.visible = !hide;
		self.rightLeg.visible = !hide;
		self.leftLeg.visible = !hide;
		if (hide) {
			self.hat.visible = false;
			self.jacket.visible = false;
			self.leftSleeve.visible = false;
			self.rightSleeve.visible = false;
			self.leftPants.visible = false;
			self.rightPants.visible = false;
		}
	}
}
