package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.ExplosionFx.Blast;
import com.ronaldw07.deku.client.LightningDraw.Layer;
import com.ronaldw07.deku.client.LightningDraw.Segment;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * What Fuga leaves behind: a column of molten light standing from the crater to the sky for a long
 * while, a white-hot core of lines with orange and red ribbons winding up around it and great
 * flames sweeping out from its foot.
 */
final class FugaBeamFx {
	static final int LIFETIME_TICKS = 640;
	private static final int RISE_TICKS = 24;
	private static final int FADE_TICKS = 120;
	private static final double HEIGHT = 300.0;
	private static final double MIN_RADIUS = 5.0;
	private static final double MAX_RADIUS = 14.0;
	private static final double RADIUS_PER_BLAST = 0.5;
	private static final int CORE_LINES = 24;
	private static final double CORE_SPREAD = 1.6;
	private static final double LEAN = 0.18; // sideways drift per block climbed, at most
	private static final double FLARE = 3.0; // extra width high up, in column radii
	private static final int RIBBONS = 22;
	private static final int TENDRILS = 18;
	private static final int STEPS = 36;
	private static final double SPIN_PER_TICK = 0.045;
	private static final double RIBBON_TURNS = 3.0;
	private static final double TENDRIL_REACH = 5.0; // how far out they sweep, in column radii
	private static final double TENDRIL_HEIGHT_SHARE = 0.45;
	private static final int FOOT_RING_SEGMENTS = 40;
	private static final double RIBBON_WIDTH_PER_RADIUS = 0.04;
	// White-gold core, then the molten orange and red it burns into.
	private static final Layer[] CORE = {
		new Layer(0.2f, 1.0f, 0.5f, 0.08f, 0.3f),
		new Layer(0.1f, 1.0f, 0.78f, 0.3f, 0.55f),
		new Layer(0.04f, 1.0f, 1.0f, 0.88f, 0.95f),
	};

	private FugaBeamFx() {
	}

	static void render(LevelRenderContext context, Blast blast, Vec3 camera, double age) {
		double rise = Math.min(1, age / RISE_TICKS);
		float fade = (float) Mth.clamp((LIFETIME_TICKS - age) / FADE_TICKS, 0, 1);
		double height = HEIGHT * (1 - Math.pow(1 - rise, 3));
		double radius = Mth.clamp(blast.radius() * RADIUS_PER_BLAST, MIN_RADIUS, MAX_RADIUS);
		Vec3 base = blast.center().subtract(camera);
		// The same seed every frame, so each strand keeps its own character while it wavers.
		RandomSource random = RandomSource.create(blast.startTick() * 131);
		double time = age * SPIN_PER_TICK;

		List<Segment> core = new ArrayList<>();
		for (int i = 0; i < CORE_LINES; i++) {
			core.addAll(strand(base, height * (0.6 + 0.4 * random.nextDouble()), radius * random.nextDouble() * CORE_SPREAD, radius, time, new Strand(random, 0.5)));
		}
		List<Segment> orange = new ArrayList<>();
		List<Segment> red = new ArrayList<>();
		for (int i = 0; i < RIBBONS; i++) {
			(i % 2 == 0 ? orange : red).addAll(strand(base, height * (0.5 + 0.5 * random.nextDouble()),
				radius * (0.4 + random.nextDouble() * 1.2), radius, time, new Strand(random, 1.5)));
		}
		List<Segment> flames = new ArrayList<>();
		for (int i = 0; i < TENDRILS; i++) {
			flames.addAll(tendril(base, height, radius, time, i, random));
		}
		List<Segment> foot = new ArrayList<>();
		for (int i = 0; i < FOOT_RING_SEGMENTS; i++) {
			double a = Math.PI * 2 * i / FOOT_RING_SEGMENTS;
			double b = Math.PI * 2 * (i + 1) / FOOT_RING_SEGMENTS;
			double reach = radius * 2.2 * (1 + 0.15 * Math.sin(time * 6 + i * 1.7));
			foot.add(new Segment(base.add(Math.cos(a) * reach, 0.3, Math.sin(a) * reach), base.add(Math.cos(b) * reach, 0.3, Math.sin(b) * reach)));
		}
		// The flames are solid ribbons of orange and red so the colours read against the sky; only the white-hot core glows.
		float ribbon = (float) Mth.clamp(radius * RIBBON_WIDTH_PER_RADIUS, 0.2, 0.6);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
			LightningDraw.drawFlat(pose.pose(), buffer, flames, ribbon, 1.0f, 0.4f, 0.04f, 0.85f * fade);
			LightningDraw.drawFlat(pose.pose(), buffer, red, ribbon, 0.9f, 0.08f, 0.04f, 0.9f * fade);
			LightningDraw.drawFlat(pose.pose(), buffer, orange, ribbon, 1.0f, 0.5f, 0.06f, 0.9f * fade);
			LightningDraw.drawFlat(pose.pose(), buffer, foot, ribbon, 0.95f, 0.2f, 0.05f, 0.9f * fade);
		});
		float glow = (float) Mth.clamp(radius * 0.15, 0.6, 1.8);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(),
			(pose, buffer) -> LightningDraw.draw(pose.pose(), buffer, core, glow, CORE, fade));
	}

	/** The random character of one strand: where it starts, how it twists, how it leans and how far it flares. */
	private record Strand(double angle, double turns, double sway, double leanX, double leanZ, double flare, double phase, double rate) {
		Strand(RandomSource random, double wildness) {
			this(random.nextDouble() * Math.PI * 2, (random.nextDouble() * 2 - 1) * 3.0 * wildness, 0.4 + random.nextDouble() * 1.4 * wildness,
				(random.nextDouble() * 2 - 1) * LEAN * wildness, (random.nextDouble() * 2 - 1) * LEAN * wildness, random.nextDouble() * FLARE * wildness,
				random.nextDouble() * Math.PI * 2, 0.6 + random.nextDouble() * 1.6);
		}
	}

	/**
	 * One strand climbing from the crater, twisting around the column, leaning off to one side, rippling
	 * with its own rhythm and flaring wide as it climbs, so no two go the same way.
	 */
	private static List<Segment> strand(Vec3 base, double height, double startReach, double radius, double time, Strand strand) {
		List<Segment> line = new ArrayList<>();
		Vec3 previous = null;
		for (int step = 0; step <= STEPS; step++) {
			double t = (double) step / STEPS;
			double angle = strand.angle() + t * strand.turns() * Math.PI * 2 + time * strand.rate() * Math.signum(strand.turns() + 1.0E-6);
			double reach = startReach + radius * strand.sway() * Math.sin(t * 7 + strand.phase() + time * 4) * (0.4 + t) + radius * strand.flare() * t * t;
			double lean = height * t * t;
			Vec3 at = base.add(Math.cos(angle) * reach + strand.leanX() * lean + Math.sin(time * 1.3 + strand.phase()) * radius * t * 1.5,
				height * t, Math.sin(angle) * reach + strand.leanZ() * lean + Math.cos(time * 1.1 + strand.phase()) * radius * t * 1.5);
			if (previous != null) {
				line.add(new Segment(previous, at));
			}
			previous = at;
		}
		return line;
	}

	/** A great flame sweeping out from the foot of the column in its own direction and curling up into the sky. */
	private static List<Segment> tendril(Vec3 base, double height, double radius, double time, int index, RandomSource random) {
		List<Segment> line = new ArrayList<>();
		Vec3 previous = null;
		double angle = random.nextDouble() * Math.PI * 2;
		double length = 0.4 + random.nextDouble() * 0.6;
		double rise = TENDRIL_HEIGHT_SHARE * (0.3 + random.nextDouble());
		double curlAmount = 0.3 + random.nextDouble() * 1.2;
		double phase = random.nextDouble() * Math.PI * 2;
		for (int step = 0; step <= STEPS; step++) {
			double t = (double) step / STEPS;
			double out = radius * (1 + TENDRIL_REACH * length * Math.pow(t, 1.4));
			double curl = angle + curlAmount * Math.sin(t * 4 + time * 3 + phase);
			Vec3 at = base.add(Math.cos(curl) * out, height * rise * length * Math.pow(t, 0.8), Math.sin(curl) * out);
			if (previous != null) {
				line.add(new Segment(previous, at));
			}
			previous = at;
		}
		return line;
	}
}
