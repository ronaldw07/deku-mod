package com.ronaldw07.deku;

import com.ronaldw07.deku.network.DomainPayload;
import com.ronaldw07.deku.network.JujutsuPayload.Move;
import com.ronaldw07.deku.network.SlashFxPayload;
import java.util.List;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Sukuna's Cursed Technique: Dismantle slices everything ahead in a flurry of cuts, Cleave
 * cuts a single target with a force that depends on how healthy it is, and Domain Expansion
 * (Malevolent Shrine) fills a huge dome with endless slashes that shred everything inside.
 */
public final class Sukuna {
	private static final double FX_VIEW_DISTANCE = 300.0;
	private static final double SLASH_REACH = 400.0;
	// Dismantle.
	private static final int DISMANTLE_SLASHES = 8;
	private static final double DISMANTLE_LENGTH = 70.0;
	private static final double DISMANTLE_HALF_HEIGHT = 18.0;
	private static final double DISMANTLE_SPREAD = 0.2;
	private static final double DISMANTLE_START = 2.0;
	private static final float DISMANTLE_DAMAGE = 60.0f;
	// Cleave.
	private static final double CLEAVE_RANGE = 40.0;
	private static final double CLEAVE_LENGTH = 30.0;
	private static final double CLEAVE_HALF_HEIGHT = 24.0;
	private static final int CLEAVE_THICKNESS = 2;
	private static final float CLEAVE_BASE_DAMAGE = 60.0f;
	private static final float CLEAVE_HEALTH_SHARE = 0.3f;
	private static final double CLEAVE_SPLASH = 12.0;
	private static final float SLASH_VOLUME = 5.0f;
	private static final double SLASH_SOUND_RANGE_SHARE = 0.5; // the crack is heard from the middle of the cut
	// Domain Expansion.
	private static final float DOMAIN_RADIUS = 60.0f;
	private static final int DOMAIN_TICKS = 200;
	private static final int DOMAIN_SLASH_INTERVAL = 2;
	private static final int DOMAIN_SLASHES = 4;
	private static final double DOMAIN_SLASH_MIN_LENGTH = 30.0;
	private static final double DOMAIN_SLASH_EXTRA_LENGTH = 25.0;
	private static final double DOMAIN_SLASH_HALF_HEIGHT = 12.0;
	private static final float DOMAIN_SLASH_DAMAGE = 40.0f;
	private static final int DOMAIN_CLEAVE_INTERVAL = 10;
	private static final float DOMAIN_CLEAVE_BASE = 3.0f;
	private static final float DOMAIN_CLEAVE_HEALTH_SHARE = 0.08f;
	private static final double DOMAIN_SLASH_AREA_SHARE = 0.85; // how far out slashes land, of the dome's radius
	private static final double DOMAIN_SLASH_DEPTH = 12.0; // slashes land this far above and below the caster's feet

	private record Dome(UUID owner, ResourceKey<Level> dimension, Vec3 center, long endTick) {
	}

	private static List<Dome> domes = List.of();

	private Sukuna() {
	}

	public static void handle(ServerPlayer player, Move move, int charge) {
		if (!DekuItems.isHolding(player, DekuItems.SUKUNA)) {
			return;
		}
		switch (move) {
			case DISMANTLE -> dismantle(player);
			case CLEAVE -> cleave(player);
			case DOMAIN -> openDomain(player);
			case FUGA -> Fuga.fire(player, charge);
			default -> {
			}
		}
	}

	/** A flurry of slashes in a cone ahead, one every tick, each a different angle. */
	private static void dismantle(ServerPlayer player) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		for (int i = 0; i < DISMANTLE_SLASHES; i++) {
			Blasts.later(level.getServer(), i, () -> {
				Vec3 eye = player.getEyePosition();
				Vec3 aim = player.getLookAngle().add(randomOffset(random, DISMANTLE_SPREAD)).normalize();
				slash(level, player, eye.add(aim.scale(DISMANTLE_START)), aim, randomBlade(random, aim), DISMANTLE_LENGTH,
					DISMANTLE_HALF_HEIGHT, 0, DISMANTLE_DAMAGE, false);
			});
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.SMASH_BLAST, SoundSource.PLAYERS, 1.5f, 1.8f);
	}

	/** One heavy cut on whatever the crosshair is on: a mob loses a third of its health on top of the hit. */
	private static void cleave(ServerPlayer player) {
		ServerLevel level = player.level();
		HitResult hit = Aim.trace(player, CLEAVE_RANGE);
		Vec3 point = hit.getLocation();
		Vec3 aim = player.getLookAngle();
		Vec3 start = point.subtract(aim.scale(CLEAVE_LENGTH / 2));
		float damage = CLEAVE_BASE_DAMAGE;
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target) {
			damage += target.getMaxHealth() * CLEAVE_HEALTH_SHARE;
			start = target.getBoundingBox().getCenter().subtract(aim.scale(CLEAVE_LENGTH / 2));
		}
		slash(level, player, start, aim, randomBlade(level.getRandom(), aim), CLEAVE_LENGTH, CLEAVE_HALF_HEIGHT, CLEAVE_THICKNESS, damage, true);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(CLEAVE_SPLASH),
				entity -> entity != player && entity.isAlive())) {
			near.hurtServer(level, player.damageSources().playerAttack(player), damage * 0.3f);
		}
		level.playSound(null, point.x, point.y, point.z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, 2.0f, 1.6f);
	}

	private static Vec3 randomOffset(RandomSource random, double size) {
		return new Vec3((random.nextDouble() - 0.5) * 2 * size, (random.nextDouble() - 0.5) * 2 * size, (random.nextDouble() - 0.5) * 2 * size);
	}

	/** A direction across the slash's path, turned to a random angle so cuts come at every slant. */
	private static Vec3 randomBlade(RandomSource random, Vec3 aim) {
		Vec3 helper = Math.abs(aim.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 first = aim.cross(helper).normalize();
		Vec3 second = aim.cross(first);
		double roll = random.nextDouble() * Math.PI;
		return first.scale(Math.cos(roll)).add(second.scale(Math.sin(roll)));
	}

	/**
	 * Cuts a thin sheet from origin forward along aim: every breakable block in it vanishes and
	 * anything living it crosses is hurt. The sheet spans halfHeight either side along blade.
	 */
	private static void slash(ServerLevel level, ServerPlayer owner, Vec3 origin, Vec3 aim, Vec3 blade, double length,
			double halfHeight, int thickness, float damage, boolean big) {
		Vec3 normal = aim.cross(blade).normalize();
		for (double along = 0; along <= length; along += 1.0) {
			for (double across = -halfHeight; across <= halfHeight; across += 1.0) {
				for (int depth = -thickness; depth <= thickness; depth++) {
					cut(level, BlockPos.containing(origin.add(aim.scale(along)).add(blade.scale(across)).add(normal.scale(depth))));
				}
			}
		}
		Vec3 end = origin.add(aim.scale(length));
		AABB reach = new AABB(origin, end).inflate(halfHeight + 1);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, reach, entity -> entity != owner && entity.isAlive())) {
			Vec3 offset = entity.getBoundingBox().getCenter().subtract(origin);
			double along = offset.dot(aim);
			double across = offset.dot(blade);
			double depth = offset.dot(normal);
			if (along >= -1 && along <= length + 1 && Math.abs(across) <= halfHeight + 1 && Math.abs(depth) <= thickness + 1.2) {
				entity.invulnerableTime = 0; // a flurry of cuts should each land
				entity.hurtServer(level, owner.damageSources().playerAttack(owner), damage);
			}
		}
		// Everything that isn't alive in the way, from boats to dropped items, is simply gone.
		for (Entity thing : level.getEntitiesOfClass(Entity.class, reach, thing -> !(thing instanceof LivingEntity) && !(thing instanceof Player) && thing.isAlive())) {
			Vec3 offset = thing.getBoundingBox().getCenter().subtract(origin);
			if (offset.dot(aim) >= -1 && offset.dot(aim) <= length + 1 && Math.abs(offset.dot(blade)) <= halfHeight + 1 && Math.abs(offset.dot(normal)) <= thickness + 1.2) {
				thing.discard();
			}
		}
		Vec3 middle = origin.add(aim.scale(length * SLASH_SOUND_RANGE_SHARE));
		level.playSound(null, middle.x, middle.y, middle.z, DekuSounds.SMASH_BLAST, SoundSource.PLAYERS, SLASH_VOLUME, 0.4f + level.getRandom().nextFloat() * 0.3f);
		if (big) {
			level.playSound(null, middle.x, middle.y, middle.z, DekuSounds.EXPLOSION_BOOM, SoundSource.PLAYERS, SLASH_VOLUME, 0.5f);
		}
		SlashFxPayload fx = new SlashFxPayload(origin, aim, blade, (float) length, (float) halfHeight, big);
		for (ServerPlayer viewer : PlayerLookup.around(level, origin, FX_VIEW_DISTANCE)) {
			if (ServerPlayNetworking.canSend(viewer, SlashFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	private static void cut(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0) {
			return;
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
	}

	/** Malevolent Shrine: a huge dome around the caster in which slashes rain down for ten seconds. */
	private static void openDomain(ServerPlayer player) {
		if (domes.stream().anyMatch(dome -> dome.owner().equals(player.getUUID()))) {
			return;
		}
		ServerLevel level = player.level();
		Dome dome = new Dome(player.getUUID(), level.dimension(), player.position(), level.getGameTime() + DOMAIN_TICKS);
		domes = Stream.concat(domes.stream(), Stream.of(dome)).toList();
		announce(level, dome.center(), DOMAIN_TICKS);
		level.playSound(null, dome.center().x, dome.center().y, dome.center().z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, 10.0f, 0.4f);
		level.playSound(null, dome.center().x, dome.center().y, dome.center().z, DekuSounds.EXPLOSION_BOOM, SoundSource.PLAYERS, 8.0f, 0.5f);
	}

	private static void announce(ServerLevel level, Vec3 center, int ticks) {
		DomainPayload fx = new DomainPayload(center, DOMAIN_RADIUS, ticks, DomainPayload.Kind.SHRINE);
		for (ServerPlayer viewer : PlayerLookup.around(level, center, DOMAIN_RADIUS + FX_VIEW_DISTANCE)) {
			if (ServerPlayNetworking.canSend(viewer, DomainPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	public static void tick(MinecraftServer server) {
		if (domes.isEmpty()) {
			return;
		}
		List<Dome> ended = domes.stream().filter(dome -> finished(server, dome)).toList();
		domes = domes.stream().filter(dome -> !ended.contains(dome)).toList();
		for (Dome dome : ended) {
			ServerLevel level = server.getLevel(dome.dimension());
			if (level != null) {
				announce(level, dome.center(), 0);
			}
		}
		for (Dome dome : domes) {
			ServerLevel level = server.getLevel(dome.dimension());
			ServerPlayer owner = server.getPlayerList().getPlayer(dome.owner());
			if (level == null || owner == null) {
				continue;
			}
			long age = DOMAIN_TICKS - (dome.endTick() - level.getGameTime());
			if (age % DOMAIN_SLASH_INTERVAL == 0) {
				domainSlashes(level, owner, dome);
			}
			if (age % DOMAIN_CLEAVE_INTERVAL == 0) {
				domainCleave(level, owner, dome);
			}
		}
	}

	private static boolean finished(MinecraftServer server, Dome dome) {
		ServerLevel level = server.getLevel(dome.dimension());
		ServerPlayer owner = server.getPlayerList().getPlayer(dome.owner());
		return level == null || owner == null || owner.isDeadOrDying() || level.getGameTime() >= dome.endTick();
	}

	/** Slashes landing at random spots across the dome, at every angle. */
	private static void domainSlashes(ServerLevel level, ServerPlayer owner, Dome dome) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < DOMAIN_SLASHES; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Math.sqrt(random.nextDouble()) * DOMAIN_RADIUS * DOMAIN_SLASH_AREA_SHARE;
			Vec3 origin = dome.center().add(Math.cos(angle) * distance, (random.nextDouble() - 0.3) * DOMAIN_SLASH_DEPTH,
				Math.sin(angle) * distance);
			Vec3 aim = new Vec3(random.nextDouble() * 2 - 1, (random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 2 - 1).normalize();
			slash(level, owner, origin, aim, randomBlade(random, aim), DOMAIN_SLASH_MIN_LENGTH + random.nextDouble() * DOMAIN_SLASH_EXTRA_LENGTH,
				DOMAIN_SLASH_HALF_HEIGHT, 0, DOMAIN_SLASH_DAMAGE, false);
		}
	}

	/** Every living thing in the dome is cut, whether or not a slash lands on it. */
	private static void domainCleave(ServerLevel level, ServerPlayer owner, Dome dome) {
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(dome.center(), dome.center()).inflate(DOMAIN_RADIUS),
				entity -> entity != owner && entity.isAlive() && entity.position().distanceTo(dome.center()) <= DOMAIN_RADIUS)) {
			entity.invulnerableTime = 0;
			entity.hurtServer(level, owner.damageSources().playerAttack(owner),
				DOMAIN_CLEAVE_BASE + entity.getMaxHealth() * DOMAIN_CLEAVE_HEALTH_SHARE);
		}
	}

	/** Whether the player has a Domain open right now. For tests. */
	public static boolean domainOpen(UUID player) {
		return domes.stream().anyMatch(dome -> dome.owner().equals(player));
	}

	public static void forget(ServerPlayer player) {
		domes = domes.stream().filter(dome -> !dome.owner().equals(player.getUUID())).toList();
	}
}
