package com.ronaldw07.deku.client.mixin;

import com.ronaldw07.deku.client.DomainCutscene;
import com.ronaldw07.deku.client.LaunchClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the One For All launch hold back jump (and crouch instead) while it charges. */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin {
	@Inject(method = "tick()V", at = @At("TAIL"))
	private void deku$launch(CallbackInfo info) {
		ClientInput input = (ClientInput) (Object) this;
		input.keyPresses = DomainCutscene.filter(LaunchClient.filter(input.keyPresses));
	}
}
