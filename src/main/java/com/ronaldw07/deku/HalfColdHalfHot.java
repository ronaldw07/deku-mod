package com.ronaldw07.deku;

import com.ronaldw07.deku.network.HalfColdHalfHotPayload.Move;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of Half Cold Half Hot. The right side freezes: a jagged glacier racing along
 * the ground that traps whatever it reaches, and a towering wall of ice. The left side burns:
 * a stream of fire held as long as the key is. Flashfreeze Heatwave does both at once, a
 * ball of ice blown apart by a blast of heat. Holding jump slides along on a bridge of ice
 * that builds itself under your feet.
 */
public final class HalfColdHalfHot {
	// Ice wave.
	private static final int WAVE_LENGTH = 30;
	private static final double WAVE_BLOCKS_PER_TICK = 3.0;
	private static final double WAVE_JITTER = 1.5;
	private static final double MIN_SPIKE_RADIUS = 1.0;
	private static final double SPIKE_RADIUS_GROWTH = 1.0 / 12; // per block out
	private static final double MIN_SPIKE_HEIGHT = 2.0;
	private static final double SPIKE_HEIGHT_GROWTH = 0.2;
	private static final double WAVE_WIDTH = 2.5;
	private static final float FREEZE_DAMAGE = 14.0f;
	// Anyone the ice erupts into is impaled: a big hit and thrown upward.
	private static final float SPIKE_DAMAGE = 12.0f;
	private static final float WALL_DAMAGE = 16.0f;
	private static final double IMPALE_LIFT = 0.9;
	private static final int FROZEN_TICKS = 300;
	private static final int GROUND_SEARCH = 6;
	// Ice wall.
	private static final int WALL_DISTANCE = 5;
	private static final int WALL_HALF_WIDTH = 20;
	private static final int WALL_THICKNESS = 3;
	private static final int WALL_PEAK = 30;
	private static final int WALL_EDGE = 10;
	private static final int WALL_JAG = 6;
	private static final int WALL_LAYERS_PER_TICK = 3;
	// Flamethrower.
	private static final double FLAME_RANGE = 30.0;
	private static final double FLAME_CONE_COS = 0.8; // about 37 degrees either side of the aim
	private static final int FLAME_STREAM = 40;
	private static final double FLAME_SPREAD = 0.35;
	private static final double FLAME_SPEED = 1.4;
	private static final double FLAME_BILLOW_SPACING = 2.5;
	private static final int FLAME_PER_BILLOW = 8;
	private static final double FLAME_PUSH = 0.12;
	private static final int FIRE_TICKS = 200;
	private static final int FLAME_DAMAGE_INTERVAL = 2;
	private static final float FLAME_DAMAGE = 3.0f; // every other tick: 30 a second
	private static final int IGNITE_INTERVAL = 2;
	private static final int IGNITE_SPOTS = 3;
	private static final int FLAME_SOUND_INTERVAL = 4;
	// Flashfreeze Heatwave.
	private static final double HEATWAVE_DISTANCE = 12.0;
	private static final int HEATWAVE_ICE_RADIUS = 6;
	private static final int HEATWAVE_DELAY = 15;
	private static final float HEATWAVE_BLAST = 16.0f;
	private static final float HEATWAVE_CRATER = 12.0f;
	private static final int HEATWAVE_CRATER_FIRES = 60;
	private static final int HEATWAVE_DEBRIS = 250;
	private static final double HEATWAVE_REACH = 35.0;
	private static final float HEATWAVE_DAMAGE = 45.0f;
	private static final double HEATWAVE_PUSH = 5.0;
	// Chain of fire explosions going off around the main blast.
	private static final int CHAIN_BLASTS = 10;
	private static final double CHAIN_MIN_DISTANCE = 14.0;
	private static final double CHAIN_EXTRA_DISTANCE = 8.0;
	private static final int CHAIN_MAX_DELAY = 20;
	private static final float CHAIN_RADIUS = 5.0f;
	// A spiralling pillar of fire left standing over the crater.
	private static final int PILLAR_TICKS = 60;
	private static final double PILLAR_HEIGHT = 40.0;
	private static final double PILLAR_RADIUS = 3.0;
	// Chunks of magma thrown out to rain back down.
	private static final int MAGMA_CHUNKS = 12;
	// Ice slide.
	private static final int SLIDE_HALF_WIDTH = 1;

	private static final Set<UUID> flaming = new HashSet<>();
	private static final Set<UUID> sliding = new HashSet<>();

	private HalfColdHalfHot() {
	}

	public static void handle(ServerPlayer player, Move move, boolean active) {
		boolean starting = active || (move != Move.FLAME && move != Move.SLIDE);
		if (starting && !DekuItems.isHolding(player, DekuItems.HALF_COLD_HALF_HOT)) {
			return;
		}
		switch (move) {
			case ICE_WAVE -> iceWave(player);
			case FLAME -> {
				if (active) {
					flaming.add(player.getUUID());
				} else {
					flaming.remove(player.getUUID());
				}
			}
			case SLIDE -> {
				if (active) {
					sliding.add(player.getUUID());
				} else {
					sliding.remove(player.getUUID());
				}
			}
			case ICE_WALL -> iceWall(player);
			case HEATWAVE -> heatwave(player);
		}
	}

	/** Which way along the ground the player faces, even when looking straight up or down. */
	private static Vec3 facing(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, player.getYRot()) : flat.normalize();
	}

	/** A glacier of jagged spikes racing out along the ground, freezing anything it reaches. */
	private static void iceWave(ServerPlayer player) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		Vec3 feet = player.position();
		Vec3 forward = facing(player);
		Vec3 right = forward.cross(new Vec3(0, 1, 0));

		for (int distance = 2; distance <= WAVE_LENGTH; distance++) {
			Vec3 spot = feet.add(forward.scale(distance)).add(right.scale((random.nextDouble() - 0.5) * WAVE_JITTER));
			double radius = MIN_SPIKE_RADIUS + distance * SPIKE_RADIUS_GROWTH;
			double height = MIN_SPIKE_HEIGHT + distance * SPIKE_HEIGHT_GROWTH;
			Blasts.later(server, (int) (distance / WAVE_BLOCKS_PER_TICK), () -> spike(level, player, spot, radius, height));
		}

		Vec3 end = feet.add(forward.scale(WAVE_LENGTH));
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(feet, end).inflate(WAVE_WIDTH + 2),
				entity -> entity != player && entity.isAlive())) {
			Vec3 offset = entity.position().subtract(feet);
			double along = offset.dot(forward);
			Vec3 sideways = offset.subtract(forward.scale(along));
			if (along < 1 || along > WAVE_LENGTH || new Vec3(sideways.x, 0, sideways.z).length() > WAVE_WIDTH + along * 0.05) {
				continue;
			}
			Blasts.later(server, (int) (along / WAVE_BLOCKS_PER_TICK), () -> freeze(level, player, entity));
		}
		level.playSound(null, feet.x, feet.y, feet.z, DekuSounds.ICE, SoundSource.PLAYERS, 1.5f, 0.8f);
	}

	/** The first open block above the ground near a point, or the point itself if nothing's found. */
	private static BlockPos ground(ServerLevel level, BlockPos start) {
		BlockPos pos = start;
		for (int i = 0; i < GROUND_SEARCH && level.getBlockState(pos.below()).canBeReplaced(); i++) {
			pos = pos.below();
		}
		for (int i = 0; i < GROUND_SEARCH && !level.getBlockState(pos).canBeReplaced(); i++) {
			pos = pos.above();
		}
		return pos;
	}

	/** A jagged cone of ice, tallest in the middle, impaling anyone standing where it bursts up. */
	private static void spike(ServerLevel level, ServerPlayer owner, Vec3 at, double radius, double height) {
		RandomSource random = level.getRandom();
		BlockPos base = ground(level, BlockPos.containing(at));
		int reach = (int) Math.ceil(radius);
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				double distance = Math.sqrt(dx * dx + dz * dz);
				if (distance > radius) {
					continue;
				}
				int top = (int) Math.round(height * (1 - distance / (radius + 1)) * (0.6 + 0.4 * random.nextDouble()));
				for (int dy = 0; dy < top; dy++) {
					placeIce(level, base.offset(dx, dy, dz));
				}
			}
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, base.getY() + height / 2, at.z, 8, radius, height / 2, radius, 0.02);
		AABB area = new AABB(at.x - radius, base.getY(), at.z - radius, at.x + radius, base.getY() + height, at.z + radius);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity != owner && entity.isAlive())) {
			impale(level, owner, entity, SPIKE_DAMAGE);
		}
	}

	/** Ice bursting up through someone: a big hit, a toss upward, and frost. */
	private static void impale(ServerLevel level, ServerPlayer owner, LivingEntity entity, float damage) {
		entity.invulnerableTime = 0; // lands even if the wave's freeze hit them a moment ago
		entity.hurtServer(level, owner.damageSources().playerAttack(owner), damage);
		entity.push(0, IMPALE_LIFT, 0);
		entity.hurtMarked = true;
		entity.setTicksFrozen(FROZEN_TICKS);
		Vec3 center = entity.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y, center.z, 12, 0.4, 0.6, 0.4, 0.1);
	}

	private static void placeIce(ServerLevel level, BlockPos pos) {
		if (level.isLoaded(pos) && level.getBlockState(pos).canBeReplaced()) {
			level.setBlockAndUpdate(pos, Blocks.PACKED_ICE.defaultBlockState());
		}
	}

	/** Hurts and chills a mob, and seals it in a block of ice. */
	private static void freeze(ServerLevel level, ServerPlayer player, LivingEntity entity) {
		if (!entity.isAlive()) {
			return;
		}
		entity.hurtServer(level, player.damageSources().freeze(), FREEZE_DAMAGE);
		entity.setTicksFrozen(FROZEN_TICKS);
		AABB box = entity.getBoundingBox().inflate(0.5);
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
				BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			if (level.isLoaded(pos) && level.getBlockState(pos).isAir()) {
				level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
			}
		}
	}

	/** A huge wall of ice erupting from the ground ahead, rising a few layers a tick. */
	private static void iceWall(ServerPlayer player) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		Vec3 feet = player.position();
		Vec3 forward = facing(player);
		Vec3 right = forward.cross(new Vec3(0, 1, 0));

		int columns = (WALL_HALF_WIDTH * 2 + 1) * WALL_THICKNESS;
		BlockPos[] bases = new BlockPos[columns];
		int[] heights = new int[columns];
		int tallest = 0;
		int i = 0;
		for (int side = -WALL_HALF_WIDTH; side <= WALL_HALF_WIDTH; side++) {
			double taper = 1 - Math.abs(side) / (double) WALL_HALF_WIDTH;
			int height = (int) (WALL_EDGE + (WALL_PEAK - WALL_EDGE) * taper) + random.nextInt(WALL_JAG + 1);
			for (int depth = 0; depth < WALL_THICKNESS; depth++, i++) {
				Vec3 at = feet.add(forward.scale(WALL_DISTANCE + depth)).add(right.scale(side));
				bases[i] = ground(level, BlockPos.containing(at));
				heights[i] = height - depth * 2; // the back of the wall a little lower, so it leans like a glacier
				tallest = Math.max(tallest, heights[i]);
			}
		}

		for (int layer = 0; layer < tallest; layer++) {
			int y = layer;
			Blasts.later(server, layer / WALL_LAYERS_PER_TICK, () -> {
				for (int c = 0; c < columns; c++) {
					if (y < heights[c]) {
						placeIce(level, bases[c].above(y));
					}
				}
			});
		}

		Vec3 front = feet.add(forward.scale(WALL_DISTANCE));
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
				new AABB(front, front).inflate(WALL_HALF_WIDTH, WALL_PEAK, WALL_THICKNESS + 2),
				entity -> entity != player && entity.isAlive())) {
			impale(level, player, entity, WALL_DAMAGE);
			entity.push(forward.scale(1.5));
			freeze(level, player, entity);
		}
		level.playSound(null, feet.x, feet.y, feet.z, DekuSounds.ICE, SoundSource.PLAYERS, 3.0f, 0.5f);
	}

	public static void tick(MinecraftServer server) {
		sliding.removeIf(id -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null || !player.isAlive() || !DekuItems.isHolding(player, DekuItems.HALF_COLD_HALF_HOT)) {
				return true;
			}
			slide(player);
			return false;
		});
		flaming.removeIf(id -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null || !player.isAlive() || !DekuItems.isHolding(player, DekuItems.HALF_COLD_HALF_HOT)) {
				return true;
			}
			flame(player);
			return false;
		});
	}

	/**
	 * One tick of the flamethrower: a roaring cone of fire that burns and shoves everything in
	 * it, sets the ground ablaze in several places at once, and melts ice.
	 */
	private static void flame(ServerPlayer player) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();

		// A fast stream of fire along the aim, fanning out with distance.
		for (int i = 0; i < FLAME_STREAM; i++) {
			Vec3 spread = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).scale(FLAME_SPREAD);
			Vec3 at = eye.add(look.scale(1 + random.nextDouble() * 2));
			Vec3 velocity = look.add(spread).normalize();
			// Count 0 makes the particle fly along (x, y, z) offset at the given speed.
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 0, velocity.x, velocity.y, velocity.z, FLAME_SPEED);
		}
		// Billows of fire along the whole length, wider the further out.
		for (double distance = 2; distance <= FLAME_RANGE; distance += FLAME_BILLOW_SPACING) {
			Vec3 at = eye.add(look.scale(distance));
			double width = distance * 0.18;
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, FLAME_PER_BILLOW, width, width, width, 0.05);
			if (random.nextInt(3) == 0) {
				level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 1, width, width, width, 0);
			}
		}
		Vec3 far = eye.add(look.scale(FLAME_RANGE * 0.7));
		level.sendParticles(ParticleTypes.LARGE_SMOKE, far.x, far.y + 1, far.z, 3, 2, 1, 2, 0.02);

		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(FLAME_RANGE),
				entity -> entity != player && entity.isAlive())) {
			Vec3 toEntity = entity.getBoundingBox().getCenter().subtract(eye);
			if (toEntity.length() > FLAME_RANGE || toEntity.normalize().dot(look) < FLAME_CONE_COS) {
				continue;
			}
			entity.setRemainingFireTicks(FIRE_TICKS);
			entity.push(look.scale(FLAME_PUSH));
			entity.hurtMarked = true;
			if (player.tickCount % FLAME_DAMAGE_INTERVAL == 0) {
				entity.invulnerableTime = 0; // a steady stream of fire, not one hit swallowed by flinch time
				entity.hurtServer(level, player.damageSources().playerAttack(player), FLAME_DAMAGE);
			}
		}

		if (player.tickCount % IGNITE_INTERVAL == 0) {
			for (int i = 0; i < IGNITE_SPOTS; i++) {
				Vec3 spread = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).scale(0.5);
				Vec3 end = eye.add(look.add(spread).normalize().scale(FLAME_RANGE));
				BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
				if (hit.getType() == HitResult.Type.BLOCK) {
					scorch(level, hit);
				}
			}
		}
		if (player.tickCount % FLAME_SOUND_INTERVAL == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.FLAME, SoundSource.PLAYERS, 2.0f, 0.8f);
		}
	}

	/** Melts ice and snow where the fire lands, or sets the spot alight. */
	private static void scorch(ServerLevel level, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
			return;
		}
		BlockPos above = pos.relative(hit.getDirection());
		BlockState fire = BaseFireBlock.getState(level, above);
		if (level.getBlockState(above).isAir() && fire.canSurvive(level, above)) {
			level.setBlockAndUpdate(above, fire);
		}
	}

	/** Lays a strip of ice under the player's feet, so they glide along a bridge of it. */
	private static void slide(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos under = BlockPos.containing(player.getX(), player.getY() - 0.5, player.getZ());
		for (int dx = -SLIDE_HALF_WIDTH; dx <= SLIDE_HALF_WIDTH; dx++) {
			for (int dz = -SLIDE_HALF_WIDTH; dz <= SLIDE_HALF_WIDTH; dz++) {
				placeIce(level, under.offset(dx, 0, dz));
			}
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY(), player.getZ(), 4, 0.6, 0.1, 0.6, 0.02);
		if (player.tickCount % 8 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.ICE, SoundSource.PLAYERS, 0.5f, 1.4f);
		}
	}

	/** Flashfreeze Heatwave: freeze a ball of ice ahead, then blow it apart with a blast of heat. */
	private static void heatwave(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		HitResult hit = Aim.trace(player, HEATWAVE_DISTANCE);
		Vec3 center = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(HEATWAVE_DISTANCE)) : hit.getLocation();

		BlockPos middle = BlockPos.containing(center);
		for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-HEATWAVE_ICE_RADIUS, -HEATWAVE_ICE_RADIUS, -HEATWAVE_ICE_RADIUS),
				middle.offset(HEATWAVE_ICE_RADIUS, HEATWAVE_ICE_RADIUS, HEATWAVE_ICE_RADIUS))) {
			if (pos.distSqr(middle) <= HEATWAVE_ICE_RADIUS * HEATWAVE_ICE_RADIUS) {
				placeIce(level, pos.immutable());
			}
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y, center.z, 60, 3, 3, 3, 0.05);
		level.playSound(null, center.x, center.y, center.z, DekuSounds.ICE, SoundSource.PLAYERS, 2.0f, 0.7f);

		Blasts.later(level.getServer(), HEATWAVE_DELAY, () -> {
			level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 400, 5, 5, 5, 0.5);
			level.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 60, 4, 4, 4, 0);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 2, 2, 2, 2, 0);
			level.sendParticles(DekuParticles.WHITE_SMOKE, center.x, center.y, center.z, 300, 8, 6, 8, 0.08);
			Blasts.blast(player, center, HEATWAVE_BLAST, Blasts.sparing(player), HEATWAVE_DEBRIS);
			Blasts.carve(level, center, HEATWAVE_CRATER, HEATWAVE_CRATER_FIRES);
			chain(player, center);
			pillar(level, center, PILLAR_TICKS);
			magma(level, center);
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(HEATWAVE_REACH),
					entity -> entity != player && entity.isAlive() && entity.position().distanceTo(center) <= HEATWAVE_REACH)) {
				Vec3 offset = entity.position().subtract(center);
				double strength = 1 - offset.length() / HEATWAVE_REACH;
				Vec3 away = offset.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : offset.normalize();
				entity.hurtServer(level, player.damageSources().playerAttack(player), HEATWAVE_DAMAGE * (float) strength);
				entity.setRemainingFireTicks(FIRE_TICKS);
				entity.push(away.scale(HEATWAVE_PUSH * strength).add(0, strength * 0.5, 0));
				entity.hurtMarked = true;
			}
			level.playSound(null, center.x, center.y, center.z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, 5.0f, 0.6f);
			level.playSound(null, center.x, center.y, center.z, DekuSounds.SMASH_BLAST, SoundSource.PLAYERS, 5.0f, 0.5f);
			level.playSound(null, center.x, center.y, center.z, DekuSounds.FLAME, SoundSource.PLAYERS, 4.0f, 0.6f);
		});
	}

	/** Smaller fire explosions going off one after another all around the main blast. */
	private static void chain(ServerPlayer player, Vec3 center) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		for (int i = 0; i < CHAIN_BLASTS; i++) {
			double angle = Math.PI * 2 * i / CHAIN_BLASTS + random.nextDouble() * 0.4;
			double distance = CHAIN_MIN_DISTANCE + random.nextDouble() * CHAIN_EXTRA_DISTANCE;
			Vec3 at = center.add(Math.cos(angle) * distance, random.nextDouble() * 4 - 2, Math.sin(angle) * distance);
			Blasts.later(level.getServer(), 3 + random.nextInt(CHAIN_MAX_DELAY), () -> {
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 80, 2, 2, 2, 0.25);
				level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 10, 1.5, 1.5, 1.5, 0);
				Blasts.blast(player, at, CHAIN_RADIUS, Blasts.sparing(player), 15);
				Blasts.carve(level, at, CHAIN_RADIUS - 1, 6);
			});
		}
	}

	/** A spiralling column of fire over the crater, burning for a few seconds. */
	private static void pillar(ServerLevel level, Vec3 center, int ticksLeft) {
		if (ticksLeft <= 0) {
			return;
		}
		double spin = ticksLeft * 0.5;
		for (double height = 0; height < PILLAR_HEIGHT; height += 1.5) {
			double radius = PILLAR_RADIUS * (1 + height / PILLAR_HEIGHT);
			for (int arm = 0; arm < 3; arm++) {
				double angle = spin + height * 0.3 + Math.PI * 2 * arm / 3;
				Vec3 at = center.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 0, 0, 1, 0, 0.15);
			}
		}
		if (ticksLeft % 4 == 0) {
			level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 2, center.z, 3, 1, 1, 1, 0);
		}
		Blasts.later(level.getServer(), 1, () -> pillar(level, center, ticksLeft - 1));
	}

	/** Chunks of magma hurled out of the blast to rain back down around it. */
	private static void magma(ServerLevel level, Vec3 center) {
		RandomSource random = level.getRandom();
		BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
		for (int i = 0; i < MAGMA_CHUNKS; i++) {
			BlockPos pos = BlockPos.containing(center.add(0, 2, 0));
			if (!level.getBlockState(pos).isAir()) {
				continue;
			}
			FallingBlockEntity chunk = FallingBlockEntity.fall(level, pos, magma);
			double angle = random.nextDouble() * Math.PI * 2;
			double speed = 0.6 + random.nextDouble() * 0.8;
			chunk.setDeltaMovement(Math.cos(angle) * speed, 1.0 + random.nextDouble() * 0.8, Math.sin(angle) * speed);
			chunk.hurtMarked = true;
		}
	}

	public static void forget(ServerPlayer player) {
		flaming.remove(player.getUUID());
		sliding.remove(player.getUUID());
	}
}
