package com.ronaldw07.deku.client;

import static com.ronaldw07.deku.network.BlackwhipFxPayload.NO_TARGET;

import com.ronaldw07.deku.client.LightningDraw.Segment;
import com.ronaldw07.deku.network.BlackwhipFxPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Blackwhip's tendrils: a few black strands with a blue glow, braided around each other
 * and writhing, that shoot out from the player's right hand to whatever they've grabbed.
 */
final class BlackwhipTendrils {
	private static final int EXTEND_TICKS = 3;
	private static final int STRANDS = 3;
	private static final double SEGMENT_LENGTH = 0.25;
	private static final double WAVE_AMPLITUDE = 0.2;
	private static final double WAVE_LENGTH = 1.5; // blocks per twist
	private static final double WAVE_SPEED = 0.6; // radians per tick
	private static final double HAND_HEIGHT = 0.6; // fraction of body height
	private static final double HAND_SIDE_OFFSET = 0.35;
	private static final float CORE_HALF_WIDTH = 0.05f;
	private static final float GLOW_EDGE_HALF_WIDTH = 0.1f;

	private record Whip(int playerId, int targetId, Vec3 anchor, long startTick, int ticks) {
	}

	private static List<Whip> whips = List.of();

	private BlackwhipTendrils() {
	}

	static void add(BlackwhipFxPayload fx) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		Whip whip = new Whip(fx.playerId(), fx.targetId(), fx.anchor(), level.getGameTime(), fx.ticks());
		whips = Stream.concat(whips.stream(), Stream.of(whip)).toList();
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || whips.isEmpty()) {
			return;
		}

		long now = level.getGameTime();
		whips = whips.stream().filter(whip -> now - whip.startTick() < whip.ticks()).toList();
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		List<Segment> segments = new ArrayList<>();

		for (Whip whip : whips) {
			Entity owner = level.getEntity(whip.playerId());
			if (owner == null) {
				continue;
			}
			Entity target = whip.targetId() == NO_TARGET ? null : level.getEntity(whip.targetId());
			Vec3 hand = handPosition(owner, partialTick);
			Vec3 end = target == null ? whip.anchor() : target.getPosition(partialTick).add(0, target.getBbHeight() / 2, 0);
			double age = now - whip.startTick() + partialTick;
			Vec3 tip = hand.add(end.subtract(hand).scale(Math.min(1, age / EXTEND_TICKS)));
			segments.addAll(braid(hand.subtract(camera), tip.subtract(camera), age));
		}
		if (segments.isEmpty()) {
			return;
		}

		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(),
			(pose, buffer) -> LightningDraw.drawEdges(pose.pose(), buffer, segments, CORE_HALF_WIDTH, GLOW_EDGE_HALF_WIDTH,
				0.15f, 0.55f, 1.0f, 0.6f));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucentEmissive(FireballChargeFx.WHITE),
			(pose, buffer) -> LightningDraw.drawFlat(pose.pose(), buffer, segments, CORE_HALF_WIDTH, 0.02f, 0.02f, 0.03f, 0.95f));
	}

	private static Vec3 handPosition(Entity owner, float partialTick) {
		double yaw = Math.toRadians(owner instanceof LivingEntity living ? living.yBodyRot : owner.getYRot());
		// Facing (-sin, 0, cos) means the right hand is off to (-cos, 0, -sin).
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		return owner.getPosition(partialTick).add(0, owner.getBbHeight() * HAND_HEIGHT, 0).add(right.scale(HAND_SIDE_OFFSET));
	}

	/** Strands spiralling around the line from one point to another, pinned at both ends. */
	static List<Segment> braid(Vec3 from, Vec3 to, double age) {
		Vec3 path = to.subtract(from);
		double length = path.length();
		if (length < 1.0E-3) {
			return List.of();
		}

		Vec3 direction = path.scale(1 / length);
		Vec3 side = direction.cross(new Vec3(0, 1, 0));
		if (side.lengthSqr() < 1.0E-6) {
			side = direction.cross(new Vec3(1, 0, 0));
		}
		side = side.normalize();
		Vec3 up = direction.cross(side);
		int steps = Math.max(4, (int) Math.ceil(length / SEGMENT_LENGTH));
		List<Segment> segments = new ArrayList<>();

		for (int strand = 0; strand < STRANDS; strand++) {
			double phase = strand * Math.PI * 2 / STRANDS;
			Vec3 previous = from;
			for (int i = 1; i <= steps; i++) {
				double t = (double) i / steps;
				double angle = t * length / WAVE_LENGTH * Math.PI * 2 - age * WAVE_SPEED + phase;
				double radius = WAVE_AMPLITUDE * Math.sin(Math.PI * t);
				Vec3 point = from.add(path.scale(t))
					.add(side.scale(Math.cos(angle) * radius))
					.add(up.scale(Math.sin(angle) * radius));
				segments.add(new Segment(previous, point));
				previous = point;
			}
		}
		return segments;
	}
}
