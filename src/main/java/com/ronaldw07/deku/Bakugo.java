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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the Explosion quirk. Every blast is a real explosion that tears up
 * terrain and hurts everything nearby except the player who set it off.
 */
public final class Bakugo {
	private static final Identifier FLIGHT_ID = DekuMod.id("explosion_flight_no_gravity");
	private static final Identifier HOWITZER_ID = DekuMod.id("howitzer_no_gravity");
	private static final double SHOT_REACH = 48.0;
	private static final float SHOT_RADIUS = 2.0f;
	private static final float BIG_SHOT_RADIUS = 5.0f;
	// The big shot is a beam: everything along it from hand to target is hit too.
	private static final double BEAM_WIDTH = 1.5;
	private static final float BEAM_DAMAGE = 12.0f;
	private static final double BEAM_KNOCKBACK = 1.5;
	private static final double HOWITZER_REACH = 1.5;
	// Howitzer Impact: a huge core blast, two rings of blasts rolling outward from it, and a
	// shockwave that throws everything within SHOCKWAVE_RANGE and badly hurts anything close.
	private static final float HOWITZER_CORE_RADIUS = 14.0f;
	private static final int HOWITZER_CORE_DEBRIS = 180;
	private static final double[] HOWITZER_RING_DISTANCES = {20, 36};
	private static final int[] HOWITZER_RING_BLASTS = {8, 10};
	private static final float[] HOWITZER_RING_RADII = {9.0f, 7.0f};
	// Blasts stacked above the core, so the explosion towers instead of just spreading.
	private static final double[] HOWITZER_COLUMN_HEIGHTS = {12, 24, 36};
	private static final float[] HOWITZER_COLUMN_RADII = {11.0f, 9.0f, 7.0f};
	private static final int HOWITZER_RING_DEBRIS = 8;
	private static final int TICKS_PER_RING = 3;
	private static final double SHOCKWAVE_RANGE = 200.0;
	private static final double SHOCKWAVE_MAX_PUSH = 3.5;
	private static final double SHOCKWAVE_DAMAGE_RANGE = 40.0;
	private static final float SHOCKWAVE_MAX_DAMAGE = 40.0f;
	// The ground blast: rows of explosions fanning out in front of the player, one row a tick.
	// Holding C longer adds rows, widens the fan and grows each blast.
	private static final int MIN_GROUND_ROWS = 3;
	private static final int EXTRA_GROUND_ROWS = 9;
	private static final double GROUND_FIRST_ROW = 4.0;
	private static final double GROUND_ROW_SPACING = 7.0;
	private static final double GROUND_BLAST_SPREAD = 0.45; // sideways offset per block of distance
	private static final float MIN_GROUND_RADIUS = 5.0f;
	private static final float EXTRA_GROUND_RADIUS = 4.0f;
	private static final int GROUND_DEBRIS = 4;
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

	public static void handle(ServerPlayer player, Move move, boolean active, int charge) {
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
			case GROUND_BLAST -> groundBlast(player, Math.clamp(charge, 0, 100) / 100.0);
		}
	}

	private static void apShot(ServerPlayer player, float radius, Style style) {
		Vec3 target = Aim.trace(player, SHOT_REACH).getLocation();
		Vec3 hand = hand(player, 1);
		if (style == Style.BIG_SHOT) {
			beam(player, hand, target);
		}
		blast(player, target, radius, 0, style, hand);
	}

	/** Hits and shoves everything along the beam's path. */
	private static void beam(ServerPlayer player, Vec3 from, Vec3 to) {
		ServerLevel level = player.level();
		Vec3 path = to.subtract(from);
		double length = path.length();
		Vec3 direction = path.scale(1 / Math.max(length, 1.0E-3));
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(BEAM_WIDTH),
				entity -> entity != player && entity.isAlive())) {
			Vec3 offset = entity.getBoundingBox().getCenter().subtract(from);
			double along = offset.dot(direction);
			if (along < 0 || along > length || offset.subtract(direction.scale(along)).length() > BEAM_WIDTH) {
				continue;
			}
			entity.hurtServer(level, player.damageSources().explosion(player, player), BEAM_DAMAGE);
			entity.push(direction.scale(BEAM_KNOCKBACK));
			entity.hurtMarked = true;
		}
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
		ServerLevel level = player.level();
		Vec3 center = player.position().add(0, 1, 0).add(player.getLookAngle().scale(HOWITZER_REACH));
		blast(player, center, HOWITZER_CORE_RADIUS, HOWITZER_CORE_DEBRIS, Style.HOWITZER, center);
		shockwave(player, center);
		level.playSound(null, center.x, center.y, center.z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, 4.0f, 0.5f);

		for (int layer = 0; layer < HOWITZER_COLUMN_HEIGHTS.length; layer++) {
			Vec3 spot = center.add(0, HOWITZER_COLUMN_HEIGHTS[layer], 0);
			float radius = HOWITZER_COLUMN_RADII[layer];
			Blasts.later(level.getServer(), layer + 1, () -> blast(player, spot, radius, HOWITZER_RING_DEBRIS, Style.HOWITZER, spot));
		}

		for (int ring = 0; ring < HOWITZER_RING_DISTANCES.length; ring++) {
			double distance = HOWITZER_RING_DISTANCES[ring];
			int blasts = HOWITZER_RING_BLASTS[ring];
			float radius = HOWITZER_RING_RADII[ring];
			Blasts.later(level.getServer(), (ring + 1) * TICKS_PER_RING, () -> {
				for (int i = 0; i < blasts; i++) {
					double angle = i * Math.PI * 2 / blasts;
					Vec3 spot = center.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
					blast(player, spot, radius, HOWITZER_RING_DEBRIS, Style.BIG_SHOT, spot);
				}
			});
		}
	}

	/** Throws everything within range away from the center, and badly hurts anything close. */
	private static void shockwave(ServerPlayer player, Vec3 center) {
		ServerLevel level = player.level();
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(SHOCKWAVE_RANGE),
				entity -> entity != player && entity.isAlive())) {
			Vec3 offset = entity.position().subtract(center);
			double distance = offset.length();
			if (distance > SHOCKWAVE_RANGE) {
				continue;
			}
			double strength = 1 - distance / SHOCKWAVE_RANGE;
			Vec3 away = distance < 1.0E-3 ? new Vec3(0, 1, 0) : offset.scale(1 / distance);
			entity.push(away.scale(SHOCKWAVE_MAX_PUSH * strength).add(0, strength, 0));
			entity.hurtMarked = true;
			if (distance < SHOCKWAVE_DAMAGE_RANGE) {
				entity.hurtServer(level, player.damageSources().explosion(player, player),
					SHOCKWAVE_MAX_DAMAGE * (float) (1 - distance / SHOCKWAVE_DAMAGE_RANGE));
			}
		}
	}

	private static void groundBlast(ServerPlayer player, double charge) {
		Vec3 look = player.getLookAngle();
		Vec3 forward = new Vec3(look.x, 0, look.z).normalize();
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		Vec3 feet = player.position().add(0, 0.5, 0);
		int rows = MIN_GROUND_ROWS + (int) Math.round(EXTRA_GROUND_ROWS * charge);
		int lanesEachSide = 1 + (int) Math.round(2 * charge);
		float radius = MIN_GROUND_RADIUS + EXTRA_GROUND_RADIUS * (float) charge;

		for (int row = 0; row < rows; row++) {
			double distance = GROUND_FIRST_ROW + row * GROUND_ROW_SPACING;
			Blasts.later(player.level().getServer(), row, () -> {
				for (int lane = -lanesEachSide; lane <= lanesEachSide; lane++) {
					Vec3 center = feet.add(forward.scale(distance)).add(right.scale(lane * distance * GROUND_BLAST_SPREAD / lanesEachSide));
					blast(player, center, radius, GROUND_DEBRIS, Style.GROUND, center);
				}
			});
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS,
			2.0f + 2.0f * (float) charge, 0.6f);
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
	private static void blast(ServerPlayer owner, Vec3 center, float radius, int debris, Style style, Vec3 from) {
		ServerLevel level = owner.level();
		Blasts.blast(owner, center, radius, Blasts.sparing(owner), debris);
		ExplosionFxPayload fx = new ExplosionFxPayload(center, radius, style, from);
		for (ServerPlayer viewer : PlayerLookup.around(level, center, FX_VIEW_DISTANCE)) {
			if (ServerPlayNetworking.canSend(viewer, ExplosionFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}
}
