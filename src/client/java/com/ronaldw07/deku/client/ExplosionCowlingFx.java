package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.LightningDraw.Segment;
import com.ronaldw07.deku.network.ExplosionCowlingFxPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Explosion quirk's Full Cowling: a red-orange glow hugging the whole body, a shell of
 * pulsing flame columns with tongues of fire licking up and curling off it.
 */
final class ExplosionCowlingFx {
	private static final int COLUMNS = 12;
	private static final double BODY_RADIUS = 0.45;
	private static final double FIRST_PERSON_RADIUS = 0.6;
	private static final double FIRST_PERSON_EYE_CLEARANCE = 0.35;
	private static final double GLOW_HEIGHT = 1.9;
	private static final double SPIN_PER_TICK = 0.05;
	private static final float GLOW_WIDTH = 1.2f;
	private static final double PULSE_SPEED = 0.3;
	private static final double PULSE_DEPTH = 0.2;
	private static final int TICKS_PER_SHAPE = 2;
	private static final int TONGUES = 5;
	private static final int TONGUE_STEPS = 6;
	private static final double TONGUE_CURL = 0.35;
	private static final double TONGUE_JAG = 0.08;
	private static final float TONGUE_WIDTH = 0.5f;

	private ExplosionCowlingFx() {
	}

	private static Set<UUID> others = Set.of();

	/** Someone nearby lit or put out their Cowling. */
	static void add(ExplosionCowlingFxPayload fx) {
		Set<UUID> next = new HashSet<>(others);
		if (fx.on()) {
			next.add(fx.player());
		} else {
			next.remove(fx.player());
		}
		others = Set.copyOf(next);
	}

	static void reset() {
		others = Set.of();
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		if (ExplosionCowlingClient.active()) {
			glow(context, minecraft.player, partialTick, minecraft.options.getCameraType().isFirstPerson());
		}
		for (UUID id : others) {
			Player other = minecraft.level.getPlayerByUUID(id);
			if (other != null && other != minecraft.player) {
				glow(context, other, partialTick, false);
			}
		}
	}

	private static void glow(LevelRenderContext context, Player player, float partialTick, boolean firstPerson) {
		double radius = firstPerson ? FIRST_PERSON_RADIUS : BODY_RADIUS;
		double height = firstPerson ? player.getEyeHeight() - FIRST_PERSON_EYE_CLEARANCE : GLOW_HEIGHT;
		Vec3 base = player.getPosition(partialTick).subtract(context.levelState().cameraRenderState.pos);
		double time = player.tickCount + partialTick;
		RandomSource random = RandomSource.create(player.getId() * 31L + player.tickCount / TICKS_PER_SHAPE);

		List<Segment> columns = new ArrayList<>();
		for (int i = 0; i < COLUMNS; i++) {
			double angle = Math.PI * 2 * i / COLUMNS + time * SPIN_PER_TICK;
			Vec3 foot = base.add(Math.cos(angle) * radius, random.nextDouble() * 0.3, Math.sin(angle) * radius);
			columns.add(new Segment(foot, foot.add(0, height * (0.6 + random.nextDouble() * 0.4), 0)));
		}

		List<Segment> tongues = new ArrayList<>();
		for (int i = 0; i < TONGUES; i++) {
			tongues.addAll(tongue(random, base, radius, height));
		}

		float pulse = (float) (1 + PULSE_DEPTH * Math.sin(time * PULSE_SPEED));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			LightningDraw.draw(pose.pose(), buffer, columns, GLOW_WIDTH * pulse, LightningDraw.FIRE, 1f);
			LightningDraw.draw(pose.pose(), buffer, tongues, TONGUE_WIDTH * pulse, LightningDraw.CRIMSON, 1f);
		});
	}

	/** One tongue of flame rising from the feet and curling outward as it climbs. */
	private static List<Segment> tongue(RandomSource random, Vec3 base, double radius, double height) {
		List<Segment> segments = new ArrayList<>();
		double angle = random.nextDouble() * Math.PI * 2;
		Vec3 point = base.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
		for (int i = 1; i <= TONGUE_STEPS; i++) {
			double climb = (double) i / TONGUE_STEPS;
			double out = radius + TONGUE_CURL * climb;
			Vec3 next = base.add(Math.cos(angle) * out, height * climb, Math.sin(angle) * out)
				.add(LightningDraw.randomDirection(random).scale(TONGUE_JAG));
			segments.add(new Segment(point, next));
			point = next;
		}
		return segments;
	}
}
