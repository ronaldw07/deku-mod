package com.ronaldw07.deku;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
		if (on && DekuItems.isHolding(player, DekuItems.EXPLOSION)) {
			active.add(player.getUUID());
		} else {
			active.remove(player.getUUID());
		}
	}

	/** Puts the Cowling out for anyone who has put Explosion away or died. */
	public static void tick(MinecraftServer server) {
		if (active.isEmpty()) {
			return;
		}
		active.removeIf(id -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			return player == null || player.isDeadOrDying() || !DekuItems.isHolding(player, DekuItems.EXPLOSION);
		});
	}

	public static void forget(ServerPlayer player) {
		active.remove(player.getUUID());
	}
}
