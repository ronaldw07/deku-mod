package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.network.UnitedStatesSmashPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of United States of Smash: rocket high into the sky, hang there for a moment
 * winding up, then dive fist first into the ground. Pressed in the air, it starts winding up
 * where you are.
 */
public final class UnitedStatesClient {
	private static final double LAUNCH_SPEED = 4.0;
	private static final int MAX_RISE_TICKS = 60;
	private static final int WIND_UP_TICKS = 20;
	private static final int WIND_UP_SOUND_INTERVAL = 5;
	private static final double DIVE_SPEED = 6.0;
	private static final double DIVE_STEER = 0.3; // how much the dive follows where you look
	private static final int MAX_DIVE_TICKS = 100;
	private static final int LANDING_POSE_TICKS = 12;

	private enum Phase {
		NONE, RISING, WINDING_UP, DIVING
	}

	private static Phase phase = Phase.NONE;
	private static int ticks;

	private UnitedStatesClient() {
	}

	static boolean rising() {
		return phase == Phase.RISING;
	}

	static boolean windingUp() {
		return phase == Phase.WINDING_UP;
	}

	static boolean diving() {
		return phase == Phase.DIVING;
	}

	static void tick(LocalPlayer player, boolean pressed) {
		if (player == null || player.isDeadOrDying()) {
			phase = Phase.NONE;
			return;
		}

		if (pressed && phase == Phase.NONE && Cooldowns.ready(Cooldowns.Ability.US_SMASH)) {
			Cooldowns.start(Cooldowns.Ability.US_SMASH);
			ticks = 0;
			if (player.onGround()) {
				player.setDeltaMovement(0, LAUNCH_SPEED, 0);
				player.setOnGround(false);
				player.level().playLocalSound(player, DekuSounds.SMASH_BLAST, SoundSource.PLAYERS, 1.5f, 0.6f);
				phase = Phase.RISING;
			} else {
				phase = Phase.WINDING_UP;
			}
			return;
		}
		if (phase == Phase.NONE) {
			return;
		}

		ticks++;
		switch (phase) {
			case RISING -> {
				if (player.getDeltaMovement().y <= 0 || ticks > MAX_RISE_TICKS) {
					phase = Phase.WINDING_UP;
					ticks = 0;
				}
			}
			case WINDING_UP -> {
				player.setDeltaMovement(Vec3.ZERO);
				if (ticks % WIND_UP_SOUND_INTERVAL == 0) {
					player.level().playLocalSound(player, DekuSounds.SMASH_CHARGE, SoundSource.PLAYERS, 1.2f,
						0.6f + ticks / (float) WIND_UP_TICKS);
				}
				if (ticks >= WIND_UP_TICKS) {
					phase = Phase.DIVING;
					ticks = 0;
					player.level().playLocalSound(player, DekuSounds.SMASH_WINDUP, SoundSource.PLAYERS, 1.5f, 0.7f);
				}
			}
			case DIVING -> {
				Vec3 look = player.getLookAngle();
				player.setDeltaMovement(look.x * DIVE_STEER * DIVE_SPEED, -DIVE_SPEED, look.z * DIVE_STEER * DIVE_SPEED);
				if (player.onGround() || ticks > MAX_DIVE_TICKS) {
					if (ClientPlayNetworking.canSend(UnitedStatesSmashPayload.TYPE)) {
						ClientPlayNetworking.send(new UnitedStatesSmashPayload(DekuSettings.get().punchPower()));
					}
					Poses.play(Poses.Pose.PUNCH, LANDING_POSE_TICKS);
					phase = Phase.NONE;
				}
			}
			default -> {
			}
		}
	}
}
