package com.ronaldw07.deku;

import com.ronaldw07.deku.network.DomainPayload;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gojo's Domain Expansion, Infinite Void: a dome in which everything but the caster is stopped
 * dead. Everything is lifted into the air and held there, mobs frozen, players unable to move
 * or swing, and arrows hanging.
 */
public final class InfiniteVoid {
	public static final float RADIUS = 40.0f;
	public static final int TICKS = 400;
	private static final double FX_VIEW_DISTANCE = 300.0;
	private static final int EFFECT_TICKS = 8; // refreshed every tick; short so it wears off quickly once the void closes
	private static final int FREEZE_SLOWNESS = 9; // enough to stop all walking
	private static final int FREEZE_FATIGUE = 4;
	private static final int LIFT_BLOCKS = 6;
	private static final int SOFT_LANDING_TICKS = 100;

	private record Void(UUID owner, ResourceKey<Level> dimension, Vec3 center, long endTick) {
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
		Void dome = new Void(player.getUUID(), level.dimension(), player.position(), level.getGameTime() + TICKS);
		voids = Stream.concat(voids.stream(), Stream.of(dome)).toList();
		announce(level, dome.center(), TICKS);
		level.playSound(null, dome.center().x, dome.center().y, dome.center().z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, 10.0f, 0.3f);
		level.playSound(null, dome.center().x, dome.center().y, dome.center().z, DekuSounds.EXPLOSION_BOOM, SoundSource.PLAYERS, 8.0f, 0.4f);
	}

	private static void announce(ServerLevel level, Vec3 center, int ticks) {
		DomainPayload fx = new DomainPayload(center, RADIUS, ticks, DomainPayload.Kind.VOID);
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
				announce(level, dome.center(), 0);
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

	/** Lifts a creature into the air the moment it is caught, then holds it there, motionless. */
	private static void hover(ServerLevel level, LivingEntity living) {
		if (hovering.add(living.getUUID())) {
			living.teleportTo(level, living.getX(), living.getY() + liftFor(level, living), living.getZ(), Set.of(), living.getYRot(), living.getXRot(), true);
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

	/** How far up there is room to lift the creature, up to the usual height. */
	private static double liftFor(ServerLevel level, LivingEntity living) {
		double lift = 0;
		while (lift < LIFT_BLOCKS && level.noCollision(living, living.getBoundingBox().move(0, lift + 1, 0))) {
			lift++;
		}
		return lift;
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
		voids = voids.stream().filter(dome -> !dome.owner().equals(player.getUUID())).toList();
	}
}
