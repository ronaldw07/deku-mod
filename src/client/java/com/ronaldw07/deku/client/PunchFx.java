package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.LightningDraw.Segment;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Red lightning wrapping the punching arm and both legs, the way Fa Jin runs through the body:
 * the legs glow red once a Smash is more than a third charged, and when the punch is thrown
 * the arm and legs blaze with it and a burst of red bolts leaps from the fist.
 */
final class PunchFx {
	private static final int PUNCH_TICKS = 10;
	private static final int FULL_POWER_EXTRA_TICKS = 2;
	private static final double LEGS_JOIN_AT = 0.4;
	private static final double LEG_SIDE = 0.12;
	private static final double LEG_HEIGHT = 0.75;
	private static final int TICKS_PER_SHAPE = 2;
	private static final int MIN_ARM_COILS = 3;
	private static final int EXTRA_ARM_COILS = 4;
	private static final int LEG_COILS = 2;
	private static final int EXTRA_LEG_COILS = 2;
	private static final float MIN_WIDTH = 0.8f;
	private static final float EXTRA_WIDTH = 1.4f;
	private static final int FIST_BOLTS = 8;
	private static final double MIN_BOLT_LENGTH = 1.5;
	private static final double EXTRA_BOLT_LENGTH = 1.5;
	private static final int BOLT_STEPS = 5;
	private static final double BOLT_JAG = 0.2;

	private static long punchStart;
	private static double punchPower;
	private static boolean punched;

	private PunchFx() {
	}

	/** The punch was just thrown at this power, 0-1. */
	static void start(LocalPlayer player, double power) {
		punchStart = player.level().getGameTime();
		punchPower = power;
		punched = true;
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		int length = PUNCH_TICKS + (punchPower >= 1 ? FULL_POWER_EXTRA_TICKS : 0);
		long age = punched ? player.level().getGameTime() - punchStart : length;
		boolean punching = age < length;
		double charge = SmashClient.charging() ? SmashClient.charge() / 100.0 : 0;
		if (!punching && charge < LEGS_JOIN_AT) {
			return;
		}

		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		double power = punching ? punchPower : charge;
		float fade = punching ? 1f - (float) (age + partialTick) / length : 1f;
		RandomSource random = RandomSource.create(player.getId() * 31L + player.tickCount / TICKS_PER_SHAPE);
		List<Segment> segments = new ArrayList<>();

		if (punching) {
			Vec3[] arm = LimbLightning.rightArm(player, partialTick);
			Vec3 shoulder = arm[0].subtract(camera);
			Vec3 fist = arm[1].subtract(camera);
			for (int i = 0; i < MIN_ARM_COILS + power * EXTRA_ARM_COILS; i++) {
				segments.addAll(LimbLightning.coil(random, shoulder, fist));
			}
			double boltLength = MIN_BOLT_LENGTH + EXTRA_BOLT_LENGTH * power;
			for (int i = 0; i < FIST_BOLTS; i++) {
				segments.addAll(bolt(random, fist, fist.add(LightningDraw.randomDirection(random).scale(boltLength * (0.5 + random.nextDouble() * 0.5)))));
			}
		}

		double yaw = Math.toRadians(Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		for (int side = -1; side <= 1; side += 2) {
			Vec3 foot = player.getPosition(partialTick).add(right.scale(side * LEG_SIDE)).subtract(camera);
			for (int i = 0; i < LEG_COILS + power * EXTRA_LEG_COILS; i++) {
				segments.addAll(LimbLightning.coil(random, foot, foot.add(0, LEG_HEIGHT, 0)));
			}
		}

		float width = (float) (MIN_WIDTH + EXTRA_WIDTH * power);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(),
			(pose, buffer) -> LightningDraw.draw(pose.pose(), buffer, segments, width, LightningDraw.CRIMSON, fade));
	}

	private static List<Segment> bolt(RandomSource random, Vec3 from, Vec3 to) {
		List<Segment> segments = new ArrayList<>();
		Vec3 point = from;
		for (int i = 1; i <= BOLT_STEPS; i++) {
			Vec3 next = from.lerp(to, (double) i / BOLT_STEPS);
			if (i < BOLT_STEPS) {
				next = next.add(LightningDraw.randomDirection(random).scale(BOLT_JAG));
			}
			segments.add(new Segment(point, next));
			point = next;
		}
		return segments;
	}
}
