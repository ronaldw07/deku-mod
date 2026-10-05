package com.ronaldw07.deku.client.mixin;

import com.ronaldw07.deku.client.DomainCutscene;
import com.ronaldw07.deku.client.ScreenShake;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
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

	@Shadow
	private Vec3 position;
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
	private void deku$shake(DeltaTracker deltaTracker, CallbackInfo ci) {
		if (DomainCutscene.active()) {
			Vec3 at = DomainCutscene.position(this.position);
			setRotation(DomainCutscene.yaw(this.yRot), DomainCutscene.pitch(this.xRot));
			setPosition(at.x, at.y, at.z);
			this.detached = true; // so the player's own body is drawn, as in third person
		}
		if (ScreenShake.active()) {
			setRotation(this.yRot + ScreenShake.yaw(), this.xRot + ScreenShake.pitch());
		}
	}
}
