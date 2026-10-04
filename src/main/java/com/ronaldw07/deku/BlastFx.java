package com.ronaldw07.deku;

import com.ronaldw07.deku.network.ExplosionFxPayload;
import com.ronaldw07.deku.network.ExplosionFxPayload.Style;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Tells the players near a blast to draw its big fiery look; see ExplosionFx on the client. */
public final class BlastFx {
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
}
