package com.ronaldw07.deku;

import com.ronaldw07.deku.network.ExplosionFxPayload.Style;
import com.ronaldw07.deku.network.FireballFlightPayload.Kind;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A hero-killing villain to fight: huge, tough, and carrying a health bar. Up close it winds up a
 * ground slam, from a distance it throws a ball of red light, and when its target backs off it
 * leaps after them.
 */
public class Villain extends Zombie {
	private static final double SCALE = 1.8;
	private static final double MAX_HEALTH = 400.0;
	private static final double ATTACK_DAMAGE = 12.0;
	private static final double ARMOR = 8.0;
	private static final double KNOCKBACK_RESISTANCE = 0.9;
	private static final double FOLLOW_RANGE = 60.0;
	private static final double SPEED = 0.27;
	private static final double FX_VIEW_DISTANCE = 128.0;
	private static final int MOVE_GAP_TICKS = 30; // a breather after any move
	// Slam.
	private static final double SLAM_TRIGGER_RANGE = 8.0;
	private static final int SLAM_WINDUP_TICKS = 24;
	private static final int SLAM_COOLDOWN_TICKS = 100;
	private static final double SLAM_RADIUS = 10.0;
	private static final float SLAM_DAMAGE = 14.0f;
	private static final double SLAM_PUSH = 1.4;
	// Red orb.
	private static final double ORB_MIN_RANGE = 10.0;
	private static final double ORB_MAX_RANGE = 50.0;
	private static final int ORB_COOLDOWN_TICKS = 90;
	private static final double ORB_SPEED = 1.6;
	private static final float ORB_SIZE = 1.5f;
	private static final double ORB_BLAST_RADIUS = 5.0;
	private static final float ORB_DAMAGE = 12.0f;
	private static final double ORB_HAND_HEIGHT = 2.6;
	// Leap.
	private static final double LEAP_MIN_RANGE = 14.0;
	private static final int LEAP_COOLDOWN_TICKS = 160;
	private static final double LEAP_SPEED = 1.8;
	private static final double LEAP_LIFT = 0.45;

	private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random), this.getDisplayName(),
		BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
	private int slamWindup;
	private int slamCooldown;
	private int orbCooldown;
	private int leapCooldown;
	private int moveGap;

	public Villain(EntityType<? extends Villain> type, Level level) {
		super(type, level);
		this.xpReward = 200;
	}

	public static AttributeSupplier.Builder createVillainAttributes() {
		return Zombie.createAttributes()
			.add(Attributes.MAX_HEALTH, MAX_HEALTH)
			.add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
			.add(Attributes.ARMOR, ARMOR)
			.add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
			.add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.SCALE, SCALE);
	}

	@Override
	public boolean isBaby() {
		return false;
	}

	@Override
	protected boolean isSunSensitive() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	@Override
	public void setCustomName(Component name) {
		super.setCustomName(name);
		bossEvent.setName(this.getDisplayName());
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bossEvent.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossEvent.removePlayer(player);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
		slamCooldown = Math.max(0, slamCooldown - 1);
		orbCooldown = Math.max(0, orbCooldown - 1);
		leapCooldown = Math.max(0, leapCooldown - 1);
		moveGap = Math.max(0, moveGap - 1);

		if (slamWindup > 0) {
			windUp(level);
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive() || moveGap > 0) {
			return;
		}
		double distance = this.distanceTo(target);
		if (distance < SLAM_TRIGGER_RANGE && slamCooldown == 0) {
			slamWindup = SLAM_WINDUP_TICKS;
			level.playSound(null, this.getX(), this.getY(), this.getZ(), DekuSounds.EXPLOSION_CHARGE, SoundSource.HOSTILE, 3.0f, 0.5f);
		} else if (distance > LEAP_MIN_RANGE && leapCooldown == 0 && this.onGround()) {
			leap(level, target);
		} else if (distance > ORB_MIN_RANGE && distance < ORB_MAX_RANGE && orbCooldown == 0 && this.hasLineOfSight(target)) {
			throwOrb(level, target);
		}
	}

	/** Rears back for the slam: stands still, dust gathering at its feet. */
	private void windUp(ServerLevel level) {
		slamWindup--;
		this.getNavigation().stop();
		this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
		level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 0.2, this.getZ(), 6, SLAM_RADIUS * 0.3, 0.1, SLAM_RADIUS * 0.3, 0.2);
		if (slamWindup == 0) {
			slam(level);
		}
	}

	private void slam(ServerLevel level) {
		Vec3 center = this.position();
		BlastFx.send(level, center.add(0, 0.5, 0), (float) SLAM_RADIUS, Style.BIG_SHOT, center, FX_VIEW_DISTANCE);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(SLAM_RADIUS),
				entity -> entity != this && entity.isAlive() && entity.position().distanceTo(center) <= SLAM_RADIUS)) {
			Vec3 offset = entity.position().subtract(center);
			Vec3 away = offset.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : offset.normalize();
			entity.hurtServer(level, this.damageSources().mobAttack(this), SLAM_DAMAGE);
			entity.push(away.scale(SLAM_PUSH).add(0, 0.7, 0));
			entity.hurtMarked = true;
		}
		level.playSound(null, center.x, center.y, center.z, DekuSounds.SMASH_THUNDER, SoundSource.HOSTILE, 5.0f, 0.6f);
		slamCooldown = SLAM_COOLDOWN_TICKS;
		moveGap = MOVE_GAP_TICKS;
	}

	private void throwOrb(ServerLevel level, LivingEntity target) {
		Vec3 start = this.position().add(0, ORB_HAND_HEIGHT, 0);
		Vec3 end = target.getBoundingBox().getCenter();
		BlastFx.sendOrb(level, start, end, ORB_SIZE, ORB_SPEED, Kind.RED, 0, FX_VIEW_DISTANCE);
		level.playSound(null, start.x, start.y, start.z, DekuSounds.SMASH_BLAST, SoundSource.HOSTILE, 3.0f, 0.8f);
		int travel = (int) Math.ceil(start.distanceTo(end) / ORB_SPEED);
		Blasts.later(level.getServer(), travel, () -> orbLands(level, end));
		orbCooldown = ORB_COOLDOWN_TICKS;
		moveGap = MOVE_GAP_TICKS;
	}

	private void orbLands(ServerLevel level, Vec3 point) {
		BlastFx.send(level, point, (float) ORB_BLAST_RADIUS, Style.BIG_SHOT, point, FX_VIEW_DISTANCE);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(ORB_BLAST_RADIUS),
				entity -> entity != this && entity.isAlive() && entity.position().distanceTo(point) <= ORB_BLAST_RADIUS)) {
			entity.hurtServer(level, this.damageSources().mobAttack(this), ORB_DAMAGE);
		}
		level.playSound(null, point.x, point.y, point.z, DekuSounds.EXPLOSION_BOOM, SoundSource.HOSTILE, 3.0f, 1.0f);
	}

	private void leap(ServerLevel level, LivingEntity target) {
		Vec3 toward = target.position().subtract(this.position()).multiply(1, 0, 1).normalize();
		this.setDeltaMovement(toward.scale(LEAP_SPEED).add(0, LEAP_LIFT, 0));
		this.hurtMarked = true;
		level.playSound(null, this.getX(), this.getY(), this.getZ(), DekuSounds.SMASH_BLAST, SoundSource.HOSTILE, 3.0f, 0.5f);
		leapCooldown = LEAP_COOLDOWN_TICKS;
		moveGap = MOVE_GAP_TICKS;
	}
}
