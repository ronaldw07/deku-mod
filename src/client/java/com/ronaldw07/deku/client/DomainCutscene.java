package com.ronaldw07.deku.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * The camera leaves Sukuna the moment the Malevolent Shrine opens and flies out to his side
 * to look across the shrine at the area ahead, swinging slowly round and pushing in as the slashes tear
 * everything in front of him apart, then settles back into his eyes.
 */
public final class DomainCutscene {
	private static final int TICKS = 130;
	private static final int BLEND_IN_TICKS = 14;
	private static final int BLEND_OUT_TICKS = 16;
	private static final double FOCUS_AHEAD = 22.0;
	private static final double FOCUS_HEIGHT = 9.0;
	private static final double START_DISTANCE = 66.0;
	private static final double END_DISTANCE = 54.0;
	private static final double START_HEIGHT = 12.0;
	private static final double END_HEIGHT = 24.0;
	private static final double SWEEP = 0.55; // radians either side of straight behind

	private record Shot(Vec3 position, float yaw, float pitch, float blend) {
	}

	private static Vec3 center = Vec3.ZERO;
	private static double forwardAngle;
	private static long startTick = -1;

	private DomainCutscene() {
	}

	static void start(Vec3 domainCenter, float playerYaw) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || DekuSettings.get().noScreenEffects()) {
			return;
		}
		center = domainCenter;
		forwardAngle = Math.toRadians(playerYaw);
		startTick = minecraft.level.getGameTime();
	}

	public static boolean active() {
		Minecraft minecraft = Minecraft.getInstance();
		return startTick >= 0 && minecraft.level != null && minecraft.level.getGameTime() - startTick < TICKS;
	}

	/** Where the camera should be this frame and how much of the way it is from the player's own view, or null. */
	private static Shot shot() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!active()) {
			return null;
		}
		double elapsed = minecraft.level.getGameTime() - startTick + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double u = Mth.clamp(elapsed / TICKS, 0, 1);
		// Minecraft's yaw 0 faces +z, and a yaw of a turns that to (-sin a, cos a).
		Vec3 forward = new Vec3(-Math.sin(forwardAngle), 0, Math.cos(forwardAngle));
		Vec3 focus = center.add(forward.scale(FOCUS_AHEAD)).add(0, FOCUS_HEIGHT, 0);
		double swing = Math.PI / 2 - SWEEP + 2 * SWEEP * u; // off to one side, so the shrine and the ground ahead are both in shot
		Vec3 behind = new Vec3(-forward.x * Math.cos(swing) - forward.z * Math.sin(swing), 0, -forward.z * Math.cos(swing) + forward.x * Math.sin(swing));
		double distance = Mth.lerp(u, START_DISTANCE, END_DISTANCE);
		Vec3 position = new Vec3(focus.x + behind.x * distance, center.y + Mth.lerp(u, START_HEIGHT, END_HEIGHT), focus.z + behind.z * distance);
		Vec3 look = focus.subtract(position);
		float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
		double edge = Math.min(Math.min(elapsed / BLEND_IN_TICKS, (TICKS - elapsed) / BLEND_OUT_TICKS), 1);
		float blend = (float) (edge * edge * (3 - 2 * edge)); // eased in and out
		return new Shot(position, yaw, pitch, Mth.clamp(blend, 0, 1));
	}

	/** Position to put the camera at, blended from where it already is. */
	public static Vec3 position(Vec3 current) {
		Shot shot = shot();
		return shot == null ? current : current.lerp(shot.position(), shot.blend());
	}

	public static float yaw(float current) {
		Shot shot = shot();
		return shot == null ? current : Mth.rotLerp(shot.blend(), current, shot.yaw());
	}

	public static float pitch(float current) {
		Shot shot = shot();
		return shot == null ? current : Mth.lerp(shot.blend(), current, shot.pitch());
	}

	/** The player stands still, in a stance of its own, while the camera is away. */
	public static Input filter(Input keys) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && active() ? Input.EMPTY : keys;
	}
}
