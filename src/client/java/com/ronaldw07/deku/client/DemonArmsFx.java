package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.LightningDraw.Segment;
import com.ronaldw07.deku.network.DemonArmsFxPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Sukuna's Demon Arms: four extra arms of black, red-banded flesh that grow out of his ribs,
 * each reaching out with a clawed hand, swaying and flexing out of step with the others.
 */
final class DemonArmsFx {
	private static final int SEGMENTS = 5;
	private static final double SEGMENT_LENGTH = 0.34;
	private static final double SHOULDER_SIDE = 0.34;
	private static final double UPPER_HEIGHT = 1.35;
	private static final double LOWER_HEIGHT = 0.95;
	private static final double SHOULDER_BACK = 0.05;
	private static final double SWAY = 0.22;
	private static final double SWAY_SPEED = 0.12;
	private static final float FLESH_WIDTH = 0.1f;
	private static final int CLAWS = 3;
	private static final double CLAW_LENGTH = 0.22;
	private static final double CLAW_SPREAD = 0.12;
	private static final double FIRST_PERSON_DROP = 0.45; // lowered and tucked in so they don't fill the view

	private static Set<UUID> others = Set.of();

	private DemonArmsFx() {
	}

	static void add(DemonArmsFxPayload fx) {
		Set<UUID> next = new HashSet<>(others);
		if (fx.on()) {
			next.add(fx.player());
		} else {
			next.remove(fx.player());
		}
		others = Set.copyOf(next);
	}

	static void reset() {
		others = Set.of();
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		List<Segment> flesh = new ArrayList<>();
		List<Segment> bands = new ArrayList<>();
		List<Segment> claws = new ArrayList<>();
		if (DemonArmsClient.active()) {
			arms(flesh, bands, claws, minecraft.player, partialTick, camera, minecraft.options.getCameraType().isFirstPerson());
		}
		for (UUID id : others) {
			Player other = minecraft.level.getPlayerByUUID(id);
			if (other != null && other != minecraft.player) {
				arms(flesh, bands, claws, other, partialTick, camera, false);
			}
		}
		if (flesh.isEmpty()) {
			return;
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), SolidRender.type(), (pose, buffer) -> {
			LightningDraw.drawFlat(pose.pose(), buffer, flesh, FLESH_WIDTH, 0.06f, 0.03f, 0.05f, 1f);
			LightningDraw.drawFlat(pose.pose(), buffer, bands, FLESH_WIDTH * 1.25f, 0.75f, 0.04f, 0.06f, 1f);
			LightningDraw.drawFlat(pose.pose(), buffer, claws, FLESH_WIDTH * 0.45f, 0.95f, 0.92f, 0.88f, 1f);
		});
	}

	private static void arms(List<Segment> flesh, List<Segment> bands, List<Segment> claws, Player player, float partialTick, Vec3 camera, boolean firstPerson) {
		double yaw = Math.toRadians(player instanceof LivingEntity living ? living.yBodyRot : player.getYRot());
		// Facing (-sin, 0, cos); right-hand side is (-cos, 0, -sin).
		Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 base = player.getPosition(partialTick).subtract(camera);
		double time = player.tickCount + partialTick;
		for (int side = -1; side <= 1; side += 2) {
			for (int row = 0; row < 2; row++) {
				double height = (row == 0 ? UPPER_HEIGHT : LOWER_HEIGHT) - (firstPerson ? FIRST_PERSON_DROP : 0);
				Vec3 shoulder = base.add(right.scale(side * SHOULDER_SIDE)).add(forward.scale(-SHOULDER_BACK)).add(0, height, 0);
				// Upper pair sweeps up and out, lower pair out and forward.
				Vec3 reach = right.scale(side * (row == 0 ? 0.8 : 0.9)).add(forward.scale(row == 0 ? 0.25 : 0.6)).add(0, row == 0 ? 0.55 : -0.2, 0).normalize();
				double phase = time * SWAY_SPEED + side * 1.7 + row * 2.9;
				Vec3 point = shoulder;
				Vec3 heading = reach;
				for (int segment = 0; segment < SEGMENTS; segment++) {
					double bend = Math.sin(phase + segment * 0.9) * SWAY;
					heading = heading.add(right.scale(bend)).add(0, Math.cos(phase * 1.3 + segment) * SWAY * 0.7, 0).normalize();
					Vec3 next = point.add(heading.scale(SEGMENT_LENGTH));
					(segment % 2 == 0 ? flesh : bands).add(new Segment(point, next));
					point = next;
				}
				Vec3 hand = heading;
				Vec3 spread = hand.cross(new Vec3(0, 1, 0)).normalize();
				for (int claw = 0; claw < CLAWS; claw++) {
					double offset = (claw - (CLAWS - 1) / 2.0) * CLAW_SPREAD;
					claws.add(new Segment(point, point.add(hand.scale(CLAW_LENGTH)).add(spread.scale(offset))));
				}
			}
		}
	}
}
