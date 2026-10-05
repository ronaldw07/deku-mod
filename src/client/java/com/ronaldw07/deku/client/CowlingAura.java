package com.ronaldw07.deku.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ronaldw07.deku.client.LightningDraw.Segment;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

/**
 * Full Cowling's look on the player's head, seen in third person: spiky hair that stands
 * up as power rises (green, turning blue toward 100%) and flame-like wisps streaming back
 * from the eyes.
 */
final class CowlingAura {
	// Head geometry, in blocks from the neck, matching the player model.
	private static final double NECK_BELOW_EYES = 0.214;
	private static final double HEAD_TOP = 0.5;
	private static final double HEAD_BACK = -0.26;
	private static final double EYE_HEIGHT = 0.205;
	private static final double EYE_SIDE = 0.12;
	private static final double FACE_FRONT = 0.245;

	// Hair: spikes rooted on the top and back of the head.
	private static final double[][] HAIR_ROOTS = {
		{-0.17, HEAD_TOP, -0.17}, {-0.06, HEAD_TOP, -0.17}, {0.06, HEAD_TOP, -0.17}, {0.17, HEAD_TOP, -0.17},
		{-0.17, HEAD_TOP, -0.05}, {-0.06, HEAD_TOP, -0.05}, {0.06, HEAD_TOP, -0.05}, {0.17, HEAD_TOP, -0.05},
		{-0.17, HEAD_TOP, 0.08}, {-0.06, HEAD_TOP, 0.1}, {0.06, HEAD_TOP, 0.1}, {0.17, HEAD_TOP, 0.08},
		{-0.12, 0.35, HEAD_BACK}, {0.12, 0.35, HEAD_BACK}, {-0.12, 0.2, HEAD_BACK}, {0.12, 0.2, HEAD_BACK},
	};
	private static final double MIN_SPIKE_LENGTH = 0.1;
	private static final double MAX_EXTRA_SPIKE_LENGTH = 0.32;
	private static final double SPIKE_HALF_WIDTH = 0.055;
	private static final double GLOW_SCALE = 1.3;
	private static final float[] GREEN_HAIR = {0.08f, 0.42f, 0.2f};
	private static final float[] BLUE_HAIR = {0.3f, 0.75f, 1.0f};

	// Eye wisps.
	private static final int WISP_SEGMENTS = 8;
	private static final double MIN_WISP_LENGTH = 0.35;
	private static final double MAX_EXTRA_WISP_LENGTH = 0.5;
	private static final float WISP_ROOT_HALF_WIDTH = 0.045f;
	private static final double WISP_SPREAD = 0.3; // how far out past the sides of the face the streaks sweep

	/** A point of view on the head: where the neck is and which way the head faces. */
	private record Head(Vec3 neck, Vec3 right, Vec3 up, Vec3 forward) {
		Vec3 at(double x, double y, double z) {
			return neck.add(right.scale(x)).add(up.scale(y)).add(forward.scale(z));
		}

		Vec3 direction(double x, double y, double z) {
			return right.scale(x).add(up.scale(y)).add(forward.scale(z)).normalize();
		}
	}

	private CowlingAura() {
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		double power = FullCowlingClient.percent() / 100.0;
		if (player == null || power <= 0 || minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}

		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Head head = head(player, partialTick, context.levelState().cameraRenderState.pos);
		double time = player.tickCount + partialTick;
		float[] hair = lerpColor(GREEN_HAIR, BLUE_HAIR, (float) (power * power));

		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), SolidRender.type(),
			(pose, buffer) -> drawHair(pose.pose(), buffer, head, power, time, hair, 1.0, 1.0f));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(),
			(pose, buffer) -> {
				drawHair(pose.pose(), buffer, head, power, time, hair, GLOW_SCALE, 0.15f);
				drawWisps(pose.pose(), buffer, head, power, time, brighten(hair));
			});
	}

	/** The head's position and facing, relative to the camera. */
	private static Head head(LocalPlayer player, float partialTick, Vec3 camera) {
		double yaw = Math.toRadians(Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot));
		double pitch = Math.toRadians(player.getXRot(partialTick));
		Vec3 forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 up = right.cross(forward);
		Vec3 neck = player.getPosition(partialTick).add(0, player.getEyeHeight() - NECK_BELOW_EYES, 0).subtract(camera);
		return new Head(neck, right, up, forward);
	}

	private static void drawHair(Matrix4fc pose, VertexConsumer buffer, Head head, double power, double time,
			float[] color, double scale, float alpha) {
		RandomSource random = RandomSource.create(42);
		for (int i = 0; i < HAIR_ROOTS.length; i++) {
			double[] root = HAIR_ROOTS[i];
			// Up, flared outward from the middle of the head, and swept a little back.
			Vec3 direction = head.direction(root[0] * 3 + (random.nextDouble() - 0.5) * 0.4, 1.0,
				-0.35 + (root[2] < HEAD_BACK + 0.01 ? -0.8 : 0) + (random.nextDouble() - 0.5) * 0.3);
			double sway = 1 + 0.06 * Math.sin(time * 0.4 + i);
			double length = (MIN_SPIKE_LENGTH + MAX_EXTRA_SPIKE_LENGTH * power) * (0.75 + 0.5 * random.nextDouble()) * sway * scale;
			Vec3 base = head.at(root[0], root[1], root[2]);
			spike(pose, buffer, base, direction, length, SPIKE_HALF_WIDTH * scale, color, alpha);
		}
	}

	/** A four-sided pyramid from base toward tip, faces shaded by which way they point. */
	private static void spike(Matrix4fc pose, VertexConsumer buffer, Vec3 base, Vec3 direction, double length,
			double halfWidth, float[] color, float alpha) {
		Vec3 tip = base.add(direction.scale(length));
		Vec3 side = direction.cross(new Vec3(0, 0, 1));
		if (side.lengthSqr() < 1.0E-6) {
			side = direction.cross(new Vec3(1, 0, 0));
		}
		Vec3 u = side.normalize().scale(halfWidth);
		Vec3 v = direction.cross(u).normalize().scale(halfWidth);
		Vec3[] corners = {base.add(u), base.add(v), base.subtract(u), base.subtract(v)};

		for (int i = 0; i < corners.length; i++) {
			Vec3 a = corners[i];
			Vec3 b = corners[(i + 1) % corners.length];
			Vec3 normal = b.subtract(a).cross(tip.subtract(a)).normalize();
			float shade = (float) (0.65 + 0.35 * Math.max(0, normal.dot(new Vec3(0.3, 1, 0.2).normalize())));
			// Both windings, so the face shows whichever way round the renderer culls.
			vertex(pose, buffer, a, color, shade, alpha);
			vertex(pose, buffer, b, color, shade, alpha);
			vertex(pose, buffer, tip, color, shade, alpha);
			vertex(pose, buffer, tip, color, shade, alpha);
			vertex(pose, buffer, tip, color, shade, alpha);
			vertex(pose, buffer, tip, color, shade, alpha);
			vertex(pose, buffer, b, color, shade, alpha);
			vertex(pose, buffer, a, color, shade, alpha);
		}
	}

	/** Flickering streaks sweeping out and back from each eye, thinning to nothing at the tail. */
	private static void drawWisps(Matrix4fc pose, VertexConsumer buffer, Head head, double power, double time, float[] color) {
		double length = MIN_WISP_LENGTH + MAX_EXTRA_WISP_LENGTH * power;
		for (int side = -1; side <= 1; side += 2) {
			List<Vec3> points = new ArrayList<>();
			for (int k = 0; k <= WISP_SEGMENTS; k++) {
				double t = (double) k / WISP_SEGMENTS;
				double flicker = Math.sin(time * 1.3 + t * 9 + side) * 0.03 * t;
				points.add(head.at(side * (EYE_SIDE + t * WISP_SPREAD) + flicker,
					EYE_HEIGHT + t * 0.1 + Math.sin(time * 0.7 + t * 5) * 0.02 * t,
					FACE_FRONT + 0.01 - t * length * 0.6));
			}
			for (int k = 0; k < WISP_SEGMENTS; k++) {
				float width = WISP_ROOT_HALF_WIDTH * (1 - (float) k / WISP_SEGMENTS);
				List<Segment> segment = List.of(new Segment(points.get(k), points.get(k + 1)));
				LightningDraw.drawFlat(pose, buffer, segment, width * 2.8f, color[0], color[1], color[2], 0.6f);
				LightningDraw.drawFlat(pose, buffer, segment, width, 0.9f, 1.0f, 0.95f, 0.9f);
			}
		}
	}

	private static void vertex(Matrix4fc pose, VertexConsumer buffer, Vec3 point, float[] color, float shade, float alpha) {
		LightningDraw.finish(buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
			.setColor(color[0] * shade, color[1] * shade, color[2] * shade, alpha));
	}

	private static float[] lerpColor(float[] from, float[] to, float t) {
		return new float[] {Mth.lerp(t, from[0], to[0]), Mth.lerp(t, from[1], to[1]), Mth.lerp(t, from[2], to[2])};
	}

	private static float[] brighten(float[] color) {
		return new float[] {Math.min(1, color[0] + 0.3f), Math.min(1, color[1] + 0.3f), Math.min(1, color[2] + 0.3f)};
	}
}
