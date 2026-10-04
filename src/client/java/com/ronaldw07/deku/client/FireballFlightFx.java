package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuParticles;
import com.ronaldw07.deku.client.LightningDraw.Layer;
import com.ronaldw07.deku.client.LightningDraw.Segment;
import com.ronaldw07.deku.network.FireballFlightPayload;
import com.ronaldw07.deku.network.FireballFlightPayload.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Glowing orbs in flight: the Cluster Bomb's fireball, Gojo's Blue, Red and Hollow Purple. Each
 * is the same crackling ball that was charged, streaking across the sky trailing its own kind of
 * light, and hanging in place for a while if it is meant to (Blue).
 */
final class FireballFlightFx {
	private static final int BALL_RAYS = 16;
	private static final int TICKS_PER_SHAPE = 2;
	private static final int TRAIL_FIREBALLS = 3;
	private static final int TRAIL_FLAMES = 5;
	private static final int TRAIL_SOOT = 2;
	private static final float TRAIL_FIREBALL_SCALE_PER_RADIUS = 0.35f;
	private static final double TRAIL_SPREAD_PER_RADIUS = 0.35;
	private static final double PULSE_SPEED = 0.5;
	private static final double PULSE_DEPTH = 0.1;
	private static final int TRAIL_STEPS = 3;
	private static final int ORB_TRAIL_DUST = 3;
	private static final int PULL_DUST = 6;
	private static final double PULL_RING_RADIUS = 9.0;
	private static final double PULL_SPEED = 0.5;
	private static final DustParticleOptions BLUE_DUST = new DustParticleOptions(0x3080FF, 2.0f);
	private static final DustParticleOptions RED_DUST = new DustParticleOptions(0xFF2020, 2.0f);
	private static final DustParticleOptions PURPLE_DUST = new DustParticleOptions(0xA040FF, 2.5f);

	private record Flight(Vec3 start, Vec3 end, float ballRadius, float speed, Kind kind, int holdTicks, long startTick) {
		double length() {
			return end.distanceTo(start);
		}

		double flightTicks() {
			return speed <= 0 ? 0 : length() / speed;
		}

		Vec3 positionAt(double ticks) {
			double travelled = speed <= 0 ? length() : Math.min(length(), speed * ticks);
			return start.add(end.subtract(start).scale(travelled / Math.max(length(), 1.0E-3)));
		}

		double radiusAt(double time) {
			return ballRadius * (1 + PULSE_DEPTH * Math.sin(time * PULSE_SPEED));
		}

		boolean over(double ticks) {
			return ticks > flightTicks() + holdTicks;
		}

		Layer[] palette() {
			return switch (kind) {
				case FIRE -> LightningDraw.CRIMSON;
				case BLUE -> LightningDraw.BLUE;
				case RED -> LightningDraw.RED;
				case PURPLE -> LightningDraw.PURPLE;
			};
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
		Flight flight = new Flight(fx.start(), fx.end(), fx.ballRadius(), fx.speed(), fx.kind(), fx.holdTicks(), level.getGameTime());
		flights = Stream.concat(flights.stream(), Stream.of(flight)).toList();
	}

	/** Leaves a trail along the ball's path, and forgets balls that have landed and finished hanging. */
	static void tick(ClientLevel level) {
		if (level == null) {
			flights = List.of();
			return;
		}
		long now = level.getGameTime();
		flights = flights.stream().filter(flight -> !flight.over(now - flight.startTick() - 1)).toList();
		RandomSource random = level.getRandom();
		Minecraft minecraft = Minecraft.getInstance();
		for (Flight flight : flights) {
			double ticks = now - flight.startTick();
			double radius = flight.radiusAt(now);
			double spread = radius * TRAIL_SPREAD_PER_RADIUS;
			boolean hanging = ticks > flight.flightTicks();
			if (flight.kind() == Kind.BLUE && hanging) {
				pullingDust(minecraft, flight.end(), random);
			}
			for (int i = 0; i < TRAIL_STEPS && !hanging; i++) {
				Vec3 at = flight.positionAt(ticks - i * (1.0 / TRAIL_STEPS)).add(LightningDraw.randomDirection(random).scale(spread * random.nextDouble()));
				trail(minecraft, flight.kind(), at, radius, i == 0);
			}
		}
	}

	private static void trail(Minecraft minecraft, Kind kind, Vec3 at, double radius, boolean main) {
		switch (kind) {
			case FIRE -> {
				if (main) {
					puff(minecraft, DekuParticles.FIREBALL, at, (float) (radius * TRAIL_FIREBALL_SCALE_PER_RADIUS), TRAIL_FIREBALLS);
				}
				puff(minecraft, ParticleTypes.FLAME, at, 1f, TRAIL_FLAMES / TRAIL_STEPS + 1);
				puff(minecraft, DekuParticles.SOOT_SMOKE, at, (float) (radius * 0.2), main ? TRAIL_SOOT : 0);
			}
			case BLUE -> {
				puff(minecraft, BLUE_DUST, at, 1f, ORB_TRAIL_DUST);
				puff(minecraft, ParticleTypes.SNOWFLAKE, at, 1f, 1);
			}
			case RED -> {
				puff(minecraft, RED_DUST, at, 1f, ORB_TRAIL_DUST);
				puff(minecraft, ParticleTypes.FLAME, at, 1f, 1);
			}
			case PURPLE -> {
				puff(minecraft, PURPLE_DUST, at, 1f, ORB_TRAIL_DUST + 2);
				puff(minecraft, ParticleTypes.REVERSE_PORTAL, at, 1f, 2);
			}
		}
	}

	/** Specks of blue light spiralling in toward a hanging Blue sphere. */
	private static void pullingDust(Minecraft minecraft, Vec3 center, RandomSource random) {
		for (int i = 0; i < PULL_DUST * DekuSettings.get().detailScale(); i++) {
			Vec3 offset = LightningDraw.randomDirection(random).scale(PULL_RING_RADIUS * (0.4 + random.nextDouble() * 0.6));
			Vec3 at = center.add(offset);
			Vec3 inward = offset.scale(-PULL_SPEED / Math.max(1, offset.length()));
			minecraft.particleEngine.createParticle(BLUE_DUST, at.x, at.y, at.z, inward.x, inward.y, inward.z);
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
		for (Kind kind : Kind.values()) {
			List<Segment> sphere = new ArrayList<>();
			List<Segment> rays = new ArrayList<>();
			Layer[] palette = null;
			for (Flight flight : flights) {
				double ticks = now - flight.startTick() + partialTick;
				if (flight.kind() != kind || flight.over(ticks)) {
					continue;
				}
				palette = flight.palette();
				RandomSource random = RandomSource.create(flight.startTick() * 31 + now / TICKS_PER_SHAPE);
				FireballChargeFx.addBall(sphere, rays, random, flight.positionAt(ticks).subtract(camera), flight.radiusAt(now + partialTick), BALL_RAYS);
			}
			if (palette == null) {
				continue;
			}
			Layer[] colors = palette;
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
				LightningDraw.draw(pose.pose(), buffer, sphere, 6f, colors, 0.7f);
				LightningDraw.draw(pose.pose(), buffer, rays, 4f, colors, 1f);
			});
		}
	}
}
