package com.ronaldw07.deku.client;

/** Detects "double-tap a key and keep holding it": on from the second press until the key is let go. */
final class DoubleTapHold {
	private static final int DOUBLE_TAP_TICKS = 7;

	private boolean wasDown;
	private int ticksSincePress = DOUBLE_TAP_TICKS + 1;
	private boolean active;

	/** Call once a tick; returns whether the double-tap-hold is on. Turns off at once when not allowed. */
	boolean tick(boolean down, boolean allowed) {
		boolean pressedNow = down && !wasDown;
		wasDown = down;
		ticksSincePress++;
		if (pressedNow) {
			if (allowed && ticksSincePress <= DOUBLE_TAP_TICKS) {
				active = true;
			}
			ticksSincePress = 0;
		}
		if (!down || !allowed) {
			active = false;
		}
		return active;
	}
}
