package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.network.ExplosionPayload;
import com.ronaldw07.deku.network.FireballChargePayload;
import com.ronaldw07.deku.network.ExplosionPayload.Move;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the Explosion quirk, used while the Explosion item is in hand:
 * right-click for AP Shot (tap for a big one, hold for rapid fire), double-tap and hold
 * jump to fly, hold V for Howitzer Impact, C for the cross-arm ground blast, and hold X to
 * grow a fireball and throw it.
 */
public final class ExplosionClient {
	private static final int TAP_TICKS = 6; // right-click held for less than this is a tap
	private static final int RAPID_FIRE_INTERVAL = 2;
	private static final int BIG_SHOT_LOAD_TICKS = 8;
	private static final int FULL_GROUND_CHARGE_TICKS = 30;
	private static final int GROUND_CHARGE_SOUND_INTERVAL = 10;
	private static final double FLIGHT_SPEED = 1.3;
	private static final double BOOST_MULTIPLIER = 2.0; // holding sprint while flying
	private static final int SONIC_BOOM_COOLDOWN = 20;
	private static final double HOWITZER_FORWARD_SPEED = 0.8;
	private static final double HOWITZER_CIRCLE_SPEED = 0.75;
	private static final double HOWITZER_TURN = 0.8; // radians per tick around the circle
	private static final int BURST_POSE_TICKS = 10;
	private static final int FULL_FIREBALL_TICKS = 60; // three seconds
	private static final int FULL_HOWITZER_TICKS = 60;
	private static final int FIREBALL_SOUND_INTERVAL = 10;
	private static final int FIREBALL_POSE_REPEAT_TICKS = 8;
	private static final int FIREBALL_POSE_TICKS = 10;

	private static int useHeldTicks;
	private static int bigShotLoad;
	private static final DoubleTapHold flightTap = new DoubleTapHold();
	private static boolean flying;
	private static boolean flightMoving;
	private static boolean boosting;
	private static int boomCooldown;
	private static boolean spinning;
	private static double spinAngle;
	private static int groundCharge;
	private static int fireballTicks;
	private static int reportedFireball;
	private static final int FIREBALL_REPORT_STEP = 10;
	private static final int FIREBALL_REPORT_REPEAT_TICKS = 20;
	private static int spinTicks;

	private ExplosionClient() {
	}

	public static boolean armsCrossed() {
		return groundCharge > 0;
	}

	/** How far the ground blast is wound up, 0-100. */
	public static int groundBlastCharge() {
		return groundCharge * 100 / FULL_GROUND_CHARGE_TICKS;
	}

	/** How far the Cluster Bomb's fireball is grown, 0-100; 0 when not charging. */
	public static int fireballCharge() {
		return fireballTicks == 0 ? 0 : Math.min(100, 1 + fireballTicks * 100 / FULL_FIREBALL_TICKS);
	}

	/** How many ticks the Howitzer spin has lasted. */
	public static int spinTicks() {
		return spinTicks;
	}

	/** How far the Howitzer is wound up, 0-100; 0 when not spinning. */
	public static int howitzerCharge() {
		return spinning ? Math.min(100, 1 + spinTicks * 100 / FULL_HOWITZER_TICKS) : 0;
	}

	/** Flying and going somewhere; hovering in place doesn't count. */
	public static boolean flightMoving() {
		return flying && flightMoving;
	}

	public static boolean flying() {
		return flying;
	}

	public static boolean spinning() {
		return spinning;
	}

	public static boolean rapidFiring() {
		return useHeldTicks >= TAP_TICKS;
	}

	static void tick(LocalPlayer player, boolean holding, boolean useDown, boolean jumpDown, boolean howitzerDown,
			boolean groundBlastDown, boolean clusterDown) {
		if (player == null) {
			useHeldTicks = 0;
			bigShotLoad = 0;
			flying = false;
			spinning = false;
			groundCharge = 0;
			fireballTicks = 0;
			reportedFireball = 0;
			spinTicks = 0;
			return;
		}

		boolean able = holding && !player.isDeadOrDying();
		apShot(player, able && useDown);
		flight(player, able, jumpDown);
		howitzer(player, able && howitzerDown);
		groundBlast(player, able && groundBlastDown);
		fireball(player, able && clusterDown);
		reportFireball(player);
	}

	/** Lets other players see the fireball: sent when it grows a notch, and now and then so it doesn't time out. */
	private static void reportFireball(LocalPlayer player) {
		int charge = fireballCharge();
		boolean changed = charge / FIREBALL_REPORT_STEP != reportedFireball / FIREBALL_REPORT_STEP;
		if (charge > 0 && (changed || player.tickCount % FIREBALL_REPORT_REPEAT_TICKS == 0) || charge == 0 && reportedFireball > 0) {
			if (ClientPlayNetworking.canSend(FireballChargePayload.TYPE)) {
				ClientPlayNetworking.send(new FireballChargePayload(charge));
			}
		}
		reportedFireball = charge;
	}

	/** Holding X grows a red fireball in front of the player; letting go throws it at the crosshair. */
	private static void fireball(LocalPlayer player, boolean down) {
		if (down && (fireballTicks > 0 || Cooldowns.ready(Cooldowns.Ability.CLUSTER))) {
			if (fireballTicks % FIREBALL_SOUND_INTERVAL == 0) {
				player.level().playLocalSound(player, DekuSounds.EXPLOSION_CHARGE, SoundSource.PLAYERS, 1.0f,
					0.5f + 0.8f * fireballCharge() / 100f);
			}
			if (fireballTicks % FIREBALL_POSE_REPEAT_TICKS == 0) {
				Poses.play(Poses.Pose.AIM_BOTH, FIREBALL_POSE_TICKS);
			}
			fireballTicks = Math.min(FULL_FIREBALL_TICKS, fireballTicks + 1);
			return;
		}
		if (fireballTicks > 0) {
			send(Move.CLUSTER, true, fireballCharge());
			fireballTicks = 0;
			Cooldowns.start(Cooldowns.Ability.CLUSTER);
			Poses.play(Poses.Pose.AIM_BOTH, FIREBALL_POSE_TICKS);
		}
	}

	private static void apShot(LocalPlayer player, boolean down) {
		if (bigShotLoad > 0 && --bigShotLoad == 0) {
			send(Move.AP_SHOT_BIG, true, 0);
			Cooldowns.start(Cooldowns.Ability.AP_SHOT);
		}

		if (down) {
			useHeldTicks++;
			if (useHeldTicks >= TAP_TICKS && (useHeldTicks - TAP_TICKS) % RAPID_FIRE_INTERVAL == 0) {
				send(Move.AP_SHOT, true, 0);
			}
			return;
		}

		// A tap loads for a moment, then fires one big shot.
		if (useHeldTicks > 0 && useHeldTicks < TAP_TICKS && bigShotLoad == 0 && Cooldowns.ready(Cooldowns.Ability.AP_SHOT)) {
			bigShotLoad = BIG_SHOT_LOAD_TICKS;
			Poses.play(Poses.Pose.AIM_BOTH, BIG_SHOT_LOAD_TICKS + 4);
			player.level().playLocalSound(player, DekuSounds.EXPLOSION_CHARGE, SoundSource.PLAYERS, 1.0f, 1.0f);
		}
		useHeldTicks = 0;
	}

	private static void flight(LocalPlayer player, boolean able, boolean jumpDown) {
		boolean nowFlying = flightTap.tick(jumpDown, able);
		if (nowFlying != flying) {
			flying = nowFlying;
			send(Move.FLIGHT, flying, 0);
		}
		if (flying) {
			Vec3 velocity = flightVelocity(player);
			flightMoving = !velocity.equals(Vec3.ZERO);
			boostRing(player, velocity.length() > FLIGHT_SPEED * 1.5);
			player.setDeltaMovement(velocity);
		} else {
			flightMoving = false;
		}
	}

	/** A ring of cloud and a sonic boom as the player breaks into a boost. */
	private static void boostRing(LocalPlayer player, boolean nowBoosting) {
		boomCooldown = Math.max(0, boomCooldown - 1);
		if (nowBoosting && !boosting && boomCooldown == 0) {
			boomCooldown = SONIC_BOOM_COOLDOWN;
			player.level().addParticle(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1, player.getZ(), 0, 0, 0);
			for (int i = 0; i < 24; i++) {
				double angle = Math.PI * 2 * i / 24;
				player.level().addParticle(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), Math.cos(angle) * 0.8, 0.05, Math.sin(angle) * 0.8);
			}
			player.level().playLocalSound(player, DekuSounds.SMASH_BLAST, SoundSource.PLAYERS, 1.0f, 1.4f);
		}
		boosting = nowBoosting;
	}

	/**
	 * Hovers in place until a movement key is pressed, then blasts that way: W toward where
	 * the player is looking, S back, A and D sideways.
	 */
	private static Vec3 flightVelocity(LocalPlayer player) {
		Input keys = player.input.keyPresses;
		double forward = (keys.forward() ? 1 : 0) - (keys.backward() ? 1 : 0);
		double strafe = (keys.right() ? 1 : 0) - (keys.left() ? 1 : 0);
		Vec3 look = player.getLookAngle();
		double yaw = Math.toRadians(player.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 direction = look.scale(forward).add(right.scale(strafe));
		double speed = keys.sprint() ? FLIGHT_SPEED * BOOST_MULTIPLIER : FLIGHT_SPEED;
		return direction.lengthSqr() < 1.0E-6 ? Vec3.ZERO : direction.normalize().scale(speed);
	}

	/** Spirals toward wherever the player is looking while the key is held; letting go explodes. */
	private static void howitzer(LocalPlayer player, boolean down) {
		if (down && !spinning && Cooldowns.ready(Cooldowns.Ability.HOWITZER)) {
			spinning = true;
			spinAngle = 0;
			spinTicks = 0;
			send(Move.HOWITZER, true, 0);
		} else if (!down && spinning) {
			int charge = howitzerCharge();
			spinning = false;
			send(Move.HOWITZER, false, charge);
			Cooldowns.start(Cooldowns.Ability.HOWITZER);
		}
		if (!spinning) {
			spinTicks = 0;
			return;
		}

		spinTicks++;
		spinAngle += HOWITZER_TURN;
		Vec3 look = player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look);
		Vec3 circle = right.scale(Math.cos(spinAngle)).add(up.scale(Math.sin(spinAngle))).scale(HOWITZER_CIRCLE_SPEED);
		player.setDeltaMovement(look.scale(HOWITZER_FORWARD_SPEED).add(circle));
	}

	/** Arms stay up in a cross while C is held, charging; letting go makes the ground in front erupt. */
	private static void groundBlast(LocalPlayer player, boolean down) {
		if (down && (groundCharge > 0 || Cooldowns.ready(Cooldowns.Ability.GROUND_BLAST))) {
			if (groundCharge % GROUND_CHARGE_SOUND_INTERVAL == 0) {
				player.level().playLocalSound(player, DekuSounds.EXPLOSION_CHARGE, SoundSource.PLAYERS, 1.0f,
					0.6f + 0.8f * groundBlastCharge() / 100f);
			}
			groundCharge = Math.min(FULL_GROUND_CHARGE_TICKS, groundCharge + 1);
			return;
		}
		if (groundCharge > 0) {
			send(Move.GROUND_BLAST, true, groundBlastCharge());
			groundCharge = 0;
			Cooldowns.start(Cooldowns.Ability.GROUND_BLAST);
			Poses.play(Poses.Pose.BURST, BURST_POSE_TICKS);
		}
	}

	private static void send(Move move, boolean active, int charge) {
		if (ClientPlayNetworking.canSend(ExplosionPayload.TYPE)) {
			ClientPlayNetworking.send(new ExplosionPayload(move, active, charge));
		}
	}
}
