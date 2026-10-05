package com.ronaldw07.deku;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Builds Malevolent Shrine out of real blocks under the caster: a wide stone platform, red
 * pillars, a tiered black roof with upturned corners and a gate out in front. Everything it
 * replaces is remembered so the whole thing can be taken down again when the domain closes.
 */
public final class ShrineBuilder {
	private static final int PLATFORM_HALF = 22;
	private static final int FOUNDATION_DEPTH = 8;
	private static final int INNER_HALF = 12;
	private static final int PILLAR_OFFSET = 10;
	private static final int PILLAR_HEIGHT = 14;
	private static final int[] ROOF_HALVES = {15, 13, 10, 7, 4, 2, 0};
	private static final int CORNER_TIP = 4;
	private static final int GATE_DISTANCE = 30;
	private static final int GATE_SPACING = 30;
	private static final int GATES = 4;
	private static final int PATH_LANTERN_EVERY = 8;
	private static final int GATE_HALF_WIDTH = 7;
	private static final int GATE_HEIGHT = 11;
	private static final int LANTERN_EVERY = 5;

	private static final BlockState STONE = Blocks.STONE_BRICKS.defaultBlockState();
	private static final BlockState DARK = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
	private static final BlockState PATH = Blocks.RED_NETHER_BRICKS.defaultBlockState();
	private static final BlockState PILLAR = Blocks.STRIPPED_CRIMSON_STEM.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
	private static final BlockState ROOF = Blocks.BLACKSTONE.defaultBlockState();
	private static final BlockState BEAM = Blocks.CRIMSON_PLANKS.defaultBlockState();
	private static final BlockState LANTERN = Blocks.SHROOMLIGHT.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private final ServerLevel level;
	private final Long2ObjectOpenHashMap<BlockState> replaced = new Long2ObjectOpenHashMap<>();
	private final LongOpenHashSet placed = new LongOpenHashSet();

	public ShrineBuilder(ServerLevel level) {
		this.level = level;
	}

	/** Whether this block is part of the shrine, so slashes should leave it be. */
	public boolean owns(BlockPos pos) {
		return placed.contains(pos.asLong());
	}

	/** Builds the shrine with the caster's feet at the centre, its gate straight ahead along the way they face. */
	public void build(Vec3 feet, Direction facing) {
		BlockPos center = BlockPos.containing(feet);
		int floor = center.getY() - 1;
		// Everything is laid out facing +z, then turned to face the caster's way.
		for (int x = -PLATFORM_HALF; x <= PLATFORM_HALF; x++) {
			for (int z = -PLATFORM_HALF; z <= PLATFORM_HALF; z++) {
				for (int depth = 1; depth <= FOUNDATION_DEPTH; depth++) {
					if (level.getBlockState(at(center, facing, x, floor - depth, z)).isAir()) {
						put(at(center, facing, x, floor - depth, z), STONE);
					}
				}
				boolean inner = Math.abs(x) <= INNER_HALF && Math.abs(z) <= INNER_HALF;
				boolean path = Math.abs(x) <= 1 && z > INNER_HALF;
				put(at(center, facing, x, floor, z), path ? PATH : inner ? DARK : STONE);
			}
		}
		// The pavilion's floor is kept clear of anything growing or standing on it.
		for (int x = -INNER_HALF; x <= INNER_HALF; x++) {
			for (int z = -INNER_HALF; z <= INNER_HALF; z++) {
				for (int up = 0; up < PILLAR_HEIGHT; up++) {
					clear(at(center, facing, x, floor + 1 + up, z));
				}
			}
		}
		int[][] pillars = {{-PILLAR_OFFSET, -PILLAR_OFFSET}, {PILLAR_OFFSET, -PILLAR_OFFSET}, {-PILLAR_OFFSET, PILLAR_OFFSET}, {PILLAR_OFFSET, PILLAR_OFFSET},
			{-PILLAR_OFFSET, 0}, {PILLAR_OFFSET, 0}, {0, -PILLAR_OFFSET}, {0, PILLAR_OFFSET}};
		for (int[] spot : pillars) {
			for (int up = 1; up <= PILLAR_HEIGHT; up++) {
				put(at(center, facing, spot[0], floor + up, spot[1]), PILLAR);
			}
		}
		int roofY = floor + PILLAR_HEIGHT + 1;
		for (int tier = 0; tier < ROOF_HALVES.length; tier++) {
			int half = ROOF_HALVES[tier];
			for (int x = -half; x <= half; x++) {
				for (int z = -half; z <= half; z++) {
					put(at(center, facing, x, roofY + tier, z), ROOF);
				}
			}
		}
		// Upturned corners: sweeping out and rising from the lowest tier.
		int half = ROOF_HALVES[0];
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int tip = 1; tip <= CORNER_TIP; tip++) {
					put(at(center, facing, sx * (half + tip), roofY + tip / 2, sz * (half + tip)), ROOF);
					put(at(center, facing, sx * (half + tip), roofY + tip / 2, sz * (half + tip - 1)), ROOF);
				}
			}
		}
		// Hanging lights along the eaves.
		for (int step = -half; step <= half; step += LANTERN_EVERY) {
			put(at(center, facing, step, roofY - 1, half), LANTERN);
			put(at(center, facing, step, roofY - 1, -half), LANTERN);
			put(at(center, facing, half, roofY - 1, step), LANTERN);
			put(at(center, facing, -half, roofY - 1, step), LANTERN);
		}
		for (int gate = 0; gate < GATES; gate++) {
			gate(center, facing, floor, GATE_DISTANCE + gate * GATE_SPACING);
		}
	}

	/** A great gate out in front of the platform, over the path that leads up to the shrine. */
	private void gate(BlockPos center, Direction facing, int floor, int distance) {
		for (int side = -1; side <= 1; side += 2) {
			for (int up = 0; up <= GATE_HEIGHT; up++) {
				put(at(center, facing, side * GATE_HALF_WIDTH, floor + up, distance), PILLAR);
				put(at(center, facing, side * (GATE_HALF_WIDTH + 1), floor + up, distance), PILLAR);
			}
		}
		for (int x = -GATE_HALF_WIDTH - 3; x <= GATE_HALF_WIDTH + 3; x++) {
			for (int z = -1; z <= 1; z++) {
				put(at(center, facing, x, floor + GATE_HEIGHT + 1, distance + z), ROOF);
			}
			put(at(center, facing, x, floor + GATE_HEIGHT - 2, distance), BEAM);
		}
		for (int x = -GATE_HALF_WIDTH - 4; x <= GATE_HALF_WIDTH + 4; x++) {
			if (Math.abs(x) > GATE_HALF_WIDTH + 2) {
				put(at(center, facing, x, floor + GATE_HEIGHT + 2, distance), ROOF);
			}
		}
		// A path of the same red brick down from the gate to the platform.
		for (int z = distance - GATE_SPACING; z <= distance; z++) {
			for (int x = -1; x <= 1; x++) {
				put(at(center, facing, x, floor, z), PATH);
				if (z % PATH_LANTERN_EVERY == 0 && Math.abs(x) == 1) {
					put(at(center, facing, x * 3, floor + 1, z), LANTERN);
				}
				for (int depth = 1; depth <= FOUNDATION_DEPTH; depth++) {
					if (level.getBlockState(at(center, facing, x, floor - depth, z)).isAir()) {
						put(at(center, facing, x, floor - depth, z), STONE);
					}
				}
			}
		}
	}

	/** A position in shrine space (+z is ahead of the caster) turned to the way the caster faces. */
	private static BlockPos at(BlockPos center, Direction facing, int x, int y, int z) {
		return switch (facing) {
			case NORTH -> center.offset(-x, 0, -z).atY(y);
			case EAST -> center.offset(z, 0, -x).atY(y);
			case WEST -> center.offset(-z, 0, x).atY(y);
			default -> center.offset(x, 0, z).atY(y);
		};
	}

	private void put(BlockPos pos, BlockState state) {
		if (!level.isLoaded(pos) || level.getBlockState(pos).getDestroySpeed(level, pos) < 0) {
			return;
		}
		replaced.putIfAbsent(pos.asLong(), level.getBlockState(pos));
		placed.add(pos.asLong());
		level.setBlock(pos, state, Block.UPDATE_CLIENTS);
	}

	private void clear(BlockPos pos) {
		if (!level.isLoaded(pos) || level.getBlockState(pos).isAir() || level.getBlockState(pos).getDestroySpeed(level, pos) < 0) {
			return;
		}
		replaced.putIfAbsent(pos.asLong(), level.getBlockState(pos));
		level.setBlock(pos, AIR, Block.UPDATE_CLIENTS);
	}

	/** Takes the shrine down and puts back whatever was there before it. */
	public void remove() {
		for (Long2ObjectOpenHashMap.Entry<BlockState> entry : replaced.long2ObjectEntrySet()) {
			BlockPos pos = BlockPos.of(entry.getLongKey());
			if (level.isLoaded(pos)) {
				level.setBlock(pos, entry.getValue(), Block.UPDATE_CLIENTS);
			}
		}
		replaced.clear();
		placed.clear();
	}

	/** The way a caster is facing, flattened to the four compass points. */
	public static Direction facing(float yaw) {
		return Direction.fromYRot(Mth.wrapDegrees(yaw));
	}
}
