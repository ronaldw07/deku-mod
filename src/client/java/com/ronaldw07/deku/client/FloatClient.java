package com.ronaldw07.deku.client;

import com.ronaldw07.deku.network.FloatPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/** Client side of Float: hover while the key is held; jump rises, sneak sinks. */
public final class FloatClient {
	private static final double VERTICAL_SPEED = 0.25;
	// Any leftover rise or fall bleeds off quickly so the player settles into a hover.
	private static final double HOVER_DAMPING = 0.6;

	private static boolean lastSent;

	private FloatClient() {
	}

	static void tick(LocalPlayer player, boolean held) {
		if (player == null) {
			lastSent = false;
			return;
		}

		boolean active = held && !player.isDeadOrDying();
		if (active != lastSent && ClientPlayNetworking.canSend(FloatPayload.TYPE)) {
			ClientPlayNetworking.send(new FloatPayload(active));
			lastSent = active;
		}

		if (active) {
			Input input = player.input.keyPresses;
			Vec3 motion = player.getDeltaMovement();
			double vertical = input.jump() ? VERTICAL_SPEED : input.shift() ? -VERTICAL_SPEED : motion.y * HOVER_DAMPING;
			player.setDeltaMovement(motion.x, vertical, motion.z);
		}
	}
}
