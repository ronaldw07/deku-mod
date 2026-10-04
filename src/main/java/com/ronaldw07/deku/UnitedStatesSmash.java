package com.ronaldw07.deku;

import com.ronaldw07.deku.network.TornadoFxPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * United States of Smash lands here: a punch from the sky that blows out a huge bowl crater,
 * cuts the signature ring into the ground around it, and leaves a towering tornado spinning
 * in the middle for a full minute, sucking up mobs, items and blocks.
 */
public final class UnitedStatesSmash {
	private static final float MIN_CRATER = 14.0f;
	private static final float EXTRA_CRATER = 16.0f; // 30 at 100%
	// The carving sphere sits this far up (as a share of its radius), so the hole is a wide, shallow bowl.
	private static final double BOWL_LIFT = 0.6;
	private static final float BLAST_RADIUS = 10.0f;
	private static final int BLAST_DEBRIS = 200;
	// The signature ring: a trench circling the crater, a bit beyond its rim.
	private static final double RING_SCALE = 1.5; // ring radius, relative to where the bowl meets the ground
	private static final float RING_WIDTH = 2.0f;
	private static final double RING_SPACING = 1.5;
	private static final double RING_BLOCKS_PER_TICK = 4.0;
	private static final double MIN_SHOCKWAVE = 20.0;
	private static final double EXTRA_SHOCKWAVE = 40.0;
	private static final float SHOCKWAVE_DAMAGE = 40.0f;
	private static final double SHOCKWAVE_PUSH = 5.0;
	private static final int HEAVY_BOLTS = 12;
	private static final double HEAVY_BOLT_LENGTH = 24.0;
	private static final int DUST_RING_POINTS = 128;
	private static final double FX_VIEW_DISTANCE = 256;
	// Tornado.
	private static final int TORNADO_TICKS = 1200; // a full minute
	private static final double TORNADO_REACH = 28.0;
	private static final double TORNADO_HEIGHT = 70.0;
	private static final double FUNNEL_BASE = 2.0;
	private static final double FUNNEL_FLARE = 0.3; // funnel widens this much per block of height
	private static final double PULL = 0.08;
	private static final double EXTRA_PULL = 0.12;
	private static final double SPIN = 0.25;
	private static final double EXTRA_SPIN = 0.35;
	private static final double CORE_LIFT = 0.25;
	private static final double OUTER_LIFT = 0.04;
	private static final double FLING = 1.5;
	private static final double DRAG = 0.8;
	private static final int DAMAGE_INTERVAL = 20;
	private static final float TORNADO_DAMAGE = 2.0f;
	private static final int DEBRIS_INTERVAL = 2;
	private static final double DEBRIS_REACH = 10.0;
	private static final int SOUND_INTERVAL = 40;

	private record Tornado(ResourceKey<Level> dimension, UUID owner, Vec3 base, long endTick) {
	}

	private static final List<Tornado> tornadoes = new ArrayList<>();

	private UnitedStatesSmash() {
	}

	public static void land(ServerPlayer player, int percent) {
		double power = Mth.clamp(percent, 1, 100) / 100.0;
		ServerLevel level = player.level();
		Vec3 feet = player.position();
		float crater = (float) (MIN_CRATER + EXTRA_CRATER * power);

		Blasts.blast(player, feet, BLAST_RADIUS, Blasts.sparing(player), BLAST_DEBRIS);
		Blasts.carve(level, feet.add(0, crater * BOWL_LIFT, 0), crater, 0);
		double rim = crater * Math.sqrt(1 - BOWL_LIFT * BOWL_LIFT);
		ring(player, feet, rim * RING_SCALE);
		shockwave(player, feet, MIN_SHOCKWAVE + EXTRA_SHOCKWAVE * power);
		show(level, feet, rim);

		Vec3 floor = feet.add(0, -crater * (1 - BOWL_LIFT), 0);
		Tornado tornado = new Tornado(level.dimension(), player.getUUID(), floor, level.getGameTime() + TORNADO_TICKS);
		tornadoes.add(tornado);
		TornadoFxPayload fx = new TornadoFxPayload(floor, TORNADO_TICKS);
		for (ServerPlayer viewer : PlayerLookup.around(level, floor, FX_VIEW_DISTANCE)) {
			if (ServerPlayNetworking.canSend(viewer, TornadoFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	/** Cuts the ring trench, racing around and outward from the impact. */
	private static void ring(ServerPlayer player, Vec3 center, double radius) {
		MinecraftServer server = player.level().getServer();
		int points = (int) Math.ceil(Math.PI * 2 * radius / RING_SPACING);
		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2 * i / points;
			Vec3 at = center.add(Math.cos(angle) * radius, -1, Math.sin(angle) * radius);
			Blasts.later(server, (int) (radius / RING_BLOCKS_PER_TICK), () -> Blasts.carve(player.level(), at, RING_WIDTH, 0));
		}
	}

	private static void shockwave(ServerPlayer player, Vec3 center, double range) {
		ServerLevel level = player.level();
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(range),
				entity -> entity != player && entity.isAlive() && entity.position().distanceTo(center) <= range)) {
			Vec3 offset = entity.position().subtract(center);
			double strength = 1 - offset.length() / range;
			Vec3 flat = new Vec3(offset.x, 0, offset.z);
			Vec3 away = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
			entity.hurtServer(level, player.damageSources().playerAttack(player), SHOCKWAVE_DAMAGE * (float) strength);
			entity.push(away.scale(SHOCKWAVE_PUSH * strength).add(0, strength, 0));
			entity.hurtMarked = true;
		}
	}

	private static void show(ServerLevel level, Vec3 center, double rim) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < 6; i++) {
			Vec3 at = center.add((random.nextDouble() * 2 - 1) * rim * 0.5, random.nextDouble() * 4, (random.nextDouble() * 2 - 1) * rim * 0.5);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		for (int i = 0; i < DUST_RING_POINTS; i++) {
			double angle = Math.PI * 2 * i / DUST_RING_POINTS;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			Vec3 at = center.add(out.scale(3)).add(0, 0.5, 0);
			// Count 0 makes the particle fly along (x, y, z) offset at the given speed.
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, out.x, 0.05, out.z, 2.0);
			if (i % 4 == 0) {
				level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			}
		}
		Vec3 up = center.add(0, 1, 0);
		for (int i = 0; i < HEAVY_BOLTS; i++) {
			double angle = Math.PI * 2 * i / HEAVY_BOLTS;
			Vec3 tip = up.add(Math.cos(angle) * HEAVY_BOLT_LENGTH, random.nextDouble() * 8, Math.sin(angle) * HEAVY_BOLT_LENGTH);
			Smash.sendLightning(level, up, tip, 1.0, true);
		}
		level.playSound(null, center.x, center.y, center.z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, 6.0f, 0.5f);
		level.playSound(null, center.x, center.y, center.z, DekuSounds.SMASH_BLAST, SoundSource.PLAYERS, 6.0f, 0.4f);
	}

	public static void tick(MinecraftServer server) {
		Iterator<Tornado> iterator = tornadoes.iterator();
		while (iterator.hasNext()) {
			Tornado tornado = iterator.next();
			ServerLevel level = server.getLevel(tornado.dimension());
			if (level == null || level.getGameTime() >= tornado.endTick()) {
				iterator.remove();
				continue;
			}
			spin(level, tornado);
		}
	}

	/** Pulls everything nearby in and around the funnel, lifts it, and flings it out the top. */
	private static void spin(ServerLevel level, Tornado tornado) {
		Vec3 base = tornado.base();
		long age = tornado.endTick() - level.getGameTime();
		AABB area = new AABB(base.x - TORNADO_REACH, base.y - 2, base.z - TORNADO_REACH,
			base.x + TORNADO_REACH, base.y + TORNADO_HEIGHT, base.z + TORNADO_REACH);
		for (Entity entity : level.getEntities((Entity) null, area, entity -> !entity.getUUID().equals(tornado.owner()) && !entity.isSpectator())) {
			Vec3 offset = entity.position().subtract(base);
			double distance = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
			if (distance > TORNADO_REACH) {
				continue;
			}
			double height = offset.y;
			double strength = 1 - distance / TORNADO_REACH;
			Vec3 inward = distance < 1.0E-3 ? Vec3.ZERO : new Vec3(-offset.x / distance, 0, -offset.z / distance);
			Vec3 around = new Vec3(-inward.z, 0, inward.x);
			Vec3 push;
			if (height > TORNADO_HEIGHT - 5) {
				push = inward.scale(-FLING).add(0, 0.3, 0);
			} else {
				boolean inFunnel = distance < FUNNEL_BASE + height * FUNNEL_FLARE + 2;
				push = inward.scale(PULL + EXTRA_PULL * strength).add(around.scale(SPIN + EXTRA_SPIN * strength))
					.add(0, inFunnel ? CORE_LIFT : OUTER_LIFT, 0);
			}
			entity.setDeltaMovement(entity.getDeltaMovement().scale(DRAG).add(push));
			entity.hurtMarked = true;
			if (entity instanceof LivingEntity living && age % DAMAGE_INTERVAL == 0) {
				living.hurtServer(level, level.damageSources().generic(), TORNADO_DAMAGE);
			}
		}

		if (age % DEBRIS_INTERVAL == 0) {
			rip(level, base);
		}
		if (age % SOUND_INTERVAL == 0) {
			level.playSound(null, base.x, base.y, base.z, DekuSounds.TORNADO, SoundSource.AMBIENT, 4.0f, 0.5f);
		}
	}

	/** Tears a block out of the ground near the tornado's foot and throws it into the wind. */
	private static void rip(ServerLevel level, Vec3 base) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2;
		double distance = random.nextDouble() * DEBRIS_REACH;
		int x = Mth.floor(base.x + Math.cos(angle) * distance);
		int z = Mth.floor(base.z + Math.sin(angle) * distance);
		BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0) {
			return;
		}
		FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
		block.disableDrop();
		block.setDeltaMovement(new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(0.5).add(0, 0.8, 0));
		block.hurtMarked = true;
	}
}
