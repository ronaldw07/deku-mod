package com.ronaldw07.deku.client;

import com.ronaldw07.deku.network.HalfColdHalfHotPayload;
import com.ronaldw07.deku.network.HalfColdHalfHotPayload.Move;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of Half Cold Half Hot, used while its item is in hand: right-click for the ice
 * wave, hold V for the flamethrower, C for the ice wall, X for Flashfreeze Heatwave, and hold
 * jump to slide along on ice.
 */
public final class HalfColdHalfHotClient {
	private static final int ICE_POSE_TICKS = 10;
	private static final int WALL_POSE_TICKS = 15;
	private static final int HEATWAVE_POSE_TICKS = 20;

	private static final double SLIDE_SPEED = 1.1;
	private static final double SLIDE_STEP_HOP = 0.6; // upward speed when the slide runs into something
	private static final double SLIDE_MAX_CLIMB = 0.5; // how steeply looking up or down tilts the slide

	private static boolean flaming;
	private static boolean sliding;

	private HalfColdHalfHotClient() {
	}

	static boolean flaming() {
		return flaming;
	}

	static boolean sliding() {
		return sliding;
	}

	static void tick(LocalPlayer player, boolean holding, boolean useDown, boolean flameDown, boolean wallPressed,
			boolean heatwavePressed, boolean slideDown) {
		if (player == null) {
			flaming = false;
			sliding = false;
			return;
		}
		boolean able = holding && !player.isDeadOrDying();

		boolean nowSliding = able && slideDown;
		if (nowSliding != sliding) {
			sliding = nowSliding;
			send(Move.SLIDE, sliding);
		}
		if (sliding) {
			// Glide where you look; the server lays ice under your feet as you go.
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			Vec3 forward = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
			double climb = Mth.clamp(look.y, -SLIDE_MAX_CLIMB, SLIDE_MAX_CLIMB);
			// Running into a step or wall hops up it, so the slide never stalls against terrain.
			double lift = player.horizontalCollision ? Math.max(climb * SLIDE_SPEED, SLIDE_STEP_HOP) : climb * SLIDE_SPEED;
			player.setDeltaMovement(forward.scale(SLIDE_SPEED).add(0, lift, 0));
			player.setOnGround(false); // so ground friction doesn't eat the speed
		}

		if (able && useDown && Cooldowns.ready(Cooldowns.Ability.ICE_WAVE)) {
			send(Move.ICE_WAVE, true);
			Cooldowns.start(Cooldowns.Ability.ICE_WAVE);
			Poses.play(Poses.Pose.GROUND_TOUCH, ICE_POSE_TICKS);
		}

		boolean nowFlaming = able && flameDown;
		if (nowFlaming != flaming) {
			flaming = nowFlaming;
			send(Move.FLAME, flaming);
		}

		if (able && wallPressed && Cooldowns.ready(Cooldowns.Ability.ICE_WALL)) {
			send(Move.ICE_WALL, true);
			Cooldowns.start(Cooldowns.Ability.ICE_WALL);
			Poses.play(Poses.Pose.GROUND_TOUCH, WALL_POSE_TICKS);
		}

		if (able && heatwavePressed && Cooldowns.ready(Cooldowns.Ability.HEATWAVE)) {
			send(Move.HEATWAVE, true);
			Cooldowns.start(Cooldowns.Ability.HEATWAVE);
			Poses.play(Poses.Pose.AIM_BOTH, HEATWAVE_POSE_TICKS);
		}
	}

	private static void send(Move move, boolean active) {
		if (ClientPlayNetworking.canSend(HalfColdHalfHotPayload.TYPE)) {
			ClientPlayNetworking.send(new HalfColdHalfHotPayload(move, active));
		}
	}
}
