package com.ronaldw07.deku.client;

import com.ronaldw07.deku.network.SmokescreenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;

/** Client side of Smokescreen: press to puff out a cloud, and keep holding to make it grow. */
public final class SmokescreenClient {
	private static final int POSE_TICKS = 12;
	private static final int POSE_REPEAT_TICKS = 10;

	private static boolean holding;
	private static int heldTicks;

	private SmokescreenClient() {
	}

	public static boolean holding() {
		return holding;
	}

	/**
	 * @param quirkInHand whether One For All is in hand; putting it away lets go
	 * @param down whether the key is down right now
	 * @param pressed whether the key was pressed since last tick (catches taps shorter than a tick)
	 */
	static void tick(LocalPlayer player, boolean quirkInHand, boolean down, boolean pressed) {
		boolean able = player != null && quirkInHand && !player.isDeadOrDying();
		if (!holding && able && (down || pressed) && Cooldowns.ready(Cooldowns.Ability.SMOKESCREEN)) {
			holding = true;
			heldTicks = 0;
			send(true);
		}
		if (!holding) {
			return;
		}
		if (!able || !down && !pressed) {
			holding = false;
			send(false);
			Cooldowns.start(Cooldowns.Ability.SMOKESCREEN);
			return;
		}
		if (heldTicks++ % POSE_REPEAT_TICKS == 0) {
			Poses.play(Poses.Pose.SMOKESCREEN, POSE_TICKS);
		}
	}

	private static void send(boolean on) {
		if (ClientPlayNetworking.canSend(SmokescreenPayload.TYPE)) {
			ClientPlayNetworking.send(new SmokescreenPayload(on));
		}
	}
}
