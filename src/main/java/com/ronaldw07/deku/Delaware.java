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
 * Delaware Smash: a finger flick that fires a small, fast bullet of air straight ahead. It hits
 * the first mob in its path and knocks it back; a lighter, quicker move than Smash.
 */
public final class Delaware {
	private static final double MIN_RANGE = 10.0;
	private static final double EXTRA_RANGE = 20.0;
	private static final float MIN_DAMAGE = 3.0f;
	private static final float EXTRA_DAMAGE = 9.0f;
	private static final double MIN_KNOCKBACK = 1.0;
	private static final double EXTRA_KNOCKBACK = 2.5;
	private static final double LIFT = 0.25;
	private static final double AIM_COS = 0.97; // about 14 degrees off the crosshair
	private static final double TRAIL_START = 1.0;
	private static final double TRAIL_SPACING = 1.0;

	private Delaware() {
	}

	public static void flick(ServerPlayer player, int percent) {
		double power = Mth.clamp(percent, 1, 100) / 100.0;
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double range = MIN_RANGE + EXTRA_RANGE * power;

		LivingEntity target = null;
		double nearest = range;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range),
				entity -> entity != player && entity.isAlive() && player.hasLineOfSight(entity))) {
			Vec3 toEntity = entity.getBoundingBox().getCenter().subtract(eye);
			double distance = toEntity.length();
			if (distance < nearest && toEntity.normalize().dot(look) >= AIM_COS) {
				target = entity;
				nearest = distance;
			}
		}

		Vec3 end = target == null ? Aim.trace(player, range).getLocation() : target.getBoundingBox().getCenter();
		if (target != null) {
			target.hurtServer(level, player.damageSources().playerAttack(player), (float) (MIN_DAMAGE + EXTRA_DAMAGE * power));
			target.push(look.scale(MIN_KNOCKBACK + EXTRA_KNOCKBACK * power).add(0, LIFT, 0));
			target.hurtMarked = true;
		}

		double length = eye.distanceTo(end);
		for (double distance = TRAIL_START; distance <= length; distance += TRAIL_SPACING) {
			Vec3 at = eye.add(look.scale(distance));
			// Count 0 makes the particle fly along (x, y, z) offset at the given speed.
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, look.x, look.y, look.z, 0.3);
		}
		level.sendParticles(ParticleTypes.GUST, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		Smash.sendLightning(level, eye.add(look.scale(TRAIL_START)), end, power * 0.5);

		level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.SMASH_BLAST, SoundSource.PLAYERS, 0.6f, 1.8f);
		player.swing(InteractionHand.MAIN_HAND, true);
	}
}
