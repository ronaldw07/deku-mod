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
import net.minecraft.core.particles.PowerParticleOption;
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
	private static final float PURPLE_RAY_WIDTH = 2f;
	private static final int PURPLE_TRAIL_FLAMES = 5;
	private static final int PURPLE_SPRAY_BOLTS = 45;
	private static final double PURPLE_SPRAY_REACH = 5.0;
	private static final int PURPLE_SPRAY_STEPS = 5;
	private static final double PURPLE_SPRAY_JAG = 0.35;
	private static final double BLUE_BASE_RADIUS = 2.5; // the ball size the pull ring is drawn for
	private static final int ARROW_TRAIL_FLAMES = 3;
	private static final double ARROW_LENGTH = 7.0;
	private static final double ARROW_HEAD_SHARE = 0.22;
	private static final double ARROW_HEAD_WIDTH = 0.14;
	private static final int ARROW_FLETCHES = 4;
	private static final double PULL_RING_RADIUS = 9.0;
	private static final double PULL_SPEED = 0.5;
	private static final DustParticleOptions BLUE_DUST = new DustParticleOptions(0x3080FF, 2.0f);
	private static final DustParticleOptions RED_DUST = new DustParticleOptions(0xFF2020, 2.0f);
	private static final DustParticleOptions PURPLE_DUST = new DustParticleOptions(0x5A12C0, 2.5f);

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
				case ARROW -> LightningDraw.FIRE;
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
				pullingDust(minecraft, flight.end(), random, flight.ballRadius() / BLUE_BASE_RADIUS);
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
			case ARROW -> {
				puff(minecraft, ParticleTypes.FLAME, at, 1f, ARROW_TRAIL_FLAMES);
				puff(minecraft, ParticleTypes.LAVA, at, 1f, 1);
			}
			case PURPLE -> {
				// A path of dark violet fire left burning behind the orb.
				puff(minecraft, PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f), at, 1.6f, PURPLE_TRAIL_FLAMES);
				puff(minecraft, PURPLE_DUST, at, 1f, ORB_TRAIL_DUST + 2);
				puff(minecraft, ParticleTypes.REVERSE_PORTAL, at, 1f, 2);
			}
		}
	}

	/** Specks of blue light spiralling in toward a hanging Blue sphere. */
	private static void pullingDust(Minecraft minecraft, Vec3 center, RandomSource random, double scale) {
		for (int i = 0; i < PULL_DUST * scale * scale * DekuSettings.get().detailScale(); i++) {
			Vec3 offset = LightningDraw.randomDirection(random).scale(PULL_RING_RADIUS * scale * (0.4 + random.nextDouble() * 0.6));
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

	/** A great spray of jagged bolts shooting out in every direction from the orb. */
	private static void addSpray(List<Segment> rays, RandomSource random, Vec3 center, double radius) {
		for (int i = 0; i < PURPLE_SPRAY_BOLTS; i++) {
			Vec3 out = LightningDraw.randomDirection(random);
			Vec3 end = center.add(out.scale(radius * (1.5 + random.nextDouble() * PURPLE_SPRAY_REACH)));
			rays.addAll(LimbLightning.jagged(random, center.add(out.scale(radius * 0.7)), end, PURPLE_SPRAY_STEPS, PURPLE_SPRAY_JAG));
		}
	}

	/** A long burning arrow: a shaft, a barbed head at the tip and a fan of fletching at the tail, with a blazing ball on the point. */
	private static void addArrow(List<Segment> sphere, List<Segment> rays, RandomSource random, Vec3 tip, Vec3 direction, double size) {
		Vec3 helper = Math.abs(direction.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 side = direction.cross(helper).normalize();
		Vec3 up = direction.cross(side);
		Vec3 tail = tip.subtract(direction.scale(ARROW_LENGTH));
		rays.add(new Segment(tail, tip));
		Vec3 headBase = tip.subtract(direction.scale(ARROW_LENGTH * ARROW_HEAD_SHARE));
		for (int i = 0; i < 4; i++) {
			double angle = Math.PI / 2 * i;
			Vec3 out = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).scale(ARROW_LENGTH * ARROW_HEAD_WIDTH);
			rays.add(new Segment(tip, headBase.add(out)));
		}
		for (int i = 0; i < ARROW_FLETCHES; i++) {
			double angle = Math.PI * 2 * i / ARROW_FLETCHES + Math.PI / 4;
			Vec3 out = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).scale(ARROW_LENGTH * ARROW_HEAD_WIDTH);
			rays.add(new Segment(tail.add(direction.scale(ARROW_LENGTH * 0.12)), tail.subtract(direction.scale(ARROW_LENGTH * 0.06)).add(out)));
		}
		FireballChargeFx.addBall(sphere, rays, random, tip, size * 0.6, 8);
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
			List<Vec3> solidCenters = new ArrayList<>();
			List<Double> solidRadii = new ArrayList<>();
			Layer[] palette = null;
			for (Flight flight : flights) {
				double ticks = now - flight.startTick() + partialTick;
				if (flight.kind() != kind || flight.over(ticks)) {
					continue;
				}
				palette = flight.palette();
				RandomSource random = RandomSource.create(flight.startTick() * 31 + now / TICKS_PER_SHAPE);
				Vec3 at = flight.positionAt(ticks).subtract(camera);
				if (kind == Kind.RED) {
					FireballChargeFx.addSmoothBall(sphere, at, flight.radiusAt(now + partialTick));
				} else if (kind == Kind.PURPLE) {
					solidCenters.add(at);
					solidRadii.add(flight.radiusAt(now + partialTick));
					addSpray(rays, random, at, flight.radiusAt(now + partialTick));
				} else if (kind == Kind.ARROW) {
					addArrow(sphere, rays, random, at, flight.end().subtract(flight.start()).normalize(), flight.radiusAt(now + partialTick));
				} else {
					FireballChargeFx.addBall(sphere, rays, random, at, flight.radiusAt(now + partialTick), BALL_RAYS);
				}
			}
			if (palette == null) {
				continue;
			}
			Layer[] colors = palette;
			if (!solidCenters.isEmpty()) {
				// Hollow Purple is a solid ball of deep violet with a brighter heart, not just a glow.
				context.submitNodeCollector().submitCustomGeometry(context.poseStack(), SolidRender.type(), (pose, buffer) -> {
					for (int i = 0; i < solidCenters.size(); i++) {
						FireballChargeFx.drawLitSphere(pose, buffer, solidCenters.get(i), solidRadii.get(i), 0.32f, 0.04f, 0.62f, 0.97f);
						FireballChargeFx.drawLitSphere(pose, buffer, solidCenters.get(i), solidRadii.get(i) * 0.55, 0.62f, 0.3f, 1.0f, 0.97f);
					}
				});
			}
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
				LightningDraw.draw(pose.pose(), buffer, sphere, 6f, colors, 0.7f);
				LightningDraw.draw(pose.pose(), buffer, rays, kind == Kind.PURPLE ? PURPLE_RAY_WIDTH : 4f, colors, 1f);
			});
		}
	}
}
