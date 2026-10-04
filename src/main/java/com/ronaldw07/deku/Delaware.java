package com.ronaldw07.deku;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Delaware Smash: a finger flick that fires a compressed bullet of air straight ahead. It
 * slams through every mob along its line and bursts where it lands, leaving a small crater.
 * Smaller than Smash, but quick to fire again.
 */
public final class Delaware {
	private static final double MIN_RANGE = 15.0;
	private static final double EXTRA_RANGE = 35.0;
	private static final double BULLET_WIDTH = 1.5; // how far off the line a mob can be and still get hit
	private static final float MIN_DAMAGE = 6.0f;
	private static final float EXTRA_DAMAGE = 18.0f;
	private static final double MIN_KNOCKBACK = 2.0;
	private static final double EXTRA_KNOCKBACK = 4.0;
	private static final double LIFT = 0.4;
	private static final float MIN_CRATER = 1.5f;
	private static final float EXTRA_CRATER = 1.0f;
	private static final int IMPACT_BOLTS = 5;
	private static final double IMPACT_BOLT_LENGTH = 3.0;
	private static final double TRAIL_START = 1.0;
	private static final double TRAIL_SPACING = 0.8;
	private static final double TRAIL_SPREAD = 0.15;

	private Delaware() {
	}

	public static void flick(ServerPlayer player, int percent) {
		double power = Mth.clamp(percent, 1, 100) / 100.0;
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = Aim.trace(player, MIN_RANGE + EXTRA_RANGE * power).getLocation();
		double length = eye.distanceTo(end);

		float damage = (float) (MIN_DAMAGE + EXTRA_DAMAGE * power);
		Vec3 push = look.scale(MIN_KNOCKBACK + EXTRA_KNOCKBACK * power).add(0, LIFT, 0);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(BULLET_WIDTH),
				entity -> entity != player && entity.isAlive())) {
			Vec3 offset = entity.getBoundingBox().getCenter().subtract(eye);
			double along = offset.dot(look);
			if (along < 0 || along > length + BULLET_WIDTH || offset.subtract(look.scale(along)).length() > BULLET_WIDTH) {
				continue;
			}
			entity.hurtServer(level, player.damageSources().playerAttack(player), damage);
			entity.push(push);
			entity.hurtMarked = true;
		}

		trail(level, eye, look, length, power);
		impact(player, end, power);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.SMASH_BLAST, SoundSource.PLAYERS,
			(float) (0.8 + 0.6 * power), 1.5f);
		player.swing(InteractionHand.MAIN_HAND, true);
	}

	/** A tight, fast stream of wind along the bullet's path. */
	private static void trail(ServerLevel level, Vec3 eye, Vec3 look, double length, double power) {
		for (double distance = TRAIL_START; distance <= length; distance += TRAIL_SPACING) {
			Vec3 at = eye.add(look.scale(distance));
			// Count 0 makes the particle fly along (x, y, z) offset at the given speed.
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, look.x, look.y, look.z, 0.8 + 0.6 * power);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 2, TRAIL_SPREAD, TRAIL_SPREAD, TRAIL_SPREAD, 0.02);
		}
		Vec3 hand = eye.add(look.scale(TRAIL_START));
		level.sendParticles(ParticleTypes.GUST, hand.x, hand.y, hand.z, 1, 0, 0, 0, 0);
		Smash.sendLightning(level, hand, eye.add(look.scale(length)), power);
	}

	/** The bullet bursts where it lands: a pop of air, green sparks and a small crater. */
	private static void impact(ServerPlayer player, Vec3 at, double power) {
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 3, 0.5, 0.5, 0.5, 0);
		Smash.lightningBurst(level, at, IMPACT_BOLTS, IMPACT_BOLT_LENGTH, power);
		Blasts.carve(level, at, (float) (MIN_CRATER + EXTRA_CRATER * power), 0);
		level.playSound(null, at.x, at.y, at.z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, (float) (0.3 + 0.4 * power), 1.6f);
	}
}
