package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.LightningDraw.Segment;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Fuga being drawn: a bow of flame floating in front of the player with a burning arrow pulled
 * back on it, flames licking off both, brighter and heavier the longer X is held.
 */
final class FugaFx {
	private static final double DISTANCE = 2.6;
	private static final double DROP = 0.5;
	private static final double SIDE = 0.8; // held out to the right so the bow is seen from the side
	private static final double ARROW_LENGTH = 3.2;
	private static final double ARROW_HEAD = 0.5;
	private static final double MIN_BOW_RADIUS = 0.9;
	private static final double EXTRA_BOW_RADIUS = 1.1;
	private static final double MAX_PULL = 1.4;
	private static final int BOW_SEGMENTS = 14;
	private static final double BOW_SWEEP = 1.25; // radians either side of the middle
	private static final double BOW_CURVE = 0.5;
	private static final int FLAMES_PER_TICK = 2;
	private static final float MAX_RUMBLE = 0.3f;

	private FugaFx() {
	}

	private record Frame(Vec3 center, Vec3 forward, Vec3 side) {
	}

	private static Frame frame(LocalPlayer player, float partialTick) {
		Vec3 forward = player.getViewVector(partialTick);
		Vec3 side = forward.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 center = player.getEyePosition(partialTick).add(forward.scale(DISTANCE)).add(side.scale(SIDE)).add(0, -DROP, 0);
		return new Frame(center, forward, side);
	}

	static void tick(LocalPlayer player) {
		int charge = SukunaClient.fugaCharge();
		if (player == null || charge == 0) {
			return;
		}
		double power = charge / 100.0;
		ScreenShake.rumble(MAX_RUMBLE * (float) (power * power));
		Frame frame = frame(player, 1f);
		RandomSource random = player.getRandom();
		int count = Math.max(1, (int) Math.round(FLAMES_PER_TICK * DekuSettings.get().detailScale()));
		for (int i = 0; i < count; i++) {
			Vec3 at = frame.center().add(frame.forward().scale(random.nextDouble() * ARROW_LENGTH - ARROW_LENGTH * 0.7))
				.add(frame.side().scale((random.nextDouble() - 0.5) * 2 * (MIN_BOW_RADIUS + EXTRA_BOW_RADIUS * power)));
			player.level().addParticle(ParticleTypes.FLAME, at.x, at.y, at.z, 0, 0.03, 0);
		}
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		int charge = SukunaClient.fugaCharge();
		if (player == null || charge == 0) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double power = charge / 100.0;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Frame frame = frame(player, partialTick);
		Vec3 center = frame.center().subtract(camera);
		Vec3 up = frame.forward().cross(frame.side()).scale(-1);

		// The bow: an arc bowed away from the player, its tips joined by the string drawn back with the arrow.
		double radius = MIN_BOW_RADIUS + EXTRA_BOW_RADIUS * power;
		List<Segment> bow = new ArrayList<>();
		Vec3 previous = null;
		Vec3 bottomTip = null;
		Vec3 topTip = null;
		for (int i = 0; i <= BOW_SEGMENTS; i++) {
			double angle = -BOW_SWEEP + 2 * BOW_SWEEP * i / BOW_SEGMENTS;
			Vec3 point = center.add(up.scale(Math.sin(angle) * radius)).add(frame.forward().scale((Math.cos(angle) - 1) * -radius * BOW_CURVE));
			if (previous != null) {
				bow.add(new Segment(previous, point));
			}
			if (i == 0) {
				bottomTip = point;
			}
			topTip = point;
			previous = point;
		}
		double pull = MAX_PULL * power;
		Vec3 nock = center.subtract(frame.forward().scale(pull));
		Vec3 tip = nock.add(frame.forward().scale(ARROW_LENGTH));
		List<Segment> arrow = new ArrayList<>();
		arrow.add(new Segment(bottomTip, nock));
		arrow.add(new Segment(nock, topTip));
		arrow.add(new Segment(nock, tip));
		Vec3 headBase = tip.subtract(frame.forward().scale(ARROW_HEAD));
		for (Vec3 out : new Vec3[] {frame.side().scale(ARROW_HEAD * 0.4), frame.side().scale(-ARROW_HEAD * 0.4), up.scale(ARROW_HEAD * 0.4), up.scale(-ARROW_HEAD * 0.4)}) {
			arrow.add(new Segment(tip, headBase.add(out)));
		}
		float width = (float) (0.8 + 1.4 * power);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			LightningDraw.draw(pose.pose(), buffer, bow, width * 1.3f, LightningDraw.FIRE, 0.8f);
			LightningDraw.draw(pose.pose(), buffer, arrow, width, LightningDraw.FIRE, 1f);
		});
	}
}
