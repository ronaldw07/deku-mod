package com.ronaldw07.deku;

import com.ronaldw07.deku.network.ExplosionFxPayload.Style;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a left click does with each quirk item in hand: Deku's Black Flash for a well-timed hit,
 * Gojo's teleporting punch, Sukuna's slash combo, Bakugo's point-blank blast and Todoroki's
 * alternating ice and fire strikes.
 */
public final class Melee {
	private static final int MIN_GAP_TICKS = 5;
	// Black Flash: a click landing this long after the one before it.
	private static final int FLASH_MIN_GAP = 8;
	private static final int FLASH_MAX_GAP = 13;
	private static final float FLASH_DAMAGE = 40.0f;
	private static final double FLASH_REACH = 5.0;
	private static final double FLASH_KNOCKBACK = 2.5;
	private static final double TELEPORT_RANGE = 16.0;
	private static final double TELEPORT_STAND_OFF = 1.6;
	private static final float TELEPORT_DAMAGE = 16.0f;
	private static final double COMBO_REACH = 6.0;
	private static final int COMBO_SLASHES = 3;
	private static final double BLAST_REACH = 5.0;
	private static final float BLAST_RADIUS = 2.6f;
	private static final double STRIKE_REACH = 5.0;
	private static final float ICE_DAMAGE = 7.0f;
	private static final float FIRE_DAMAGE = 9.0f;
	private static final int FREEZE_TICKS = 80;
	private static final int BURN_TICKS = 120;
	private static final double FX_VIEW_DISTANCE = 96.0;

	private static final Map<UUID, Long> lastClick = new HashMap<>();
	private static final Map<UUID, Boolean> fireNext = new HashMap<>();

	private Melee() {
	}

	public static void handle(ServerPlayer player) {
		long now = player.level().getGameTime();
		long previous = lastClick.getOrDefault(player.getUUID(), -100L);
		long gap = now - previous;
		if (gap < MIN_GAP_TICKS) {
			return;
		}
		lastClick.put(player.getUUID(), now);
		if (DekuItems.isHolding(player, DekuItems.ONE_FOR_ALL)) {
			blackFlash(player, gap);
		} else if (DekuItems.isHolding(player, DekuItems.GOJO)) {
			teleportPunch(player);
		} else if (DekuItems.isHolding(player, DekuItems.SUKUNA)) {
			Sukuna.combo(player, COMBO_SLASHES, COMBO_REACH);
		} else if (DekuItems.isHolding(player, DekuItems.EXPLOSION)) {
			pointBlank(player);
		} else if (DekuItems.isHolding(player, DekuItems.HALF_COLD_HALF_HOT)) {
			elementalStrike(player);
		}
	}

	private static LivingEntity target(ServerPlayer player, double reach) {
		HitResult hit = Aim.trace(player, reach);
		return hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living ? living : null;
	}

	/** Hit something with a click timed just right after the last one and it lands as a Black Flash. */
	private static void blackFlash(ServerPlayer player, long gap) {
		LivingEntity target = target(player, FLASH_REACH);
		if (target == null || gap < FLASH_MIN_GAP || gap > FLASH_MAX_GAP) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 at = target.getBoundingBox().getCenter();
		target.hurtServer(level, player.damageSources().playerAttack(player), FLASH_DAMAGE);
		target.push(player.getLookAngle().scale(FLASH_KNOCKBACK).add(0, 0.4, 0));
		target.hurtMarked = true;
		BlastFx.send(level, at, 7.0f, Style.SMASH_HIT, at, FX_VIEW_DISTANCE);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 60, 0.6, 0.6, 0.6, 0.6);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 60, 0.6, 0.6, 0.6, 0.6);
		level.playSound(null, at.x, at.y, at.z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, 3.0f, 1.4f);
		player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Black Flash!"));
	}

	/** Blinks to just in front of whatever is in view and hits it, leaving a streak of afterimage behind. */
	private static void teleportPunch(ServerPlayer player) {
		LivingEntity target = target(player, TELEPORT_RANGE);
		if (target == null) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 from = player.position();
		Vec3 toward = target.position().subtract(from).multiply(1, 0, 1).normalize();
		Vec3 to = target.position().subtract(toward.scale(TELEPORT_STAND_OFF));
		for (int i = 0; i <= 8; i++) {
			Vec3 point = from.lerp(to, i / 8.0).add(0, 1, 0);
			level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 3, 0.2, 0.4, 0.2, 0.0);
		}
		player.teleportTo(level, to.x, to.y, to.z, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
		target.hurtServer(level, player.damageSources().playerAttack(player), TELEPORT_DAMAGE);
		target.push(toward.scale(1.2).add(0, 0.3, 0));
		target.hurtMarked = true;
		level.playSound(null, to.x, to.y, to.z, DekuSounds.SMASH_BLAST, SoundSource.PLAYERS, 2.0f, 1.6f);
	}

	/** A blast right on the fist: point-blank and hard to dodge. */
	private static void pointBlank(ServerPlayer player) {
		HitResult hit = Aim.trace(player, BLAST_REACH);
		if (hit.getType() == HitResult.Type.MISS) {
			return;
		}
		Vec3 at = hit.getLocation();
		Bakugo.blast(player, at, BLAST_RADIUS, 0, Style.SHOT, player.getEyePosition(), 0);
	}

	/** Alternates a freezing ice strike and a burning fire strike, left and right like his two sides. */
	private static void elementalStrike(ServerPlayer player) {
		LivingEntity target = target(player, STRIKE_REACH);
		if (target == null) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 at = target.getBoundingBox().getCenter();
		boolean fire = fireNext.getOrDefault(player.getUUID(), false);
		fireNext.put(player.getUUID(), !fire);
		if (fire) {
			target.hurtServer(level, player.damageSources().playerAttack(player), FIRE_DAMAGE);
			target.setRemainingFireTicks(BURN_TICKS);
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 50, 0.5, 0.6, 0.5, 0.15);
			level.playSound(null, at.x, at.y, at.z, DekuSounds.FLAME, SoundSource.PLAYERS, 2.0f, 1.0f);
		} else {
			target.hurtServer(level, player.damageSources().playerAttack(player), ICE_DAMAGE);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FREEZE_TICKS, 4));
			target.setTicksFrozen(target.getTicksRequiredToFreeze() + FREEZE_TICKS);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), at.x, at.y, at.z, 50, 0.5, 0.6, 0.5, 0.2);
			level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 30, 0.5, 0.6, 0.5, 0.1);
			level.playSound(null, at.x, at.y, at.z, DekuSounds.ICE, SoundSource.PLAYERS, 2.0f, 1.2f);
		}
	}

	public static void forget(ServerPlayer player) {
		lastClick.remove(player.getUUID());
		fireNext.remove(player.getUUID());
	}
}
