package com.ronaldw07.deku;

import com.ronaldw07.deku.network.DecayPayload.Move;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the Decay quirk. Whatever the hand touches crumbles to dust, and the decay
 * spreads outward through every block connected to it, a ring at a time. Living things it
 * reaches rot away over a few seconds. Holding the charge and slamming the ground sends a
 * wave of decay rolling out across the land; Catastrophe is that at full scale, levelling
 * everything all the way around. Decay Cowling powers the body up and decays whatever it
 * runs into.
 */
public final class Decay {
	private static final double TOUCH_REACH = 6.0;
	private static final double TOUCH_RADIUS = 10.0;
	private static final double TOUCH_GROWTH = 0.8; // blocks per tick
	private static final double MIN_WAVE_RADIUS = 10.0;
	private static final double EXTRA_WAVE_RADIUS = 54.0;
	private static final double WAVE_GROWTH = 1.5;
	private static final int WAVE_DEPTH = 6; // blocks below the ground the wave eats into
	private static final int WAVE_HEIGHT = 40; // so trees and buildings on top crumble too
	// Catastrophe: the whole surroundings, out to 128 blocks, mountains and all.
	private static final double MIN_CATASTROPHE_RADIUS = 48.0;
	private static final double EXTRA_CATASTROPHE_RADIUS = 80.0;
	private static final double CATASTROPHE_GROWTH = 2.5;
	private static final int CATASTROPHE_DEPTH = 12;
	private static final int CATASTROPHE_HEIGHT = 64;
	private static final int CATASTROPHE_RING_POINTS = 96;
	private static final int BLOCKS_PER_TICK = 3000;
	private static final int CATASTROPHE_BLOCKS_PER_TICK = 6000;
	private static final int PARTICLE_EVERY = 4; // only some crumbling blocks puff, to keep packets down
	private static final int CATASTROPHE_PARTICLE_EVERY = 10;
	// Decay Cowling: stat boosts like Full Cowling at 100%, and anything run into crumbles.
	private static final Identifier COWLING_SPEED_ID = DekuMod.id("decay_cowling_speed");
	private static final Identifier COWLING_JUMP_ID = DekuMod.id("decay_cowling_jump");
	private static final Identifier COWLING_SAFE_FALL_ID = DekuMod.id("decay_cowling_safe_fall");
	private static final Identifier COWLING_ATTACK_ID = DekuMod.id("decay_cowling_attack");
	private static final double COWLING_SPEED_BONUS = 1.0;
	private static final double COWLING_JUMP_BONUS = 0.5;
	private static final double COWLING_SAFE_FALL_BONUS = 20.0;
	private static final double COWLING_ATTACK_BONUS = 10.0;
	private static final int COWLING_INTERVAL = 2; // ticks between decaying what's touched
	private static final double COWLING_REACH = 0.6; // around the body; the floor underfoot is spared
	private static final double COWLING_ROT_REACH = 1.5;
	private static final int SOUND_INTERVAL = 4;
	// Rotting: a few hearts every other tick for three seconds.
	private static final int ROT_TICKS = 60;
	private static final int ROT_INTERVAL = 2;
	private static final float ROT_DAMAGE = 3.0f;
	private static final DustParticleOptions ASH = new DustParticleOptions(0x8A8A8A, 1.5f);
	private static final DustParticleOptions DRIED_BLOOD = new DustParticleOptions(0x5A1010, 1.2f);

	/** One block waiting in a spread, ordered by how far it is from where the decay began. */
	private record Node(BlockPos pos, double distanceSqr) {
	}

	private static final class Spread {
		final ResourceKey<Level> dimension;
		final UUID owner;
		final Vec3 origin;
		final double maxRadius;
		final double growth;
		final int minY;
		final int maxY;
		final PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingDouble(Node::distanceSqr));
		final LongOpenHashSet seen = new LongOpenHashSet();
		final boolean catastrophe;
		double radius;
		int age;

		Spread(ResourceKey<Level> dimension, UUID owner, Vec3 origin, double maxRadius, double growth, int minY, int maxY,
				boolean catastrophe) {
			this.dimension = dimension;
			this.owner = owner;
			this.origin = origin;
			this.maxRadius = maxRadius;
			this.growth = growth;
			this.minY = minY;
			this.maxY = maxY;
			this.catastrophe = catastrophe;
		}

		boolean inside(BlockPos pos) {
			return pos.getY() >= minY && pos.getY() <= maxY && Vec3.atCenterOf(pos).distanceToSqr(origin) <= maxRadius * maxRadius;
		}

		/** Queues the block if it's in range, not checked before, and something decay can eat. */
		void offer(ServerLevel level, BlockPos pos) {
			if (inside(pos) && seen.add(pos.asLong()) && level.isLoaded(pos) && decayable(level, pos, level.getBlockState(pos))) {
				queue.add(new Node(pos, Vec3.atCenterOf(pos).distanceToSqr(origin)));
			}
		}
	}

	private record Rot(ResourceKey<Level> dimension, int ticksLeft) {
	}

	private static final List<Spread> spreads = new ArrayList<>();
	private static final Map<UUID, Rot> rotting = new HashMap<>();
	private static final Set<UUID> cowling = new HashSet<>();

	private Decay() {
	}

	public static void handle(ServerPlayer player, Move move, int charge) {
		if (!DekuItems.isHolding(player, DekuItems.DECAY)) {
			return;
		}
		switch (move) {
			case TOUCH -> touch(player);
			case WAVE -> wave(player, Mth.clamp(charge, 0, 100) / 100.0, false);
			case CATASTROPHE -> wave(player, Mth.clamp(charge, 0, 100) / 100.0, true);
			case COWLING -> setCowling(player, charge > 0);
		}
	}

	/** Decays the mob or block under the crosshair, if it's within arm's reach. */
	private static void touch(ServerPlayer player) {
		ServerLevel level = player.level();
		HitResult hit = Aim.trace(player, TOUCH_REACH);
		Vec3 at = hit.getLocation();
		if (hit instanceof EntityHitResult entityHit) {
			rot(level, entityHit.getEntity());
		} else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = blockHit.getBlockPos();
			Spread spread = new Spread(level.dimension(), player.getUUID(), Vec3.atCenterOf(pos), TOUCH_RADIUS, TOUCH_GROWTH,
				level.getMinY(), level.getMaxY(), false);
			spread.offer(level, pos);
			spreads.add(spread);
		} else {
			return;
		}
		level.sendParticles(ASH, at.x, at.y, at.z, 20, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, at.x, at.y, at.z, DekuSounds.DECAY_TOUCH, SoundSource.PLAYERS, 1.0f, 1.0f);
	}

	/** Both hands on the ground: decay floods out from beneath the player, all the way around. */
	private static void wave(ServerPlayer player, double power, boolean catastrophe) {
		ServerLevel level = player.level();
		BlockPos ground = player.blockPosition().below();
		int top = Math.min(level.getMaxY(), ground.getY() + (catastrophe ? CATASTROPHE_HEIGHT : WAVE_HEIGHT));
		int bottom = Math.max(level.getMinY(), ground.getY() - (catastrophe ? CATASTROPHE_DEPTH : WAVE_DEPTH));
		double radius = catastrophe ? MIN_CATASTROPHE_RADIUS + EXTRA_CATASTROPHE_RADIUS * power : MIN_WAVE_RADIUS + EXTRA_WAVE_RADIUS * power;
		Spread spread = new Spread(level.dimension(), player.getUUID(), Vec3.atCenterOf(ground), radius,
			catastrophe ? CATASTROPHE_GROWTH : WAVE_GROWTH, bottom, top, catastrophe);
		// Start from the whole patch under the hands, so the wave doesn't depend on one block.
		for (BlockPos pos : BlockPos.betweenClosed(ground.offset(-1, -1, -1), ground.offset(1, 0, 1))) {
			spread.offer(level, pos.immutable());
		}
		spreads.add(spread);
		level.sendParticles(ASH, player.getX(), player.getY(), player.getZ(), catastrophe ? 200 : 60, 1.5, 0.2, 1.5, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.DECAY_TOUCH, SoundSource.PLAYERS,
			(float) (1.0 + power) * (catastrophe ? 3 : 1), catastrophe ? 0.4f : 0.6f);
		if (catastrophe) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, 3.0f, 0.5f);
		}
	}

	private static void rot(ServerLevel level, Entity entity) {
		if (entity instanceof LivingEntity && entity.isAlive()) {
			rotting.putIfAbsent(entity.getUUID(), new Rot(level.dimension(), ROT_TICKS));
		}
	}

	public static void tick(MinecraftServer server) {
		for (UUID id : cowling) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null && player.isAlive() && player.tickCount % COWLING_INTERVAL == 0) {
				cowlingTouch(player);
			}
		}

		Iterator<Spread> spreadIterator = spreads.iterator();
		while (spreadIterator.hasNext()) {
			Spread spread = spreadIterator.next();
			ServerLevel level = server.getLevel(spread.dimension);
			if (level == null || !advance(level, spread)) {
				spreadIterator.remove();
			}
		}

		Iterator<Map.Entry<UUID, Rot>> rotIterator = rotting.entrySet().iterator();
		while (rotIterator.hasNext()) {
			Map.Entry<UUID, Rot> entry = rotIterator.next();
			Rot rot = entry.getValue();
			ServerLevel level = server.getLevel(rot.dimension());
			Entity entity = level == null ? null : level.getEntity(entry.getKey());
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || rot.ticksLeft() <= 0) {
				rotIterator.remove();
				continue;
			}
			wither(level, living, rot.ticksLeft());
			if (living.isAlive()) {
				entry.setValue(new Rot(rot.dimension(), rot.ticksLeft() - 1));
			} else {
				crumble(level, living);
				rotIterator.remove();
			}
		}
	}

	/** Grows a spread by one step. Returns false once it has run its course. */
	private static boolean advance(ServerLevel level, Spread spread) {
		spread.radius = Math.min(spread.maxRadius, spread.radius + spread.growth);
		double reachSqr = spread.radius * spread.radius;
		int decayed = 0;
		int budget = spread.catastrophe ? CATASTROPHE_BLOCKS_PER_TICK : BLOCKS_PER_TICK;
		int particleEvery = spread.catastrophe ? CATASTROPHE_PARTICLE_EVERY : PARTICLE_EVERY;
		while (!spread.queue.isEmpty() && decayed < budget && spread.queue.peek().distanceSqr() <= reachSqr) {
			BlockPos pos = spread.queue.poll().pos();
			BlockState state = level.getBlockState(pos);
			// Checked again: something else may have changed it since it was queued.
			if (!decayable(level, pos, state)) {
				continue;
			}
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			if (decayed % particleEvery == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5,
					pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
				level.sendParticles(ASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.4, 0.4, 0.4, 0.01);
			}
			decayed++;
			for (Direction direction : Direction.values()) {
				spread.offer(level, pos.relative(direction));
			}
		}
		if (spread.catastrophe && spread.radius < spread.maxRadius) {
			ring(level, spread);
		}

		// Anything alive inside the ring rots, except the one doing the decaying.
		Vec3 o = spread.origin;
		AABB area = new AABB(o.x - spread.radius, spread.minY - 1, o.z - spread.radius, o.x + spread.radius, spread.maxY + 2,
			o.z + spread.radius);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area,
				entity -> entity.isAlive() && !entity.getUUID().equals(spread.owner)
					&& entity.position().distanceToSqr(spread.origin.x, entity.getY(), spread.origin.z) <= reachSqr)) {
			rot(level, entity);
		}

		if (decayed > 0 && spread.age++ % SOUND_INTERVAL == 0) {
			Vec3 at = spread.origin;
			level.playSound(null, at.x, at.y, at.z, DekuSounds.DECAY_CRUMBLE, SoundSource.BLOCKS,
				(float) Math.min(3.0, 0.6 + decayed / 300.0), 0.6f + level.getRandom().nextFloat() * 0.3f);
		}
		return !spread.queue.isEmpty();
	}

	private static boolean decayable(ServerLevel level, BlockPos pos, BlockState state) {
		return !state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty() && state.getDestroySpeed(level, pos) >= 0;
	}

	private static void wither(ServerLevel level, LivingEntity entity, int ticksLeft) {
		AABB box = entity.getBoundingBox();
		Vec3 center = box.getCenter();
		level.sendParticles(ASH, center.x, center.y, center.z, 4, box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2, 0.01);
		level.sendParticles(DRIED_BLOOD, center.x, center.y, center.z, 2, box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2, 0.01);
		if (ticksLeft % ROT_INTERVAL == 0) {
			entity.invulnerableTime = 0; // every hit lands, instead of being swallowed by the usual flinch time
			entity.hurtServer(level, level.damageSources().wither(), ROT_DAMAGE);
		}
	}

	/** What's left of a mob that rotted away: a cloud of dust. */
	private static void crumble(ServerLevel level, LivingEntity entity) {
		AABB box = entity.getBoundingBox();
		Vec3 center = box.getCenter();
		level.sendParticles(ASH, center.x, center.y, center.z, 40, box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2, 0.05);
		level.playSound(null, center.x, center.y, center.z, DekuSounds.DECAY_CRUMBLE, SoundSource.HOSTILE, 1.0f, 0.8f);
	}

	/** A wall of dust racing outward at the edge of a Catastrophe. */
	private static void ring(ServerLevel level, Spread spread) {
		for (int i = 0; i < CATASTROPHE_RING_POINTS; i++) {
			double angle = Math.PI * 2 * i / CATASTROPHE_RING_POINTS;
			double x = spread.origin.x + Math.cos(angle) * spread.radius;
			double z = spread.origin.z + Math.sin(angle) * spread.radius;
			level.sendParticles(ASH, x, spread.origin.y + 1.5, z, 3, 0.5, 1.5, 0.5, 0.02);
			if (i % 3 == 0) {
				level.sendParticles(DRIED_BLOOD, x, spread.origin.y + 1.5, z, 2, 0.5, 1.5, 0.5, 0.02);
			}
		}
	}

	private static void setCowling(ServerPlayer player, boolean on) {
		boolean wasOn = cowling.contains(player.getUUID());
		if (on) {
			cowling.add(player.getUUID());
			if (!wasOn) {
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.DECAY_TOUCH, SoundSource.PLAYERS,
					1.5f, 0.5f);
			}
		} else {
			cowling.remove(player.getUUID());
		}
		setBonus(player, Attributes.MOVEMENT_SPEED, COWLING_SPEED_ID, on ? COWLING_SPEED_BONUS : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		setBonus(player, Attributes.JUMP_STRENGTH, COWLING_JUMP_ID, on ? COWLING_JUMP_BONUS : 0, AttributeModifier.Operation.ADD_VALUE);
		setBonus(player, Attributes.SAFE_FALL_DISTANCE, COWLING_SAFE_FALL_ID, on ? COWLING_SAFE_FALL_BONUS : 0,
			AttributeModifier.Operation.ADD_VALUE);
		setBonus(player, Attributes.ATTACK_DAMAGE, COWLING_ATTACK_ID, on ? COWLING_ATTACK_BONUS : 0, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void setBonus(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount,
			AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0) {
			instance.removeModifier(id);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** Decay Cowling: walls and anything else the body runs into crumble, and mobs up close rot. */
	private static void cowlingTouch(ServerPlayer player) {
		ServerLevel level = player.level();
		AABB body = player.getBoundingBox().inflate(COWLING_REACH);
		int floor = player.blockPosition().getY();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, floor, body.minZ),
				BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
			BlockState state = level.getBlockState(pos);
			if (decayable(level, pos, state)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5,
					pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
			}
		}
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(COWLING_ROT_REACH),
				entity -> entity != player && entity.isAlive())) {
			rot(level, entity);
		}
		Vec3 center = player.getBoundingBox().getCenter();
		level.sendParticles(ASH, center.x, center.y, center.z, 3, 0.4, 0.8, 0.4, 0.01);
	}

	public static void forget(ServerPlayer player) {
		spreads.removeIf(spread -> spread.owner.equals(player.getUUID()));
		cowling.remove(player.getUUID());
	}
}
