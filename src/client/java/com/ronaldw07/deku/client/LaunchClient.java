package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuItems;
import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.network.LaunchPayload;
import com.ronaldw07.deku.network.SmashFxPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * What jump does with One For All in hand. On the ground, holding it crouches and charges a
 * launch, and letting go rockets the player toward the crosshair; a quick tap still jumps,
 * on release. In the air, each tap flicks a blast of air backward that pushes the player
 * where they look, and holding it flicks rapidly. A double-tap is left alone so it can
 * start Float.
 */
public final class LaunchClient {
	private static final int CHARGE_DELAY_TICKS = 6; // held shorter than this is a tap
	private static final int FULL_CHARGE_TICKS = 20;
	private static final int DOUBLE_TAP_TICKS = 7;
	private static final int CHARGE_SOUND_INTERVAL = 6;
	private static final double MIN_SPEED = 1.5;
	private static final double EXTRA_SPEED = 3.0;
	private static final double LIFT = 0.4;
	private static final int MIN_FLIGHT_TICKS = 3;
	private static final int MAX_FLIGHT_TICKS = 60;
	private static final double FLICK_SPEED = 0.9;
	private static final double FLICK_KEEP = 0.6; // share of the old speed kept on each flick
	private static final double FLICK_LIFT = 0.15;
	private static final int RAPID_FLICK_DELAY = 4;
	private static final int RAPID_FLICK_INTERVAL = 3;
	private static final int FLICK_POSE_TICKS = 6;
	private static final double HAND_HEIGHT = 1.1;
	private static final double HAND_SIDE = 0.35;
	private static final double FLICK_BOLT_LENGTH = 2.5;
	private static final int FLICK_PUFFS = 4;

	private static boolean wasDown;
	private static int ticksSincePress = DOUBLE_TAP_TICKS + 1;
	private static boolean doubleTap;
	private static int heldTicks;
	private static int flightTicks;
	private static int flickHeldTicks;

	private LaunchClient() {
	}

	/** How far the launch is wound up, 0-100; 0 when not charging. */
	public static int charge() {
		int ticks = heldTicks - CHARGE_DELAY_TICKS;
		return ticks < 0 ? 0 : Math.min(100, 1 + ticks * 100 / FULL_CHARGE_TICKS);
	}

	public static boolean charging() {
		return charge() > 0;
	}

	public static boolean launching() {
		return flightTicks > 0;
	}

	/** Runs once a tick as the keys are read, before the player moves; returns the keys the player acts on. */
	public static Input filter(Input keys) {
		LocalPlayer player = Minecraft.getInstance().player;
		boolean down = keys.jump();
		boolean pressedNow = down && !wasDown;
		boolean releasedNow = !down && wasDown;
		wasDown = down;
		ticksSincePress++;
		if (pressedNow) {
			doubleTap = ticksSincePress <= DOUBLE_TAP_TICKS;
			ticksSincePress = 0;
		}
		if (player == null) {
			heldTicks = 0;
			flightTicks = 0;
			return keys;
		}
		trackFlight(player);

		boolean oneForAll = DekuItems.isHolding(player, DekuItems.ONE_FOR_ALL) && !player.isDeadOrDying();
		if (oneForAll && !player.onGround() && !FloatClient.active() && heldTicks == 0) {
			airFlicks(player, down, pressedNow);
			return keys;
		}
		flickHeldTicks = 0;

		boolean able = DekuItems.isHolding(player, DekuItems.ONE_FOR_ALL) && !player.isDeadOrDying()
			&& !FloatClient.active() && !doubleTap && (heldTicks > 0 || player.onGround() && Cooldowns.ready(Cooldowns.Ability.LAUNCH));
		if (!able) {
			heldTicks = 0;
			return keys;
		}

		if (down) {
			heldTicks++;
			chargeSounds(player);
			return withJump(keys, false, charging());
		}
		if (releasedNow) {
			boolean tap = !charging();
			if (!tap) {
				launch(player);
			}
			heldTicks = 0;
			return withJump(keys, tap, false);
		}
		return keys;
	}

	private static void launch(LocalPlayer player) {
		double power = charge() / 100.0;
		player.setDeltaMovement(player.getLookAngle().scale(MIN_SPEED + EXTRA_SPEED * power).add(0, LIFT, 0));
		player.setOnGround(false); // so this tick's move isn't slowed by ground friction
		if (ClientPlayNetworking.canSend(LaunchPayload.TYPE)) {
			ClientPlayNetworking.send(new LaunchPayload(charge()));
		}
		Cooldowns.start(Cooldowns.Ability.LAUNCH);
		flightTicks = 1;
	}

	private static void airFlicks(LocalPlayer player, boolean down, boolean pressedNow) {
		if (pressedNow) {
			flickHeldTicks = 0;
			flick(player);
			return;
		}
		if (!down || doubleTap) {
			return;
		}
		flickHeldTicks++;
		if (flickHeldTicks >= RAPID_FLICK_DELAY && (flickHeldTicks - RAPID_FLICK_DELAY) % RAPID_FLICK_INTERVAL == 0) {
			flick(player);
		}
	}

	/** Both hands snap back and blast the air behind, pushing the player where they look. */
	private static void flick(LocalPlayer player) {
		Vec3 look = player.getLookAngle();
		player.setDeltaMovement(player.getDeltaMovement().scale(FLICK_KEEP).add(look.scale(FLICK_SPEED)).add(0, FLICK_LIFT, 0));
		Poses.play(Poses.Pose.FLICK, FLICK_POSE_TICKS);

		double yaw = Math.toRadians(player.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 back = look.scale(-1);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 hand = player.position().add(0, HAND_HEIGHT, 0).add(right.scale(side * HAND_SIDE));
			SmashLightning.add(new SmashFxPayload(hand, hand.add(back.scale(FLICK_BOLT_LENGTH)), 0.4f));
			for (int i = 0; i < FLICK_PUFFS; i++) {
				Vec3 v = back.scale(0.4 + player.getRandom().nextDouble() * 0.3);
				player.level().addParticle(ParticleTypes.CLOUD, hand.x, hand.y, hand.z, v.x, v.y, v.z);
			}
		}
		player.level().playLocalSound(player, DekuSounds.SMASH_BLAST, SoundSource.PLAYERS, 0.3f, 1.7f);
	}

	private static void trackFlight(LocalPlayer player) {
		if (flightTicks == 0) {
			return;
		}
		boolean landed = flightTicks >= MIN_FLIGHT_TICKS && (player.onGround() || player.isInWater());
		flightTicks = landed || flightTicks >= MAX_FLIGHT_TICKS || player.isDeadOrDying() ? 0 : flightTicks + 1;
	}

	private static void chargeSounds(LocalPlayer player) {
		int ticks = heldTicks - CHARGE_DELAY_TICKS;
		if (ticks == 0) {
			player.level().playLocalSound(player, DekuSounds.SMASH_WINDUP, SoundSource.PLAYERS, 1.0f, 1.2f);
		}
		if (ticks >= 0 && ticks % CHARGE_SOUND_INTERVAL == 0) {
			player.level().playLocalSound(player, DekuSounds.SMASH_CHARGE, SoundSource.PLAYERS, 0.8f, 0.6f + charge() / 100f);
		}
	}

	/** The same keys with jump and sneak replaced; sneaking while charging makes the player crouch. */
	private static Input withJump(Input keys, boolean jump, boolean crouch) {
		return new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), jump, keys.shift() || crouch, keys.sprint());
	}
}
