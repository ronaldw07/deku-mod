package com.ronaldw07.deku;

import com.ronaldw07.deku.network.SmashFxPayload;
import java.util.Comparator;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the Smash punch: a lightning-charged blast of wind that locks onto
 * whatever is nearest your crosshair, then hits and launches everything around it.
 */
public final class Smash {
	// Each stat scales linearly from its MIN at 1% to its MAX at 100%.
	private static final double MIN_RANGE = 3.0;
	private static final double MAX_RANGE = 12.0;
	private static final float MIN_DAMAGE = 2.0f;
	private static final float MAX_DAMAGE = 20.0f;
	private static final double MIN_KNOCKBACK = 0.5;
	private static final double MAX_KNOCKBACK = 4.0;
	private static final double MAX_LIFT = 0.6;
	private static final double CONE_COS = 0.8; // about 37 degrees either side of the aim
	private static final double LOCK_ON_COS = 0.6; // locks onto targets up to about 53 degrees off the crosshair
	private static final double POINT_BLANK = 1.0;
	// Effects start a little way out so they don't cover the screen in first person.
	private static final double LIGHTNING_START = 1.5;
	private static final double TRAIL_START = 2.0;
	private static final double EXPLOSION_DISTANCE = 3.5;
	private static final double TRAIL_SPACING = 0.75;
	private static final int GUST_EVERY = 3;
	private static final double HEAVY_THRESHOLD = 0.5; // adds an explosion and thunder
	private static final double FX_VIEW_DISTANCE = 64;
	// A full-power punch blasts a tunnel through terrain along its path.
	private static final int TUNNEL_PERCENT = 100;
	private static final double TUNNEL_START = 2.0;
	private static final double TUNNEL_SPACING = 2.5;
	private static final float TUNNEL_RADIUS = 2.5f;
	private static final ExplosionDamageCalculator TERRAIN_ONLY = new ExplosionDamageCalculator() {
		@Override
		public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
			return false;
		}

		@Override
		public float getKnockbackMultiplier(Entity entity) {
			return 0;
		}
	};

	private Smash() {
	}

	public static void perform(ServerPlayer player, int percent) {
		double power = Mth.clamp(percent, 1, 100) / 100.0;
		double range = Mth.lerp(power, MIN_RANGE, MAX_RANGE);
		float damage = (float) Mth.lerp(power, MIN_DAMAGE, MAX_DAMAGE);
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();

		Optional<LivingEntity> locked = lockOn(level, player, eye, player.getLookAngle(), range);
		Vec3 aim = locked.map(target -> directionTo(eye, target)).orElse(player.getLookAngle());
		Vec3 push = aim.scale(Mth.lerp(power, MIN_KNOCKBACK, MAX_KNOCKBACK)).add(0, MAX_LIFT * power, 0);

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range),
				target -> target != player && target.isAlive() && inCone(eye, aim, target, range))) {
			target.hurtServer(level, player.damageSources().playerAttack(player), damage);
			target.push(push);
			target.hurtMarked = true;
		}

		if (percent >= TUNNEL_PERCENT) {
			for (double distance = TUNNEL_START; distance <= range; distance += TUNNEL_SPACING) {
				level.explode(player, player.damageSources().explosion(player, player), TERRAIN_ONLY, eye.add(aim.scale(distance)),
					TUNNEL_RADIUS, false, Level.ExplosionInteraction.TNT);
			}
		}

		Vec3 end = locked.map(target -> target.getBoundingBox().getCenter()).orElse(eye.add(aim.scale(range)));
		sendLightning(level, eye.add(aim.scale(LIGHTNING_START)), end, power);
		showBlast(level, player, eye, aim, range, power);
	}

	/** The living thing in view nearest the crosshair, if it's close enough to the crosshair to lock onto. */
	private static Optional<LivingEntity> lockOn(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 look, double range) {
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range),
				target -> target != player && target.isAlive() && player.hasLineOfSight(target)
					&& target.getBoundingBox().getCenter().distanceTo(eye) <= range)
			.stream()
			.max(Comparator.comparingDouble(target -> directionTo(eye, target).dot(look)))
			.filter(target -> directionTo(eye, target).dot(look) >= LOCK_ON_COS);
	}

	private static Vec3 directionTo(Vec3 eye, LivingEntity target) {
		return target.getBoundingBox().getCenter().subtract(eye).normalize();
	}

	private static boolean inCone(Vec3 eye, Vec3 aim, LivingEntity target, double range) {
		Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
		double distance = toTarget.length();
		return distance <= range && (distance < POINT_BLANK || toTarget.normalize().dot(aim) >= CONE_COS);
	}

	private static void sendLightning(ServerLevel level, Vec3 from, Vec3 to, double power) {
		SmashFxPayload fx = new SmashFxPayload(from, to, (float) power);
		for (ServerPlayer viewer : PlayerLookup.around(level, from, FX_VIEW_DISTANCE)) {
			if (ServerPlayNetworking.canSend(viewer, SmashFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	private static void showBlast(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 aim, double range, double power) {
		int step = 0;
		for (double distance = TRAIL_START; distance <= range; distance += TRAIL_SPACING, step++) {
			Vec3 point = eye.add(aim.scale(distance));
			// Count 0 makes the cloud fly along (x, y, z) offset at the given speed.
			level.sendParticles(ParticleTypes.CLOUD, point.x, point.y, point.z, 0, aim.x, aim.y, aim.z, 0.2 + 0.6 * power);
			if (step % GUST_EVERY == 0) {
				level.sendParticles(ParticleTypes.GUST, point.x, point.y, point.z, 1, 0, 0, 0, 0);
			}
		}

		if (power >= HEAVY_THRESHOLD) {
			Vec3 burst = eye.add(aim.scale(EXPLOSION_DISTANCE));
			level.sendParticles(ParticleTypes.EXPLOSION, burst.x, burst.y, burst.z, 1, 0, 0, 0, 0);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.SMASH_THUNDER,
				SoundSource.PLAYERS, (float) power, 1.0f);
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.SMASH_BLAST,
			SoundSource.PLAYERS, (float) (0.5 + power), (float) (1.2 - 0.4 * power));
		player.swing(InteractionHand.MAIN_HAND, true);
	}
}
