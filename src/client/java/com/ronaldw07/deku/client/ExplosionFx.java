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
	private static final int RAYS = 22;
	private static final int RING_SEGMENTS = 40;
	private static final double RING_GROWTH = 1.8;
	private static final int MAX_FLAMES = 160;

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
		blasts = blasts.stream().filter(blast -> now - blast.startTick() < LIFETIME_TICKS).toList();
		double age0 = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;

		for (Blast blast : blasts) {
			double age = now - blast.startTick() + age0;
			float fade = age < GROW_TICKS ? 1f : (float) Math.max(0, 1 - (age - GROW_TICKS) / (LIFETIME_TICKS - GROW_TICKS));
			Layer[] palette = blast.style() == Style.GROUND ? LightningDraw.RED : LightningDraw.FIRE;
			Vec3 center = blast.center().subtract(camera);
			List<Segment> rays = rays(center, blast, Math.min(1, age / GROW_TICKS));
			List<Segment> ring = ring(center, blast.radius() * RING_GROWTH * Math.min(1, age / LIFETIME_TICKS * 1.5));
			List<Segment> tracer = isShot(blast) && age < TRACER_TICKS
				? List.of(new Segment(blast.from().subtract(camera), center)) : List.of();
			float width = blast.radius() / 2;

			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
				LightningDraw.draw(pose.pose(), buffer, rays, width, palette, fade);
				LightningDraw.draw(pose.pose(), buffer, ring, width / 2, palette, fade * 0.8f);
				LightningDraw.draw(pose.pose(), buffer, tracer, 0.5f, palette, 1f);
			});
		}
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
