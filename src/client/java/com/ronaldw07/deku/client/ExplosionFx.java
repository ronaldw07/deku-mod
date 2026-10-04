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
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The look of an Explosion blast, on top of the vanilla boom: a burst of fiery rays,
 * a shockwave ring racing outward, flames, sparks and smoke, and for shots a streak
 * from the hand. The ground blast and the Cluster Bomb's nuke burn red.
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
	// Howitzer Impact is in a league of its own: it lasts longer, and its shockwave races
	// out to the edge of its 400-block reach, with a second ring chasing the first.
	private static final int HOWITZER_LIFETIME_TICKS = 40;
	private static final double HOWITZER_SHOCKWAVE_RADIUS = 400.0;
	private static final double SECOND_RING_FRACTION = 0.55;
	private static final double HOWITZER_RAY_REACH = 2.5;
	// The Cluster Bomb's nuke: a long-lived flash with a shockwave ring racing out to eight times its radius.
	private static final int NUKE_LIFETIME_TICKS = 30;
	private static final double NUKE_SHOCKWAVE_PER_RADIUS = 8.0;
	private static final double NUKE_RAY_REACH = 2.0;
	// Flashfreeze's dome of ice, drawn as a glowing lattice that cracks open with fire just before it blows.
	private static final int DOME_LIFETIME_TICKS = 36;
	private static final double DOME_SURFACE_OFFSET = 0.7;
	private static final int DOME_LATITUDES = 9;
	private static final int DOME_LONGITUDES = 14;
	private static final int DOME_SEGMENTS = 14;
	private static final int CRACKS_START_TICK = 12;
	private static final int MAX_CRACKS = 40;
	private static final double CRACK_LENGTH_SHARE = 0.5;

	record Blast(Vec3 center, float radius, Style style, Vec3 from, long startTick) {
	}

	private static List<Blast> blasts = List.of();
	private static List<Blast> emitting = List.of();

	private ExplosionFx() {
	}

	static void add(ExplosionFxPayload fx) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		Blast blast = new Blast(fx.center(), fx.radius(), fx.style(), fx.from(), level.getGameTime());
		blasts = Stream.concat(blasts.stream(), Stream.of(blast)).toList();
		emitting = Stream.concat(emitting.stream(), Stream.of(blast)).toList();
		ExplosionSmoke.emit(level, blast, 0);
		if (Minecraft.getInstance().player != null) {
			ScreenShake.blast(blast, Minecraft.getInstance().player.getEyePosition());
		}
	}

	/** Plays each blast's fireball and smoke over the ticks after it goes off. */
	static void tick(ClientLevel level) {
		ExplosionSmoke.tick(level);
		ScreenShake.tick();
		if (level == null) {
			emitting = List.of();
			return;
		}
		long now = level.getGameTime();
		emitting = emitting.stream().filter(blast -> now - blast.startTick() < ExplosionSmoke.emitTicks(blast)).toList();
		for (Blast blast : emitting) {
			if (now > blast.startTick()) {
				ExplosionSmoke.emit(level, blast, (int) (now - blast.startTick()));
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
			Layer[] palette = switch (blast.style()) {
				case GROUND -> LightningDraw.RED;
				case NUKE -> LightningDraw.CRIMSON;
				default -> LightningDraw.FIRE;
			};
			Vec3 center = blast.center().subtract(camera);
			if (blast.style() == Style.ICE_DOME) {
				drawDome(context, blast, center, age);
				continue;
			}
			boolean howitzer = isHowitzer(blast.style());
			boolean nuke = blast.style() == Style.NUKE || blast.style() == Style.HEATWAVE;
			double rayReach = howitzer ? HOWITZER_RAY_REACH : nuke ? NUKE_RAY_REACH : 1;
			List<Segment> rays = rays(center, blast, Math.min(1, age / GROW_TICKS) * rayReach);
			double progress = Math.min(1, age / lifetime * 1.5);
			double outward = 1 - Math.pow(1 - Math.min(1, age / lifetime), 3);
			List<Segment> ring = new ArrayList<>(ring(center, howitzer
				? HOWITZER_SHOCKWAVE_RADIUS * outward
				: nuke ? blast.radius() * NUKE_SHOCKWAVE_PER_RADIUS * outward
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

	/** A lattice of glowing ice over the dome, fading in as it is built, then cracking open with fire. */
	private static void drawDome(LevelRenderContext context, Blast blast, Vec3 center, double age) {
		double radius = blast.radius() + DOME_SURFACE_OFFSET; // just outside the ice, so the glow isn't buried in it
		List<Segment> lattice = new ArrayList<>();
		for (int i = 1; i < DOME_LATITUDES; i++) {
			double polar = Math.PI * i / DOME_LATITUDES;
			for (int j = 0; j < DOME_SEGMENTS * 2; j++) {
				lattice.add(new Segment(onSphere(center, radius, polar, Math.PI * j / DOME_SEGMENTS),
					onSphere(center, radius, polar, Math.PI * (j + 1) / DOME_SEGMENTS)));
			}
		}
		for (int i = 0; i < DOME_LONGITUDES; i++) {
			double around = Math.PI * 2 * i / DOME_LONGITUDES;
			for (int j = 0; j < DOME_SEGMENTS; j++) {
				lattice.add(new Segment(onSphere(center, radius, Math.PI * j / DOME_SEGMENTS, around),
					onSphere(center, radius, Math.PI * (j + 1) / DOME_SEGMENTS, around)));
			}
		}
		List<Segment> cracks = new ArrayList<>();
		if (age > CRACKS_START_TICK) {
			RandomSource random = RandomSource.create(blast.startTick() * 17);
			int count = (int) (MAX_CRACKS * Math.min(1, (age - CRACKS_START_TICK) / (DOME_LIFETIME_TICKS - CRACKS_START_TICK)));
			for (int i = 0; i < count; i++) {
				Vec3 start = center.add(LightningDraw.randomDirection(random).scale(radius));
				Vec3 end = center.add(LightningDraw.randomDirection(random).scale(radius)).lerp(start, 1 - CRACK_LENGTH_SHARE);
				cracks.addAll(LimbLightning.jagged(random, start, end, 5, 0.6));
			}
		}
		float fade = (float) Math.min(1, age / 6);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			LightningDraw.draw(pose.pose(), buffer, lattice, 4f, LightningDraw.ICE, fade);
			LightningDraw.draw(pose.pose(), buffer, cracks, 6f, LightningDraw.FIRE, 1f);
		});
	}

	private static Vec3 onSphere(Vec3 center, double radius, double polar, double around) {
		return center.add(radius * Math.sin(polar) * Math.cos(around), radius * Math.cos(polar), radius * Math.sin(polar) * Math.sin(around));
	}

	private static int lifetime(Blast blast) {
		return isHowitzer(blast.style()) ? HOWITZER_LIFETIME_TICKS : blast.style() == Style.NUKE || blast.style() == Style.HEATWAVE ? NUKE_LIFETIME_TICKS
			: blast.style() == Style.ICE_DOME ? DOME_LIFETIME_TICKS : LIFETIME_TICKS;
	}

	/** Howitzer Impact's core and its column blasts; the lighter ring blasts around it look like plain big shots. */
	static boolean isHowitzer(Style style) {
		return style == Style.HOWITZER || style == Style.HOWITZER_CORE;
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
