package com.ronaldw07.deku.client.mixin;

import com.ronaldw07.deku.client.ExplosionClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Arm poses: crossed in front of the face while the ground blast charges, and both arms
 * swept back with palms behind, blasting like thrusters, while flying on explosions.
 */
@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
	private static final float ARM_RAISE = -2.0f;
	private static final float ARM_CROSS = 0.55f;
	// The body lies flat along the view while flying, so arms at the sides point back toward the feet.
	private static final float ARMS_BACK = 0.2f;
	private static final float ARMS_SPREAD = 0.35f;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void deku$crossArms(AvatarRenderState state, CallbackInfo info) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || state.id != minecraft.player.getId()) {
			return;
		}

		PlayerModel model = (PlayerModel) (Object) this;
		if (ExplosionClient.flying() || ExplosionClient.spinning()) {
			model.rightArm.xRot = ARMS_BACK;
			model.rightArm.yRot = 0;
			model.rightArm.zRot = ARMS_SPREAD;
			model.leftArm.xRot = ARMS_BACK;
			model.leftArm.yRot = 0;
			model.leftArm.zRot = -ARMS_SPREAD;
			return;
		}
		if (!ExplosionClient.armsCrossed()) {
			return;
		}

		model.rightArm.xRot = ARM_RAISE;
		model.rightArm.yRot = ARM_CROSS;
		model.rightArm.zRot = 0;
		model.leftArm.xRot = ARM_RAISE;
		model.leftArm.yRot = -ARM_CROSS;
		model.leftArm.zRot = 0;
	}
}
