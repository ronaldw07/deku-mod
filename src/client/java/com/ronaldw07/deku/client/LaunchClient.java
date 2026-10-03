package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuItems;
import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.network.LaunchPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the One For All launch: on the ground with One For All in hand, holding
 * jump crouches and charges, and letting go rockets the player toward the crosshair. A
 * quick tap still jumps, on release. A double-tap is left alone so it can start Float.
 */
public final class LaunchClient {
	private static final int CHARGE_DELAY_TICKS = 6; // held shorter than this is a tap
	private static final int FULL_CHARGE_TICKS = 20;
	private static final int DOUBLE_TAP_TICKS = 7;
	private static final int CHARGE_SOUND_INTERVAL = 6;
	private static final double MIN_SPEED = 1.5;
	private static final double EXTRA_SPEED = 3.0;
	private static final double LIFT = 0.4;
	private static final int MIN_FLIGHT_TICKS = 3;
	private static final int MAX_FLIGHT_TICKS = 60;

	private static boolean wasDown;
	private static int ticksSincePress = DOUBLE_TAP_TICKS + 1;
	private static boolean doubleTap;
	private static int heldTicks;
	private static int flightTicks;

	private LaunchClient() {
	}

	/** How far the launch is wound up, 0-100; 0 when not charging. */
	public static int charge() {
		int ticks = heldTicks - CHARGE_DELAY_TICKS;
		return ticks < 0 ? 0 : Math.min(100, 1 + ticks * 100 / FULL_CHARGE_TICKS);
	}

	public static boolean charging() {
		return charge() > 0;
	}

	public static boolean launching() {
		return flightTicks > 0;
	}

	/** Runs once a tick as the keys are read, before the player moves; returns the keys the player acts on. */
	public static Input filter(Input keys) {
		LocalPlayer player = Minecraft.getInstance().player;
		boolean down = keys.jump();
		boolean pressedNow = down && !wasDown;
		boolean releasedNow = !down && wasDown;
		wasDown = down;
		ticksSincePress++;
		if (pressedNow) {
			doubleTap = ticksSincePress <= DOUBLE_TAP_TICKS;
			ticksSincePress = 0;
		}
		if (player == null) {
			heldTicks = 0;
			flightTicks = 0;
			return keys;
		}
		trackFlight(player);

		boolean able = DekuItems.isHolding(player, DekuItems.ONE_FOR_ALL) && !player.isDeadOrDying()
			&& !FloatClient.active() && !doubleTap && (heldTicks > 0 || player.onGround() && Cooldowns.ready(Cooldowns.Ability.LAUNCH));
		if (!able) {
			heldTicks = 0;
			return keys;
		}

		if (down) {
			heldTicks++;
			chargeSounds(player);
			return withJump(keys, false, charging());
		}
		if (releasedNow) {
			boolean tap = !charging();
			if (!tap) {
				launch(player);
			}
			heldTicks = 0;
			return withJump(keys, tap, false);
		}
		return keys;
	}

	private static void launch(LocalPlayer player) {
		double power = charge() / 100.0;
		player.setDeltaMovement(player.getLookAngle().scale(MIN_SPEED + EXTRA_SPEED * power).add(0, LIFT, 0));
		player.setOnGround(false); // so this tick's move isn't slowed by ground friction
		if (ClientPlayNetworking.canSend(LaunchPayload.TYPE)) {
			ClientPlayNetworking.send(new LaunchPayload(charge()));
		}
		Cooldowns.start(Cooldowns.Ability.LAUNCH);
		flightTicks = 1;
	}

	private static void trackFlight(LocalPlayer player) {
		if (flightTicks == 0) {
			return;
		}
		boolean landed = flightTicks >= MIN_FLIGHT_TICKS && (player.onGround() || player.isInWater());
		flightTicks = landed || flightTicks >= MAX_FLIGHT_TICKS || player.isDeadOrDying() ? 0 : flightTicks + 1;
	}

	private static void chargeSounds(LocalPlayer player) {
		int ticks = heldTicks - CHARGE_DELAY_TICKS;
		if (ticks == 0) {
			player.level().playLocalSound(player, DekuSounds.SMASH_WINDUP, SoundSource.PLAYERS, 1.0f, 1.2f);
		}
		if (ticks >= 0 && ticks % CHARGE_SOUND_INTERVAL == 0) {
			player.level().playLocalSound(player, DekuSounds.SMASH_CHARGE, SoundSource.PLAYERS, 0.8f, 0.6f + charge() / 100f);
		}
	}

	/** The same keys with jump and sneak replaced; sneaking while charging makes the player crouch. */
	private static Input withJump(Input keys, boolean jump, boolean crouch) {
		return new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), jump, keys.shift() || crouch, keys.sprint());
	}
}
