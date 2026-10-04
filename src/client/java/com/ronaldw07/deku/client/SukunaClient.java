package com.ronaldw07.deku.client;

import com.ronaldw07.deku.network.JujutsuPayload;
import com.ronaldw07.deku.network.JujutsuPayload.Move;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;

/** Client side of Sukuna, used while his item is in hand: right-click Dismantle, V Cleave, C Domain Expansion. */
public final class SukunaClient {
	private static final int SLASH_POSE_TICKS = 8;
	private static final int DOMAIN_POSE_TICKS = 40;

	private static boolean wasUsing;

	private SukunaClient() {
	}

	static void tick(LocalPlayer player, boolean holding, boolean useDown, boolean cleavePressed, boolean domainPressed) {
		if (player == null) {
			wasUsing = false;
			return;
		}
		boolean able = holding && !player.isDeadOrDying();
		if (able && useDown && !wasUsing && Cooldowns.ready(Cooldowns.Ability.DISMANTLE)) {
			send(Move.DISMANTLE);
			Cooldowns.start(Cooldowns.Ability.DISMANTLE);
			Poses.play(Poses.Pose.AIM_RIGHT, SLASH_POSE_TICKS);
		}
		wasUsing = useDown;
		if (able && cleavePressed && Cooldowns.ready(Cooldowns.Ability.CLEAVE)) {
			send(Move.CLEAVE);
			Cooldowns.start(Cooldowns.Ability.CLEAVE);
			Poses.play(Poses.Pose.WHIP, SLASH_POSE_TICKS);
		}
		if (able && domainPressed && Cooldowns.ready(Cooldowns.Ability.DOMAIN)) {
			send(Move.DOMAIN);
			Cooldowns.start(Cooldowns.Ability.DOMAIN);
			Poses.play(Poses.Pose.CROSS, DOMAIN_POSE_TICKS);
		}
	}

	private static void send(Move move) {
		if (ClientPlayNetworking.canSend(JujutsuPayload.TYPE)) {
			ClientPlayNetworking.send(new JujutsuPayload(move, true, 0));
		}
	}
}
