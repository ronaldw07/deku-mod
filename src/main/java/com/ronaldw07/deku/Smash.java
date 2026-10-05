package com.ronaldw07.deku;

import com.ronaldw07.deku.network.ExplosionFxPayload.Style;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the Smash punch: a lightning-charged blast of wind that locks onto
 * whatever is nearest your crosshair, then hits and launches everything around it.
 */
public final class Smash {
	// Each stat scales linearly from its MIN at 1% to its MAX at 100%, then the FULL_POWER
	// extras kick in steeply near the top: barely noticeable at 50%, everything at 100%.
	private static final double MIN_RANGE = 3.0;
	private static final double MAX_RANGE = 12.0;
	private static final int LONGEST_RANGE = 150; // blocks at 100% power, unless the player asks for less
	private static final double FULL_POWER_EXTRA_KNOCKBACK = 8.0;
	private static final double FULL_POWER_CURVE = 8.0;
	private static final float MIN_DAMAGE = 3.0f;
	private static final float MAX_DAMAGE = 30.0f;
	private static final float FULL_POWER_EXTRA_DAMAGE = 20.0f;
	private static final double MIN_KNOCKBACK = 0.75;
	private static final double MAX_KNOCKBACK = 6.0;
	private static final double MAX_LIFT = 0.6;
	// The blast widens with power: about 37 degrees either side of the aim at 1%, 53 at 100%.
	private static final double NARROW_CONE_COS = 0.8;
	private static final double WIDE_CONE_COS = 0.6;
	// Wind: more cloud puffs further off the aim line as power rises.
	private static final int MAX_EXTRA_PUFFS = 4;
	private static final double WIND_SPREAD = 0.5; // sideways reach per block of distance, at 100%
	// Lightning bursts dotted along a long punch's path.
	private static final double PATH_BURST_SPACING = 8.0;
	private static final int PATH_BURST_BOLTS = 7;
	private static final double PATH_BURST_LENGTH = 6.0;
	private static final double LOCK_ON_COS = 0.6; // locks onto targets up to about 53 degrees off the crosshair
	private static final double POINT_BLANK = 1.0;
	// Effects start a little way out so they don't cover the screen in first person.
	private static final double LIGHTNING_START = 1.5;
	private static final double TRAIL_START = 2.0;
	private static final double EXPLOSION_DISTANCE = 3.5;
	private static final double TRAIL_SPACING = 0.75;
	private static final int GUST_EVERY = 3;
	private static final double HEAVY_THRESHOLD = 0.5; // adds an explosion and thunder
	private static final double FX_VIEW_DISTANCE = 192;
	// Every punch carves a tunnel along its path, rolling outward a few blocks a tick: every
	// block inside is cleared, stone included. It widens with power, and ends in an impact
	// crater; a full-power punch also sets off a huge blast there.
	private static final double TUNNEL_START = 2.0;
	private static final double TUNNEL_SPACING = 2.0;
	private static final float MIN_TUNNEL_RADIUS = 1.5f;
	private static final float MAX_TUNNEL_RADIUS = 4.5f;
	private static final float FULL_POWER_EXTRA_TUNNEL_RADIUS = 2.5f; // 7 blocks at 100%
	private static final int TUNNEL_DEBRIS = 6;
	private static final int BOOM_EVERY_STEPS = 3;
	private static final double TUNNEL_BLOCKS_PER_TICK = 6.0;
	private static final double IMPACT_CRATER_SCALE = 1.6; // crater radius, relative to the tunnel's
	private static final float IMPACT_RADIUS = 12.0f;
	private static final int IMPACT_DEBRIS = 120;
	private static final int IMPACT_BOLTS = 20;
	private static final double IMPACT_BOLT_LENGTH = 10.0;
	private static final int HIT_BOLTS = 6;
	private static final double HIT_LAUNCH_BOOST = 1.5;
	private static final int MAX_RINGS = 3;
	private static final double RING_ON_TERRAIN_POWER = 0.5;
	private static final float MIN_RING_RADIUS = 6.0f;
	private static final float MAX_RING_RADIUS = 18.0f;
	private static final float FULL_POWER_EXTRA_RING_RADIUS = 8.0f;
	private static final double RING_PUSH = 2.5;
	private static final double RING_LIFT = 1.0;
	private static final double HIT_BOLT_LENGTH = 4.0;

	private Smash() {
	}

	/** @param maxRange the longest reach this player wants a full-power punch to have, in blocks */
	public static void perform(ServerPlayer player, int percent, int maxRange) {
		double power = Mth.clamp(percent, 1, 100) / 100.0;
		double fullPower = Math.pow(power, FULL_POWER_CURVE);
		double fullPowerExtraRange = Mth.clamp(maxRange, MAX_RANGE, LONGEST_RANGE) - MAX_RANGE;
		double range = Mth.lerp(power, MIN_RANGE, MAX_RANGE) + fullPowerExtraRange * fullPower;
		float damage = (float) (Mth.lerp(power, MIN_DAMAGE, MAX_DAMAGE) + FULL_POWER_EXTRA_DAMAGE * Math.pow(power, FULL_POWER_CURVE));
		double coneCos = Mth.lerp(power, NARROW_CONE_COS, WIDE_CONE_COS);
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();

		Optional<LivingEntity> locked = lockOn(level, player, eye, player.getLookAngle(), range);
		Vec3 aim = locked.map(target -> directionTo(eye, target)).orElse(player.getLookAngle());
		double knockback = Mth.lerp(power, MIN_KNOCKBACK, MAX_KNOCKBACK) + FULL_POWER_EXTRA_KNOCKBACK * fullPower;
		Vec3 push = aim.scale(knockback).add(0, MAX_LIFT * power, 0);

		int rings = 0;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range),
				target -> target != player && target.isAlive() && inCone(eye, aim, target, range, coneCos))) {
			target.hurtServer(level, player.damageSources().playerAttack(player), damage);
			target.push(push.scale(HIT_LAUNCH_BOOST));
			target.hurtMarked = true;
			lightningBurst(level, target.getBoundingBox().getCenter(), HIT_BOLTS, HIT_BOLT_LENGTH, power);
			if (rings < MAX_RINGS) {
				rings++;
				shockwave(level, player, target.getBoundingBox().getCenter(), power, fullPower);
			}
		}
		if (rings == 0 && power >= RING_ON_TERRAIN_POWER) {
			HitResult terrain = Aim.trace(player, range);
			if (terrain.getType() == HitResult.Type.BLOCK) {
				shockwave(level, player, terrain.getLocation(), power, fullPower);
			}
		}

		Vec3 end = locked.map(target -> target.getBoundingBox().getCenter()).orElse(eye.add(aim.scale(range)));
		float tunnelRadius = (float) (Mth.lerp(power, MIN_TUNNEL_RADIUS, MAX_TUNNEL_RADIUS) + FULL_POWER_EXTRA_TUNNEL_RADIUS * fullPower);
		tunnel(player, eye, aim, eye.distanceTo(end), tunnelRadius, percent >= 100);

		sendLightning(level, eye.add(aim.scale(LIGHTNING_START)), end, power, true);
		showBlast(level, player, eye, aim, range, power);
	}

	/** A huge red ring blasting out from where the punch landed, hurling everything near it away. */
	private static void shockwave(ServerLevel level, ServerPlayer player, Vec3 center, double power, double fullPower) {
		float radius = (float) (Mth.lerp(power, MIN_RING_RADIUS, MAX_RING_RADIUS) + FULL_POWER_EXTRA_RING_RADIUS * fullPower);
		BlastFx.send(level, center, radius, Style.SMASH_HIT, center, FX_VIEW_DISTANCE);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
				near -> near != player && near.isAlive() && near.position().distanceTo(center) <= radius)) {
			Vec3 offset = near.position().subtract(center);
			double strength = 1 - offset.length() / radius;
			Vec3 away = offset.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : offset.normalize();
			near.push(away.scale(RING_PUSH * (0.4 + power) * strength).add(0, RING_LIFT * strength, 0));
			near.hurtMarked = true;
		}
		level.playSound(null, center.x, center.y, center.z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, 2.0f + (float) power * 3, 0.9f);
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

	private static boolean inCone(Vec3 eye, Vec3 aim, LivingEntity target, double range, double coneCos) {
		Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
		double distance = toTarget.length();
		return distance <= range && (distance < POINT_BLANK || toTarget.normalize().dot(aim) >= coneCos);
	}

	/** Carves a tunnel from the fist to the end of the punch's reach, a few blocks a tick, then an impact crater. */
	private static void tunnel(ServerPlayer player, Vec3 eye, Vec3 aim, double length, float radius, boolean fullPower) {
		MinecraftServer server = player.level().getServer();
		int step = 0;
		for (double distance = TUNNEL_START; distance <= length; distance += TUNNEL_SPACING, step++) {
			Vec3 center = eye.add(aim.scale(distance));
			// A real explosion every few steps throws debris and booms; carving does the clearing.
			boolean boom = step % BOOM_EVERY_STEPS == 0;
			Blasts.later(server, (int) (distance / TUNNEL_BLOCKS_PER_TICK), () -> {
				if (boom) {
					Blasts.blast(player, center, radius, Blasts.TERRAIN_ONLY, TUNNEL_DEBRIS);
				}
				Blasts.carve(player.level(), center, radius, 0);
			});
		}

		Vec3 impact = eye.add(aim.scale(length));
		Blasts.later(server, (int) (length / TUNNEL_BLOCKS_PER_TICK), () -> {
			if (fullPower) {
				Blasts.blast(player, impact, IMPACT_RADIUS, Blasts.sparing(player), IMPACT_DEBRIS);
				lightningBurst(player.level(), impact, IMPACT_BOLTS, IMPACT_BOLT_LENGTH, 1.0);
			}
			Blasts.carve(player.level(), impact, (float) (radius * IMPACT_CRATER_SCALE), 0);
		});
	}

	/** Green bolts crackling out in every direction from a point the punch hit. */
	static void lightningBurst(ServerLevel level, Vec3 center, int bolts, double length, double power) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < bolts; i++) {
			Vec3 direction = new Vec3(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1).normalize();
			sendLightning(level, center, center.add(direction.scale(length * (0.5 + random.nextDouble() * 0.5))), power);
		}
	}

	static void sendLightning(ServerLevel level, Vec3 from, Vec3 to, double power) {
		sendLightning(level, from, to, power, false);
	}

	/** Heavy lightning is for the punch itself; see SmashFxPayload. */
	static void sendLightning(ServerLevel level, Vec3 from, Vec3 to, double power, boolean heavy) {
		SmashFxPayload fx = new SmashFxPayload(from, to, (float) power, heavy);
		for (ServerPlayer viewer : PlayerLookup.around(level, from, FX_VIEW_DISTANCE)) {
			if (ServerPlayNetworking.canSend(viewer, SmashFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	private static void showBlast(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 aim, double range, double power) {
		RandomSource random = level.getRandom();
		int puffs = 1 + (int) Math.round(MAX_EXTRA_PUFFS * power);
		// Spread puffs out along very long punches so the packet count stays sane.
		double spacing = TRAIL_SPACING + range / 100;
		int step = 0;
		for (double distance = TRAIL_START; distance <= range; distance += spacing, step++) {
			Vec3 onAim = eye.add(aim.scale(distance));
			for (int puff = 0; puff < puffs; puff++) {
				Vec3 point = onAim.add(new Vec3(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1)
					.scale(puff == 0 ? 0 : distance * WIND_SPREAD * power));
				// Count 0 makes the cloud fly along (x, y, z) offset at the given speed.
				level.sendParticles(ParticleTypes.CLOUD, point.x, point.y, point.z, 0, aim.x, aim.y, aim.z, 0.2 + 0.8 * power);
			}
			if (step % GUST_EVERY == 0) {
				level.sendParticles(ParticleTypes.GUST, onAim.x, onAim.y, onAim.z, 1, 0, 0, 0, 0);
			}
		}

		for (double distance = PATH_BURST_SPACING; distance < range; distance += PATH_BURST_SPACING) {
			lightningBurst(level, eye.add(aim.scale(distance)), PATH_BURST_BOLTS, PATH_BURST_LENGTH, power);
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
