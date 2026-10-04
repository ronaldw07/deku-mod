package com.ronaldw07.deku;

import com.ronaldw07.deku.network.ExplosionCowlingFxPayload;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the Explosion quirk's Full Cowling: while it is on, the player's blasts are
 * bigger and so hit harder. It stays on only while Explosion is in hand.
 */
public final class ExplosionCowling {
	/** How much bigger every blast is while the Cowling is on. */
	public static final float BLAST_BOOST = 1.25f;

	private static final Set<UUID> active = new HashSet<>();

	private ExplosionCowling() {
	}

	public static boolean active(ServerPlayer player) {
		return active.contains(player.getUUID());
	}

	public static void set(ServerPlayer player, boolean on) {
		boolean was = active.contains(player.getUUID());
		if (on && DekuItems.isHolding(player, DekuItems.EXPLOSION)) {
			active.add(player.getUUID());
		} else {
			active.remove(player.getUUID());
		}
		if (was != active.contains(player.getUUID())) {
			announce(player, !was);
		}
	}

	/** Lets everyone nearby see the glow too. */
	private static void announce(ServerPlayer player, boolean on) {
		ExplosionCowlingFxPayload fx = new ExplosionCowlingFxPayload(player.getUUID(), on);
		for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
			if (ServerPlayNetworking.canSend(viewer, ExplosionCowlingFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	/** Puts the Cowling out for anyone who has put Explosion away or died. */
	public static void tick(MinecraftServer server) {
		if (active.isEmpty()) {
			return;
		}
		active.removeIf(id -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			boolean over = player == null || player.isDeadOrDying() || !DekuItems.isHolding(player, DekuItems.EXPLOSION);
			if (over && player != null) {
				announce(player, false);
			}
			return over;
		});
	}

	public static void forget(ServerPlayer player) {
		active.remove(player.getUUID());
	}
}
