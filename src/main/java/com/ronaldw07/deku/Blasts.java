package com.ronaldw07.deku;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Terrain-wrecking blasts shared by the quirks: a real explosion, with some of the blocks
 * thrown into the air as flying debris. Huge attacks are split into many blasts spread
 * over several ticks, which both looks like a wave rolling outward and keeps the game from
 * freezing on one enormous explosion.
 */
public final class Blasts {
	/** Breaks blocks only: nobody is hurt or pushed. */
	public static final ExplosionDamageCalculator TERRAIN_ONLY = new ExplosionDamageCalculator() {
		@Override
		public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
			return false;
		}

		@Override
		public float getKnockbackMultiplier(Entity entity) {
			return 0;
		}
	};

	private static final double DEBRIS_MIN_SPEED = 0.6;
	private static final double DEBRIS_EXTRA_SPEED = 0.8;
	private static final int DEBRIS_ATTEMPTS_PER_BLOCK = 4;

	private record Scheduled(int runAt, Runnable action) {
	}

	private static List<Scheduled> queue = List.of();

	private Blasts() {
	}

	/** Hurts and pushes everything except the player who set it off. */
	public static ExplosionDamageCalculator sparing(Entity owner) {
		return new ExplosionDamageCalculator() {
			@Override
			public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
				return entity != owner;
			}

			@Override
			public float getKnockbackMultiplier(Entity entity) {
				return entity == owner ? 0 : 1;
			}
		};
	}

	/** Throws up to debris blocks from around the center into the air, then blows the rest apart. */
	public static void blast(ServerPlayer owner, Vec3 center, float radius, ExplosionDamageCalculator rules, int debris) {
		ServerLevel level = owner.level();
		throwDebris(level, center, radius, debris);
		// BLOCK interaction drops only some of what it breaks, so big blasts don't bury the world in items.
		level.explode(owner, owner.damageSources().explosion(owner, owner), rules, center, radius, false, Level.ExplosionInteraction.BLOCK);
	}

	private static void throwDebris(ServerLevel level, Vec3 center, float radius, int count) {
		RandomSource random = level.getRandom();
		int thrown = 0;
		for (int attempt = 0; attempt < count * DEBRIS_ATTEMPTS_PER_BLOCK && thrown < count; attempt++) {
			Vec3 offset = randomDirection(random).scale(radius * 0.8 * Math.cbrt(random.nextDouble()));
			BlockPos pos = BlockPos.containing(center.add(offset));
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
				continue;
			}

			FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
			block.disableDrop();
			Vec3 outward = Vec3.atCenterOf(pos).subtract(center).normalize();
			double speed = DEBRIS_MIN_SPEED + random.nextDouble() * DEBRIS_EXTRA_SPEED;
			block.setDeltaMovement(outward.scale(speed).add(0, DEBRIS_MIN_SPEED + random.nextDouble() * DEBRIS_EXTRA_SPEED, 0));
			block.hurtMarked = true;
			thrown++;
		}
	}

	private static Vec3 randomDirection(RandomSource random) {
		return new Vec3(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1).normalize();
	}

	/** Runs the action after the given number of server ticks. */
	public static void later(MinecraftServer server, int ticks, Runnable action) {
		queue = Stream.concat(queue.stream(), Stream.of(new Scheduled(server.getTickCount() + ticks, action))).toList();
	}

	public static void tick(MinecraftServer server) {
		if (queue.isEmpty()) {
			return;
		}

		int now = server.getTickCount();
		List<Scheduled> due = queue.stream().filter(entry -> entry.runAt() <= now).toList();
		queue = queue.stream().filter(entry -> entry.runAt() > now).toList();
		due.forEach(entry -> entry.action().run());
	}
}
