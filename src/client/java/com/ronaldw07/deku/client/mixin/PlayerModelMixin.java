package com.ronaldw07.deku.client.mixin;

import com.ronaldw07.deku.client.ExplosionClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Raises the player's arms in a cross in front of their face while the ground blast winds up. */
@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
	private static final float ARM_RAISE = -2.0f;
	private static final float ARM_CROSS = 0.55f;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void deku$crossArms(AvatarRenderState state, CallbackInfo info) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || state.id != minecraft.player.getId() || !ExplosionClient.armsCrossed()) {
			return;
		}

		PlayerModel model = (PlayerModel) (Object) this;
		model.rightArm.xRot = ARM_RAISE;
		model.rightArm.yRot = ARM_CROSS;
		model.rightArm.zRot = 0;
		model.leftArm.xRot = ARM_RAISE;
		model.leftArm.yRot = -ARM_CROSS;
		model.leftArm.zRot = 0;
	}
}
