package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.LightningDraw.Layer;
import com.ronaldw07.deku.client.LightningDraw.Segment;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * What Gojo's charging moves look like in front of him: a red orb gathering for Red, and for
 * Hollow Purple a blue orb and a red one drawing together until they fuse into purple; plus the
 * faint shimmering sphere of Infinity.
 */
final class GojoFx {
	private static final int TICKS_PER_SHAPE = 2;
	private static final double ORB_DISTANCE = 2.4;
	private static final double ORB_HEIGHT_OFFSET = -0.2;
	private static final double RED_HAND_FORWARD = 2.4;
	private static final double RED_HAND_SIDE = 0.9;
	private static final double MIN_BLUE_BALL = 0.25;
	private static final double MAX_BLUE_BALL = 1.5;
	private static final double SOLID_STARTS = 0.55;
	private static final int RED_BOLTS = 30;
	private static final double RED_BOLT_REACH = 1.6;
	private static final int RED_BOLT_STEPS = 5;
	private static final double RED_BOLT_JAG = 0.25;
	private static final double MIN_RED_BALL = 0.2;
	private static final double MAX_RED_BALL = 1.1;
	private static final double MIN_PURPLE_ORB = 0.25;
	private static final double MAX_PURPLE_ORB = 1.6;
	private static final double ORBIT_START_SPREAD = 2.2;
	private static final double FUSE_AT = 0.85;
	private static final int ORB_RAYS = 8;
	private static final float RUMBLE = 0.3f;
	private static final float GLOW = 0.15f;
	private static final double INFINITY_RADIUS = 1.7;
	private static final double INFINITY_PULSE = 0.08;
	private static final float INFINITY_ALPHA = 0.35f;
	private static final DustParticleOptions PURPLE_DUST = new DustParticleOptions(0x6A20D0, 1.4f);
	private static final DustParticleOptions BLUE_DUST = new DustParticleOptions(0x4090FF, 1.4f);
	private static final DustParticleOptions RED_DUST = new DustParticleOptions(0xFF2020, 1.4f);

	private GojoFx() {
	}

	private static Vec3 front(LocalPlayer player, float partialTick, double distance) {
		return player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(distance)).add(0, ORB_HEIGHT_OFFSET, 0);
	}

	private static Vec3 right(LocalPlayer player, float partialTick) {
		double yaw = Math.toRadians(player.getViewYRot(partialTick));
		return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
	}

	/** Sparks drifting off the orbs, light on the screen and a tremble as Hollow Purple builds. */
	static void tick(LocalPlayer player) {
		if (player == null) {
			return;
		}
		RandomSource random = player.getRandom();
		double purple = GojoClient.purpleCharge() / 100.0;
		double red = GojoClient.redCharge() / 100.0;
		double blue = GojoClient.blueCharge() / 100.0;
		if (blue > 0) {
			ScreenShake.rumble(RUMBLE * (float) (blue * blue));
			Vec3 center = front(player, 1f, ORB_DISTANCE);
			for (int i = 0; i < 3; i++) {
				Vec3 at = center.add(LightningDraw.randomDirection(random).scale(1 + blue * 3));
				Vec3 inward = center.subtract(at).scale(0.1);
				player.level().addParticle(BLUE_DUST, at.x, at.y, at.z, inward.x, inward.y, inward.z);
			}
		}
		if (purple > 0) {
			ScreenShake.rumble(RUMBLE * (float) (purple * purple));
			ScreenShake.glow(GLOW * (float) (purple * purple));
			Vec3 center = front(player, 1f, ORB_DISTANCE);
			for (int i = 0; i < 4; i++) {
				Vec3 at = center.add(LightningDraw.randomDirection(random).scale(0.5 + purple * 1.5));
				player.level().addParticle(purple >= FUSE_AT ? PURPLE_DUST : i % 2 == 0 ? BLUE_DUST : RED_DUST, at.x, at.y, at.z, 0, 0.02, 0);
			}
		}
		if (red > 0) {
			Vec3 at = orbPosition(player, 1f).add(LightningDraw.randomDirection(random).scale(Mth.lerp(red, MIN_RED_BALL, MAX_RED_BALL)));
			player.level().addParticle(RED_DUST, at.x, at.y, at.z, 0, 0.02, 0);
		}
		if (GojoClient.infinity() && player.tickCount % 3 == 0) {
			Vec3 at = player.position().add(LightningDraw.randomDirection(random).scale(INFINITY_RADIUS)).add(0, player.getBbHeight() / 2, 0);
			player.level().addParticle(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 0, 0, 0);
		}
	}

	private static Vec3 orbPosition(LocalPlayer player, float partialTick) {
		return player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(RED_HAND_FORWARD))
			.add(right(player, partialTick).scale(RED_HAND_SIDE)).add(0, -0.5, 0);
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		RandomSource random = RandomSource.create(player.getId() * 31L + player.tickCount / TICKS_PER_SHAPE);
		double time = player.tickCount + partialTick;

		double blue = GojoClient.blueCharge() / 100.0;
		if (blue > 0) {
			draw(context, LightningDraw.BLUE, front(player, partialTick, ORB_DISTANCE).subtract(camera), Mth.lerp(blue, MIN_BLUE_BALL, MAX_BLUE_BALL), random,
				ORB_RAYS + (int) (blue * ORB_RAYS), 1f + (float) blue * 3f);
		}
		double red = GojoClient.redCharge() / 100.0;
		if (red > 0) {
			drawRed(context, orbPosition(player, partialTick).subtract(camera), red, random);
		}

		double purple = GojoClient.purpleCharge() / 100.0;
		if (purple > 0) {
			Vec3 center = front(player, partialTick, ORB_DISTANCE).subtract(camera);
			double size = Mth.lerp(purple, MIN_PURPLE_ORB, MAX_PURPLE_ORB);
			if (purple >= FUSE_AT) {
				draw(context, LightningDraw.PURPLE, center, size * 1.2, random, ORB_RAYS * 2, 2f + (float) purple * 3f);
			} else {
				// A blue orb on one side and a red one on the other, drawing together as the charge builds.
				double spread = ORBIT_START_SPREAD * (1 - purple / FUSE_AT);
				Vec3 side = right(player, partialTick).scale(spread);
				draw(context, LightningDraw.BLUE, center.add(side), size, random, ORB_RAYS, 1.5f + (float) purple * 2f);
				draw(context, LightningDraw.RED, center.subtract(side), size, random, ORB_RAYS, 1.5f + (float) purple * 2f);
			}
		}

		if (GojoClient.infinity()) {
			Vec3 chest = player.getPosition(partialTick).add(0, player.getBbHeight() / 2, 0).subtract(camera);
			double radius = INFINITY_RADIUS * (1 + INFINITY_PULSE * Math.sin(time * 0.3));
			List<Segment> rings = new ArrayList<>();
			FireballChargeFx.addBall(rings, new ArrayList<>(), random, chest, radius, 0);
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(),
				(pose, buffer) -> LightningDraw.draw(pose.pose(), buffer, rings, 2f, LightningDraw.BLUE, INFINITY_ALPHA));
		}
	}

	/**
	 * Red building: first a storm of long bolts crackling in from all around onto the hand; past the
	 * halfway point they die down as the light packs into a smooth ball of red.
	 */
	private static void drawRed(LevelRenderContext context, Vec3 center, double red, RandomSource random) {
		double radius = Mth.lerp(red, MIN_RED_BALL, MAX_RED_BALL);
		List<Segment> sphere = new ArrayList<>();
		List<Segment> bolts = new ArrayList<>();
		double storm = Math.max(0, 1 - Math.max(0, red - SOLID_STARTS) / (1 - SOLID_STARTS));
		int count = (int) (RED_BOLTS * storm);
		for (int i = 0; i < count; i++) {
			Vec3 out = LightningDraw.randomDirection(random);
			Vec3 start = center.add(out.scale(radius * (RED_BOLT_REACH + random.nextDouble() * RED_BOLT_REACH)));
			bolts.addAll(LimbLightning.jagged(random, start, center.add(out.scale(radius * 0.7)), RED_BOLT_STEPS, RED_BOLT_JAG));
		}
		if (red >= SOLID_STARTS) {
			FireballChargeFx.addSmoothBall(sphere, center, radius);
		} else {
			FireballChargeFx.addBall(sphere, bolts, random, center, radius, 0);
		}
		float width = 0.6f + (float) red * 1.4f;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			LightningDraw.draw(pose.pose(), buffer, sphere, width, LightningDraw.RED, 0.6f);
			LightningDraw.draw(pose.pose(), buffer, bolts, width * 0.8f, LightningDraw.RED, 1f);
		});
	}

	private static void draw(LevelRenderContext context, Layer[] palette, Vec3 center, double radius, RandomSource random, int rays, float width) {
		List<Segment> sphere = new ArrayList<>();
		List<Segment> spokes = new ArrayList<>();
		FireballChargeFx.addBall(sphere, spokes, random, center, radius, rays);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			LightningDraw.draw(pose.pose(), buffer, sphere, width * 2, palette, 0.6f);
			LightningDraw.draw(pose.pose(), buffer, spokes, width, palette, 1f);
		});
	}
}
