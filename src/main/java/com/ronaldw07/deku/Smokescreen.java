package com.ronaldw07.deku;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of Smokescreen: a burst of purple smoke that spreads out in every
 * direction, hangs in the air, and makes mobs lose track of anyone inside it.
 */
public final class Smokescreen {
	private static final int DURATION_TICKS = 240;
	private static final double RADIUS = 6.0;
	private static final double HEIGHT = 4.0;
	// The opening burst flies outward and slows to a stop at about RADIUS (see the particle's drag).
	private static final int BURST_PARTICLES = 400;
	private static final double MIN_BURST_SPEED = 0.25;
	private static final double MAX_BURST_SPEED = 0.5;
	// Then a few puffs at a time keep it thick until it clears.
	private static final int REFILL_INTERVAL = 4;
	private static final int REFILL_PARTICLES = 16;
	private static final int REFILL_STOP_BEFORE_END = 60;
	// Mobs this far outside the cloud still lose a target that's hiding inside it.
	private static final double TARGET_SEARCH_MARGIN = 24.0;

	private record Cloud(ResourceKey<Level> dimension, Vec3 center, long startTick) {
		boolean contains(Vec3 point) {
			double dx = point.x - center.x;
			double dz = point.z - center.z;
			return dx * dx + dz * dz <= RADIUS * RADIUS && point.y >= center.y - 1 && point.y <= center.y + HEIGHT + 1;
		}
	}

	private static List<Cloud> clouds = List.of();

	private Smokescreen() {
	}

	public static void deploy(ServerPlayer player) {
		ServerLevel level = player.level();
		Cloud cloud = new Cloud(level.dimension(), player.position(), level.getGameTime());
		clouds = Stream.concat(clouds.stream(), Stream.of(cloud)).toList();

		RandomSource random = level.getRandom();
		for (int i = 0; i < BURST_PARTICLES; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double speed = MIN_BURST_SPEED + random.nextDouble() * (MAX_BURST_SPEED - MIN_BURST_SPEED);
			Vec3 direction = new Vec3(Math.cos(angle), (random.nextDouble() - 0.3) * 0.4, Math.sin(angle));
			// Count 0 makes the puff fly along (x, y, z) offset at the given speed.
			level.sendParticles(DekuParticles.PURPLE_SMOKE, cloud.center.x, cloud.center.y + random.nextDouble() * HEIGHT,
				cloud.center.z, 0, direction.x, direction.y, direction.z, speed);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.SMOKESCREEN, SoundSource.PLAYERS, 1.0f, 1.0f);
	}

	public static void tick(MinecraftServer server) {
		if (clouds.isEmpty()) {
			return;
		}

		clouds = clouds.stream().filter(cloud -> {
			ServerLevel level = server.getLevel(cloud.dimension());
			return level != null && level.getGameTime() - cloud.startTick() < DURATION_TICKS;
		}).toList();

		for (Cloud cloud : clouds) {
			ServerLevel level = server.getLevel(cloud.dimension());
			long age = level.getGameTime() - cloud.startTick();
			if (age % REFILL_INTERVAL == 0 && age < DURATION_TICKS - REFILL_STOP_BEFORE_END) {
				refill(level, cloud);
			}
			blindMobs(level, cloud);
		}
	}

	private static void refill(ServerLevel level, Cloud cloud) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < REFILL_PARTICLES; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Math.sqrt(random.nextDouble()) * RADIUS;
			level.sendParticles(DekuParticles.PURPLE_SMOKE,
				cloud.center.x + Math.cos(angle) * distance, cloud.center.y + random.nextDouble() * HEIGHT,
				cloud.center.z + Math.sin(angle) * distance, 1, 0, 0, 0, 0);
		}
	}

	/** Mobs inside the smoke can't see out, and mobs outside can't see in. */
	private static void blindMobs(ServerLevel level, Cloud cloud) {
		AABB area = new AABB(cloud.center, cloud.center).inflate(RADIUS + TARGET_SEARCH_MARGIN);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, area, mob -> mob.getTarget() != null)) {
			LivingEntity target = mob.getTarget();
			if (cloud.contains(mob.position()) || cloud.contains(target.position())) {
				mob.setTarget(null);
			}
		}
	}
}
