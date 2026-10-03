package com.ronaldw07.deku.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronaldw07.deku.client.LightningDraw.Segment;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Green lightning that crawls over the player's body while Full Cowling is on.
 * Bolts are rebuilt every few ticks so they flicker, and there are more and longer
 * bolts at higher power.
 */
final class CowlingLightning {
	private static final int TICKS_PER_SHAPE = 2;
	private static final int MIN_BOLTS = 2;
	private static final int MAX_BOLTS = 22;
	private static final int MIN_SEGMENTS = 3;
	private static final int EXTRA_SEGMENTS = 3;
	private static final double MIN_SEGMENT_LENGTH = 0.12;
	private static final double MAX_SEGMENT_LENGTH = 0.3;
	private static final double BRANCH_CHANCE = 0.3;
	private static final double BODY_RADIUS = 0.35;
	// In first person the camera sits inside the body, so bolts stay below eye level,
	// a little further out, and thinner; they crackle along the bottom of the view.
	private static final double FIRST_PERSON_RADIUS = 0.5;
	private static final double FIRST_PERSON_EYE_CLEARANCE = 0.35;
	private static final float FIRST_PERSON_WIDTH_SCALE = 0.5f;
	private static final double MAX_SPREAD = 0.15;

	private CowlingLightning() {
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		double percent = FullCowlingClient.percent();
		if (player == null || percent <= 0) {
			return;
		}

		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 offset = player.getPosition(partialTick).subtract(context.levelState().cameraRenderState.pos);
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		double radius = firstPerson ? FIRST_PERSON_RADIUS : BODY_RADIUS;
		double height = firstPerson ? player.getEyeHeight() - FIRST_PERSON_EYE_CLEARANCE : player.getBbHeight();
		float widthScale = firstPerson ? FIRST_PERSON_WIDTH_SCALE : 1f;
		long seed = player.getId() * 31L + player.tickCount / TICKS_PER_SHAPE;
		List<Segment> segments = buildBolts(percent / 100.0, seed, radius, height);

		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(offset);
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lightning(),
			(pose, buffer) -> LightningDraw.drawGreen(pose.pose(), buffer, segments, widthScale));
		poseStack.popPose();
	}

	/** Jagged bolts that wrap around a body of the given radius and height, centered on the feet. */
	static List<Segment> buildBolts(double power, long seed, double radius, double height) {
		RandomSource random = RandomSource.create(seed);
		int bolts = MIN_BOLTS + (int) Math.round(power * (MAX_BOLTS - MIN_BOLTS));
		double segmentLength = MIN_SEGMENT_LENGTH + power * (MAX_SEGMENT_LENGTH - MIN_SEGMENT_LENGTH);
		List<Segment> segments = new ArrayList<>();

		for (int i = 0; i < bolts; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			Vec3 point = new Vec3(Math.cos(angle) * radius, random.nextDouble() * height, Math.sin(angle) * radius);
			// Start running around the body or up/down it, then jag from there.
			Vec3 direction = new Vec3(-Math.sin(angle), random.nextDouble() * 2 - 1, Math.cos(angle))
				.scale(random.nextBoolean() ? 1 : -1);
			int length = MIN_SEGMENTS + random.nextInt(EXTRA_SEGMENTS + 1);

			for (int s = 0; s < length; s++) {
				direction = direction.add(LightningDraw.randomDirection(random).scale(0.9)).normalize();
				double step = segmentLength * (0.6 + random.nextDouble() * 0.8);
				double surface = radius + random.nextDouble() * MAX_SPREAD * power;
				Vec3 next = hugBody(point.add(direction.scale(step)), surface, height);
				segments.add(new Segment(point, next));

				if (random.nextDouble() < BRANCH_CHANCE) {
					Vec3 branchEnd = hugBody(next.add(LightningDraw.randomDirection(random).scale(step * 0.6)), surface + MAX_SPREAD, height);
					segments.add(new Segment(next, branchEnd));
				}
				point = next;
			}
		}
		return segments;
	}

	/** Pulls a point back onto the body's surface so bolts wrap around it instead of flying off. */
	private static Vec3 hugBody(Vec3 point, double surface, double height) {
		double horizontal = Math.sqrt(point.x * point.x + point.z * point.z);
		double scale = horizontal == 0 ? 1 : surface / horizontal;
		return new Vec3(point.x * scale, Math.clamp(point.y, 0, height), point.z * scale);
	}
}
