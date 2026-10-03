package com.ronaldw07.deku.client;

import com.ronaldw07.deku.network.SmashPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;

/** Client side of Smash: hold to charge up to the chosen power, let go to throw the punch. */
public final class SmashClient {
	private static boolean charging;
	private static double charge;

	private SmashClient() {
	}

	public static double charge() {
		return charge;
	}

	/**
	 * @param held whether the key is down right now
	 * @param pressed whether the key was pressed since last tick (catches taps shorter than a tick)
	 */
	static void tick(LocalPlayer player, boolean held, boolean pressed) {
		if (player == null || player.isDeadOrDying()) {
			reset();
			return;
		}

		if (held || pressed) {
			DekuSettings settings = DekuSettings.get();
			charge = Ramp.toward(charging ? charge : 0, settings.punchPower(), settings.punchChargeSeconds());
			charging = true;
		}

		if (charging && !held) {
			if (ClientPlayNetworking.canSend(SmashPayload.TYPE)) {
				ClientPlayNetworking.send(new SmashPayload(Math.max(1, (int) Math.round(charge))));
			}
			reset();
		}
	}

	private static void reset() {
		charging = false;
		charge = 0;
	}
}
