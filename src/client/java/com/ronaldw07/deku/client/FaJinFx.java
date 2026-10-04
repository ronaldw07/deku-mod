package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.LightningDraw.Segment;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Fa Jin on the legs for the launch: while it charges the legs glow deep red with red
 * electricity coiling up them, thicker the more it's charged; it bursts out from the feet
 * on takeoff, and red bolts trail off the legs through the flight.
 */
final class FaJinFx {
	private static final double LEG_SIDE = 0.12;
	private static final double HIP_HEIGHT = 0.75;
	private static final double COIL_RADIUS = 0.17;
	private static final double COIL_STEP = 0.09;
	private static final double COIL_TURN = 0.7; // radians per step
	private static final double COIL_JAG = 0.05;
	private static final int MIN_COILS = 2;
	private static final int EXTRA_COILS = 4;
	private static final float MIN_COIL_WIDTH = 0.5f;
	private static final float EXTRA_COIL_WIDTH = 1.2f;
	private static final float MAX_GLOW_WIDTH = 3.0f;
	private static final int TICKS_PER_SHAPE = 2;
	private static final int MAX_EMBERS_PER_TICK = 4;
	private static final DustParticleOptions EMBER = new DustParticleOptions(0xFF1010, 1.2f);
	// Flight: bolts streaming back along the body from the hips.
	private static final double FLIGHT_LEG_LENGTH = 1.4;
	private static final int FLIGHT_BOLTS = 3;
	private static final double FLIGHT_JAG = 0.15;
	// Takeoff burst.
	private static final int BURST_TICKS = 10;
	private static final int BURST_RAYS = 18;
	private static final double MIN_BURST_LENGTH = 2.5;
	private static final double EXTRA_BURST_LENGTH = 3.0;

	private static Vec3 burstAt;
	private static long burstStart;

	private FaJinFx() {
	}

	/** Red lightning bursts out from the feet as the player takes off. */
	static void burst(LocalPlayer player) {
		burstAt = player.position();
		burstStart = player.level().getGameTime();
	}

	/** Red embers drifting off the legs while charging. */
	static void tick(LocalPlayer player) {
		double charge = LaunchClient.charge() / 100.0;
		if (charge <= 0) {
			return;
		}
		RandomSource random = player.getRandom();
		for (int i = 0; i < 1 + charge * MAX_EMBERS_PER_TICK; i++) {
			Vec3 at = player.position().add((random.nextDouble() - 0.5) * 0.6, random.nextDouble() * HIP_HEIGHT, (random.nextDouble() - 0.5) * 0.6);
			player.level().addParticle(EMBER, at.x, at.y, at.z, 0, 0.05, 0);
		}
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Vec3 feet = player.getPosition(partialTick);
		RandomSource random = RandomSource.create(player.getId() * 31L + player.tickCount / TICKS_PER_SHAPE);

		double charge = LaunchClient.charge() / 100.0;
		if (charge > 0) {
			float yaw = (float) Math.toRadians(Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot));
			Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
			List<Segment> coils = new ArrayList<>();
			List<Segment> glow = new ArrayList<>();
			for (int side = -1; side <= 1; side += 2) {
				Vec3 leg = feet.add(right.scale(side * LEG_SIDE)).subtract(camera);
				glow.add(new Segment(leg, leg.add(0, HIP_HEIGHT, 0)));
				for (int i = 0; i < MIN_COILS + charge * EXTRA_COILS; i++) {
					coils.addAll(coil(random, leg));
				}
			}
			float coilWidth = (float) (MIN_COIL_WIDTH + EXTRA_COIL_WIDTH * charge);
			float glowWidth = (float) (MAX_GLOW_WIDTH * charge);
			draw(context, (pose, buffer) -> {
				LightningDraw.draw(pose.pose(), buffer, glow, glowWidth, LightningDraw.CRIMSON, (float) (0.35 * charge));
				LightningDraw.draw(pose.pose(), buffer, coils, coilWidth, LightningDraw.CRIMSON, 1f);
			});
		}

		if (LaunchClient.launching()) {
			Vec3 hips = player.getBoundingBox().getCenter().subtract(player.position()).add(feet).subtract(camera);
			Vec3 back = player.getLookAngle().scale(-1);
			List<Segment> trail = new ArrayList<>();
			for (int i = 0; i < FLIGHT_BOLTS; i++) {
				trail.addAll(jagged(random, hips, hips.add(back.scale(FLIGHT_LEG_LENGTH * (1 + random.nextDouble()))), FLIGHT_JAG));
			}
			draw(context, (pose, buffer) -> LightningDraw.draw(pose.pose(), buffer, trail, 1.2f, LightningDraw.CRIMSON, 1f));
		}

		long age = burstAt == null ? BURST_TICKS : player.level().getGameTime() - burstStart;
		if (age < BURST_TICKS) {
			Vec3 center = burstAt.subtract(camera);
			RandomSource rays = RandomSource.create(burstStart);
			List<Segment> segments = new ArrayList<>();
			for (int i = 0; i < BURST_RAYS; i++) {
				Vec3 direction = LightningDraw.randomDirection(rays);
				double length = MIN_BURST_LENGTH + rays.nextDouble() * EXTRA_BURST_LENGTH;
				segments.addAll(jagged(random, center, center.add(direction.x * length, Math.abs(direction.y) * length * 0.5, direction.z * length), 0.4));
			}
			float fade = 1f - (float) age / BURST_TICKS;
			draw(context, (pose, buffer) -> LightningDraw.draw(pose.pose(), buffer, segments, 1.5f, LightningDraw.CRIMSON, fade));
		}
	}

	/** One bolt spiralling up a leg from the foot to the hip. */
	private static List<Segment> coil(RandomSource random, Vec3 leg) {
		List<Segment> segments = new ArrayList<>();
		double angle = random.nextDouble() * Math.PI * 2;
		Vec3 point = leg.add(Math.cos(angle) * COIL_RADIUS, 0, Math.sin(angle) * COIL_RADIUS);
		for (double height = COIL_STEP; height <= HIP_HEIGHT; height += COIL_STEP) {
			angle += COIL_TURN * (0.5 + random.nextDouble());
			Vec3 next = leg.add(Math.cos(angle) * COIL_RADIUS, height, Math.sin(angle) * COIL_RADIUS)
				.add(LightningDraw.randomDirection(random).scale(COIL_JAG));
			segments.add(new Segment(point, next));
			point = next;
		}
		return segments;
	}

	private static List<Segment> jagged(RandomSource random, Vec3 from, Vec3 to, double jag) {
		List<Segment> segments = new ArrayList<>();
		int steps = 5;
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

	private static void draw(LevelRenderContext context, CustomGeometryRenderer geometry) {
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), geometry);
	}
}
