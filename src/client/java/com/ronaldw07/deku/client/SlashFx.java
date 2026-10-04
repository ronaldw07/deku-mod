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
 * Sukuna's slashes: a thin sheet of white-hot light that shoots out along its path, edged in red,
 * with a trail of sweeping cuts and sparks left behind, fading in a few ticks.
 */
final class SlashFx {
	private static final int LIFETIME_TICKS = 8;
	private static final int GROW_TICKS = 2;
	private static final int LINES_EACH_SIDE = 3;
	private static final float WIDTH = 2.5f;
	private static final float BIG_WIDTH = 4.5f;
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
			List<Segment> lines = new ArrayList<>();
			// The sheet drawn as parallel lines along the cut, with its two edges closed off.
			for (int i = -LINES_EACH_SIDE; i <= LINES_EACH_SIDE; i++) {
				Vec3 shift = slash.blade().scale(slash.halfHeight() * i / LINES_EACH_SIDE);
				lines.add(new Segment(origin.add(shift), tip.add(shift)));
			}
			Vec3 top = slash.blade().scale(slash.halfHeight());
			lines.add(new Segment(tip.add(top), tip.subtract(top)));
			lines.add(new Segment(origin.add(top), origin.subtract(top)));
			float fade = (float) Math.max(0, 1 - age / LIFETIME_TICKS);
			float width = slash.big() ? BIG_WIDTH : WIDTH;
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(),
				(pose, buffer) -> LightningDraw.draw(pose.pose(), buffer, lines, width, LightningDraw.SLASH, fade));
		}
	}
}
