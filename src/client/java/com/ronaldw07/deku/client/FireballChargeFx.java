package com.ronaldw07.deku.client;

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
 * The Cluster Bomb's fireball while it's being charged: a glowing red sphere floating in front of
 * the player, crackling with red rays and shedding embers, bigger and angrier the longer X is held.
 */
final class FireballChargeFx {
	private static final double BALL_DISTANCE = 2.6;
	private static final double BALL_DROP = 0.4;
	private static final double MIN_RADIUS = 0.25;
	private static final double MAX_RADIUS = 1.8;
	private static final int SPHERE_SEGMENTS = 16;
	private static final int MIN_RAYS = 8;
	private static final int EXTRA_RAYS = 12;
	private static final double MIN_RAY_LENGTH = 1.2;
	private static final double EXTRA_RAY_LENGTH = 1.0;
	private static final int RAY_STEPS = 4;
	private static final double RAY_JAG = 0.15;
	private static final int TICKS_PER_SHAPE = 2;
	private static final double PULSE_SPEED = 0.5;
	private static final double PULSE_DEPTH = 0.1;
	private static final int MIN_EMBERS = 3;
	private static final int EXTRA_EMBERS = 6;
	private static final DustParticleOptions EMBER = new DustParticleOptions(0xFF1000, 1.6f);

	private FireballChargeFx() {
	}

	private static Vec3 ballCenter(LocalPlayer player, float partialTick) {
		return player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(BALL_DISTANCE)).add(0, -BALL_DROP, 0);
	}

	private static double radius(double power, double time) {
		return Mth.lerp(power, MIN_RADIUS, MAX_RADIUS) * (1 + PULSE_DEPTH * Math.sin(time * PULSE_SPEED));
	}

	/** Embers shedding off the ball's surface. */
	static void tick(LocalPlayer player) {
		int charge = ExplosionClient.fireballCharge();
		if (player == null || charge == 0) {
			return;
		}
		double power = charge / 100.0;
		Vec3 center = ballCenter(player, 1f);
		double radius = radius(power, player.tickCount);
		RandomSource random = player.getRandom();
		int count = Math.max(1, (int) Math.round((MIN_EMBERS + EXTRA_EMBERS * power) * DekuSettings.get().detailScale()));
		for (int i = 0; i < count; i++) {
			Vec3 at = center.add(LightningDraw.randomDirection(random).scale(radius));
			player.level().addParticle(i % 3 == 0 ? ParticleTypes.FLAME : EMBER, at.x, at.y, at.z, 0, 0.03, 0);
		}
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		int charge = ExplosionClient.fireballCharge();
		if (player == null || charge == 0) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double power = charge / 100.0;
		Vec3 center = ballCenter(player, partialTick).subtract(context.levelState().cameraRenderState.pos);
		double radius = radius(power, player.tickCount + partialTick);
		RandomSource random = RandomSource.create(player.getId() * 31L + player.tickCount / TICKS_PER_SHAPE);

		// A sphere drawn as three crossed rings, with rays leaping off it.
		List<Segment> sphere = new ArrayList<>();
		sphere.addAll(ring(center, radius, 0));
		sphere.addAll(ring(center, radius, 1));
		sphere.addAll(ring(center, radius, 2));
		List<Segment> rays = new ArrayList<>();
		for (int i = 0; i < MIN_RAYS + EXTRA_RAYS * power; i++) {
			Vec3 out = LightningDraw.randomDirection(random);
			double length = radius * (MIN_RAY_LENGTH + random.nextDouble() * EXTRA_RAY_LENGTH);
			rays.addAll(LimbLightning.jagged(random, center.add(out.scale(radius * 0.6)), center.add(out.scale(length)), RAY_STEPS, RAY_JAG));
		}
		float width = (float) (1 + 4 * power);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			LightningDraw.draw(pose.pose(), buffer, sphere, width * 2, LightningDraw.CRIMSON, 0.6f);
			LightningDraw.draw(pose.pose(), buffer, rays, width, LightningDraw.CRIMSON, 1f);
		});
	}

	/** A circle around the ball in one of its three planes. */
	private static List<Segment> ring(Vec3 center, double radius, int plane) {
		List<Segment> segments = new ArrayList<>();
		for (int i = 0; i < SPHERE_SEGMENTS; i++) {
			segments.add(new Segment(onRing(center, radius, plane, i), onRing(center, radius, plane, i + 1)));
		}
		return segments;
	}

	private static Vec3 onRing(Vec3 center, double radius, int plane, int index) {
		double angle = Math.PI * 2 * index / SPHERE_SEGMENTS;
		double a = Math.cos(angle) * radius;
		double b = Math.sin(angle) * radius;
		return switch (plane) {
			case 0 -> center.add(a, b, 0);
			case 1 -> center.add(0, a, b);
			default -> center.add(a, 0, b);
		};
	}
}
