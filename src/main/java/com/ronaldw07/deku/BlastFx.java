package com.ronaldw07.deku;

import com.ronaldw07.deku.network.ExplosionFxPayload;
import com.ronaldw07.deku.network.ExplosionFxPayload.Style;
import com.ronaldw07.deku.network.FireballChargeFxPayload;
import com.ronaldw07.deku.network.FireballFlightPayload;
import com.ronaldw07.deku.network.FireballFlightPayload.Kind;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Tells the players near a blast to draw its big fiery look; see ExplosionFx on the client. */
public final class BlastFx {
	private static final double CHARGE_VIEW_DISTANCE = 128.0;

	private BlastFx() {
	}

	public static void send(ServerLevel level, Vec3 center, float radius, Style style, Vec3 from, double viewDistance) {
		ExplosionFxPayload fx = new ExplosionFxPayload(center, radius, style, from);
		for (ServerPlayer viewer : PlayerLookup.around(level, center, viewDistance)) {
			if (ServerPlayNetworking.canSend(viewer, ExplosionFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	/** Lets everyone nearby see a player's Cluster Bomb fireball growing. */
	public static void showFireballCharge(ServerPlayer player, int charge) {
		if (!DekuItems.isHolding(player, DekuItems.EXPLOSION)) {
			return;
		}
		FireballChargeFxPayload fx = new FireballChargeFxPayload(player.getUUID(), Math.max(0, Math.min(100, charge)));
		for (ServerPlayer viewer : PlayerLookup.around(player.level(), player.position(), CHARGE_VIEW_DISTANCE)) {
			if (ServerPlayNetworking.canSend(viewer, FireballChargeFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	/** Tells nearby players to draw a glowing orb flying from one point to another, then hanging there. */
	public static void sendOrb(ServerLevel level, Vec3 start, Vec3 end, float ballRadius, double speed, Kind kind, int holdTicks,
			double viewDistance) {
		FireballFlightPayload flight = new FireballFlightPayload(start, end, ballRadius, (float) speed, kind, holdTicks);
		for (ServerPlayer viewer : PlayerLookup.around(level, start, viewDistance)) {
			if (ServerPlayNetworking.canSend(viewer, FireballFlightPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, flight);
			}
		}
	}
}
