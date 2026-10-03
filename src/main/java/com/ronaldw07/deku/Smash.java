package com.ronaldw07.deku;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server side of the Smash punch: a blast of wind that hits and launches everything in front of you. */
public final class Smash {
	// Each stat scales linearly from its MIN at 1% to its MAX at 100%.
	private static final double MIN_RANGE = 3.0;
	private static final double MAX_RANGE = 12.0;
	private static final float MIN_DAMAGE = 2.0f;
	private static final float MAX_DAMAGE = 20.0f;
	private static final double MIN_KNOCKBACK = 0.5;
	private static final double MAX_KNOCKBACK = 4.0;
	private static final double MAX_LIFT = 0.6;
	private static final double CONE_COS = 0.8; // about 37 degrees either side of where you look
	private static final double POINT_BLANK = 1.0;
	// Effects start a little way out so they don't cover the screen in first person.
	private static final double TRAIL_START = 2.0;
	private static final double EXPLOSION_DISTANCE = 3.5;
	private static final double TRAIL_SPACING = 0.75;
	private static final int GUST_EVERY = 3;
	private static final double EXPLOSION_THRESHOLD = 0.5;

	private Smash() {
	}

	public static void perform(ServerPlayer player, int percent) {
		double power = Mth.clamp(percent, 1, 100) / 100.0;
		double range = Mth.lerp(power, MIN_RANGE, MAX_RANGE);
		float damage = (float) Mth.lerp(power, MIN_DAMAGE, MAX_DAMAGE);
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 push = look.scale(Mth.lerp(power, MIN_KNOCKBACK, MAX_KNOCKBACK)).add(0, MAX_LIFT * power, 0);

		AABB reach = new AABB(eye, eye).inflate(range);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, reach,
				target -> target != player && target.isAlive() && inCone(eye, look, target, range))) {
			target.hurtServer(level, player.damageSources().playerAttack(player), damage);
			target.push(push);
			target.hurtMarked = true;
		}

		showBlast(level, player, eye, look, range, power);
	}

	private static boolean inCone(Vec3 eye, Vec3 look, LivingEntity target, double range) {
		Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
		double distance = toTarget.length();
		return distance <= range && (distance < POINT_BLANK || toTarget.normalize().dot(look) >= CONE_COS);
	}

	private static void showBlast(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 look, double range, double power) {
		int step = 0;
		for (double distance = TRAIL_START; distance <= range; distance += TRAIL_SPACING, step++) {
			Vec3 point = eye.add(look.scale(distance));
			// Count 0 makes the cloud fly along (x, y, z) offset at the given speed.
			level.sendParticles(ParticleTypes.CLOUD, point.x, point.y, point.z, 0, look.x, look.y, look.z, 0.2 + 0.6 * power);
			if (step % GUST_EVERY == 0) {
				level.sendParticles(ParticleTypes.GUST, point.x, point.y, point.z, 1, 0, 0, 0, 0);
			}
		}

		if (power >= EXPLOSION_THRESHOLD) {
			Vec3 burst = eye.add(look.scale(EXPLOSION_DISTANCE));
			level.sendParticles(ParticleTypes.EXPLOSION, burst.x, burst.y, burst.z, 1, 0, 0, 0, 0);
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST,
			SoundSource.PLAYERS, (float) (0.5 + power), (float) (1.2 - 0.4 * power));
		player.swing(InteractionHand.MAIN_HAND, true);
	}
}
