package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.LightningDraw.Layer;
import com.ronaldw07.deku.client.LightningDraw.Segment;
import com.ronaldw07.deku.network.ExplosionFxPayload;
import com.ronaldw07.deku.network.ExplosionFxPayload.Style;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The look of an Explosion blast, on top of the vanilla boom: a burst of fiery rays,
 * a shockwave ring racing outward, flames, sparks and smoke, and for shots a streak
 * from the hand. The ground blast burns red.
 */
final class ExplosionFx {
	private static final int LIFETIME_TICKS = 12;
	private static final int GROW_TICKS = 3;
	private static final int TRACER_TICKS = 3;
	private static final int BEAM_TICKS = 7;
	private static final float BEAM_WIDTH = 3.0f;
	private static final int RAYS = 22;
	private static final int RING_SEGMENTS = 40;
	private static final double RING_GROWTH = 1.8;
	private static final int MAX_FLAMES = 160;
	// Howitzer Impact is in a league of its own: it lasts longer, and its shockwave races
	// out to the edge of its 200-block reach, with a second ring chasing the first.
	private static final int HOWITZER_LIFETIME_TICKS = 40;
	private static final double HOWITZER_SHOCKWAVE_RADIUS = 200.0;
	private static final double SECOND_RING_FRACTION = 0.55;
	private static final double HOWITZER_RAY_REACH = 2.5;
	private static final int HOWITZER_SPARKS = 200;
	private static final int HOWITZER_SMOKE_COLUMN = 90;
	private static final double HOWITZER_COLUMN_HEIGHT = 40.0;

	private record Blast(Vec3 center, float radius, Style style, Vec3 from, long startTick) {
	}

	private static List<Blast> blasts = List.of();

	private ExplosionFx() {
	}

	static void add(ExplosionFxPayload fx) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		Blast blast = new Blast(fx.center(), fx.radius(), fx.style(), fx.from(), level.getGameTime());
		blasts = Stream.concat(blasts.stream(), Stream.of(blast)).toList();
		spawnParticles(level, blast);
	}

	private static void spawnParticles(ClientLevel level, Blast blast) {
		RandomSource random = level.getRandom();
		Vec3 c = blast.center();
		double spread = blast.radius() / 3.0;
		int flames = Math.min(MAX_FLAMES, (int) (blast.radius() * blast.radius() * 8));
		for (int i = 0; i < flames; i++) {
			Vec3 v = LightningDraw.randomDirection(random).scale((0.15 + random.nextDouble() * 0.3) * spread);
			level.addParticle(ParticleTypes.FLAME, c.x, c.y, c.z, v.x, v.y, v.z);
		}
		for (int i = 0; i < blast.radius() * 3; i++) {
			level.addParticle(ParticleTypes.LAVA, c.x, c.y, c.z, 0, 0, 0);
		}
		for (int i = 0; i < blast.radius() * 6; i++) {
			Vec3 v = LightningDraw.randomDirection(random).scale(0.08 * spread);
			level.addParticle(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, v.x, v.y + 0.02, v.z);
		}
		if (blast.style() == Style.HOWITZER) {
			level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true, c.x, c.y, c.z, 0, 0, 0);
			for (int i = 0; i < HOWITZER_SPARKS; i++) {
				Vec3 v = LightningDraw.randomDirection(random).scale(0.6 + random.nextDouble() * 0.8);
				level.addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, c.x, c.y, c.z, v.x, Math.abs(v.y), v.z);
			}
			// A mushroom of smoke rising from ground zero.
			for (int i = 0; i < HOWITZER_SMOKE_COLUMN; i++) {
				double height = random.nextDouble() * HOWITZER_COLUMN_HEIGHT;
				double spreadAtHeight = 1.5 + height * 0.3;
				level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
					c.x + (random.nextDouble() - 0.5) * spreadAtHeight, c.y + height, c.z + (random.nextDouble() - 0.5) * spreadAtHeight,
					0, 0.05 + random.nextDouble() * 0.1, 0);
			}
		}
		if (blast.radius() >= 3) {
			for (int i = 0; i < blast.radius() * 2; i++) {
				Vec3 offset = LightningDraw.randomDirection(random).scale(random.nextDouble() * blast.radius() * 0.6);
				level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
					c.x + offset.x, c.y + offset.y, c.z + offset.z, 0, 0.03, 0);
			}
		}
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || blasts.isEmpty()) {
			return;
		}

		long now = minecraft.level.getGameTime();
		blasts = blasts.stream().filter(blast -> now - blast.startTick() < lifetime(blast)).toList();
		double age0 = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;

		for (Blast blast : blasts) {
			double age = now - blast.startTick() + age0;
			int lifetime = lifetime(blast);
			float fade = age < GROW_TICKS ? 1f : (float) Math.max(0, 1 - (age - GROW_TICKS) / (lifetime - GROW_TICKS));
			Layer[] palette = blast.style() == Style.GROUND ? LightningDraw.RED : LightningDraw.FIRE;
			Vec3 center = blast.center().subtract(camera);
			boolean howitzer = blast.style() == Style.HOWITZER;
			List<Segment> rays = rays(center, blast, Math.min(1, age / GROW_TICKS) * (howitzer ? HOWITZER_RAY_REACH : 1));
			double progress = Math.min(1, age / lifetime * 1.5);
			List<Segment> ring = new ArrayList<>(ring(center, howitzer
				? HOWITZER_SHOCKWAVE_RADIUS * (1 - Math.pow(1 - Math.min(1, age / lifetime), 3))
				: blast.radius() * RING_GROWTH * progress));
			if (howitzer) {
				ring.addAll(ring(center, HOWITZER_SHOCKWAVE_RADIUS * SECOND_RING_FRACTION * (1 - Math.pow(1 - Math.min(1, age / lifetime), 2))));
			}
			boolean beam = blast.style() == Style.BIG_SHOT;
			List<Segment> tracer = isShot(blast) && age < (beam ? BEAM_TICKS : TRACER_TICKS)
				? List.of(new Segment(blast.from().subtract(camera), center)) : List.of();
			float tracerWidth = beam ? BEAM_WIDTH * (float) Math.max(0, 1 - age / BEAM_TICKS) : 0.5f;
			float width = blast.radius() / 2;

			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
				LightningDraw.draw(pose.pose(), buffer, rays, width, palette, fade);
				LightningDraw.draw(pose.pose(), buffer, ring, width / 2, palette, fade * 0.8f);
				LightningDraw.draw(pose.pose(), buffer, tracer, tracerWidth, palette, 1f);
			});
		}
	}

	private static int lifetime(Blast blast) {
		return blast.style() == Style.HOWITZER ? HOWITZER_LIFETIME_TICKS : LIFETIME_TICKS;
	}

	private static boolean isShot(Blast blast) {
		return blast.style() == Style.SHOT || blast.style() == Style.BIG_SHOT;
	}

	/** Fiery spikes bursting out from the center, each kinked once so they look torn rather than drawn. */
	private static List<Segment> rays(Vec3 center, Blast blast, double reach) {
		RandomSource random = RandomSource.create(blast.startTick() * 31 + Double.hashCode(blast.center().x));
		List<Segment> segments = new ArrayList<>();
		for (int i = 0; i < RAYS; i++) {
			Vec3 direction = LightningDraw.randomDirection(random);
			double length = blast.radius() * reach * (0.6 + random.nextDouble() * 0.5);
			Vec3 kink = center.add(direction.scale(length * 0.5)).add(LightningDraw.randomDirection(random).scale(length * 0.12));
			segments.add(new Segment(center, kink));
			segments.add(new Segment(kink, center.add(direction.scale(length))));
		}
		return segments;
	}

	/** A flat ring around the center, for the shockwave. */
	private static List<Segment> ring(Vec3 center, double radius) {
		List<Segment> segments = new ArrayList<>();
		for (int i = 0; i < RING_SEGMENTS; i++) {
			double a0 = i * Math.PI * 2 / RING_SEGMENTS;
			double a1 = (i + 1) * Math.PI * 2 / RING_SEGMENTS;
			segments.add(new Segment(
				center.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius),
				center.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius)));
		}
		return segments;
	}
}
