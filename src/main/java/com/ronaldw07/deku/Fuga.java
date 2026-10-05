package com.ronaldw07.deku;

import com.ronaldw07.deku.network.ExplosionFxPayload.Style;
import com.ronaldw07.deku.network.FireballFlightPayload.Kind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Sukuna's Fuga: a burning arrow drawn for as long as X is held, loosed at the crosshair to land as a huge firestorm. */
public final class Fuga {
	private static final double VIEW_DISTANCE = 300.0;
	private static final double RANGE = 200.0;
	private static final double SPEED = 6.0;
	private static final double HAND_FORWARD = 1.5;
	private static final double HAND_HEIGHT = 1.3;
	private static final float ARROW_SIZE = 1.0f;
	private static final float MIN_RADIUS = 22.0f;
	private static final float MAX_RADIUS = 48.0f;
	private static final double FULL_RADIUS = 36.0; // the size the blast effects scale from
	private static final int DEBRIS_PER_RADIUS = 6;
	private static final int SCORCH_FIRES = 10;
	private static final double BURN_REACH_PER_RADIUS = 1.6;
	private static final int BURN_TICKS = 160;
	private static final int RUMBLE_DELAY_TICKS = 20;

	private Fuga() {
	}

	public static void fire(ServerPlayer player, int charge) {
		ServerLevel level = player.level();
		double power = Mth.clamp(charge, 1, 100) / 100.0;
		Vec3 start = player.position().add(0, HAND_HEIGHT, 0).add(player.getLookAngle().scale(HAND_FORWARD));
		Vec3 target = Aim.trace(player, RANGE).getLocation();
		float radius = (float) Mth.lerp(power, MIN_RADIUS, MAX_RADIUS);
		BlastFx.sendOrb(level, start, target, ARROW_SIZE, SPEED, Kind.ARROW, 0, VIEW_DISTANCE);
		level.playSound(null, start.x, start.y, start.z, DekuSounds.SMASH_BLAST, SoundSource.PLAYERS, 3.0f, 1.4f);
		fly(player, start, target, radius, 1);
	}

	private static void fly(ServerPlayer player, Vec3 start, Vec3 target, float radius, int tick) {
		Blasts.later(player.level().getServer(), 1, () -> {
			if (SPEED * tick >= target.distanceTo(start)) {
				land(player, target, radius);
			} else {
				fly(player, start, target, radius, tick + 1);
			}
		});
	}

	/** The arrow lands: a fire blast with a cloud, a shockwave, and everything near it set alight. */
	private static void land(ServerPlayer player, Vec3 center, float radius) {
		ServerLevel level = player.level();
		Bakugo.blast(player, center, radius, (int) (radius * DEBRIS_PER_RADIUS), Style.FUGA, center, SCORCH_FIRES);
		Bakugo.shockwave(player, center, radius / FULL_RADIUS);
		double reach = radius * BURN_REACH_PER_RADIUS;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(reach),
				entity -> entity != player && entity.isAlive() && entity.position().distanceTo(center) <= reach)) {
			entity.setRemainingFireTicks(BURN_TICKS);
		}
		level.playSound(null, center.x, center.y, center.z, DekuSounds.SMASH_THUNDER, SoundSource.PLAYERS, 6.0f, 0.7f);
		level.playSound(null, center.x, center.y, center.z, DekuSounds.EXPLOSION_BOOM, SoundSource.PLAYERS, 8.0f, 0.6f);
		Blasts.later(level.getServer(), RUMBLE_DELAY_TICKS, () -> level.playSound(null, center.x, center.y, center.z, DekuSounds.EXPLOSION_RUMBLE,
			SoundSource.PLAYERS, 8.0f, 1.0f));
	}
}
