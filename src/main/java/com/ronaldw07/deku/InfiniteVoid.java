package com.ronaldw07.deku;

import com.ronaldw07.deku.network.DomainPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gojo's Domain Expansion, Infinite Void: a dome in which everything but the caster is stopped
 * dead. The caster and everything near them are carried high into the sky, the caster on an
 * invisible floor, and everything else is held there, mobs frozen, players unable to move
 * or swing, and arrows hanging.
 */
public final class InfiniteVoid {
	public static final float RADIUS = 40.0f;
	public static final int TICKS = 400;
	private static final double FX_VIEW_DISTANCE = 300.0;
	private static final int EFFECT_TICKS = 8; // refreshed every tick; short so it wears off quickly once the void closes
	private static final int FREEZE_SLOWNESS = 9; // enough to stop all walking
	private static final int FREEZE_FATIGUE = 4;
	private static final double SKY_LIFT = 160.0;
	private static final double SKY_HEADROOM = 50.0;
	private static final int PLATFORM_RADIUS = 3;
	private static final int SOFT_LANDING_TICKS = 100;

	/** One open void: where it sits in the sky, how far it was lifted, who was brought up, and the platform under the caster. */
	private record Void(UUID owner, ResourceKey<Level> dimension, Vec3 center, long endTick, double lift, Map<UUID, Vec3> origins,
			List<BlockPos> platform) {
	}

	private static List<Void> voids = List.of();
	private static final Set<UUID> frozenMobs = new HashSet<>();
	private static final Set<UUID> hovering = new HashSet<>();

	private InfiniteVoid() {
	}

	public static void open(ServerPlayer player) {
		if (voids.stream().anyMatch(open -> open.owner().equals(player.getUUID()))) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 ground = player.position();
		double lift = Math.max(0, Math.min(ground.y + SKY_LIFT, level.getMaxY() - SKY_HEADROOM) - ground.y);
		// Everything caught is carried up into the sky with the caster, keeping where it stood relative to them.
		Map<UUID, Vec3> origins = new HashMap<>();
		origins.put(player.getUUID(), ground);
		for (LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class, new AABB(ground, ground).inflate(RADIUS),
				caught -> caught != player && caught.isAlive() && !caught.isSpectator() && caught.position().distanceTo(ground) <= RADIUS)) {
			origins.put(caught.getUUID(), caught.position());
		}
		List<BlockPos> platform = platform(level, BlockPos.containing(ground.add(0, lift, 0)).below());
		for (Map.Entry<UUID, Vec3> entry : origins.entrySet()) {
			if (level.getEntity(entry.getKey()) instanceof LivingEntity moved) {
				Vec3 to = entry.getValue().add(0, lift, 0);
				moved.teleportTo(level, to.x, to.y, to.z, Set.of(), moved.getYRot(), moved.getXRot(), true);
				moved.setDeltaMovement(Vec3.ZERO);
				moved.fallDistance = 0;
			}
		}
		Void dome = new Void(player.getUUID(), level.dimension(), ground.add(0, lift, 0), level.getGameTime() + TICKS, lift, origins, platform);
		voids = Stream.concat(voids.stream(), Stream.of(dome)).toList();
		announce(level, dome.center(), TICKS, dome.owner());
		level.playSound(null, dome.center().x, dome.center().y, dome.center().z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, 10.0f, 0.3f);
		level.playSound(null, dome.center().x, dome.center().y, dome.center().z, DekuSounds.EXPLOSION_BOOM, SoundSource.PLAYERS, 8.0f, 0.4f);
	}

	private static void announce(ServerLevel level, Vec3 center, int ticks, UUID owner) {
		DomainPayload fx = new DomainPayload(center, RADIUS, ticks, DomainPayload.Kind.VOID, owner);
		for (ServerPlayer viewer : PlayerLookup.around(level, center, RADIUS + FX_VIEW_DISTANCE)) {
			if (ServerPlayNetworking.canSend(viewer, DomainPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	public static void tick(MinecraftServer server) {
		if (voids.isEmpty() && hovering.isEmpty()) {
			return;
		}
		List<Void> ended = voids.stream().filter(dome -> finished(server, dome)).toList();
		voids = voids.stream().filter(dome -> !ended.contains(dome)).toList();
		for (Void dome : ended) {
			ServerLevel level = server.getLevel(dome.dimension());
			if (level != null) {
				announce(level, dome.center(), 0, dome.owner());
				bringDown(level, dome);
			}
		}

		Set<UUID> stillFrozen = new HashSet<>();
		for (Void dome : voids) {
			ServerLevel level = server.getLevel(dome.dimension());
			if (level != null) {
				freezeAll(level, dome, stillFrozen);
			}
		}
		for (UUID id : Set.copyOf(hovering)) {
			if (!stillFrozen.contains(id)) {
				release(server, id);
			}
		}
	}

	private static boolean finished(MinecraftServer server, Void dome) {
		ServerLevel level = server.getLevel(dome.dimension());
		ServerPlayer owner = server.getPlayerList().getPlayer(dome.owner());
		return level == null || owner == null || owner.isDeadOrDying() || level.getGameTime() >= dome.endTick();
	}

	private static void freezeAll(ServerLevel level, Void dome, Set<UUID> stillFrozen) {
		AABB box = new AABB(dome.center(), dome.center()).inflate(RADIUS);
		for (Entity entity : level.getEntities((Entity) null, box,
				entity -> entity.isAlive() && !entity.getUUID().equals(dome.owner()) && entity.position().distanceTo(dome.center()) <= RADIUS)) {
			if (entity instanceof Projectile) {
				entity.setDeltaMovement(Vec3.ZERO);
				entity.hurtMarked = true;
			} else if (entity instanceof LivingEntity living) {
				hover(level, living);
				stillFrozen.add(living.getUUID());
			}
		}
	}

	/** An invisible floor under the caster, so they stand in the sky level with everything they caught. */
	private static List<BlockPos> platform(ServerLevel level, BlockPos center) {
		List<BlockPos> placed = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-PLATFORM_RADIUS, 0, -PLATFORM_RADIUS), center.offset(PLATFORM_RADIUS, 0, PLATFORM_RADIUS))) {
			if (level.getBlockState(pos).isAir()) {
				level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
				placed.add(pos.immutable());
			}
		}
		return placed;
	}

	/** The void closes: the platform goes and everyone carried up is set back where they were. */
	private static void bringDown(ServerLevel level, Void dome) {
		for (BlockPos pos : dome.platform()) {
			if (level.getBlockState(pos).is(Blocks.BARRIER)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		for (Map.Entry<UUID, Vec3> entry : dome.origins().entrySet()) {
			if (level.getEntity(entry.getKey()) instanceof LivingEntity moved && moved.isAlive() && moved.getY() > entry.getValue().y + dome.lift() / 2) {
				// Back to the same spot below wherever they ended up, so a creature knocked about lands near where it was.
				moved.teleportTo(level, moved.getX(), moved.getY() - dome.lift(), moved.getZ(), Set.of(), moved.getYRot(), moved.getXRot(), true);
				moved.setDeltaMovement(Vec3.ZERO);
				moved.fallDistance = 0;
			}
		}
	}

	/** Lifts a creature into the air the moment it is caught, then holds it there, motionless. */
	private static void hover(ServerLevel level, LivingEntity living) {
		if (hovering.add(living.getUUID())) {
			living.setNoGravity(true);
			if (living instanceof Mob mob && !mob.isNoAi()) {
				mob.setNoAi(true);
				frozenMobs.add(mob.getUUID());
			}
		}
		living.setDeltaMovement(Vec3.ZERO);
		if (living instanceof ServerPlayer player) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EFFECT_TICKS, FREEZE_SLOWNESS, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, EFFECT_TICKS, FREEZE_FATIGUE, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS, FREEZE_FATIGUE, false, false));
			player.hurtMarked = true;
		}
	}

	private static void release(MinecraftServer server, UUID id) {
		hovering.remove(id);
		boolean wasFrozenMob = frozenMobs.remove(id);
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof LivingEntity living) {
				living.setNoGravity(false);
				living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SOFT_LANDING_TICKS, 0, false, false));
				if (wasFrozenMob && living instanceof Mob mob) {
					mob.setNoAi(false);
				}
				return;
			}
		}
	}

	/** Whether the player has an Infinite Void open right now. For tests. */
	public static boolean open(UUID player) {
		return voids.stream().anyMatch(dome -> dome.owner().equals(player));
	}

	public static void forget(ServerPlayer player) {
		voids.stream().filter(dome -> dome.owner().equals(player.getUUID())).forEach(dome -> bringDown(player.level(), dome));
		voids = voids.stream().filter(dome -> !dome.owner().equals(player.getUUID())).toList();
	}
}
