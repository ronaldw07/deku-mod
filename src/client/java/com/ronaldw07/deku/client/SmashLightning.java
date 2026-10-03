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

/** Green bolts that run along a Smash blast for a moment, flickering to a new shape every tick. */
final class SmashLightning {
	private static final int LIFETIME_TICKS = 8;
	private static final double SEGMENT_LENGTH = 0.6;
	private static final int MIN_BOLTS = 2;
	private static final int MAX_BOLTS = 6;
	private static final double MIN_JAG = 0.15;
	private static final double MAX_JAG = 0.5;
	private static final double BRANCH_CHANCE = 0.25;
	private static final float WIDTH_SCALE = 2f;

	private record Blast(Vec3 from, Vec3 to, float power, long startTick) {
	}

	private static List<Blast> blasts = List.of();

	private SmashLightning() {
	}

	static void add(SmashFxPayload fx) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		Blast blast = new Blast(fx.from(), fx.to(), fx.power(), minecraft.level.getGameTime());
		blasts = Stream.concat(blasts.stream(), Stream.of(blast)).toList();
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || blasts.isEmpty()) {
			return;
		}

		long now = minecraft.level.getGameTime();
		blasts = blasts.stream().filter(blast -> now - blast.startTick() < LIFETIME_TICKS).toList();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		List<Segment> segments = blasts.stream()
			.flatMap(blast -> buildBolts(blast.from().subtract(camera), blast.to().subtract(camera), blast.power(),
				blast.startTick() * 31 + now).stream())
			.toList();
		if (segments.isEmpty()) {
			return;
		}

		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(),
			(pose, buffer) -> LightningDraw.drawGreen(pose.pose(), buffer, segments, WIDTH_SCALE));
	}

	/** Several jagged bolts from one point to another, wilder at higher power. */
	static List<Segment> buildBolts(Vec3 from, Vec3 to, double power, long seed) {
		RandomSource random = RandomSource.create(seed);
		Vec3 path = to.subtract(from);
		int steps = Math.max(2, (int) Math.ceil(path.length() / SEGMENT_LENGTH));
		int bolts = MIN_BOLTS + (int) Math.round(power * (MAX_BOLTS - MIN_BOLTS));
		double jag = Mth.lerp(power, MIN_JAG, MAX_JAG);
		List<Segment> segments = new ArrayList<>();

		for (int bolt = 0; bolt < bolts; bolt++) {
			Vec3 previous = from;
			for (int i = 1; i <= steps; i++) {
				Vec3 onPath = from.add(path.scale((double) i / steps));
				Vec3 next = i == steps ? to : onPath.add(LightningDraw.randomDirection(random).scale(jag * random.nextDouble()));
				segments.add(new Segment(previous, next));

				if (random.nextDouble() < BRANCH_CHANCE) {
					segments.add(new Segment(next, next.add(LightningDraw.randomDirection(random).scale(jag * 1.5))));
				}
				previous = next;
			}
		}
		return segments;
	}
}
