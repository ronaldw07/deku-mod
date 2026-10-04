package com.ronaldw07.deku;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the One For All launch. The client moves the player; here the ground they
 * kicked off erupts, a shockwave throws everything nearby outward, and green lightning
 * trails behind them for the first stretch of the flight.
 */
public final class Launch {
	private static final float MIN_LIFTOFF_RADIUS = 4.0f;
	private static final float EXTRA_LIFTOFF_RADIUS = 8.0f;
	private static final int MAX_LIFTOFF_DEBRIS = 80;
	private static final double MIN_SHOCKWAVE_RANGE = 12.0;
	private static final double EXTRA_SHOCKWAVE_RANGE = 28.0;
	private static final double SHOCKWAVE_MAX_PUSH = 3.5;
	private static final float SHOCKWAVE_MAX_DAMAGE = 20.0f;
	private static final int RING_POINTS = 48;
	private static final double RING_SPEED = 1.2;
	private static final int LIFTOFF_BOLTS = 10;
	private static final double LIFTOFF_BOLT_LENGTH = 5.0;
	private static final int TRAIL_TICKS = 40;
	private static final int TRAIL_BOLTS = 3;
	private static final double TRAIL_SPREAD = 0.6;

	private Launch() {
	}

	public static void perform(ServerPlayer player, int charge) {
		double power = Mth.clamp(charge, 1, 100) / 100.0;
		ServerLevel level = player.level();
		Vec3 feet = player.position();

		float radius = MIN_LIFTOFF_RADIUS + EXTRA_LIFTOFF_RADIUS * (float) power;
		Blasts.blast(player, feet, radius, Blasts.sparing(player), (int) (MAX_LIFTOFF_DEBRIS * power));
		Blasts.carve(level, feet, radius, 0);
		shockwave(player, feet, MIN_SHOCKWAVE_RANGE + EXTRA_SHOCKWAVE_RANGE * power);
		Smash.lightningBurst(level, feet.add(0, 1, 0), LIFTOFF_BOLTS, LIFTOFF_BOLT_LENGTH * (0.5 + power), 1.0);
		trail(player, player.getBoundingBox().getCenter(), TRAIL_TICKS);

		level.playSound(null, feet.x, feet.y, feet.z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, (float) (0.5 + power), 1.0f);
		level.playSound(null, feet.x, feet.y, feet.z, DekuSounds.SMASH_BLAST, SoundSource.PLAYERS, (float) (0.5 + power), 0.8f);
	}

	/** Throws everything within range away from where the player kicked off, with a ring of wind racing outward. */
	private static void shockwave(ServerPlayer player, Vec3 center, double range) {
		ServerLevel level = player.level();
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(range),
				entity -> entity != player && entity.isAlive() && entity.position().distanceTo(center) <= range)) {
			Vec3 offset = entity.position().subtract(center);
			double distance = offset.length();
			double strength = 1 - distance / range;
			Vec3 away = distance < 1.0E-3 ? new Vec3(0, 1, 0) : offset.scale(1 / distance);
			entity.push(away.scale(SHOCKWAVE_MAX_PUSH * strength).add(0, strength, 0));
			entity.hurtMarked = true;
			entity.hurtServer(level, player.damageSources().explosion(player, player), SHOCKWAVE_MAX_DAMAGE * (float) strength);
		}

		for (int i = 0; i < RING_POINTS; i++) {
			double angle = Math.PI * 2 * i / RING_POINTS;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			Vec3 at = center.add(out.scale(1.5)).add(0, 0.3, 0);
			// Count 0 makes the particle fly along (x, y, z) offset at the given speed.
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, out.x, 0.05, out.z, RING_SPEED);
			if (i % 4 == 0) {
				level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			}
		}
	}

	/** Every tick for a while, bolts run from where the player was to where they are now. */
	private static void trail(ServerPlayer player, Vec3 from, int ticksLeft) {
		if (ticksLeft == 0 || !player.isAlive()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		Blasts.later(server, 1, () -> {
			Vec3 to = player.getBoundingBox().getCenter();
			for (int i = 0; i < TRAIL_BOLTS; i++) {
				Vec3 jitter = new Vec3(player.getRandom().nextDouble() * 2 - 1, player.getRandom().nextDouble() * 2 - 1,
					player.getRandom().nextDouble() * 2 - 1).scale(TRAIL_SPREAD);
				Smash.sendLightning(player.level(), from.add(jitter), to.add(jitter), 1.0);
			}
			trail(player, to, ticksLeft - 1);
		});
	}
}
