package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.network.DecayPayload;
import com.ronaldw07.deku.network.DecayPayload.Move;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * Client side of the Decay quirk, used while the Decay item is in hand: right-click to decay
 * whatever you touch, and hold V to wind up a wave of decay that floods out when you let go.
 */
public final class DecayClient {
	private static final int TOUCH_POSE_TICKS = 8;
	private static final int FULL_WAVE_CHARGE_TICKS = 40;
	private static final int CHARGE_SOUND_INTERVAL = 10;
	private static final int SLAM_POSE_TICKS = 15;

	private static int waveCharge;

	private DecayClient() {
	}

	public static boolean charging() {
		return waveCharge > 0;
	}

	/** How far the wave is wound up, 0-100. */
	public static int waveCharge() {
		return waveCharge * 100 / FULL_WAVE_CHARGE_TICKS;
	}

	static void tick(LocalPlayer player, boolean holding, boolean useDown, boolean waveDown) {
		if (player == null) {
			waveCharge = 0;
			return;
		}
		boolean able = holding && !player.isDeadOrDying();

		if (able && useDown && Cooldowns.ready(Cooldowns.Ability.DECAY_TOUCH)) {
			send(Move.TOUCH, 0);
			Cooldowns.start(Cooldowns.Ability.DECAY_TOUCH);
			Poses.play(Poses.Pose.AIM_RIGHT, TOUCH_POSE_TICKS);
		}

		if (able && waveDown && (waveCharge > 0 || Cooldowns.ready(Cooldowns.Ability.DECAY_WAVE))) {
			if (waveCharge % CHARGE_SOUND_INTERVAL == 0) {
				player.level().playLocalSound(player, DekuSounds.DECAY_CHARGE, SoundSource.PLAYERS, 1.0f, 0.8f + 0.6f * waveCharge() / 100f);
			}
			waveCharge = Math.min(FULL_WAVE_CHARGE_TICKS, waveCharge + 1);
			return;
		}
		if (waveCharge > 0) {
			if (able) {
				send(Move.WAVE, waveCharge());
				Cooldowns.start(Cooldowns.Ability.DECAY_WAVE);
				Poses.play(Poses.Pose.GROUND_TOUCH, SLAM_POSE_TICKS);
			}
			waveCharge = 0;
		}
	}

	private static void send(Move move, int charge) {
		if (ClientPlayNetworking.canSend(DecayPayload.TYPE)) {
			ClientPlayNetworking.send(new DecayPayload(move, charge));
		}
	}
}
