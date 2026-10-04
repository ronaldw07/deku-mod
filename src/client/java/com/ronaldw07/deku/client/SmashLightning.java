package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.LightningDraw.Segment;
import com.ronaldw07.deku.network.SmashFxPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Green bolts that run along a blast for a moment, flickering to a new shape every tick.
 * The Smash punch's own lightning is heavy: more, thicker bolts that last longer, with arcs.
 */
final class SmashLightning {
	private static final double SEGMENT_LENGTH = 0.6;
	private static final double MIN_JAG = 0.15;
	private static final double MAX_JAG = 0.5;

	/** How one kind of lightning looks. */
	private record Style(int lifetimeTicks, int minBolts, int maxBolts, double branchChance, float widthScale, boolean arcs) {
	}

	private static final Style LIGHT = new Style(8, 2, 6, 0.25, 2f, false);
	private static final Style HEAVY = new Style(14, 5, 14, 0.45, 3f, true);

	// Arcs: bolts leaping sideways off the punch, longer at higher power.
	private static final int MIN_ARCS = 6;
	private static final int MAX_ARCS = 24;
	private static final double MIN_ARC_LENGTH = 1.5;
	private static final double MAX_ARC_LENGTH = 6.0;
	private static final int ARC_STEPS = 6;

	private record Blast(Vec3 from, Vec3 to, float power, Style style, long startTick) {
	}

	private static List<Blast> blasts = List.of();

	private SmashLightning() {
	}

	static void add(SmashFxPayload fx) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		Blast blast = new Blast(fx.from(), fx.to(), fx.power(), fx.heavy() ? HEAVY : LIGHT, minecraft.level.getGameTime());
		blasts = Stream.concat(blasts.stream(), Stream.of(blast)).toList();
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || blasts.isEmpty()) {
			return;
		}

		long now = minecraft.level.getGameTime();
		blasts = blasts.stream().filter(blast -> now - blast.startTick() < blast.style().lifetimeTicks()).toList();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Style style : List.of(LIGHT, HEAVY)) {
			List<Segment> segments = blasts.stream()
				.filter(blast -> blast.style() == style)
				.flatMap(blast -> {
					Vec3 from = blast.from().subtract(camera);
					Vec3 to = blast.to().subtract(camera);
					long seed = blast.startTick() * 31 + now;
					List<Segment> bolts = buildBolts(from, to, blast.power(), seed, style);
					return style.arcs() ? Stream.concat(bolts.stream(), buildArcs(from, to, blast.power(), seed).stream()) : bolts.stream();
				})
				.toList();
			if (!segments.isEmpty()) {
				context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(),
					(pose, buffer) -> LightningDraw.drawGreen(pose.pose(), buffer, segments, style.widthScale()));
			}
		}
	}

	/** Several jagged bolts from one point to another, wilder at higher power. */
	private static List<Segment> buildBolts(Vec3 from, Vec3 to, double power, long seed, Style style) {
		RandomSource random = RandomSource.create(seed);
		Vec3 path = to.subtract(from);
		int steps = Math.max(2, (int) Math.ceil(path.length() / SEGMENT_LENGTH));
		int bolts = style.minBolts() + (int) Math.round(power * (style.maxBolts() - style.minBolts()));
		double jag = Mth.lerp(power, MIN_JAG, MAX_JAG);
		List<Segment> segments = new ArrayList<>();

		for (int bolt = 0; bolt < bolts; bolt++) {
			Vec3 previous = from;
			for (int i = 1; i <= steps; i++) {
				Vec3 onPath = from.add(path.scale((double) i / steps));
				Vec3 next = i == steps ? to : onPath.add(LightningDraw.randomDirection(random).scale(jag * random.nextDouble()));
				segments.add(new Segment(previous, next));

				if (random.nextDouble() < style.branchChance()) {
					segments.add(new Segment(next, next.add(LightningDraw.randomDirection(random).scale(jag * 1.5))));
				}
				previous = next;
			}
		}
		return segments;
	}

	/** Jagged arcs jumping out sideways from random points along the punch. */
	private static List<Segment> buildArcs(Vec3 from, Vec3 to, double power, long seed) {
		RandomSource random = RandomSource.create(seed ^ 0x5DEECE66DL);
		Vec3 path = to.subtract(from);
		int arcs = MIN_ARCS + (int) Math.round(power * (MAX_ARCS - MIN_ARCS));
		double length = Mth.lerp(power, MIN_ARC_LENGTH, MAX_ARC_LENGTH);
		double jag = Mth.lerp(power, MIN_JAG, MAX_JAG);
		List<Segment> segments = new ArrayList<>();
		for (int arc = 0; arc < arcs; arc++) {
			Vec3 start = from.add(path.scale(random.nextDouble()));
			Vec3 end = start.add(LightningDraw.randomDirection(random).scale(length * (0.5 + random.nextDouble())));
			Vec3 previous = start;
			for (int i = 1; i <= ARC_STEPS; i++) {
				Vec3 next = start.lerp(end, (double) i / ARC_STEPS);
				if (i < ARC_STEPS) {
					next = next.add(LightningDraw.randomDirection(random).scale(jag * random.nextDouble()));
				}
				segments.add(new Segment(previous, next));
				previous = next;
			}
		}
		return segments;
	}
}
