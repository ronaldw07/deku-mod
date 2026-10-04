package com.ronaldw07.deku;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
	private static final int FIRE_DELAY_TICKS = 4;
	private static final int CARVE_AT_ONCE_RADIUS = 12;
	private static final int CARVE_LAYERS_PER_TICK = 3; // each way from the middle
	private static final double SCORCH_REACH = 2.5; // how far past the crater's edge the burn spreads
	private static final double SCORCH_STONE_CHANCE = 0.55;
	private static final double SCORCH_SOIL_CHANCE = 0.75;
	private static final int MAX_SCORCH_BLOCKS = 6000;

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

	/**
	 * Clears every breakable block inside the sphere, so the crater is as big as the fireball,
	 * and leaves a few fires burning on its floor. Fluids and block entities are left alone.
	 */
	public static void carve(ServerLevel level, Vec3 center, float radius, int fires) {
		int reach = (int) Math.ceil(radius);
		// Huge craters are cleared a few layers a tick, growing up and down from the middle,
		// so the game doesn't freeze clearing them all at once.
		boolean spread = reach > CARVE_AT_ONCE_RADIUS;
		for (int dy = -reach; dy <= reach; dy++) {
			int layer = dy;
			if (spread) {
				later(level.getServer(), Math.abs(dy) / CARVE_LAYERS_PER_TICK, () -> carveLayer(level, center, radius, layer));
			} else {
				carveLayer(level, center, radius, layer);
			}
		}
		// A beat later, so blasts landing right after this one don't snuff the fires out.
		later(level.getServer(), lastLayerTick(radius) + FIRE_DELAY_TICKS, () -> light(level, center, radius, fires));
	}

	/** The tick, counting from the call to carve, on which the last layer of a crater is cleared. */
	private static int lastLayerTick(float radius) {
		int reach = (int) Math.ceil(radius);
		return reach > CARVE_AT_ONCE_RADIUS ? reach / CARVE_LAYERS_PER_TICK : 0;
	}

	/**
	 * Burns the rim of a crater just carved by carve with the same radius: exposed stone turns to
	 * blackstone and grass and dirt to coarse dirt, and a few more fires are left burning. Runs
	 * after the crater is cleared, a few layers a tick, and never touches unloaded chunks.
	 */
	public static void scorch(ServerLevel level, Vec3 center, float radius, int extraFires) {
		int reach = (int) Math.ceil(radius + SCORCH_REACH);
		boolean spread = reach > CARVE_AT_ONCE_RADIUS;
		int start = lastLayerTick(radius) + FIRE_DELAY_TICKS + 1;
		int[] budget = {MAX_SCORCH_BLOCKS};
		for (int dy = -reach; dy <= reach; dy++) {
			int layer = dy;
			int delay = start + (spread ? Math.abs(dy) / CARVE_LAYERS_PER_TICK : 0);
			later(level.getServer(), delay, () -> scorchLayer(level, center, radius, layer, budget));
		}
		int lastTick = start + (spread ? reach / CARVE_LAYERS_PER_TICK : 0);
		later(level.getServer(), lastTick + 1, () -> light(level, center, radius + (float) SCORCH_REACH, extraFires));
	}

	private static void scorchLayer(ServerLevel level, Vec3 center, float radius, int dy, int[] budget) {
		BlockPos middle = BlockPos.containing(center).above(dy);
		int reach = (int) Math.ceil(radius + SCORCH_REACH);
		double innerSqr = Math.max(0, radius - 0.5) * Math.max(0, radius - 0.5);
		double outer = radius + SCORCH_REACH;
		RandomSource random = level.getRandom();
		for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-reach, 0, -reach), middle.offset(reach, 0, reach))) {
			if (budget[0] <= 0) {
				return;
			}
			double distanceSqr = Vec3.atCenterOf(pos).distanceToSqr(center);
			if (distanceSqr < innerSqr || distanceSqr > outer * outer || !level.isLoaded(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			BlockState burnt = burnt(state, random);
			if (burnt != null && exposed(level, pos)) {
				level.setBlock(pos, burnt, Block.UPDATE_CLIENTS);
				budget[0]--;
			}
		}
	}

	/** What a block turns into when scorched, or null if it is left as it is. */
	private static BlockState burnt(BlockState state, RandomSource random) {
		if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
			return null;
		}
		if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.DEEPSLATE)) {
			return random.nextDouble() < SCORCH_STONE_CHANCE ? Blocks.BLACKSTONE.defaultBlockState() : null;
		}
		if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
				|| state.is(Blocks.ROOTED_DIRT)) {
			return random.nextDouble() < SCORCH_SOIL_CHANCE ? Blocks.COARSE_DIRT.defaultBlockState() : null;
		}
		return null;
	}

	private static boolean exposed(ServerLevel level, BlockPos pos) {
		for (Direction side : Direction.values()) {
			if (level.getBlockState(pos.relative(side)).isAir()) {
				return true;
			}
		}
		return false;
	}

	/** Clears one horizontal slice of the sphere, dy blocks above or below its middle. */
	private static void carveLayer(ServerLevel level, Vec3 center, float radius, int dy) {
		BlockPos middle = BlockPos.containing(center).above(dy);
		int reach = (int) Math.ceil(radius);
		double radiusSqr = radius * radius;
		for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-reach, 0, -reach), middle.offset(reach, 0, reach))) {
			if (Vec3.atCenterOf(pos).distanceToSqr(center) > radiusSqr) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0) {
				continue;
			}
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Sets fires on open floor at random spots in the crater. */
	private static void light(ServerLevel level, Vec3 center, float radius, int fires) {
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < fires * DEBRIS_ATTEMPTS_PER_BLOCK && fires > 0; attempt++) {
			Vec3 spot = center.add((random.nextDouble() * 2 - 1) * radius, 0, (random.nextDouble() * 2 - 1) * radius);
			BlockPos pos = BlockPos.containing(spot);
			// Drop from the middle of the crater down to its floor.
			for (int down = 0; down < radius * 2 && level.getBlockState(pos.below()).isAir() && pos.getY() > level.getMinY(); down++) {
				pos = pos.below();
			}
			BlockState fire = BaseFireBlock.getState(level, pos);
			if (level.getBlockState(pos).isAir() && fire.canSurvive(level, pos)) {
				level.setBlockAndUpdate(pos, fire);
				fires--;
			}
		}
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
