package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.network.ExplosionPayload;
import com.ronaldw07.deku.network.ExplosionPayload.Move;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the Explosion quirk, used while the Explosion item is in hand:
 * right-click for AP Shot (tap for a big one, hold for rapid fire), double-tap and hold
 * jump to fly, hold V for Howitzer Impact, and C for the cross-arm ground blast.
 */
public final class ExplosionClient {
	private static final int TAP_TICKS = 6; // right-click held for less than this is a tap
	private static final int RAPID_FIRE_INTERVAL = 2;
	private static final int BIG_SHOT_LOAD_TICKS = 8;
	private static final int DOUBLE_TAP_TICKS = 7;
	private static final int CROSS_ARMS_TICKS = 10;
	private static final double FLIGHT_SPEED = 0.9;
	private static final double HOWITZER_FORWARD_SPEED = 0.55;
	private static final double HOWITZER_CIRCLE_SPEED = 0.55;
	private static final double HOWITZER_TURN = 0.6; // radians per tick around the circle
	private static final float HOWITZER_BODY_SPIN = 50; // degrees per tick

	private static int useHeldTicks;
	private static int bigShotLoad;
	private static boolean jumpWasDown;
	private static int ticksSinceJumpPress = DOUBLE_TAP_TICKS + 1;
	private static boolean flying;
	private static boolean spinning;
	private static double spinAngle;
	private static int crossArms;

	private ExplosionClient() {
	}

	public static boolean armsCrossed() {
		return crossArms > 0;
	}

	public static boolean flying() {
		return flying;
	}

	public static boolean spinning() {
		return spinning;
	}

	static void tick(LocalPlayer player, boolean holding, boolean useDown, boolean jumpDown, boolean howitzerDown,
			boolean groundBlastPressed) {
		if (player == null) {
			useHeldTicks = 0;
			bigShotLoad = 0;
			flying = false;
			spinning = false;
			crossArms = 0;
			return;
		}

		boolean able = holding && !player.isDeadOrDying();
		apShot(player, able && useDown);
		flight(player, able, jumpDown);
		howitzer(player, able && howitzerDown);
		groundBlast(player, able && groundBlastPressed);
	}

	private static void apShot(LocalPlayer player, boolean down) {
		if (bigShotLoad > 0 && --bigShotLoad == 0) {
			send(Move.AP_SHOT_BIG, true);
		}

		if (down) {
			useHeldTicks++;
			if (useHeldTicks >= TAP_TICKS && (useHeldTicks - TAP_TICKS) % RAPID_FIRE_INTERVAL == 0) {
				send(Move.AP_SHOT, true);
			}
			return;
		}

		// A tap loads for a moment, then fires one big shot.
		if (useHeldTicks > 0 && useHeldTicks < TAP_TICKS && bigShotLoad == 0) {
			bigShotLoad = BIG_SHOT_LOAD_TICKS;
			player.level().playLocalSound(player, DekuSounds.EXPLOSION_CHARGE, SoundSource.PLAYERS, 1.0f, 1.0f);
		}
		useHeldTicks = 0;
	}

	private static void flight(LocalPlayer player, boolean able, boolean jumpDown) {
		boolean pressedNow = jumpDown && !jumpWasDown;
		jumpWasDown = jumpDown;
		ticksSinceJumpPress++;
		if (pressedNow) {
			if (able && ticksSinceJumpPress <= DOUBLE_TAP_TICKS && !flying) {
				flying = true;
				send(Move.FLIGHT, true);
			}
			ticksSinceJumpPress = 0;
		}

		if (flying && (!jumpDown || !able)) {
			flying = false;
			send(Move.FLIGHT, false);
		}
		if (flying) {
			player.setDeltaMovement(player.getLookAngle().scale(FLIGHT_SPEED));
		}
	}

	/** Spirals toward wherever the player is looking while the key is held; letting go explodes. */
	private static void howitzer(LocalPlayer player, boolean down) {
		if (down && !spinning) {
			spinning = true;
			spinAngle = 0;
			send(Move.HOWITZER, true);
		} else if (!down && spinning) {
			spinning = false;
			send(Move.HOWITZER, false);
		}
		if (!spinning) {
			return;
		}

		spinAngle += HOWITZER_TURN;
		Vec3 look = player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look);
		Vec3 circle = right.scale(Math.cos(spinAngle)).add(up.scale(Math.sin(spinAngle))).scale(HOWITZER_CIRCLE_SPEED);
		player.setDeltaMovement(look.scale(HOWITZER_FORWARD_SPEED).add(circle));
		player.setYBodyRot(player.yBodyRot + HOWITZER_BODY_SPIN);
	}

	/** Arms go up in a cross for a moment, then the ground in front erupts. */
	private static void groundBlast(LocalPlayer player, boolean pressed) {
		if (crossArms > 0 && --crossArms == 0) {
			send(Move.GROUND_BLAST, true);
		}
		if (pressed && crossArms == 0) {
			crossArms = CROSS_ARMS_TICKS;
			player.level().playLocalSound(player, DekuSounds.EXPLOSION_CHARGE, SoundSource.PLAYERS, 1.0f, 0.7f);
		}
	}

	private static void send(Move move, boolean active) {
		if (ClientPlayNetworking.canSend(ExplosionPayload.TYPE)) {
			ClientPlayNetworking.send(new ExplosionPayload(move, active));
		}
	}
}
