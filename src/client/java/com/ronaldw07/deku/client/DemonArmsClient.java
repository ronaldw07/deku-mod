package com.ronaldw07.deku.client;

import com.ronaldw07.deku.network.DemonArmsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;

/** Client side of Sukuna's Demon Arms: Z switches them on and off while he is in hand. The arms are drawn by DemonArmsFx. */
public final class DemonArmsClient {
	private static final int POSE_TICKS = 14;

	private static boolean active;

	private DemonArmsClient() {
	}

	public static boolean active() {
		return active;
	}

	static void tick(LocalPlayer player, boolean holdingSukuna, boolean togglePressed) {
		boolean was = active;
		if (player == null || !holdingSukuna || player.isDeadOrDying()) {
			active = false;
		} else if (togglePressed) {
			active = !active;
		}
		if (active != was) {
			if (ClientPlayNetworking.canSend(DemonArmsPayload.TYPE)) {
				ClientPlayNetworking.send(new DemonArmsPayload(active));
			}
			if (active && player != null) {
				Poses.play(Poses.Pose.POWER_UP, POSE_TICKS);
			}
		}
	}
}
