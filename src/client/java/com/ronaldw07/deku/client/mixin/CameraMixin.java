package com.ronaldw07.deku.client.mixin;

import com.ronaldw07.deku.client.ScreenShake;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Twists the camera a little each frame while a nearby blast has the screen shaking. */
@Mixin(Camera.class)
abstract class CameraMixin {
	@Shadow
	private float xRot;
	@Shadow
	private float yRot;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
	private void deku$shake(DeltaTracker deltaTracker, CallbackInfo ci) {
		if (ScreenShake.active()) {
			setRotation(this.yRot + ScreenShake.yaw(), this.xRot + ScreenShake.pitch());
		}
	}
}
