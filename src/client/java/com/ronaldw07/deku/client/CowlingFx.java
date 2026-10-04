package com.ronaldw07.deku.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.client.LightningDraw.Segment;
import com.ronaldw07.deku.network.SmashFxPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Full Cowling powering up: red lightning climbs the body from the feet while it charges;
 * reaching full power sets off a burst of lightning and a ring of wind; and while it's on,
 * a light wind swirls around the player.
 */
final class CowlingFx {
	private static final int CLIMB_BOLTS = 7;
	private static final double CLIMB_SEGMENT = 0.2;
	private static final double CLIMB_JAG = 0.09;
	private static final double BODY_RADIUS = 0.4;
	private static final double FIRST_PERSON_RADIUS = 0.55;
	private static final double FIRST_PERSON_EYE_CLEARANCE = 0.35;
	private static final float CLIMB_WIDTH = 0.4f;
	private static final int TICKS_PER_SHAPE = 2;
	private static final int BURST_BOLTS = 14;
	private static final double MIN_BURST_LENGTH = 2.0;
	private static final double EXTRA_BURST_LENGTH = 2.5;
	private static final int WIND_RING_PUFFS = 36;
	private static final double WIND_RING_SPEED = 0.9;
	private static final int WIND_RING_GUSTS = 4;
	private static final double SWIRL_RADIUS = 0.9;
	private static final double SWIRL_SPEED = 0.18;
	private static final int SWIRL_EVERY_TICKS = 2;
	private static final double FULL_PERCENT = 100.0;
	private static final double EXTRA_SWIRL_RADIUS = 0.9; // up to 1.7 blocks out at full power
	private static final double EXTRA_SWIRL_WISPS = 4;
	private static final double OUTER_ORBIT_RADIUS = 2.6;
	private static final double SWIRL_MIN_RISE = 0.04;
	private static final double SWIRL_EXTRA_RISE = 0.08;
	private static final int FULL_POWER_GUST_EVERY_TICKS = 6;
	private static final double GUST_RADIUS = 1.4;

	private CowlingFx() {
	}

	/** Called each tick once Full Cowling's power for the tick is known. */
	static void tick(LocalPlayer player, double previousPercent, double percent, double target) {
		if (percent > 0 && previousPercent < target && percent >= target) {
			burst(player);
		}
		if (percent > 0 && player.tickCount % SWIRL_EVERY_TICKS == 0) {
			swirl(player, Mth.clamp(percent / FULL_PERCENT, 0, 1));
		}
	}

	private static void burst(LocalPlayer player) {
		RandomSource random = player.getRandom();
		Vec3 chest = player.position().add(0, player.getBbHeight() * 0.55, 0);
		for (int i = 0; i < BURST_BOLTS; i++) {
			Vec3 direction = LightningDraw.randomDirection(random);
			double length = MIN_BURST_LENGTH + random.nextDouble() * EXTRA_BURST_LENGTH;
			SmashLightning.add(new SmashFxPayload(chest, chest.add(direction.scale(length)), 1f, false));
		}

		Vec3 feet = player.position().add(0, 0.3, 0);
		for (int i = 0; i < WIND_RING_PUFFS; i++) {
			double angle = Math.PI * 2 * i / WIND_RING_PUFFS;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			player.level().addParticle(ParticleTypes.CLOUD, feet.x + out.x * 0.6, feet.y, feet.z + out.z * 0.6,
				out.x * WIND_RING_SPEED, 0.02, out.z * WIND_RING_SPEED);
		}
		for (int i = 0; i < WIND_RING_GUSTS; i++) {
			double angle = Math.PI * 2 * i / WIND_RING_GUSTS;
			player.level().addParticle(ParticleTypes.GUST, feet.x + Math.cos(angle) * 1.5, feet.y + 0.5, feet.z + Math.sin(angle) * 1.5, 0, 0, 0);
		}
		player.level().playLocalSound(player, DekuSounds.SMASH_BLAST, SoundSource.PLAYERS, 0.8f, 1.3f);
	}

	/**
	 * Wisps of wind circling the body, wider, denser and faster the more power Full Cowling has;
	 * at full power a second wide orbit joins in with gusts and ribbons rising off the body.
	 * Kept below the eyes in first person so it doesn't cloud the view.
	 */
	private static void swirl(LocalPlayer player, double power) {
		RandomSource random = player.getRandom();
		boolean firstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
		double top = firstPerson ? player.getEyeHeight() - 0.6 : player.getBbHeight();
		double radius = SWIRL_RADIUS * (1 + EXTRA_SWIRL_RADIUS * power);
		double speed = SWIRL_SPEED * (1 + power);
		int wisps = Math.max(1, (int) Math.round((1 + EXTRA_SWIRL_WISPS * power) * DekuSettings.get().detailScale()));
		for (int i = 0; i < wisps; i++) {
			// At full power every other wisp rides the wide outer orbit.
			double orbit = power >= 1 && i % 2 == 1 ? OUTER_ORBIT_RADIUS : radius;
			double angle = random.nextDouble() * Math.PI * 2;
			Vec3 at = player.position().add(Math.cos(angle) * orbit, random.nextDouble() * top, Math.sin(angle) * orbit);
			double rise = SWIRL_MIN_RISE + random.nextDouble() * SWIRL_EXTRA_RISE * power;
			Vec3 tangent = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(speed).add(0, rise, 0);
			player.level().addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, tangent.x, tangent.y, tangent.z);
		}
		if (power >= 1 && player.tickCount % FULL_POWER_GUST_EVERY_TICKS == 0) {
			double angle = random.nextDouble() * Math.PI * 2;
			player.level().addParticle(ParticleTypes.GUST, player.getX() + Math.cos(angle) * GUST_RADIUS,
				player.getY() + random.nextDouble() * top, player.getZ() + Math.sin(angle) * GUST_RADIUS, 0, 0, 0);
		}
	}

	/** Red bolts climbing from the feet, reaching higher the closer Full Cowling is to full power. */
	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		double progress = FullCowlingClient.chargeProgress();
		if (player == null || progress <= 0) {
			return;
		}

		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		double radius = firstPerson ? FIRST_PERSON_RADIUS : BODY_RADIUS;
		double fullHeight = firstPerson ? player.getEyeHeight() - FIRST_PERSON_EYE_CLEARANCE : player.getBbHeight() * 1.05;
		List<Segment> bolts = climb(RandomSource.create(player.getId() * 31L + player.tickCount / TICKS_PER_SHAPE), radius,
			fullHeight * progress);

		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(player.getPosition(partialTick).subtract(context.levelState().cameraRenderState.pos));
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lightning(),
			(pose, buffer) -> LightningDraw.draw(pose.pose(), buffer, bolts, CLIMB_WIDTH, LightningDraw.RED, 1f));
		poseStack.popPose();
	}

	private static List<Segment> climb(RandomSource random, double radius, double height) {
		List<Segment> segments = new ArrayList<>();
		for (int bolt = 0; bolt < CLIMB_BOLTS; bolt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			Vec3 point = new Vec3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
			while (point.y < height) {
				Vec3 jag = new Vec3((random.nextDouble() * 2 - 1) * CLIMB_JAG, CLIMB_SEGMENT, (random.nextDouble() * 2 - 1) * CLIMB_JAG);
				Vec3 next = point.add(jag);
				segments.add(new Segment(point, next));
				point = next;
			}
		}
		return segments;
	}
}
