package com.ronaldw07.deku.client;

import com.ronaldw07.deku.network.HalfColdHalfHotPayload;
import com.ronaldw07.deku.network.HalfColdHalfHotPayload.Move;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;

/**
 * Client side of Half Cold Half Hot, used while its item is in hand: right-click for the ice
 * wave, hold V for the flamethrower, C for the ice wall, and X for Flashfreeze Heatwave.
 */
public final class HalfColdHalfHotClient {
	private static final int ICE_POSE_TICKS = 10;
	private static final int WALL_POSE_TICKS = 15;
	private static final int HEATWAVE_POSE_TICKS = 20;

	private static boolean flaming;

	private HalfColdHalfHotClient() {
	}

	static boolean flaming() {
		return flaming;
	}

	static void tick(LocalPlayer player, boolean holding, boolean useDown, boolean flameDown, boolean wallPressed,
			boolean heatwavePressed) {
		if (player == null) {
			flaming = false;
			return;
		}
		boolean able = holding && !player.isDeadOrDying();

		if (able && useDown && Cooldowns.ready(Cooldowns.Ability.ICE_WAVE)) {
			send(Move.ICE_WAVE, true);
			Cooldowns.start(Cooldowns.Ability.ICE_WAVE);
			Poses.play(Poses.Pose.GROUND_TOUCH, ICE_POSE_TICKS);
		}

		boolean nowFlaming = able && flameDown;
		if (nowFlaming != flaming) {
			flaming = nowFlaming;
			send(Move.FLAME, flaming);
		}

		if (able && wallPressed && Cooldowns.ready(Cooldowns.Ability.ICE_WALL)) {
			send(Move.ICE_WALL, true);
			Cooldowns.start(Cooldowns.Ability.ICE_WALL);
			Poses.play(Poses.Pose.GROUND_TOUCH, WALL_POSE_TICKS);
		}

		if (able && heatwavePressed && Cooldowns.ready(Cooldowns.Ability.HEATWAVE)) {
			send(Move.HEATWAVE, true);
			Cooldowns.start(Cooldowns.Ability.HEATWAVE);
			Poses.play(Poses.Pose.AIM_BOTH, HEATWAVE_POSE_TICKS);
		}
	}

	private static void send(Move move, boolean active) {
		if (ClientPlayNetworking.canSend(HalfColdHalfHotPayload.TYPE)) {
			ClientPlayNetworking.send(new HalfColdHalfHotPayload(move, active));
		}
	}
}
