package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.LightningDraw.Segment;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Red lightning crackling out of the player in every direction while spinning for Howitzer Impact,
 * longer and denser the longer they spin.
 */
final class SpinFx {
	private static final int FULL_BUILD_TICKS = 40;
	private static final int MIN_BOLTS = 8;
	private static final int EXTRA_BOLTS = 14;
	private static final double MIN_LENGTH = 1.5;
	private static final double EXTRA_LENGTH = 4.0;
	private static final int BOLT_STEPS = 5;
	private static final double BOLT_JAG = 0.3;
	private static final float MIN_WIDTH = 1.0f;
	private static final float EXTRA_WIDTH = 1.5f;
	private static final int TICKS_PER_SHAPE = 2;
	private static final int MAX_EMBERS = 3;
	private static final DustParticleOptions EMBER = new DustParticleOptions(0xFF1010, 1.2f);

	private SpinFx() {
	}

	private static double build() {
		return Mth.clamp(ExplosionClient.spinTicks() / (double) FULL_BUILD_TICKS, 0, 1);
	}

	static void tick(LocalPlayer player) {
		if (player == null || !ExplosionClient.spinning()) {
			return;
		}
		RandomSource random = player.getRandom();
		int count = Math.max(1, (int) Math.round(MAX_EMBERS * (0.3 + 0.7 * build()) * DekuSettings.get().detailScale()));
		for (int i = 0; i < count; i++) {
			Vec3 at = player.position().add((random.nextDouble() - 0.5) * 1.0, random.nextDouble() * player.getBbHeight(), (random.nextDouble() - 0.5) * 1.0);
			Vec3 out = LightningDraw.randomDirection(random).scale(0.3);
			player.level().addParticle(EMBER, at.x, at.y, at.z, out.x, out.y, out.z);
		}
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !ExplosionClient.spinning()) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double build = build();
		Vec3 chest = player.getPosition(partialTick).add(0, player.getBbHeight() * 0.55, 0).subtract(context.levelState().cameraRenderState.pos);
		RandomSource random = RandomSource.create(player.getId() * 31L + player.tickCount / TICKS_PER_SHAPE);

		List<Segment> bolts = new ArrayList<>();
		for (int i = 0; i < MIN_BOLTS + EXTRA_BOLTS * build; i++) {
			double length = (MIN_LENGTH + EXTRA_LENGTH * build) * (0.5 + random.nextDouble() * 0.5);
			Vec3 end = chest.add(LightningDraw.randomDirection(random).scale(length));
			bolts.addAll(LimbLightning.jagged(random, chest, end, BOLT_STEPS, BOLT_JAG));
		}
		float width = (float) (MIN_WIDTH + EXTRA_WIDTH * build);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(),
			(pose, buffer) -> LightningDraw.draw(pose.pose(), buffer, bolts, width, LightningDraw.CRIMSON, 1f));
	}
}
