package com.ronaldw07.deku;

import com.ronaldw07.deku.network.DemonArmsFxPayload;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/** Sukuna's Demon Arms: four extra arms that make every cut hit harder and come faster. Toggled on and off while he is in hand. */
public final class DemonArms {
	public static final float DAMAGE_BOOST = 1.5f;
	public static final int EXTRA_DISMANTLE_SLASHES = 4;

	private static final Set<UUID> active = new HashSet<>();

	private DemonArms() {
	}

	public static boolean active(ServerPlayer player) {
		return active.contains(player.getUUID());
	}

	public static void set(ServerPlayer player, boolean on) {
		boolean was = active.contains(player.getUUID());
		if (on && DekuItems.isHolding(player, DekuItems.SUKUNA)) {
			active.add(player.getUUID());
		} else {
			active.remove(player.getUUID());
		}
		if (was != active.contains(player.getUUID())) {
			announce(player, !was);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.EXPLOSION_RUMBLE, SoundSource.PLAYERS, 2.0f, on ? 0.6f : 1.0f);
		}
	}

	private static void announce(ServerPlayer player, boolean on) {
		DemonArmsFxPayload fx = new DemonArmsFxPayload(player.getUUID(), on);
		for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
			if (ServerPlayNetworking.canSend(viewer, DemonArmsFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
		if (ServerPlayNetworking.canSend(player, DemonArmsFxPayload.TYPE)) {
			ServerPlayNetworking.send(player, fx);
		}
	}

	/** Puts the arms away for anyone who has put Sukuna away or died. */
	public static void tick(MinecraftServer server) {
		if (active.isEmpty()) {
			return;
		}
		active.removeIf(id -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			boolean over = player == null || player.isDeadOrDying() || !DekuItems.isHolding(player, DekuItems.SUKUNA);
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
