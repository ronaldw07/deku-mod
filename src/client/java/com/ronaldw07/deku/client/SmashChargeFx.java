package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.LightningDraw.Segment;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Smash charging: pale blue lightning crackling around the whole body and spiralling the arm, with
 * red energy gathering in the punching arm itself, thicker and wilder the closer it is to full.
 */
final class SmashChargeFx {
	private static final int MIN_COILS = 1;
	private static final int EXTRA_COILS = 4;
	private static final int RED_COILS = 2;
	private static final int EXTRA_RED_COILS = 4;
	private static final int TICKS_PER_SHAPE = 2;
	private static final float MIN_WIDTH = 1.0f;
	private static final float EXTRA_WIDTH = 2.0f;
	private static final int MIN_BODY_BOLTS = 3;
	private static final int EXTRA_BODY_BOLTS = 14;
	private static final double BODY_REACH = 0.7;
	private static final double EXTRA_BODY_REACH = 1.4;
	private static final double BODY_HEIGHT = 1.8;
	private static final double HAND_SHARE = 0.45; // the red starts this far down the arm and burns hottest at the fist

	private SmashChargeFx() {
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !SmashClient.charging()) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		double charge = SmashClient.charge() / 100.0;
		Vec3[] arm = LimbLightning.rightArm(player, partialTick);
		Vec3 shoulder = arm[0].subtract(camera);
		Vec3 fist = arm[1].subtract(camera);
		Vec3 elbow = shoulder.lerp(fist, HAND_SHARE);
		RandomSource random = RandomSource.create(player.getId() * 31L + player.tickCount / TICKS_PER_SHAPE);

		List<Segment> blue = new ArrayList<>();
		for (int i = 0; i < MIN_COILS + charge * EXTRA_COILS; i++) {
			blue.addAll(LimbLightning.coil(random, shoulder, fist));
		}
		// Bolts arcing off the body in all directions, more and longer as the charge builds.
		Vec3 feet = player.getPosition(partialTick).subtract(camera);
		int bolts = (int) (MIN_BODY_BOLTS + EXTRA_BODY_BOLTS * charge);
		for (int i = 0; i < bolts; i++) {
			Vec3 start = feet.add((random.nextDouble() - 0.5) * 0.5, random.nextDouble() * BODY_HEIGHT, (random.nextDouble() - 0.5) * 0.5);
			Vec3 out = LightningDraw.randomDirection(random).scale(BODY_REACH + EXTRA_BODY_REACH * charge * random.nextDouble());
			blue.addAll(LimbLightning.jagged(random, start, start.add(out), 4, 0.12));
		}
		List<Segment> red = new ArrayList<>();
		for (int i = 0; i < RED_COILS + charge * EXTRA_RED_COILS; i++) {
			red.addAll(LimbLightning.coil(random, elbow, fist));
		}
		float width = (float) (MIN_WIDTH + EXTRA_WIDTH * charge);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			LightningDraw.draw(pose.pose(), buffer, blue, width, LightningDraw.CYAN, 1f);
			LightningDraw.draw(pose.pose(), buffer, red, width * 1.2f, LightningDraw.RED, 1f);
		});
	}
}
