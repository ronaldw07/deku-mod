package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.ExplosionFx.Blast;
import com.ronaldw07.deku.client.LightningDraw.Segment;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * What Fuga leaves behind: a towering mass of solid flame, standing from the crater into the sky
 * for a long while. It is built from hundreds of tapering tongues of lava-coloured fire, white-gold
 * at the root, orange in the middle and deep red at the tips, all licking and swaying out of
 * step, so it reads as one great roaring fire rather than a pillar or a cloud of sparks.
 */
final class FugaBeamFx {
	static final int LIFETIME_TICKS = 640;
	private static final int RISE_TICKS = 70;
	private static final int FADE_TICKS = 120;
	private static final double HEIGHT = 300.0;
	private static final double MIN_RADIUS = 9.0;
	private static final double MAX_RADIUS = 26.0;
	private static final double RADIUS_PER_BLAST = 0.8;
	private static final int STEPS = 14;
	private static final int COLUMN_SIDES = 32;
	private static final double RING_SPACING = 6.0;
	private static final int SWIRLS = 18;
	private static final int SWIRL_STEPS = 48;
	private static final float SWIRL_WIDTH = 0.25f;
	private static final double HEAT_RANGE = 120.0; // blocks of climb from white-gold to the deepest red
	private static final double SWAY_SPEED = 0.09;
	// How many tongues of each size: great ones that reach the sky, middling ones, and small ones roiling at the foot.
	private static final int GREAT_TONGUES = 34;
	private static final int MIDDLE_TONGUES = 50;
	private static final int SMALL_TONGUES = 60;
	// The colour of a flame from its root to its tip.
	private static final float[][] HEAT = {
		{1.0f, 0.72f, 0.28f},
		{1.0f, 0.46f, 0.08f},
		{0.95f, 0.28f, 0.04f},
		{0.8f, 0.12f, 0.03f},
		{0.5f, 0.05f, 0.02f},
	};

	private FugaBeamFx() {
	}

	private record Tongue(double angle, double reach, double height, double width, double sway, double phase, double lean, double twist) {
	}

	static void render(LevelRenderContext context, Blast blast, Vec3 camera, double age) {
		double rise = Math.min(1, age / RISE_TICKS);
		float fade = (float) Mth.clamp((LIFETIME_TICKS - age) / FADE_TICKS, 0, 1);
		double grown = 1 - Math.pow(1 - rise, 2); // the fire climbs from the ground up
		double radius = Mth.clamp(blast.radius() * RADIUS_PER_BLAST, MIN_RADIUS, MAX_RADIUS);
		// The fire starts at the bottom of the crater, not at the surface the arrow hit.
		Vec3 base = new Vec3(blast.center().x, craterFloor(blast.center()), blast.center().z).subtract(camera);
		// The same seed every frame, so each tongue keeps its own character while it sways.
		RandomSource random = RandomSource.create(blast.startTick() * 131);
		double time = age * SWAY_SPEED;

		List<Tongue> tongues = new ArrayList<>();
		for (int i = 0; i < GREAT_TONGUES; i++) {
			tongues.add(tongue(random, radius, HEIGHT * (0.45 + 0.55 * random.nextDouble()), 0.7, 1.1, 1.0));
		}
		for (int i = 0; i < MIDDLE_TONGUES; i++) {
			tongues.add(tongue(random, radius, HEIGHT * (0.12 + 0.2 * random.nextDouble()), 0.5, 1.4, 1.2));
		}
		for (int i = 0; i < SMALL_TONGUES; i++) {
			tongues.add(tongue(random, radius, 8 + random.nextDouble() * 30, 0.35, 1.8, 1.4));
		}
		List<Segment> swirls = new ArrayList<>();
		List<Segment> pale = new ArrayList<>();
		for (int i = 0; i < SWIRLS; i++) {
			(i % 3 == 0 ? pale : swirls).addAll(swirl(base, radius, grown, time, i));
		}
		List<Segment> embers = new ArrayList<>();
		for (int i = SWIRLS; i < SWIRLS + 10; i++) {
			embers.addAll(swirl(base, radius * 1.25, grown, time, i));
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), SolidRender.darkType(), (pose, buffer) -> {
			column(pose.pose(), buffer, base, radius, grown, time, fade);
			for (Tongue tongue : tongues) {
				flame(pose.pose(), buffer, base, tongue, grown, time, 0.0, fade);
				flame(pose.pose(), buffer, base, tongue, grown, time, Math.PI / 2, fade);
			}
			LightningDraw.drawFlat(pose.pose(), buffer, swirls, SWIRL_WIDTH, 1.0f, 0.92f, 0.45f, 0.95f * fade);
			LightningDraw.drawFlat(pose.pose(), buffer, embers, SWIRL_WIDTH * 1.3f, 1.0f, 0.35f, 0.05f, 0.95f * fade);
			LightningDraw.drawFlat(pose.pose(), buffer, pale, SWIRL_WIDTH, 0.82f, 0.82f, 1.0f, 0.95f * fade);
		});
	}

	/** The height of the floor of the crater under the point the arrow hit. */
	private static double craterFloor(Vec3 center) {
		var level = net.minecraft.client.Minecraft.getInstance().level;
		if (level == null) {
			return center.y;
		}
		int x = net.minecraft.util.Mth.floor(center.x);
		int z = net.minecraft.util.Mth.floor(center.z);
		for (int y = net.minecraft.util.Mth.floor(center.y) + 2; y > level.getMinY(); y--) {
			if (!level.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).isAir()) {
				return y + 1;
			}
		}
		return center.y;
	}

	/**
	 * The body of the fire: a wide, solid column, bright as the sun at its foot, narrowing as it
	 * climbs and burning from yellow through orange to a dark red at the top, rippling as it goes.
	 */
	private static void column(org.joml.Matrix4fc pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, Vec3 base, double radius, double grown, double time, float fade) {
		double height = HEIGHT * grown;
		int rings = (int) Math.ceil(height / RING_SPACING);
		Vec3[] previous = null;
		float[] previousColor = null;
		float previousWisp = 1f;
		for (int ring = 0; ring <= rings; ring++) {
			double h = Math.min(height, ring * RING_SPACING);
			double t = h / HEIGHT;
			// Wide at the foot, thinning fast as it climbs until it is only a wisp.
			double width = radius * Math.max(0.06, 1.15 - 1.1 * Math.pow(t, 0.5)) * (1 + 0.12 * Math.sin(h * 0.05 + time * 4));
			float wisp = (float) Math.max(0.1, 1 - 0.9 * Math.pow(t, 1.3));
			Vec3[] points = new Vec3[COLUMN_SIDES];
			for (int i = 0; i < COLUMN_SIDES; i++) {
				double angle = Math.PI * 2 * i / COLUMN_SIDES;
				double ripple = 1 + 0.18 * Math.sin(angle * 3 + h * 0.07 - time * 5) + 0.1 * Math.sin(angle * 7 - h * 0.12 + time * 3);
				double sway = Math.sin(h * 0.02 + time) * radius * 0.5 * t;
				points[i] = base.add(Math.cos(angle) * width * ripple + sway, h, Math.sin(angle) * width * ripple);
			}
			float[] color = heat(h / HEAT_RANGE * 0.8);
			if (previous != null) {
				for (int i = 0; i < COLUMN_SIDES; i++) {
					int next = (i + 1) % COLUMN_SIDES;
					LightningDraw.drawGradientQuad(pose, buffer, new Vec3[] {previous[i], previous[next], points[next], points[i]},
						new float[][] {previousColor, previousColor, color, color}, 0.97f * fade * (wisp + previousWisp) / 2);
				}
			}
			previous = points;
			previousColor = color;
			previousWisp = wisp;
		}
	}

	/** A thin line of light spiralling up around the fire, like the streaks of light whirling about it. */
	private static List<Segment> swirl(Vec3 base, double radius, double grown, double time, int index) {
		List<Segment> line = new ArrayList<>();
		Vec3 previous = null;
		double phase = index * 2.4;
		double turns = 2.0 + (index % 4);
		double height = HEIGHT * grown * (0.5 + 0.5 * ((index * 7) % 10) / 10.0);
		for (int step = 0; step <= SWIRL_STEPS; step++) {
			double t = (double) step / SWIRL_STEPS;
			double angle = phase + t * turns * Math.PI * 2 + time * (index % 2 == 0 ? 1.4 : -1.1);
			double reach = radius * (1.5 + 1.7 * Math.sin(t * 5 + phase)) * (1.5 - 0.7 * t); // lines of fire reaching a little further out
			Vec3 at = base.add(Math.cos(angle) * reach, height * t, Math.sin(angle) * reach);
			if (previous != null) {
				line.add(new Segment(previous, at));
			}
			previous = at;
		}
		return line;
	}

	private static Tongue tongue(RandomSource random, double radius, double height, double widthShare, double reachShare, double swayScale) {
		return new Tongue(random.nextDouble() * Math.PI * 2, radius * reachShare * Math.sqrt(random.nextDouble()), height,
			radius * widthShare * (0.6 + random.nextDouble() * 0.8), swayScale * (0.5 + random.nextDouble()), random.nextDouble() * Math.PI * 2,
			(random.nextDouble() * 2 - 1) * 0.35, (random.nextDouble() * 2 - 1) * 0.8);
	}

	/**
	 * One tongue of fire: a sheet that is wide at the root and tapers to a point, bending and
	 * drifting as it climbs, shaded from white-gold to deep red along its length.
	 */
	private static void flame(org.joml.Matrix4fc pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, Vec3 base, Tongue tongue, double grown,
			double time, double turn, float fade) {
		double height = tongue.height() * grown;
		if (height < 1) {
			return;
		}
		double facing = tongue.angle() + tongue.twist() + turn;
		Vec3 side = new Vec3(-Math.sin(facing), 0, Math.cos(facing));
		Vec3 root = base.add(Math.cos(tongue.angle()) * tongue.reach(), 0, Math.sin(tongue.angle()) * tongue.reach());
		Vec3 previousLeft = null;
		Vec3 previousRight = null;
		float[] previousColor = null;
		for (int step = 0; step <= STEPS; step++) {
			double t = (double) step / STEPS;
			double bend = Math.sin(t * 5 + time * 3 * tongue.sway() + tongue.phase()) * tongue.width() * 0.8 * t
				+ Math.sin(t * 2.3 - time * 2 + tongue.phase() * 1.7) * tongue.width() * 0.6 * t * t;
			double drift = tongue.lean() * height * t * t;
			Vec3 center = root.add(Math.cos(tongue.angle()) * drift + Math.cos(facing + Math.PI / 2) * bend, height * t,
				Math.sin(tongue.angle()) * drift + Math.sin(facing + Math.PI / 2) * bend);
			double half = tongue.width() * Math.pow(1 - t, 0.75) * (1 + 0.15 * Math.sin(t * 9 + time * 5 + tongue.phase()));
			Vec3 left = center.subtract(side.scale(half));
			Vec3 right = center.add(side.scale(half));
			float[] color = heat(height * t / HEAT_RANGE + 0.12 * Math.sin(time * 4 + tongue.phase() + t * 6));
			if (previousLeft != null) {
				LightningDraw.drawGradientQuad(pose, buffer, new Vec3[] {previousLeft, previousRight, right, left},
					new float[][] {previousColor, previousColor, color, color}, 0.98f * fade);
			}
			previousLeft = left;
			previousRight = right;
			previousColor = color;
		}
	}

	/** The colour of fire at a height, 0 at the ground (white-gold) to 1 at the top of its range (deep red). */
	private static float[] heat(double along) {
		double scaled = Mth.clamp(along, 0, 1) * (HEAT.length - 1);
		int low = (int) Math.floor(scaled);
		int high = Math.min(HEAT.length - 1, low + 1);
		float mix = (float) (scaled - low);
		return new float[] {Mth.lerp(mix, HEAT[low][0], HEAT[high][0]), Mth.lerp(mix, HEAT[low][1], HEAT[high][1]), Mth.lerp(mix, HEAT[low][2], HEAT[high][2])};
	}
}
