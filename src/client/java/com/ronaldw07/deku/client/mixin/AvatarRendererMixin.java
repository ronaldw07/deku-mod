package com.ronaldw07.deku.client.mixin;

import com.ronaldw07.deku.client.ExplosionClient;
import com.ronaldw07.deku.client.LaunchClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lays the player out flat along their view, like elytra gliding, while blasting through
 * the air or launching with One For All; during Howitzer Impact they also spin like a drill around their length.
 */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
	private static final float FULLY_GLIDING_TICKS = 20;
	private static final float HOWITZER_ROLL_PER_TICK = 0.9f; // radians

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void deku$supermanPose(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo info) {
		Minecraft minecraft = Minecraft.getInstance();
		if (entity != minecraft.player || !(ExplosionClient.flying() || ExplosionClient.spinning() || LaunchClient.launching())) {
			return;
		}
		state.isFallFlying = true;
		state.fallFlyingTimeInTicks = FULLY_GLIDING_TICKS;
		state.shouldApplyFlyingYRot = ExplosionClient.spinning();
		state.flyingYRot = (entity.tickCount + partialTicks) * HOWITZER_ROLL_PER_TICK;
		state.isAutoSpinAttack = false;
	}
}
