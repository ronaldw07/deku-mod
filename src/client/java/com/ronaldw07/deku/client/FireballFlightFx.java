package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuParticles;
import com.ronaldw07.deku.client.LightningDraw.Segment;
import com.ronaldw07.deku.network.FireballFlightPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Cluster Bomb's fireball in flight: the same glowing red ball that was charged, sized to the
 * blast it will become, streaking across the sky with a long trail of fire and black smoke.
 */
final class FireballFlightFx {
	private static final double MAX_BLAST_RADIUS = 40.0;
	private static final double MIN_BLAST_RADIUS = 12.0;
	private static final int BALL_RAYS = 16;
	private static final int TICKS_PER_SHAPE = 2;
	private static final int TRAIL_FIREBALLS = 3;
	private static final int TRAIL_FLAMES = 5;
	private static final int TRAIL_SOOT = 2;
	private static final float TRAIL_FIREBALL_SCALE_PER_RADIUS = 0.35f;
	private static final double TRAIL_SPREAD_PER_RADIUS = 0.35;

	private record Flight(Vec3 start, Vec3 end, float blastRadius, float speed, long startTick) {
		double length() {
			return end.distanceTo(start);
		}

		Vec3 positionAt(double ticks) {
			double travelled = Math.min(length(), speed * ticks);
			return start.add(end.subtract(start).scale(travelled / Math.max(length(), 1.0E-3)));
		}

		double ballRadius(double time) {
			double power = (blastRadius - MIN_BLAST_RADIUS) / (MAX_BLAST_RADIUS - MIN_BLAST_RADIUS);
			return FireballChargeFx.radius(Math.max(0, Math.min(1, power)), time);
		}
	}

	private static List<Flight> flights = List.of();

	private FireballFlightFx() {
	}

	static void add(FireballFlightPayload fx) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		flights = Stream.concat(flights.stream(), Stream.of(new Flight(fx.start(), fx.end(), fx.blastRadius(), fx.speed(), level.getGameTime()))).toList();
	}

	/** Leaves fire and smoke along the ball's path, and forgets balls that have landed. */
	static void tick(ClientLevel level) {
		if (level == null) {
			flights = List.of();
			return;
		}
		long now = level.getGameTime();
		flights = flights.stream().filter(flight -> flight.speed() * (now - flight.startTick()) <= flight.length() + flight.speed()).toList();
		RandomSource random = level.getRandom();
		Minecraft minecraft = Minecraft.getInstance();
		for (Flight flight : flights) {
			double ticks = now - flight.startTick();
			double radius = flight.ballRadius(now);
			double spread = radius * TRAIL_SPREAD_PER_RADIUS;
			// A few puffs spaced along the stretch of sky the ball crossed this tick, so the trail has no gaps.
			for (int i = 0; i < 3; i++) {
				Vec3 at = flight.positionAt(ticks - i * 0.33).add(LightningDraw.randomDirection(random).scale(spread * random.nextDouble()));
				if (i == 0) {
					puff(minecraft, DekuParticles.FIREBALL, at, (float) (radius * TRAIL_FIREBALL_SCALE_PER_RADIUS), TRAIL_FIREBALLS);
				}
				puff(minecraft, ParticleTypes.FLAME, at, 1f, TRAIL_FLAMES / 3 + 1);
				puff(minecraft, DekuParticles.SOOT_SMOKE, at, (float) (radius * 0.2), i == 0 ? TRAIL_SOOT : 0);
			}
		}
	}

	private static void puff(Minecraft minecraft, ParticleOptions options, Vec3 at, float scale, int count) {
		for (int i = 0; i < Math.max(0, count) * DekuSettings.get().detailScale(); i++) {
			var particle = minecraft.particleEngine.createParticle(options, at.x, at.y, at.z, 0, 0.01, 0);
			if (particle != null && scale != 1f) {
				particle.scale(Math.max(0.2f, scale));
			}
		}
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || flights.isEmpty()) {
			return;
		}
		double partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		long now = minecraft.level.getGameTime();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		List<Segment> sphere = new ArrayList<>();
		List<Segment> rays = new ArrayList<>();
		for (Flight flight : flights) {
			double ticks = now - flight.startTick() + partialTick;
			if (flight.speed() * ticks > flight.length()) {
				continue;
			}
			RandomSource random = RandomSource.create(flight.startTick() * 31 + now / TICKS_PER_SHAPE);
			FireballChargeFx.addBall(sphere, rays, random, flight.positionAt(ticks).subtract(camera), flight.ballRadius(now + partialTick), BALL_RAYS);
		}
		if (sphere.isEmpty()) {
			return;
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			LightningDraw.draw(pose.pose(), buffer, sphere, 6f, LightningDraw.CRIMSON, 0.7f);
			LightningDraw.draw(pose.pose(), buffer, rays, 4f, LightningDraw.CRIMSON, 1f);
		});
	}
}
