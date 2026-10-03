package com.ronaldw07.deku.client;

/**
 * Which body pose the local player shows for the move they're doing. Held moves (charging,
 * flying, aiming) pose for as long as they last; quick moves play a short timed pose.
 */
public final class Poses {
	public enum Pose {
		NONE, POWER_UP, SMASH_CHARGE, PUNCH, SMOKESCREEN, FLOAT, WHIP, AIM_RIGHT, AIM_BOTH, CROSS, BURST, THRUSTERS, LAUNCH_CHARGE, LAUNCH, FLICK
	}

	private static Pose timed = Pose.NONE;
	private static int ticksLeft;

	private Poses() {
	}

	static void play(Pose pose, int ticks) {
		timed = pose;
		ticksLeft = ticks;
	}

	static void tick() {
		if (ticksLeft > 0 && --ticksLeft == 0) {
			timed = Pose.NONE;
		}
	}

	public static Pose current() {
		if (timed == Pose.FLICK) {
			return timed;
		}
		if (ExplosionClient.flying() || ExplosionClient.spinning()) {
			return Pose.THRUSTERS;
		}
		if (LaunchClient.launching()) {
			return Pose.LAUNCH;
		}
		if (LaunchClient.charging()) {
			return Pose.LAUNCH_CHARGE;
		}
		if (ExplosionClient.armsCrossed()) {
			return Pose.CROSS;
		}
		if (SmashClient.charging()) {
			return Pose.SMASH_CHARGE;
		}
		if (ExplosionClient.rapidFiring()) {
			return Pose.AIM_RIGHT;
		}
		if (timed != Pose.NONE) {
			return timed;
		}
		return FloatClient.active() ? Pose.FLOAT : Pose.NONE;
	}
}
