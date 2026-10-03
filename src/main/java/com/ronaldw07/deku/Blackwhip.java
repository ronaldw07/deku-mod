package com.ronaldw07.deku;

import static com.ronaldw07.deku.network.BlackwhipFxPayload.NO_TARGET;

import com.ronaldw07.deku.network.BlackwhipFxPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of Blackwhip: tendrils shoot where you aim. A mob gets reeled in to you;
 * a block reels you to it. Misses lash out and snap back.
 */
public final class Blackwhip {
	private static final double REACH = 24.0;
	private static final double PULL_SPEED = 1.2;
	private static final double LIFT = 0.15; // keeps whatever is being reeled from dragging on the ground
	private static final double ARRIVE_DISTANCE = 2.0;
	private static final int MAX_PULL_TICKS = 20;
	private static final int MISS_TICKS = 6;
	private static final double FX_VIEW_DISTANCE = 64;

	/** A whip that's holding on: to a grabbed entity, or to a block anchor the player is reeled toward. */
	private record Whip(ResourceKey<Level> dimension, UUID playerId, int targetId, Vec3 anchor, long startTick) {
	}

	private static List<Whip> whips = List.of();

	private Blackwhip() {
	}

	public static void lash(ServerPlayer player) {
		ServerLevel level = player.level();
		HitResult hit = Aim.trace(player, REACH);

		level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.BLACKWHIP_LASH, SoundSource.PLAYERS, 1.0f, 1.0f);
		if (hit instanceof EntityHitResult entityHit) {
			grab(level, player, entityHit.getEntity().getId(), entityHit.getLocation());
		} else if (hit.getType() != HitResult.Type.MISS) {
			grab(level, player, NO_TARGET, hit.getLocation());
		} else {
			sendFx(level, new BlackwhipFxPayload(player.getId(), NO_TARGET, hit.getLocation(), MISS_TICKS));
		}
	}

	private static void grab(ServerLevel level, ServerPlayer player, int targetId, Vec3 anchor) {
		Whip whip = new Whip(level.dimension(), player.getUUID(), targetId, anchor, level.getGameTime());
		whips = Stream.concat(whips.stream(), Stream.of(whip)).toList();
		level.playSound(null, anchor.x, anchor.y, anchor.z, DekuSounds.BLACKWHIP_GRAB, SoundSource.PLAYERS, 1.0f, 1.0f);
		sendFx(level, new BlackwhipFxPayload(player.getId(), targetId, anchor, MAX_PULL_TICKS));
	}

	public static void tick(MinecraftServer server) {
		if (whips.isEmpty()) {
			return;
		}

		List<Whip> holding = new ArrayList<>();
		for (Whip whip : whips) {
			if (pull(server, whip)) {
				holding.add(whip);
			}
		}
		whips = List.copyOf(holding);
	}

	/** Reels for one tick; returns whether the whip is still holding on. */
	private static boolean pull(MinecraftServer server, Whip whip) {
		ServerLevel level = server.getLevel(whip.dimension());
		ServerPlayer player = server.getPlayerList().getPlayer(whip.playerId());
		if (level == null || player == null || player.level() != level || level.getGameTime() - whip.startTick() >= MAX_PULL_TICKS) {
			return false;
		}

		if (whip.targetId() == NO_TARGET) {
			return reel(player, whip.anchor());
		}

		Entity target = level.getEntity(whip.targetId());
		return target != null && target.isAlive() && reel(target, player.position());
	}

	/** Moves the entity one tick toward the destination; stops it and returns false once it's arrived. */
	private static boolean reel(Entity mover, Vec3 destination) {
		Vec3 offset = destination.subtract(mover.position());
		boolean arrived = offset.length() < ARRIVE_DISTANCE;
		mover.setDeltaMovement(arrived ? Vec3.ZERO : offset.normalize().scale(PULL_SPEED).add(0, LIFT, 0));
		mover.hurtMarked = true;
		mover.resetFallDistance();
		return !arrived;
	}

	private static void sendFx(ServerLevel level, BlackwhipFxPayload fx) {
		for (ServerPlayer viewer : PlayerLookup.around(level, fx.anchor(), FX_VIEW_DISTANCE)) {
			if (ServerPlayNetworking.canSend(viewer, BlackwhipFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}
}
