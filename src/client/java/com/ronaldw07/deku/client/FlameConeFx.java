package com.ronaldw07.deku.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Todoroki's flamethrower drawn as a solid roaring cone of fire pouring from the left hand. */
final class FlameConeFx {
	private static final double LENGTH = 22.0;
	private static final double SPREAD = 0.32;
	private static final double HAND_FORWARD = 0.8;
	private static final double HAND_SIDE = 0.4;
	private static final double HAND_DROP = 0.35;

	private FlameConeFx() {
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !HalfColdHalfHotClient.flaming()) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 look = player.getViewVector(partialTick);
		double yaw = Math.toRadians(player.getViewYRot(partialTick));
		Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		Vec3 hand = player.getEyePosition(partialTick).add(look.scale(HAND_FORWARD)).add(left.scale(HAND_SIDE)).add(0, -HAND_DROP, 0)
			.subtract(context.levelState().cameraRenderState.pos);
		double time = player.tickCount + partialTick;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), SolidRender.darkType(),
			(pose, buffer) -> SolidFireFx.cone(pose.pose(), buffer, hand, look, LENGTH, SPREAD, time));
	}
}
