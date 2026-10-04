package com.ronaldw07.deku;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of Smokescreen: a burst of purple smoke that spreads out in every direction,
 * hangs in the air, and makes mobs lose track of anyone inside it. While the key is held the
 * cloud keeps growing; letting go starts its countdown.
 */
public final class Smokescreen {
	private static final int DURATION_TICKS = 240;
	private static final double RADIUS = 9.0;
	private static final double MAX_RADIUS = 30.0;
	private static final double GROWTH_PER_TICK = 0.2;
	private static final double HEIGHT = 6.0;
	private static final double HEIGHT_PER_EXTRA_RADIUS = 0.3;
	// The opening burst flies outward and slows to a stop at about RADIUS (see the particle's drag).
	private static final int BURST_PARTICLES = 550;
	private static final double MIN_BURST_SPEED = 0.35;
	private static final double MAX_BURST_SPEED = 0.75;
	// Then a few puffs at a time keep it thick until it clears.
	private static final int REFILL_INTERVAL = 4;
	private static final int REFILL_PARTICLES = 24;
	private static final int MAX_REFILL_PARTICLES = 120;
	private static final int REFILL_STOP_BEFORE_END = 60;
	// A growing cloud's edge rolls outward as a ring of puffs.
	private static final int EDGE_INTERVAL = 2;
	private static final int EDGE_PUFFS = 16;
	private static final double EDGE_SPEED = 0.15;
	private static final int GROW_SOUND_INTERVAL = 20;
	private static final float GROW_SOUND_VOLUME = 0.5f;
	// Mobs this far outside the cloud still lose a target that's hiding inside it.
	private static final double TARGET_SEARCH_MARGIN = 24.0;

	/** A cloud; while it is growing it has no end, and letting go sets one. */
	private record Cloud(UUID owner, ResourceKey<Level> dimension, Vec3 center, double radius, long endTick, boolean growing) {
		double height() {
			return HEIGHT + (radius - RADIUS) * HEIGHT_PER_EXTRA_RADIUS;
		}

		boolean contains(Vec3 point) {
			double dx = point.x - center.x;
			double dz = point.z - center.z;
			return dx * dx + dz * dz <= radius * radius && point.y >= center.y - 1 && point.y <= center.y + height() + 1;
		}

		Cloud grown() {
			return new Cloud(owner, dimension, center, Math.min(MAX_RADIUS, radius + GROWTH_PER_TICK), endTick, true);
		}

		Cloud released(long now) {
			return new Cloud(owner, dimension, center, radius, now + DURATION_TICKS, false);
		}
	}

	private static List<Cloud> clouds = List.of();

	private Smokescreen() {
	}

	/** Puts a cloud out where the player stands, and keeps it growing until they let go. */
	public static void deploy(ServerPlayer player) {
		if (clouds.stream().anyMatch(cloud -> cloud.growing() && cloud.owner().equals(player.getUUID()))) {
			return;
		}
		ServerLevel level = player.level();
		Cloud cloud = new Cloud(player.getUUID(), level.dimension(), player.position(), RADIUS, Long.MAX_VALUE, true);
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

	/** The player let go: the cloud stops growing and clears after its usual time. */
	public static void release(ServerPlayer player) {
		long now = player.level().getGameTime();
		clouds = clouds.stream()
			.map(cloud -> cloud.growing() && cloud.owner().equals(player.getUUID()) ? cloud.released(now) : cloud)
			.toList();
	}

	public static void tick(MinecraftServer server) {
		if (clouds.isEmpty()) {
			return;
		}

		clouds = clouds.stream().filter(cloud -> {
			ServerLevel level = server.getLevel(cloud.dimension());
			return level != null && level.getGameTime() < cloud.endTick();
		}).map(cloud -> keepGrowing(server, cloud)).toList();

		for (Cloud cloud : clouds) {
			ServerLevel level = server.getLevel(cloud.dimension());
			long now = level.getGameTime();
			if (cloud.growing()) {
				growEdge(level, cloud, now);
			}
			if (now % REFILL_INTERVAL == 0 && (cloud.growing() || cloud.endTick() - now > REFILL_STOP_BEFORE_END)) {
				refill(level, cloud);
			}
			blindMobs(level, cloud);
		}
	}

	/** A growing cloud gets bigger, unless its owner has gone, died or put the quirk away. */
	private static Cloud keepGrowing(MinecraftServer server, Cloud cloud) {
		if (!cloud.growing()) {
			return cloud;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(cloud.owner());
		if (owner == null || owner.isDeadOrDying() || !DekuItems.isHolding(owner, DekuItems.ONE_FOR_ALL)) {
			return cloud.released(server.getLevel(cloud.dimension()).getGameTime());
		}
		return cloud.grown();
	}

	/** Gone for good, so no cloud is left growing with nobody to stop it. */
	public static void forget(ServerPlayer player) {
		release(player);
	}

	/** A ring of puffs at the cloud's edge, rolling out as it grows. */
	private static void growEdge(ServerLevel level, Cloud cloud, long now) {
		if (now % EDGE_INTERVAL == 0 && cloud.radius() < MAX_RADIUS) {
			RandomSource random = level.getRandom();
			for (int i = 0; i < EDGE_PUFFS; i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
				level.sendParticles(DekuParticles.PURPLE_SMOKE, cloud.center.x + out.x * cloud.radius(),
					cloud.center.y + random.nextDouble() * cloud.height(), cloud.center.z + out.z * cloud.radius(),
					0, out.x, 0, out.z, EDGE_SPEED);
			}
		}
		if (now % GROW_SOUND_INTERVAL == 0) {
			level.playSound(null, cloud.center.x, cloud.center.y, cloud.center.z, DekuSounds.SMOKESCREEN, SoundSource.PLAYERS,
				GROW_SOUND_VOLUME, 1.0f);
		}
	}

	private static void refill(ServerLevel level, Cloud cloud) {
		RandomSource random = level.getRandom();
		double areaGrowth = Mth.square(cloud.radius() / RADIUS);
		int puffs = Math.min(MAX_REFILL_PARTICLES, (int) (REFILL_PARTICLES * areaGrowth));
		for (int i = 0; i < puffs; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Math.sqrt(random.nextDouble()) * cloud.radius();
			level.sendParticles(DekuParticles.PURPLE_SMOKE,
				cloud.center.x + Math.cos(angle) * distance, cloud.center.y + random.nextDouble() * cloud.height(),
				cloud.center.z + Math.sin(angle) * distance, 1, 0, 0, 0, 0);
		}
	}

	/** Mobs inside the smoke can't see out, and mobs outside can't see in. */
	private static void blindMobs(ServerLevel level, Cloud cloud) {
		AABB area = new AABB(cloud.center, cloud.center).inflate(cloud.radius() + TARGET_SEARCH_MARGIN);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, area, mob -> mob.getTarget() != null)) {
			LivingEntity target = mob.getTarget();
			if (cloud.contains(mob.position()) || cloud.contains(target.position())) {
				mob.setTarget(null);
			}
		}
	}

	/** The biggest radius among clouds right now; 0 if none. For tests. */
	public static double largestRadius() {
		return clouds.stream().mapToDouble(Cloud::radius).max().orElse(0);
	}
}
