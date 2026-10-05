package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.LightningDraw.Segment;
import com.ronaldw07.deku.network.SlashFxPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Sukuna's slashes: a thin sheet of black that shoots out along its path, outlined in white with
 * a glow of red, with a trail of sweeping cuts and sparks left behind, fading in a few ticks.
 */
final class SlashFx {
	private static final int LIFETIME_TICKS = 12;
	private static final int GROW_TICKS = 2;
	private static final float BIG_CUT_HEIGHT = 10f;
	private static final float EDGE = 0.04f;
	private static final float BIG_EDGE = 0.06f;
	private static final float RED_EDGE = 0.05f;
	private static final float EDGE_PER_HEIGHT = 0.003f; // a bigger cut gets a heavier outline
	private static final float RUMBLE = 0.25f;
	private static final double PARTICLE_SPACING = 5.0;

	private record Slash(Vec3 origin, Vec3 aim, Vec3 blade, float length, float halfHeight, boolean big, long startTick) {
	}

	private static List<Slash> slashes = List.of();

	private SlashFx() {
	}

	static void add(SlashFxPayload fx) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		if (fx.halfHeight() >= BIG_CUT_HEIGHT) {
			ScreenShake.rumble(RUMBLE);
		}
		slashes = Stream.concat(slashes.stream(), Stream.of(new Slash(fx.origin(), fx.aim(), fx.blade(), fx.length(), fx.halfHeight(), fx.big(), level.getGameTime()))).toList();
		// Sweeping cuts and sparks left along the path.
		for (double along = 0; along <= fx.length(); along += PARTICLE_SPACING) {
			Vec3 at = fx.origin().add(fx.aim().scale(along));
			level.addParticle(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 0, 0, 0);
			level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, fx.blade().x * 0.3, fx.blade().y * 0.3, fx.blade().z * 0.3);
		}
	}

	static void tick(ClientLevel level) {
		if (level == null) {
			slashes = List.of();
			return;
		}
		long now = level.getGameTime();
		slashes = slashes.stream().filter(slash -> now - slash.startTick() < LIFETIME_TICKS).toList();
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || slashes.isEmpty()) {
			return;
		}
		double partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		long now = minecraft.level.getGameTime();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Slash slash : slashes) {
			double age = now - slash.startTick() + partialTick;
			double reach = slash.length() * Math.min(1, age / GROW_TICKS);
			Vec3 origin = slash.origin().subtract(camera);
			Vec3 tip = origin.add(slash.aim().scale(reach));
			Vec3 top = slash.blade().scale(slash.halfHeight());
			Vec3 a = origin.add(top);
			Vec3 b = origin.subtract(top);
			Vec3 c = tip.subtract(top);
			Vec3 d = tip.add(top);
			// The cut is a black sheet, outlined along its four edges in white with a thin red glow outside that.
			List<Segment> border = List.of(new Segment(a, d), new Segment(d, c), new Segment(c, b), new Segment(b, a));
			float fade = (float) Math.max(0, 1 - age / LIFETIME_TICKS);
			float core = Math.max(slash.big() ? BIG_EDGE : EDGE, slash.halfHeight() * EDGE_PER_HEIGHT);
			float outer = core + RED_EDGE;
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(),
				(pose, buffer) -> LightningDraw.drawFlatQuad(pose.pose(), buffer, a, b, c, d, 0.0f, 0.0f, 0.0f, 0.93f * fade));
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
				LightningDraw.drawEdges(pose.pose(), buffer, border, 0f, core, 1.0f, 0.97f, 0.95f, fade);
				LightningDraw.drawEdges(pose.pose(), buffer, border, core, outer, 0.9f, 0.05f, 0.1f, 0.6f * fade);
			});
		}
	}
}
