package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.network.SmashPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;

/** Client side of Smash: hold to charge up to the chosen power, let go to throw the punch. */
public final class SmashClient {
	private static final int CHARGE_SOUND_INTERVAL = 6;
	private static final int PUNCH_POSE_TICKS = 8;

	private static boolean charging;
	private static double charge;
	private static int chargeTicks;

	private SmashClient() {
	}

	public static double charge() {
		return charge;
	}

	public static boolean charging() {
		return charging;
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

		if ((held || pressed) && (charging || Cooldowns.ready(Cooldowns.Ability.SMASH))) {
			DekuSettings settings = DekuSettings.get();
			if (!charging) {
				player.level().playLocalSound(player, DekuSounds.SMASH_WINDUP, SoundSource.PLAYERS, 1.0f, 1.0f);
			}
			charge = Ramp.toward(charging ? charge : 0, settings.punchPower(), settings.punchChargeSeconds());
			charging = true;
			// Hum that rises in pitch as the punch charges.
			if (chargeTicks++ % CHARGE_SOUND_INTERVAL == 0) {
				player.level().playLocalSound(player, DekuSounds.SMASH_CHARGE, SoundSource.PLAYERS, 0.8f, (float) (0.6 + charge / 100.0));
			}
		}

		if (charging && !held) {
			if (ClientPlayNetworking.canSend(SmashPayload.TYPE)) {
				ClientPlayNetworking.send(new SmashPayload(Math.max(1, (int) Math.round(charge)), DekuSettings.get().smashMaxRange()));
			}
			Cooldowns.start(Cooldowns.Ability.SMASH);
			Poses.play(Poses.Pose.PUNCH, PUNCH_POSE_TICKS);
			reset();
		}
	}

	private static void reset() {
		charging = false;
		charge = 0;
		chargeTicks = 0;
	}
}
