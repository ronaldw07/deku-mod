package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.network.CowlingPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;

/** Client side of Full Cowling: the on/off toggle and the ramp up to the chosen power. */
public final class FullCowlingClient {
	// Average gap between crackles at 100%; lower power crackles less often and more quietly.
	private static final double CRACKLE_TICKS_AT_FULL = 8;

	private static boolean active;
	private static double percent;
	private static int lastSent;

	private FullCowlingClient() {
	}

	public static double percent() {
		return percent;
	}

	/** @param holdingQuirk whether One For All is in hand; letting go of it switches Full Cowling off */
	static void tick(LocalPlayer player, boolean togglePressed, boolean holdingQuirk) {
		if (player == null) {
			active = false;
			percent = 0;
			lastSent = 0;
			return;
		}

		if (togglePressed) {
			active = !active;
		}

		if (!holdingQuirk || player.isDeadOrDying()) {
			active = false;
		}

		DekuSettings settings = DekuSettings.get();
		percent = active ? Ramp.toward(percent, settings.cowlingPower(), settings.cowlingRampSeconds()) : 0;

		crackle(player);

		int rounded = (int) Math.round(percent);
		if (rounded != lastSent && ClientPlayNetworking.canSend(CowlingPayload.TYPE)) {
			ClientPlayNetworking.send(new CowlingPayload(rounded));
			lastSent = rounded;
		}
	}

	private static void crackle(LocalPlayer player) {
		double power = percent / 100.0;
		if (power <= 0 || player.getRandom().nextDouble() > power / CRACKLE_TICKS_AT_FULL) {
			return;
		}

		player.level().playLocalSound(player, DekuSounds.COWLING_CRACKLE, SoundSource.PLAYERS,
			(float) (0.4 + 0.6 * power), 0.9f + player.getRandom().nextFloat() * 0.2f);
	}
}
