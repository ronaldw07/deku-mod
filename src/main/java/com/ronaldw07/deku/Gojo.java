package com.ronaldw07.deku;

import com.ronaldw07.deku.network.ExplosionFxPayload.Style;
import com.ronaldw07.deku.network.FireballFlightPayload.Kind;
import com.ronaldw07.deku.network.JujutsuPayload.Move;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gojo's Limitless: Blue pulls everything toward a point, Red blasts everything away from one,
 * Hollow Purple fuses the two into a beam that erases whatever it passes through, and Infinity
 * keeps everything from reaching the player.
 */
public final class Gojo {
	private static final double FX_VIEW_DISTANCE = 300.0;
	private static final double ORB_HAND_FORWARD = 1.5;
	private static final double HAND_HEIGHT = 1.1;
	// Blue.
	private static final double BLUE_RANGE = 24.0;
	private static final float BLUE_BALL_RADIUS = 2.5f;
	private static final double BLUE_SPEED = 2.5;
	private static final int BLUE_HOLD_TICKS = 50;
	private static final double BLUE_PULL_RANGE = 16.0;
	private static final double BLUE_PULL_STRENGTH = 0.9;
	private static final double BLUE_CRUSH_RANGE = 3.0;
	private static final int BLUE_CRUSH_INTERVAL = 4;
	private static final float BLUE_CRUSH_DAMAGE = 3.0f;
	private static final float BLUE_FINISH_RADIUS = 3.0f;
	// Red.
	private static final double RED_RANGE = 60.0;
	private static final double RED_SPEED = 3.0;
	private static final float MIN_RED_RADIUS = 6.0f;
	private static final float MAX_RED_RADIUS = 14.0f;
	private static final float MIN_RED_BALL = 0.8f;
	private static final float MAX_RED_BALL = 2.0f;
	private static final double RED_PUSH_PER_RADIUS = 0.3;
	private static final float MIN_RED_DAMAGE = 10.0f;
	private static final float EXTRA_RED_DAMAGE = 25.0f;
	private static final double RED_REACH_PER_RADIUS = 3.0;
	private static final int RED_DEBRIS = 60;
	// Hollow Purple.
	private static final double PURPLE_RANGE = 150.0;
	private static final double PURPLE_SPEED = 4.0;
	private static final float MIN_PURPLE_BALL = 1.5f;
	private static final float MAX_PURPLE_BALL = 3.5f;
	private static final float MIN_ERASE_RADIUS = 5.0f;
	private static final float EXTRA_ERASE_RADIUS = 4.0f;
	private static final float MIN_PURPLE_BLAST = 14.0f;
	private static final float EXTRA_PURPLE_BLAST = 16.0f;
	private static final float PURPLE_DAMAGE = 40.0f;
	private static final double PURPLE_REACH = 2.0;
	private static final double PURPLE_PUSH = 2.5;
	// Infinity.
	private static final double INFINITY_RADIUS = 3.5;
	private static final double INFINITY_PROJECTILE_RADIUS = 6.0;
	private static final double INFINITY_PUSH = 0.35;
	private static final double DEFLECTED_SPEED = 0.8;

	private static final Set<UUID> infinity = new HashSet<>();

	private Gojo() {
	}

	public static void handle(ServerPlayer player, Move move, boolean active, int charge) {
		if (!DekuItems.isHolding(player, DekuItems.GOJO)) {
			if (move == Move.INFINITY) {
				infinity.remove(player.getUUID());
			}
			return;
		}
		double power = Mth.clamp(charge, 1, 100) / 100.0;
		switch (move) {
			case BLUE -> blue(player);
			case RED -> red(player, power);
			case PURPLE -> purple(player, power);
			case INFINITY -> {
				if (active) {
					infinity.add(player.getUUID());
				} else {
					infinity.remove(player.getUUID());
				}
			}
			default -> {
			}
		}
	}

	private static Vec3 handPoint(ServerPlayer player) {
		return player.position().add(0, HAND_HEIGHT, 0).add(player.getLookAngle().scale(ORB_HAND_FORWARD));
	}

	/** A blue sphere lands at the crosshair and drags everything nearby into it. */
	private static void blue(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 target = Aim.trace(player, BLUE_RANGE).getLocation();
		Vec3 start = handPoint(player);
		BlastFx.sendOrb(level, start, target, BLUE_BALL_RADIUS, BLUE_SPEED, Kind.BLUE, BLUE_HOLD_TICKS, FX_VIEW_DISTANCE);
		int travel = (int) Math.ceil(target.distanceTo(start) / BLUE_SPEED);
		Blasts.later(level.getServer(), travel, () -> pull(player, target, BLUE_HOLD_TICKS));
		level.playSound(null, start.x, start.y, start.z, DekuSounds.EXPLOSION_CHARGE, SoundSource.PLAYERS, 1.5f, 1.6f);
	}

	private static void pull(ServerPlayer player, Vec3 center, int ticksLeft) {
		ServerLevel level = player.level();
		if (ticksLeft <= 0) {
			Blasts.carve(level, center, BLUE_FINISH_RADIUS, 0);
			return;
		}
		for (Entity entity : level.getEntities(player, new AABB(center, center).inflate(BLUE_PULL_RANGE),
				entity -> entity.isAlive() && !entity.isSpectator() && entity.position().distanceTo(center) <= BLUE_PULL_RANGE)) {
			Vec3 toward = center.subtract(entity.position().add(0, entity.getBbHeight() / 2, 0));
			double distance = Math.max(0.5, toward.length());
			double strength = BLUE_PULL_STRENGTH * (1 - distance / BLUE_PULL_RANGE) + 0.1;
			entity.setDeltaMovement(entity.getDeltaMovement().scale(0.5).add(toward.scale(strength / distance)));
			entity.hurtMarked = true;
			if (entity instanceof LivingEntity living && distance < BLUE_CRUSH_RANGE && ticksLeft % BLUE_CRUSH_INTERVAL == 0) {
				living.hurtServer(level, player.damageSources().playerAttack(player), BLUE_CRUSH_DAMAGE);
			}
		}
		Blasts.later(level.getServer(), 1, () -> pull(player, center, ticksLeft - 1));
	}

	/** A red sphere flies to the crosshair and bursts, hurling everything around it outward. */
	private static void red(ServerPlayer player, double power) {
		ServerLevel level = player.level();
		Vec3 target = Aim.trace(player, RED_RANGE).getLocation();
		Vec3 start = handPoint(player);
		float radius = (float) Mth.lerp(power, MIN_RED_RADIUS, MAX_RED_RADIUS);
		BlastFx.sendOrb(level, start, target, (float) Mth.lerp(power, MIN_RED_BALL, MAX_RED_BALL), RED_SPEED, Kind.RED, 0, FX_VIEW_DISTANCE);
		int travel = (int) Math.ceil(target.distanceTo(start) / RED_SPEED);
		Blasts.later(level.getServer(), travel, () -> redBurst(player, target, radius, power));
		level.playSound(null, start.x, start.y, start.z, DekuSounds.EXPLOSION_CHARGE, SoundSource.PLAYERS, 2.0f, 0.8f);
	}

	private static void redBurst(ServerPlayer player, Vec3 center, float radius, double power) {
		ServerLevel level = player.level();
		Blasts.blast(player, center, radius, Blasts.sparing(player), RED_DEBRIS);
		Blasts.carve(level, center, radius * 0.8f, 0);
		BlastFx.send(level, center, radius, Style.GROUND, center, FX_VIEW_DISTANCE);
		double reach = radius * RED_REACH_PER_RADIUS;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(reach),
				entity -> entity != player && entity.isAlive() && entity.position().distanceTo(center) <= reach)) {
			Vec3 offset = entity.position().subtract(center);
			double strength = 1 - offset.length() / reach;
			Vec3 away = offset.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : offset.normalize();
			entity.push(away.scale(radius * RED_PUSH_PER_RADIUS * strength).add(0, strength, 0));
			entity.hurtMarked = true;
			entity.hurtServer(level, player.damageSources().playerAttack(player), (MIN_RED_DAMAGE + EXTRA_RED_DAMAGE * (float) power) * (float) strength);
		}
		level.playSound(null, center.x, center.y, center.z, DekuSounds.SMASH_BLAST, SoundSource.PLAYERS, 5.0f, 0.6f);
		level.playSound(null, center.x, center.y, center.z, DekuSounds.EXPLOSION_BOOM, SoundSource.PLAYERS, 4.0f, 0.8f);
	}

	/** Hollow Purple: a purple orb that erases everything along its path, then collapses where it ends. */
	private static void purple(ServerPlayer player, double power) {
		ServerLevel level = player.level();
		Vec3 start = handPoint(player);
		Vec3 aim = player.getLookAngle();
		Vec3 end = start.add(aim.scale(PURPLE_RANGE));
		BlastFx.sendOrb(level, start, end, (float) Mth.lerp(power, MIN_PURPLE_BALL, MAX_PURPLE_BALL), PURPLE_SPEED, Kind.PURPLE, 0, FX_VIEW_DISTANCE);
		float eraseRadius = (float) (MIN_ERASE_RADIUS + EXTRA_ERASE_RADIUS * power);
		level.playSound(null, start.x, start.y, start.z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, 3.0f, 0.5f);
		flyPurple(player, start, aim, eraseRadius, (float) (MIN_PURPLE_BLAST + EXTRA_PURPLE_BLAST * power), 1);
	}

	private static void flyPurple(ServerPlayer player, Vec3 start, Vec3 aim, float eraseRadius, float blastRadius, int tick) {
		Blasts.later(player.level().getServer(), 1, () -> {
			ServerLevel level = player.level();
			double travelled = Math.min(PURPLE_RANGE, PURPLE_SPEED * tick);
			Vec3 at = start.add(aim.scale(travelled));
			// Everything in the orb's path is wiped out: blocks vanish and anything living is torn apart.
			Blasts.carve(level, at, eraseRadius, 0);
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(eraseRadius + PURPLE_REACH),
					entity -> entity != player && entity.isAlive())) {
				entity.hurtServer(level, player.damageSources().playerAttack(player), PURPLE_DAMAGE);
				entity.push(aim.scale(PURPLE_PUSH));
				entity.hurtMarked = true;
			}
			if (travelled >= PURPLE_RANGE) {
				purpleCollapse(player, at, blastRadius);
			} else {
				flyPurple(player, start, aim, eraseRadius, blastRadius, tick + 1);
			}
		});
	}

	private static void purpleCollapse(ServerPlayer player, Vec3 center, float radius) {
		ServerLevel level = player.level();
		Blasts.carve(level, center, radius, 0);
		BlastFx.send(level, center, radius, Style.PURPLE, center, FX_VIEW_DISTANCE);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius * 2),
				entity -> entity != player && entity.isAlive() && entity.position().distanceTo(center) <= radius * 2)) {
			entity.hurtServer(level, player.damageSources().playerAttack(player), PURPLE_DAMAGE * 2);
		}
		level.playSound(null, center.x, center.y, center.z, DekuSounds.EXPLOSION_BOOM, SoundSource.PLAYERS, 6.0f, 0.5f);
		level.playSound(null, center.x, center.y, center.z, DekuSounds.EXPLOSION_THUNDER, SoundSource.PLAYERS, 8.0f, 0.7f);
	}

	/** Anything that gets close is pushed away, and nothing that touches the user does damage. */
	public static void tick(MinecraftServer server) {
		if (infinity.isEmpty()) {
			return;
		}
		infinity.removeIf(id -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null || player.isDeadOrDying() || !DekuItems.isHolding(player, DekuItems.GOJO)) {
				return true;
			}
			repel(player);
			return false;
		});
	}

	private static void repel(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 center = player.position().add(0, player.getBbHeight() / 2, 0);
		for (Entity entity : level.getEntities(player, new AABB(center, center).inflate(INFINITY_PROJECTILE_RADIUS), Entity::isAlive)) {
			Vec3 offset = entity.position().subtract(center);
			double distance = Math.max(0.1, offset.length());
			Vec3 away = offset.scale(1 / distance);
			if (entity instanceof Projectile && distance < INFINITY_PROJECTILE_RADIUS) {
				entity.setDeltaMovement(away.scale(Math.max(DEFLECTED_SPEED, entity.getDeltaMovement().length())));
				entity.hurtMarked = true;
			} else if (entity instanceof LivingEntity && distance < INFINITY_RADIUS) {
				entity.push(away.scale(INFINITY_PUSH * (1 - distance / INFINITY_RADIUS) + 0.1));
				entity.hurtMarked = true;
			}
		}
	}

	/** Whether Infinity stops this damage: anything done by another creature, a projectile or an explosion. */
	public static boolean blocks(ServerPlayer player, DamageSource source) {
		return infinity.contains(player.getUUID())
			&& (source.getEntity() != null || source.getDirectEntity() != null
				|| source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_PROJECTILE));
	}

	public static boolean infinityOn(UUID player) {
		return infinity.contains(player);
	}

	public static void forget(ServerPlayer player) {
		infinity.remove(player.getUUID());
	}
}
