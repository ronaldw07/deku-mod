package com.ronaldw07.deku.client;

import com.ronaldw07.deku.network.CowlingPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;

/** Client side of Full Cowling: the on/off toggle and the ramp up to the chosen power. */
public final class FullCowlingClient {
	private static boolean active;
	private static double percent;
	private static int lastSent;

	private FullCowlingClient() {
	}

	public static double percent() {
		return percent;
	}

	static void tick(LocalPlayer player, boolean togglePressed) {
		if (player == null) {
			active = false;
			percent = 0;
			lastSent = 0;
			return;
		}

		if (togglePressed) {
			active = !active;
		}

		if (player.isDeadOrDying()) {
			active = false;
		}

		DekuSettings settings = DekuSettings.get();
		percent = active ? Ramp.toward(percent, settings.cowlingPower(), settings.cowlingRampSeconds()) : 0;

		int rounded = (int) Math.round(percent);
		if (rounded != lastSent && ClientPlayNetworking.canSend(CowlingPayload.TYPE)) {
			ClientPlayNetworking.send(new CowlingPayload(rounded));
			lastSent = rounded;
		}
	}
}
