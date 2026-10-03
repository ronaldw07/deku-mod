package com.ronaldw07.deku;

import com.ronaldw07.deku.network.ExplosionFxPayload;
import com.ronaldw07.deku.network.ExplosionFxPayload.Style;
import com.ronaldw07.deku.network.ExplosionPayload.Move;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the Explosion quirk. Every blast is a real explosion that tears up
 * terrain and hurts everything nearby except the player who set it off.
 */
public final class Bakugo {
	private static final Identifier FLIGHT_ID = DekuMod.id("explosion_flight_no_gravity");
	private static final Identifier HOWITZER_ID = DekuMod.id("howitzer_no_gravity");
	private static final double SHOT_REACH = 48.0;
	private static final float SHOT_RADIUS = 1.3f;
	private static final float BIG_SHOT_RADIUS = 3.0f;
	private static final float HOWITZER_RADIUS = 6.0f;
	private static final double HOWITZER_REACH = 1.5;
	// The ground blast: rows of explosions fanning out in front of the player.
	private static final double[] GROUND_BLAST_DISTANCES = {3, 6, 9, 12};
	private static final double GROUND_BLAST_SPREAD = 0.45; // sideways offset per block of distance
	private static final float GROUND_BLAST_RADIUS = 3.5f;
	private static final double HAND_HEIGHT = 1.1;
	private static final double HAND_SIDE = 0.35;
	private static final int POP_INTERVAL = 4;
	private static final int VORTEX_PUFFS = 14;
	private static final double VORTEX_RADIUS = 2.0;
	private static final double VORTEX_HEIGHT = 2.5;
	private static final int SPIN_SOUND_INTERVAL = 10;
	private static final double FX_VIEW_DISTANCE = 96;

	private static final Set<UUID> flying = new HashSet<>();
	private static final Set<UUID> spinning = new HashSet<>();

	private Bakugo() {
	}

	public static void handle(ServerPlayer player, Move move, boolean active) {
		boolean starting = active || move == Move.AP_SHOT || move == Move.AP_SHOT_BIG || move == Move.GROUND_BLAST;
		if (starting && !DekuItems.isHolding(player, DekuItems.EXPLOSION)) {
			return;
		}

		switch (move) {
			case AP_SHOT -> apShot(player, SHOT_RADIUS, Style.SHOT);
			case AP_SHOT_BIG -> apShot(player, BIG_SHOT_RADIUS, Style.BIG_SHOT);
			case FLIGHT -> setFlying(player, active);
			case HOWITZER -> {
				if (active) {
					startHowitzer(player);
				} else {
					releaseHowitzer(player);
				}
			}
			case GROUND_BLAST -> groundBlast(player);
		}
	}

	private static void apShot(ServerPlayer player, float radius, Style style) {
		Vec3 target = Aim.trace(player, SHOT_REACH).getLocation();
		blast(player, target, radius, style, hand(player, 1));
	}

	private static void setFlying(ServerPlayer player, boolean on) {
		NoGravity.set(player, FLIGHT_ID, on);
		if (on) {
			flying.add(player.getUUID());
		} else {
			flying.remove(player.getUUID());
		}
	}

	private static void startHowitzer(ServerPlayer player) {
		NoGravity.set(player, HOWITZER_ID, true);
		spinning.add(player.getUUID());
	}

	private static void releaseHowitzer(ServerPlayer player) {
		if (!spinning.remove(player.getUUID())) {
			return;
		}
		NoGravity.set(player, HOWITZER_ID, false);
		Vec3 center = player.position().add(0, 1, 0).add(player.getLookAngle().scale(HOWITZER_REACH));
		blast(player, center, HOWITZER_RADIUS, Style.HOWITZER, center);
		player.level().playSound(null, center.x, center.y, center.z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, 1.5f, 0.7f);
	}

	private static void groundBlast(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 forward = new Vec3(look.x, 0, look.z).normalize();
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		Vec3 feet = player.position().add(0, 0.5, 0);
		for (double distance : GROUND_BLAST_DISTANCES) {
			for (int side = -1; side <= 1; side++) {
				Vec3 center = feet.add(forward.scale(distance)).add(right.scale(side * distance * GROUND_BLAST_SPREAD));
				blast(player, center, GROUND_BLAST_RADIUS, Style.GROUND, center);
			}
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, 2.0f, 0.6f);
	}

	public static void tick(MinecraftServer server) {
		for (UUID id : flying) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				flightBlasts(player);
			}
		}
		for (UUID id : spinning) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				vortex(player);
			}
		}
	}

	public static void forget(ServerPlayer player) {
		flying.remove(player.getUUID());
		spinning.remove(player.getUUID());
	}

	/** Little blasts out of both palms, pushing the player along. */
	private static void flightBlasts(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 backward = player.getLookAngle().scale(-1);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 palm = hand(player, side).add(backward.scale(0.3));
			level.sendParticles(ParticleTypes.FLAME, palm.x, palm.y, palm.z, 0, backward.x, backward.y, backward.z, 0.25);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, palm.x, palm.y, palm.z, 1, 0.05, 0.05, 0.05, 0.02);
		}
		if (player.tickCount % POP_INTERVAL == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.EXPLOSION_POP, SoundSource.PLAYERS, 0.6f, 1.0f);
		}
	}

	/** A ring of cloud spinning around the player while Howitzer Impact winds up. */
	private static void vortex(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 center = player.position();
		double turn = player.tickCount * 0.5;
		for (int i = 0; i < VORTEX_PUFFS; i++) {
			double angle = turn + i * Math.PI * 2 / VORTEX_PUFFS;
			double height = (double) i / VORTEX_PUFFS * VORTEX_HEIGHT;
			Vec3 tangent = new Vec3(-Math.sin(angle), 0.05, Math.cos(angle));
			level.sendParticles(DekuParticles.WHITE_SMOKE,
				center.x + Math.cos(angle) * VORTEX_RADIUS, center.y + height, center.z + Math.sin(angle) * VORTEX_RADIUS,
				0, tangent.x, tangent.y, tangent.z, 0.4);
		}
		if (player.tickCount % SPIN_SOUND_INTERVAL == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.HOWITZER_SPIN, SoundSource.PLAYERS, 1.0f, 1.0f);
		}
	}

	/** A palm's position: side 1 is the right hand, -1 the left. */
	private static Vec3 hand(ServerPlayer player, int side) {
		double yaw = Math.toRadians(player.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		return player.position().add(0, HAND_HEIGHT, 0).add(right.scale(side * HAND_SIDE));
	}

	/** A real, terrain-breaking explosion that spares its owner, plus the custom fireball effect. */
	static void blast(ServerPlayer owner, Vec3 center, float radius, Style style, Vec3 from) {
		ServerLevel level = owner.level();
		level.explode(owner, owner.damageSources().explosion(owner, owner), new SparesOwner(owner), center, radius, false,
			Level.ExplosionInteraction.TNT);
		ExplosionFxPayload fx = new ExplosionFxPayload(center, radius, style, from);
		for (ServerPlayer viewer : PlayerLookup.around(level, center, FX_VIEW_DISTANCE)) {
			if (ServerPlayNetworking.canSend(viewer, ExplosionFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	/** Explosion rules that hurt and push everything except the player who caused it. */
	private static final class SparesOwner extends ExplosionDamageCalculator {
		private final Entity owner;

		SparesOwner(Entity owner) {
			this.owner = owner;
		}

		@Override
		public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
			return entity != owner;
		}

		@Override
		public float getKnockbackMultiplier(Entity entity) {
			return entity == owner ? 0 : 1;
		}
	}
}
