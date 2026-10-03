package com.ronaldw07.deku.client;

/** Per-tick power ramp shared by Full Cowling and Smash charging. */
final class Ramp {
	private static final int TICKS_PER_SECOND = 20;

	private Ramp() {
	}

	/** One tick of climbing from 0 to target over the given seconds; drops straight down if target was lowered. */
	static double toward(double current, int target, double seconds) {
		if (seconds == 0 || current >= target) {
			return target;
		}

		return Math.min(target, current + target / (seconds * TICKS_PER_SECOND));
	}
}
