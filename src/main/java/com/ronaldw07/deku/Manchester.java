package com.ronaldw07.deku;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Manchester Smash lands here: the axe kick slams into the ground, leaving a crater and a
 * shockwave that throws everything around it up and away.
 */
public final class Manchester {
	private static final float MIN_CRATER = 3.0f;
	private static final float EXTRA_CRATER = 6.0f;
	private static final int MAX_DEBRIS = 60;
	private static final double MIN_REACH = 6.0;
	private static final double EXTRA_REACH = 18.0;
	private static final float MIN_DAMAGE = 8.0f;
	private static final float EXTRA_DAMAGE = 22.0f;
	private static final double MIN_PUSH = 1.0;
	private static final double EXTRA_PUSH = 2.5;
	private static final double MIN_LIFT = 0.6;
	private static final double EXTRA_LIFT = 0.6;
	private static final int RING_POINTS = 64;
	private static final int MIN_BOLTS = 10;
	private static final int EXTRA_BOLTS = 10;
	private static final double MIN_BOLT_LENGTH = 4.0;
	private static final double EXTRA_BOLT_LENGTH = 6.0;

	private Manchester() {
	}

	public static void slam(ServerPlayer player, int percent) {
		double power = Mth.clamp(percent, 1, 100) / 100.0;
		ServerLevel level = player.level();
		Vec3 feet = player.position();

		float crater = (float) (MIN_CRATER + EXTRA_CRATER * power);
		Blasts.blast(player, feet, crater, Blasts.TERRAIN_ONLY, (int) (MAX_DEBRIS * power));
		Blasts.carve(level, feet, crater, 0);

		double reach = MIN_REACH + EXTRA_REACH * power;
		float damage = (float) (MIN_DAMAGE + EXTRA_DAMAGE * power);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(feet, feet).inflate(reach),
				entity -> entity != player && entity.isAlive() && entity.position().distanceTo(feet) <= reach)) {
			Vec3 offset = entity.position().subtract(feet);
			Vec3 flat = new Vec3(offset.x, 0, offset.z);
			double strength = 1 - offset.length() / reach;
			Vec3 away = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
			entity.hurtServer(level, player.damageSources().playerAttack(player), damage * (float) (0.4 + 0.6 * strength));
			entity.push(away.scale((MIN_PUSH + EXTRA_PUSH * power) * strength).add(0, (MIN_LIFT + EXTRA_LIFT * power) * strength, 0));
			entity.hurtMarked = true;
		}

		for (int i = 0; i < RING_POINTS; i++) {
			double angle = Math.PI * 2 * i / RING_POINTS;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			Vec3 at = feet.add(out.scale(crater)).add(0, 0.5, 0);
			// Count 0 makes the particle fly along (x, y, z) offset at the given speed.
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, out.x, 0.05, out.z, 0.8 + 0.8 * power);
			if (i % 4 == 0) {
				level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, feet.x, feet.y, feet.z, 1, 0, 0, 0, 0);
		Smash.lightningBurst(level, feet.add(0, 0.5, 0), MIN_BOLTS + (int) (EXTRA_BOLTS * power),
			MIN_BOLT_LENGTH + EXTRA_BOLT_LENGTH * power, power);

		level.playSound(null, feet.x, feet.y, feet.z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, (float) (0.6 + power), 0.9f);
		level.playSound(null, feet.x, feet.y, feet.z, DekuSounds.SMASH_BLAST, SoundSource.PLAYERS, (float) (0.6 + power), 0.6f);
	}
}
