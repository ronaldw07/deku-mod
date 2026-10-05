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
	// The wave of Decay rolling outward, matching how fast the server eats the ground.
	private static final double DECAY_WAVE_SPEED = 1.5;
	private static final double DECAY_CATASTROPHE_SPEED = 2.5;
	private static final int DECAY_LIFETIME_TICKS = 70;
	private static final int SMASH_HIT_LIFETIME_TICKS = 16;
	private static final int SMASH_HIT_BOLTS = 26;
	private static final int SMASH_HIT_SKY_BOLTS = 3;
	private static final double SMASH_HIT_SKY_HEIGHT = 40.0;
	private static final float DECAY_RING_WIDTH = 6f;
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
			if (blast.style() == Style.FUGA) {
				FugaBeamFx.render(context, blast, camera, age);
				if (age >= NUKE_LIFETIME_TICKS) {
					continue; // the burst around the foot is brief; the column stays
				}
			}
			int lifetime = blast.style() == Style.FUGA ? NUKE_LIFETIME_TICKS : lifetime(blast);
			float fade = age < GROW_TICKS ? 1f : (float) Math.max(0, 1 - (age - GROW_TICKS) / (lifetime - GROW_TICKS));
			Layer[] palette = switch (blast.style()) {
				case GROUND -> LightningDraw.RED;
				case NUKE -> LightningDraw.CRIMSON;
				case PURPLE -> LightningDraw.PURPLE;
				default -> LightningDraw.FIRE;
			};
			Vec3 center = blast.center().subtract(camera);
			if (blast.style() == Style.ICE_DOME) {
				drawDome(context, blast, center, age);
				continue;
			}
			if (blast.style() == Style.SMASH_HIT) {
				drawSmashHit(context, blast, center, age);
				continue;
			}
			if (isDecay(blast.style())) {
				List<Segment> front = ring(center, decayFront(blast, age));
				context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) ->
					LightningDraw.draw(pose.pose(), buffer, front, DECAY_RING_WIDTH, LightningDraw.DECAY, fade * 0.7f));
				continue;
			}
			boolean howitzer = isHowitzer(blast.style());
			boolean nuke = blast.style() == Style.NUKE || blast.style() == Style.HEATWAVE || blast.style() == Style.PURPLE || blast.style() == Style.FUGA;
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

	static boolean isDecay(Style style) {
		return style == Style.DECAY_WAVE || style == Style.DECAY_CATASTROPHE;
	}

	/** How far out the wave of Decay has reached, the same pace as the ground being eaten. */
	static double decayFront(Blast blast, double age) {
		double speed = blast.style() == Style.DECAY_CATASTROPHE ? DECAY_CATASTROPHE_SPEED : DECAY_WAVE_SPEED;
		return Math.min(blast.radius(), age * speed);
	}

	/**
	 * Where a Smash lands on something: a huge red ring racing outward along the ground and two more
	 * standing upright, red and pale blue lightning bursting from the point, and bolts striking it from above.
	 */
	private static void drawSmashHit(LevelRenderContext context, Blast blast, Vec3 center, double age) {
		double progress = Math.min(1, age / SMASH_HIT_LIFETIME_TICKS);
		double outward = 1 - Math.pow(1 - progress, 3);
		float fade = (float) Math.max(0, 1 - progress);
		double radius = blast.radius() * outward;
		List<Segment> rings = new ArrayList<>(ring(center, radius));
		rings.addAll(ring(center, radius * 0.7));
		for (int i = 0; i < 2; i++) {
			double turn = Math.PI * i / 2;
			for (int j = 0; j < RING_SEGMENTS; j++) {
				double a0 = j * Math.PI * 2 / RING_SEGMENTS;
				double a1 = (j + 1) * Math.PI * 2 / RING_SEGMENTS;
				rings.add(new Segment(
					center.add(Math.cos(a0) * radius * Math.cos(turn), Math.sin(a0) * radius, Math.cos(a0) * radius * Math.sin(turn)),
					center.add(Math.cos(a1) * radius * Math.cos(turn), Math.sin(a1) * radius, Math.cos(a1) * radius * Math.sin(turn))));
			}
		}
		RandomSource random = RandomSource.create(blast.startTick() * 37 + Double.hashCode(blast.center().y));
		List<Segment> red = new ArrayList<>();
		List<Segment> blue = new ArrayList<>();
		for (int i = 0; i < SMASH_HIT_BOLTS; i++) {
			Vec3 direction = LightningDraw.randomDirection(random);
			Vec3 end = center.add(direction.scale(blast.radius() * (0.5 + random.nextDouble() * 0.7) * Math.min(1, age / 3 + 0.3)));
			(i % 2 == 0 ? red : blue).addAll(LimbLightning.jagged(random, center, end, 5, blast.radius() * 0.05));
		}
		for (int i = 0; i < SMASH_HIT_SKY_BOLTS; i++) {
			Vec3 top = center.add((random.nextDouble() - 0.5) * blast.radius() * 0.6, SMASH_HIT_SKY_HEIGHT, (random.nextDouble() - 0.5) * blast.radius() * 0.6);
			blue.addAll(LimbLightning.jagged(random, top, center.add((random.nextDouble() - 0.5) * 1.5, 0, (random.nextDouble() - 0.5) * 1.5), 12, 1.6));
		}
		float width = Math.max(2f, blast.radius() * 0.35f);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			LightningDraw.draw(pose.pose(), buffer, rings, width, LightningDraw.RED, fade);
			LightningDraw.draw(pose.pose(), buffer, red, width * 0.6f, LightningDraw.RED, fade);
			LightningDraw.draw(pose.pose(), buffer, blue, width * 0.5f, LightningDraw.CYAN, fade);
		});
	}

	private static int lifetime(Blast blast) {
		if (blast.style() == Style.SMASH_HIT) {
			return SMASH_HIT_LIFETIME_TICKS;
		}
		if (isDecay(blast.style())) {
			return DECAY_LIFETIME_TICKS;
		}
		if (blast.style() == Style.FUGA) {
			return FugaBeamFx.LIFETIME_TICKS;
		}
		return isHowitzer(blast.style()) ? HOWITZER_LIFETIME_TICKS : blast.style() == Style.NUKE || blast.style() == Style.HEATWAVE || blast.style() == Style.PURPLE ? NUKE_LIFETIME_TICKS
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
