package com.ronaldw07.deku;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shoot Style's St. Louis Smash: a sweeping kick that throws a crescent of wind and green
 * lightning ahead, hitting everything inside it. A strong enough kick slices through terrain.
 */
public final class ShootStyle {
	private static final double MIN_RANGE = 6.0;
	private static final double EXTRA_RANGE = 24.0;
	private static final float MIN_DAMAGE = 6.0f;
	private static final float EXTRA_DAMAGE = 24.0f;
	private static final double MIN_KNOCKBACK = 1.5;
	private static final double EXTRA_KNOCKBACK = 4.5;
	private static final double LIFT = 0.3;
	private static final double HALF_ARC = Math.toRadians(60);
	private static final double ARC_COS = Math.cos(HALF_ARC);
	private static final int ARC_POINTS = 12;
	private static final double[] BLADE_DISTANCES = {0.45, 0.85}; // fractions of the range
	private static final double WIND_START = 2.0;
	private static final double WIND_SPACING = 2.5;
	// Terrain slash, only from half power up.
	private static final double SLASH_THRESHOLD = 0.5;
	private static final double SLASH_START = 3.0;
	private static final double SLASH_SPACING = 2.0;
	private static final float MIN_SLASH_RADIUS = 1.0f;
	private static final float EXTRA_SLASH_RADIUS = 1.5f;
	private static final double SLASH_BLOCKS_PER_TICK = 6.0;

	private ShootStyle() {
	}

	public static void kick(ServerPlayer player, int percent) {
		double power = Mth.clamp(percent, 1, 100) / 100.0;
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
		double range = MIN_RANGE + EXTRA_RANGE * power;
		float damage = (float) (MIN_DAMAGE + EXTRA_DAMAGE * power);
		double knockback = MIN_KNOCKBACK + EXTRA_KNOCKBACK * power;

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range),
				target -> target != player && target.isAlive())) {
			Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
			double distance = toTarget.length();
			if (distance > range || (distance > 1 && toTarget.normalize().dot(look) < ARC_COS)) {
				continue;
			}
			target.hurtServer(level, player.damageSources().playerAttack(player), damage);
			target.push(look.scale(knockback).add(0, LIFT, 0));
			target.hurtMarked = true;
			Smash.lightningBurst(level, target.getBoundingBox().getCenter(), 3, 2.5, power);
		}

		crescent(level, eye, look, right, range, power);
		if (power >= SLASH_THRESHOLD) {
			slash(player, eye, look, right, range, (float) (MIN_SLASH_RADIUS + EXTRA_SLASH_RADIUS * (power - SLASH_THRESHOLD) * 2));
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.SMASH_BLAST, SoundSource.PLAYERS,
			(float) (0.6 + power), 1.4f);
		if (power >= SLASH_THRESHOLD) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS,
				(float) power, 1.3f);
		}
		player.swing(InteractionHand.MAIN_HAND, true);
	}

	/** Which way the arc points at a step across it, from -1 (far left) to 1 (far right). */
	private static Vec3 arcDirection(Vec3 look, Vec3 right, double across) {
		double angle = across * HALF_ARC;
		return look.scale(Math.cos(angle)).add(right.scale(Math.sin(angle))).normalize();
	}

	/** Lightning drawn along the curve of the blade, and wind streaming out through it. */
	private static void crescent(ServerLevel level, Vec3 eye, Vec3 look, Vec3 right, double range, double power) {
		for (double fraction : BLADE_DISTANCES) {
			Vec3 previous = null;
			for (int i = 0; i <= ARC_POINTS; i++) {
				Vec3 point = eye.add(arcDirection(look, right, i * 2.0 / ARC_POINTS - 1).scale(range * fraction));
				if (previous != null) {
					Smash.sendLightning(level, previous, point, power);
				}
				previous = point;
			}
		}

		for (int i = 0; i <= ARC_POINTS; i++) {
			Vec3 direction = arcDirection(look, right, i * 2.0 / ARC_POINTS - 1);
			for (double distance = WIND_START; distance <= range; distance += WIND_SPACING) {
				Vec3 at = eye.add(direction.scale(distance));
				// Count 0 makes the particle fly along (x, y, z) offset at the given speed.
				level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, direction.x, direction.y, direction.z, 0.4 + 0.8 * power);
			}
			Vec3 tip = eye.add(direction.scale(range * 0.6));
			level.sendParticles(ParticleTypes.GUST, tip.x, tip.y, tip.z, 1, 0, 0, 0, 0);
		}
	}

	/** Cuts the crescent into the terrain, sweeping outward a few blocks a tick. */
	private static void slash(ServerPlayer player, Vec3 eye, Vec3 look, Vec3 right, double range, float radius) {
		MinecraftServer server = player.level().getServer();
		for (double distance = SLASH_START; distance <= range; distance += SLASH_SPACING) {
			double reach = distance;
			Blasts.later(server, (int) (distance / SLASH_BLOCKS_PER_TICK), () -> {
				for (int i = 0; i <= ARC_POINTS; i++) {
					Vec3 point = eye.add(arcDirection(look, right, i * 2.0 / ARC_POINTS - 1).scale(reach));
					Blasts.carve(player.level(), point, radius, 0);
				}
			});
		}
	}
}
