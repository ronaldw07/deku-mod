package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.LightningDraw.Segment;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Shared shapes for lightning wrapped around the player's arm and legs. */
final class LimbLightning {
	private static final double SHOULDER_HEIGHT = 1.35;
	private static final double SHOULDER_SIDE = 0.3;
	private static final double ARM_LENGTH = 0.7;
	private static final double ARM_DROOP = 0.15;
	// In first person the arm is out of view beside the camera, so the lightning sits where the arm is drawn.
	private static final double FIRST_PERSON_FORWARD = 0.9;
	private static final double FIRST_PERSON_DOWN = 0.3;
	private static final double COIL_RADIUS = 0.11;
	private static final double COIL_STEP = 0.09;
	private static final double COIL_TURN = 0.7;
	private static final double COIL_JAG = 0.04;

	private LimbLightning() {
	}

	/** The right arm's two ends in the world, shoulder first, as it swings toward where the player looks. */
	static Vec3[] rightArm(LocalPlayer player, float partialTick) {
		double yaw = Math.toRadians(player.getViewYRot(partialTick));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 look = player.getViewVector(partialTick);
		Vec3 shoulder = player.getPosition(partialTick).add(0, SHOULDER_HEIGHT, 0).add(right.scale(SHOULDER_SIDE));
		Vec3 fist = shoulder.add(look.scale(ARM_LENGTH)).add(0, -ARM_DROOP, 0);
		if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
			Vec3 shift = look.scale(FIRST_PERSON_FORWARD).add(0, -FIRST_PERSON_DOWN, 0);
			return new Vec3[] {shoulder.add(shift), fist.add(shift)};
		}
		return new Vec3[] {shoulder, fist};
	}

	/** One bolt spiralling from one end of a limb to the other. */
	static List<Segment> coil(RandomSource random, Vec3 from, Vec3 to) {
		Vec3 axis = to.subtract(from);
		double length = axis.length();
		Vec3 along = axis.scale(1 / Math.max(length, 1.0E-3));
		Vec3 helper = Math.abs(along.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 u = along.cross(helper).normalize();
		Vec3 v = along.cross(u);
		List<Segment> segments = new ArrayList<>();
		double angle = random.nextDouble() * Math.PI * 2;
		Vec3 point = from.add(u.scale(Math.cos(angle) * COIL_RADIUS)).add(v.scale(Math.sin(angle) * COIL_RADIUS));
		for (double distance = COIL_STEP; distance <= length; distance += COIL_STEP) {
			angle += COIL_TURN * (0.5 + random.nextDouble());
			Vec3 next = from.add(along.scale(distance))
				.add(u.scale(Math.cos(angle) * COIL_RADIUS)).add(v.scale(Math.sin(angle) * COIL_RADIUS))
				.add(LightningDraw.randomDirection(random).scale(COIL_JAG));
			segments.add(new Segment(point, next));
			point = next;
		}
		return segments;
	}

	/** A bolt from one point to another, kinked along the way so it looks torn rather than drawn. */
	static List<Segment> jagged(RandomSource random, Vec3 from, Vec3 to, int steps, double jag) {
		List<Segment> segments = new ArrayList<>();
		Vec3 point = from;
		for (int i = 1; i <= steps; i++) {
			Vec3 next = from.lerp(to, (double) i / steps);
			if (i < steps) {
				next = next.add(LightningDraw.randomDirection(random).scale(jag));
			}
			segments.add(new Segment(point, next));
			point = next;
		}
		return segments;
	}
}
