package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.network.ManchesterPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of Manchester Smash: leap up and forward, flip at the top, then dive heel first
 * into the ground. Pressed in the air, it skips straight to the dive.
 */
public final class ManchesterClient {
	private static final double LEAP_UP = 1.3;
	private static final double LEAP_FORWARD = 0.7;
	private static final double DIVE_SPEED = 3.0;
	private static final double DIVE_DRIFT = 0.9; // how much sideways speed carries into the dive each tick
	private static final int MAX_RISE_TICKS = 20;
	private static final int MAX_DIVE_TICKS = 60;
	private static final int LANDING_POSE_TICKS = 8;

	private enum Phase {
		NONE, RISING, DIVING
	}

	private static Phase phase = Phase.NONE;
	private static int ticks;

	private ManchesterClient() {
	}

	static boolean rising() {
		return phase == Phase.RISING;
	}

	static boolean diving() {
		return phase == Phase.DIVING;
	}

	static void tick(LocalPlayer player, boolean pressed) {
		if (player == null || player.isDeadOrDying()) {
			phase = Phase.NONE;
			return;
		}

		if (pressed && phase == Phase.NONE && Cooldowns.ready(Cooldowns.Ability.MANCHESTER)) {
			Cooldowns.start(Cooldowns.Ability.MANCHESTER);
			player.level().playLocalSound(player, DekuSounds.SMASH_WINDUP, SoundSource.PLAYERS, 1.0f, 1.2f);
			ticks = 0;
			if (player.onGround()) {
				Vec3 look = player.getLookAngle();
				Vec3 flat = new Vec3(look.x, 0, look.z);
				Vec3 forward = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
				player.setDeltaMovement(forward.scale(LEAP_FORWARD).add(0, LEAP_UP, 0));
				player.setOnGround(false);
				phase = Phase.RISING;
			} else {
				phase = Phase.DIVING;
			}
			return;
		}
		if (phase == Phase.NONE) {
			return;
		}

		ticks++;
		if (phase == Phase.RISING && (player.getDeltaMovement().y <= 0 || ticks > MAX_RISE_TICKS)) {
			phase = Phase.DIVING;
			ticks = 0;
		}
		if (phase == Phase.DIVING) {
			Vec3 velocity = player.getDeltaMovement();
			player.setDeltaMovement(velocity.x * DIVE_DRIFT, -DIVE_SPEED, velocity.z * DIVE_DRIFT);
			if (player.onGround() || ticks > MAX_DIVE_TICKS) {
				if (ClientPlayNetworking.canSend(ManchesterPayload.TYPE)) {
					ClientPlayNetworking.send(new ManchesterPayload(DekuSettings.get().punchPower()));
				}
				Poses.play(Poses.Pose.KICK, LANDING_POSE_TICKS);
				phase = Phase.NONE;
			}
		}
	}
}
