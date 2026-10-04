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

/** Green lightning crawling around the punching arm while Smash charges, thicker and denser the closer it is to full. */
final class SmashChargeFx {
	private static final int MIN_COILS = 1;
	private static final int EXTRA_COILS = 4;
	private static final int TICKS_PER_SHAPE = 2;
	private static final float MIN_WIDTH = 1.0f;
	private static final float EXTRA_WIDTH = 2.0f;

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
		RandomSource random = RandomSource.create(player.getId() * 31L + player.tickCount / TICKS_PER_SHAPE);

		List<Segment> segments = new ArrayList<>();
		for (int i = 0; i < MIN_COILS + charge * EXTRA_COILS; i++) {
			segments.addAll(LimbLightning.coil(random, arm[0].subtract(camera), arm[1].subtract(camera)));
		}
		float width = (float) (MIN_WIDTH + EXTRA_WIDTH * charge);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(),
			(pose, buffer) -> LightningDraw.drawGreen(pose.pose(), buffer, segments, width));
	}
}
