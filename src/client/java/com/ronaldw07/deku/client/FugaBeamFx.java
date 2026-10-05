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
	private static final double MIN_RADIUS = 2.5;
	private static final double MAX_RADIUS = 7.0;
	private static final double RADIUS_PER_BLAST = 0.25;
	private static final int CORE_LINES = 16;
	private static final int RIBBONS = 12;
	private static final int TENDRILS = 10;
	private static final int STEPS = 36;
	private static final double SPIN_PER_TICK = 0.045;
	private static final double RIBBON_TURNS = 3.0;
	private static final double TENDRIL_REACH = 9.0; // how far out they sweep, in column radii
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
		RandomSource random = RandomSource.create(blast.startTick() * 131);
		double time = age * SPIN_PER_TICK;

		List<Segment> core = new ArrayList<>();
		for (int i = 0; i < CORE_LINES; i++) {
			double angle = Math.PI * 2 * i / CORE_LINES;
			double reach = radius * (0.1 + 0.5 * random.nextDouble());
			core.addAll(column(base, height, reach, angle, time * (1 + i % 3 * 0.4), radius * 0.15, 1.0));
		}
		List<Segment> orange = new ArrayList<>();
		List<Segment> red = new ArrayList<>();
		for (int i = 0; i < RIBBONS; i++) {
			double phase = Math.PI * 2 * i / RIBBONS;
			(i % 2 == 0 ? orange : red).addAll(ribbon(base, height, radius, phase, time, i));
		}
		List<Segment> flames = new ArrayList<>();
		for (int i = 0; i < TENDRILS; i++) {
			double angle = Math.PI * 2 * i / TENDRILS + time * 0.3;
			flames.addAll(tendril(base, height, radius, angle, time, i));
		}
		List<Segment> foot = new ArrayList<>();
		for (int i = 0; i < FOOT_RING_SEGMENTS; i++) {
			double a = Math.PI * 2 * i / FOOT_RING_SEGMENTS;
			double b = Math.PI * 2 * (i + 1) / FOOT_RING_SEGMENTS;
			double reach = radius * 2.2 * (1 + 0.08 * Math.sin(time * 6 + i));
			foot.add(new Segment(base.add(Math.cos(a) * reach, 0.3, Math.sin(a) * reach), base.add(Math.cos(b) * reach, 0.3, Math.sin(b) * reach)));
		}
		// The flames are solid ribbons of orange and red so the colours read against the sky; only the white-hot core glows.
		float ribbon = (float) Mth.clamp(radius * RIBBON_WIDTH_PER_RADIUS, 0.15, 0.4);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
			LightningDraw.drawFlat(pose.pose(), buffer, flames, ribbon * 1.3f, 1.0f, 0.4f, 0.04f, 0.85f * fade);
			LightningDraw.drawFlat(pose.pose(), buffer, red, ribbon, 0.9f, 0.08f, 0.04f, 0.9f * fade);
			LightningDraw.drawFlat(pose.pose(), buffer, orange, ribbon, 1.0f, 0.5f, 0.06f, 0.9f * fade);
			LightningDraw.drawFlat(pose.pose(), buffer, foot, ribbon, 0.95f, 0.2f, 0.05f, 0.9f * fade);
		});
		float glow = (float) Mth.clamp(radius * 0.2, 0.6, 1.5);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(),
			(pose, buffer) -> LightningDraw.draw(pose.pose(), buffer, core, glow, CORE, fade));
	}

	/** One line standing up from the ground, wavering as it climbs. */
	private static List<Segment> column(Vec3 base, double height, double reach, double angle, double time, double wobble, double heightShare) {
		List<Segment> line = new ArrayList<>();
		Vec3 previous = null;
		for (int step = 0; step <= STEPS; step++) {
			double t = (double) step / STEPS;
			double sway = wobble * Math.sin(t * 9 + time * 6 + angle * 3);
			Vec3 at = base.add(Math.cos(angle) * reach + sway, height * heightShare * t, Math.sin(angle) * reach + sway * 0.7);
			if (previous != null) {
				line.add(new Segment(previous, at));
			}
			previous = at;
		}
		return line;
	}

	/** A ribbon winding up the column, swelling and thinning as it climbs. */
	private static List<Segment> ribbon(Vec3 base, double height, double radius, double phase, double time, int index) {
		List<Segment> line = new ArrayList<>();
		Vec3 previous = null;
		for (int step = 0; step <= STEPS; step++) {
			double t = (double) step / STEPS;
			double angle = phase + t * RIBBON_TURNS * Math.PI * 2 + time * (index % 2 == 0 ? 1 : -1);
			double reach = radius * (1.0 + 0.7 * Math.sin(t * 8 + phase + time * 5)) * (1 - 0.35 * t);
			Vec3 at = base.add(Math.cos(angle) * reach, height * t * 0.9, Math.sin(angle) * reach);
			if (previous != null) {
				line.add(new Segment(previous, at));
			}
			previous = at;
		}
		return line;
	}

	/** A great flame sweeping out from the foot of the column and curling up into the sky. */
	private static List<Segment> tendril(Vec3 base, double height, double radius, double angle, double time, int index) {
		List<Segment> line = new ArrayList<>();
		Vec3 previous = null;
		double length = 0.7 + 0.3 * ((index * 37) % 10) / 10.0;
		for (int step = 0; step <= STEPS; step++) {
			double t = (double) step / STEPS;
			double out = radius * (1 + TENDRIL_REACH * length * Math.pow(t, 1.4));
			double curl = angle + 0.5 * Math.sin(t * 4 + time * 3 + index);
			Vec3 at = base.add(Math.cos(curl) * out, height * TENDRIL_HEIGHT_SHARE * length * Math.pow(t, 0.8), Math.sin(curl) * out);
			if (previous != null) {
				line.add(new Segment(previous, at));
			}
			previous = at;
		}
		return line;
	}
}
