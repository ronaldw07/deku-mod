package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuParticles;
import com.ronaldw07.deku.client.LightningDraw.Segment;
import com.ronaldw07.deku.network.DomainPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Malevolent Shrine: a vast dome of blood-red lattice closing over the area, a shrine of
 * glowing red tiers floating at its heart, a darkness over the screen of anyone caught inside, and
 * ash and embers drifting through the air.
 */
final class DomainFx {
	private static final int OPEN_TICKS = 20;
	private static final int CLOSE_TICKS = 15;
	private static final int LATITUDES = 12;
	private static final int LONGITUDES = 20;
	private static final int ARC_SEGMENTS = 36;
	private static final float LATTICE_WIDTH = 8f;
	private static final float SHRINE_WIDTH = 5f;
	private static final double SHRINE_HEIGHT = 18.0;
	private static final int SHRINE_TIERS = 3;
	private static final double SHRINE_BASE_WIDTH = 14.0;
	private static final double SHRINE_TIER_SHRINK = 3.0;
	private static final double SHRINE_TIER_HEIGHT = 3.0;
	private static final double SHRINE_TIER_SPACING = 4.0;
	private static final double SHRINE_ROOF_RISE = 4.0;
	private static final double FLOOR_RING_RADIUS = 18.0;
	private static final int FLOOR_RING_SEGMENTS = 40;
	private static final float MAX_DARKNESS = 0.5f;
	private static final int DARKNESS_COLOR = 0x100000;
	private static final int ASH_PER_TICK = 6;
	private static final double ASH_RANGE = 25.0;
	private static final DustParticleOptions EMBER = new DustParticleOptions(0xFF2030, 1.5f);

	private record Dome(Vec3 center, float radius, long startTick, int ticks) {
	}

	private static Dome current;

	private DomainFx() {
	}

	static void add(DomainPayload fx) {
		ClientLevel level = Minecraft.getInstance().level;
		current = fx.ticks() <= 0 || level == null ? null : new Dome(fx.center(), fx.radius(), level.getGameTime(), fx.ticks());
	}

	static boolean inside() {
		LocalPlayer player = Minecraft.getInstance().player;
		return current != null && player != null && player.position().distanceTo(current.center()) <= current.radius();
	}

	/** Ash and embers drifting around anyone inside, and the dome clearing when its time is up. */
	static void tick(ClientLevel level) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (level == null || current == null) {
			return;
		}
		if (level.getGameTime() - current.startTick() > current.ticks()) {
			current = null;
			return;
		}
		if (player == null || !inside()) {
			return;
		}
		RandomSource random = level.getRandom();
		int count = Math.max(1, (int) Math.round(ASH_PER_TICK * DekuSettings.get().detailScale()));
		for (int i = 0; i < count; i++) {
			Vec3 at = player.position().add((random.nextDouble() * 2 - 1) * ASH_RANGE, random.nextDouble() * 12, (random.nextDouble() * 2 - 1) * ASH_RANGE);
			level.addParticle(i % 2 == 0 ? DekuParticles.ASH_FLAKE : EMBER, at.x, at.y, at.z, 0, -0.05, 0);
		}
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (current == null || minecraft.level == null) {
			return;
		}
		double age = minecraft.level.getGameTime() - current.startTick() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double open = Math.min(1, age / OPEN_TICKS);
		double radius = current.radius() * (1 - Math.pow(1 - open, 3));
		float fade = (float) Math.min(1, Math.max(0, (current.ticks() - age) / CLOSE_TICKS));
		Vec3 center = current.center().subtract(context.levelState().cameraRenderState.pos);

		List<Segment> lattice = new ArrayList<>();
		for (int i = 1; i < LATITUDES; i++) {
			double polar = Math.PI * i / LATITUDES;
			for (int j = 0; j < ARC_SEGMENTS; j++) {
				lattice.add(new Segment(onSphere(center, radius, polar, Math.PI * 2 * j / ARC_SEGMENTS),
					onSphere(center, radius, polar, Math.PI * 2 * (j + 1) / ARC_SEGMENTS)));
			}
		}
		for (int i = 0; i < LONGITUDES; i++) {
			double around = Math.PI * 2 * i / LONGITUDES + age * 0.003;
			for (int j = 0; j < ARC_SEGMENTS / 2; j++) {
				lattice.add(new Segment(onSphere(center, radius, Math.PI * j / (ARC_SEGMENTS / 2), around),
					onSphere(center, radius, Math.PI * (j + 1) / (ARC_SEGMENTS / 2), around)));
			}
		}
		List<Segment> shrine = shrine(center.add(0, SHRINE_HEIGHT * open, 0));
		List<Segment> floor = new ArrayList<>();
		for (int i = 0; i < FLOOR_RING_SEGMENTS; i++) {
			double a = Math.PI * 2 * i / FLOOR_RING_SEGMENTS;
			double b = Math.PI * 2 * (i + 1) / FLOOR_RING_SEGMENTS;
			floor.add(new Segment(center.add(Math.cos(a) * FLOOR_RING_RADIUS, 0.2, Math.sin(a) * FLOOR_RING_RADIUS),
				center.add(Math.cos(b) * FLOOR_RING_RADIUS, 0.2, Math.sin(b) * FLOOR_RING_RADIUS)));
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			LightningDraw.draw(pose.pose(), buffer, lattice, LATTICE_WIDTH, LightningDraw.CRIMSON, 0.7f * fade);
			LightningDraw.draw(pose.pose(), buffer, shrine, SHRINE_WIDTH, LightningDraw.RED, fade);
			LightningDraw.draw(pose.pose(), buffer, floor, SHRINE_WIDTH, LightningDraw.CRIMSON, fade);
		});
	}

	/** Stacked tiers, each a smaller glowing box, joined by corner pillars and topped with a pointed roof. */
	private static List<Segment> shrine(Vec3 base) {
		List<Segment> lines = new ArrayList<>();
		double topY = 0;
		double topHalf = 0;
		for (int tier = 0; tier < SHRINE_TIERS; tier++) {
			double half = (SHRINE_BASE_WIDTH - SHRINE_TIER_SHRINK * tier) / 2;
			double y0 = tier * SHRINE_TIER_SPACING;
			double y1 = y0 + SHRINE_TIER_HEIGHT;
			box(lines, base, half, y0, y1);
			if (tier > 0) {
				double lowerHalf = (SHRINE_BASE_WIDTH - SHRINE_TIER_SHRINK * (tier - 1)) / 2;
				for (int sx = -1; sx <= 1; sx += 2) {
					for (int sz = -1; sz <= 1; sz += 2) {
						lines.add(new Segment(base.add(sx * lowerHalf, y0 - SHRINE_TIER_SPACING + SHRINE_TIER_HEIGHT, sz * lowerHalf),
							base.add(sx * half, y0, sz * half)));
					}
				}
			}
			topY = y1;
			topHalf = half;
		}
		Vec3 apex = base.add(0, topY + SHRINE_ROOF_RISE, 0);
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				lines.add(new Segment(base.add(sx * topHalf, topY, sz * topHalf), apex));
			}
		}
		return lines;
	}

	private static void box(List<Segment> lines, Vec3 base, double half, double y0, double y1) {
		for (double y : new double[] {y0, y1}) {
			Vec3 a = base.add(-half, y, -half);
			Vec3 b = base.add(half, y, -half);
			Vec3 c = base.add(half, y, half);
			Vec3 d = base.add(-half, y, half);
			lines.add(new Segment(a, b));
			lines.add(new Segment(b, c));
			lines.add(new Segment(c, d));
			lines.add(new Segment(d, a));
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				lines.add(new Segment(base.add(sx * half, y0, sz * half), base.add(sx * half, y1, sz * half)));
			}
		}
	}

	private static Vec3 onSphere(Vec3 center, double radius, double polar, double around) {
		return center.add(radius * Math.sin(polar) * Math.cos(around), radius * Math.cos(polar), radius * Math.sin(polar) * Math.sin(around));
	}

	/** A darkness over the screen while inside the dome. */
	static void extractOverlay(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!inside()) {
			return;
		}
		int alpha = (int) (Mth.clamp(MAX_DARKNESS, 0, 1) * 255);
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | DARKNESS_COLOR);
	}
}
