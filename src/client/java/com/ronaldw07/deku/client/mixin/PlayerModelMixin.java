package com.ronaldw07.deku.client.mixin;

import com.ronaldw07.deku.client.Poses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Poses the local player's arms, and legs for kicks, for whatever move they're doing. Angles are radians; a
 * negative xRot swings an arm forward and up, and zRot swings it out to the side.
 */
@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
	private static final float STRAIGHT_AHEAD = (float) (-Math.PI / 2);

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void deku$pose(AvatarRenderState state, CallbackInfo info) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || state.id != minecraft.player.getId()) {
			return;
		}

		PlayerModel model = (PlayerModel) (Object) this;
		ModelPart right = model.rightArm;
		ModelPart left = model.leftArm;
		ModelPart rightLeg = model.rightLeg;
		ModelPart leftLeg = model.leftLeg;
		float aim = model.head.xRot + STRAIGHT_AHEAD; // pointing where the player looks
		switch (Poses.current()) {
			case NONE -> {
			}
			case POWER_UP -> {
				set(right, 0.3f, 0, 0.45f);
				set(left, 0.3f, 0, -0.45f);
			}
			case SMASH_CHARGE -> {
				set(right, 0.9f, 0, 0.25f);
				set(left, -0.7f, 0.3f, 0);
			}
			case PUNCH -> {
				set(right, aim, model.head.yRot, 0);
				set(left, 0.6f, 0, -0.1f);
			}
			case SMOKESCREEN -> {
				set(right, -0.3f, 0, 1.4f);
				set(left, -0.3f, 0, -1.4f);
			}
			case FLOAT -> {
				set(right, 0, 0, 0.6f);
				set(left, 0, 0, -0.6f);
			}
			case WHIP, AIM_RIGHT -> set(right, aim, model.head.yRot, 0);
			case AIM_BOTH -> {
				set(right, aim, model.head.yRot + 0.15f, 0);
				set(left, aim, model.head.yRot - 0.15f, 0);
			}
			case CROSS -> {
				set(right, -2.0f, 0.55f, 0);
				set(left, -2.0f, -0.55f, 0);
			}
			case BURST -> {
				set(right, -1.2f, 0, 1.0f);
				set(left, -1.2f, 0, -1.0f);
			}
			// The body lies flat along the view while flying, so arms at the sides point back
			// toward the feet, palms blasting like thrusters.
			case THRUSTERS -> {
				set(right, 0.2f, 0, 0.35f);
				set(left, 0.2f, 0, -0.35f);
			}
			// Crouched, arms swept back, ready to spring.
			case LAUNCH_CHARGE -> {
				set(right, 1.1f, 0, 0.3f);
				set(left, 1.1f, 0, -0.3f);
			}
			// Both hands snapped back, palms flicking the air behind.
			case FLICK -> {
				set(right, 1.3f, 0, 0.25f);
				set(left, 1.3f, 0, -0.25f);
			}
			// Shoot Style: right leg snapped out ahead, arms flung out for balance.
			case KICK -> {
				set(rightLeg, -1.5f, 0, 0);
				set(right, -0.3f, 0, 0.9f);
				set(left, -0.3f, 0, -0.9f);
			}
			// Tucked into a flip at the top of the Manchester Smash leap.
			case FLIP -> {
				set(rightLeg, -1.2f, 0, 0);
				set(leftLeg, -1.2f, 0, 0);
				set(right, -1.0f, 0, 0.2f);
				set(left, -1.0f, 0, -0.2f);
			}
			// Coming down heel first: right leg raised overhead, arms out wide.
			case AXE_KICK -> {
				set(rightLeg, -2.6f, 0, 0);
				set(right, 0, 0, 1.2f);
				set(left, 0, 0, -1.2f);
			}
			// Decay: right hand reaching down to the ground.
			case GROUND_TOUCH -> {
				set(right, -0.6f, 0, 0);
				set(left, 0.3f, 0, -0.2f);
			}
			// Lying along the flight path: right fist punched out ahead, left arm trailing.
			case LAUNCH -> {
				set(right, (float) -Math.PI, 0, 0);
				set(left, 0.3f, 0, -0.2f);
			}
		}
	}

	private static void set(ModelPart limb, float xRot, float yRot, float zRot) {
		limb.xRot = xRot;
		limb.yRot = yRot;
		limb.zRot = zRot;
	}
}
