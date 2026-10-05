package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuParticles;
import com.ronaldw07.deku.client.LightningDraw.Segment;
import com.ronaldw07.deku.network.DomainPayload;
import com.ronaldw07.deku.network.DomainPayload.Kind;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Sukuna's Malevolent Shrine: a vast dome of blood-red lattice closing over the area, a shrine of
 * glowing red tiers floating at its heart, a darkness over the screen of anyone caught inside, and
 * ash and embers drifting through the air. Gojo's Infinite Void is the same dome in cold white:
 * orbiting rings, a blinding star at the center, and static flickering over the screen.
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
	// Infinite Void.
	private static final float VOID_SPHERE_ALPHA = 0.93f;
	private static final int VOID_SPHERE_LATITUDES = 16;
	private static final int VOID_SPHERE_LONGITUDES = 32;
	private static final double VOID_STAR_SHELL = 0.97; // just inside the black, so the stars are never buried
	private static final int VOID_STAR_GROUPS = 3;
	private static final int VOID_STARS_PER_GROUP = 140;
	private static final double VOID_STAR_SIZE = 0.7;
	private static final float VOID_STAR_WIDTH = 7f;
	private static final long VOID_STAR_SEED = 4242;
	private static final float VOID_EDGE_WIDTH = 3f;
	private static final int VOID_RINGS = 3;
	private static final double VOID_RING_SHARE = 0.85; // of the dome's radius
	private static final double VOID_SPIN_PER_TICK = 0.02;
	private static final float VOID_RING_WIDTH = 6f;
	private static final double VOID_STAR_RADIUS = 5.0;
	private static final double VOID_STAR_HEIGHT = 20.0;
	private static final int VOID_STAR_RAYS = 24;
	private static final float VOID_DARKNESS = 0.25f;
	private static final int VOID_DARK_COLOR = 0x000018;
	private static final int VOID_FLICKER_COLOR = 0xE8F0FF;
	private static final float VOID_FLICKER_ALPHA = 0.3f;
	private static final int VOID_FLICKER_ODDS = 6; // one tick in this many flashes white
	private static final DustParticleOptions STARDUST = new DustParticleOptions(0xC8E0FF, 1.2f);

	private record Dome(Vec3 center, float radius, long startTick, int ticks, Kind kind) {
	}

	private static Dome current;

	private DomainFx() {
	}

	static void add(DomainPayload fx) {
		ClientLevel level = Minecraft.getInstance().level;
		current = fx.ticks() <= 0 || level == null ? null : new Dome(fx.center(), fx.radius(), level.getGameTime(), fx.ticks(), fx.kind());
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
			if (current.kind() == Kind.VOID) {
				level.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : STARDUST, at.x, at.y, at.z, 0, 0, 0);
			} else {
				level.addParticle(i % 2 == 0 ? DekuParticles.ASH_FLAKE : EMBER, at.x, at.y, at.z, 0, -0.05, 0);
			}
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
		if (current.kind() == Kind.VOID) {
			renderVoid(context, lattice, center, radius, age, fade);
			return;
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

	/**
	 * The void: a black ball closing all the way around, no sky or ground beyond it, scattered with
	 * twinkling stars, edged in a thin cold light, with great rings turning inside and a blinding
	 * star where it began.
	 */
	private static void renderVoid(LevelRenderContext context, List<Segment> lattice, Vec3 center, double radius, double age, float fade) {
		List<Segment> rings = new ArrayList<>();
		for (int ring = 0; ring < VOID_RINGS; ring++) {
			double tilt = Math.PI * ring / VOID_RINGS;
			double spin = age * VOID_SPIN_PER_TICK * (ring % 2 == 0 ? 1 : -1);
			for (int i = 0; i < ARC_SEGMENTS * 2; i++) {
				rings.add(new Segment(onRing(center, radius * VOID_RING_SHARE, tilt, spin + Math.PI * i / ARC_SEGMENTS),
					onRing(center, radius * VOID_RING_SHARE, tilt, spin + Math.PI * (i + 1) / ARC_SEGMENTS)));
			}
		}
		List<Segment> star = new ArrayList<>();
		RandomSource random = RandomSource.create((long) (age * 0.5) * 31);
		Vec3 starAt = center.add(0, VOID_STAR_HEIGHT, 0);
		for (int i = 0; i < VOID_STAR_RAYS; i++) {
			star.addAll(LimbLightning.jagged(random, starAt, starAt.add(LightningDraw.randomDirection(random).scale(VOID_STAR_RADIUS * (0.5 + random.nextDouble()))), 4, 0.5));
		}
		List<List<Segment>> starGroups = stars(center, radius * VOID_STAR_SHELL);
		float darkness = VOID_SPHERE_ALPHA * fade;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(),
			(pose, buffer) -> blackSphere(pose.pose(), buffer, center, radius, darkness));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			LightningDraw.draw(pose.pose(), buffer, lattice, VOID_EDGE_WIDTH, LightningDraw.ICE, 0.5f * fade);
			LightningDraw.draw(pose.pose(), buffer, rings, VOID_RING_WIDTH, LightningDraw.BLUE, fade * 0.8f);
			LightningDraw.draw(pose.pose(), buffer, star, SHRINE_WIDTH * 2, LightningDraw.ICE, fade);
			for (int group = 0; group < starGroups.size(); group++) {
				float twinkle = (float) (0.55 + 0.45 * Math.sin(age * 0.25 + group * 2.1));
				LightningDraw.draw(pose.pose(), buffer, starGroups.get(group), VOID_STAR_WIDTH, LightningDraw.ICE, twinkle * fade);
			}
		});
	}

	/** The inside of a ball in plain black, drawn as a mesh of faces. */
	private static void blackSphere(org.joml.Matrix4fc pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, Vec3 center, double radius, float alpha) {
		for (int i = 0; i < VOID_SPHERE_LATITUDES; i++) {
			double polar0 = Math.PI * i / VOID_SPHERE_LATITUDES;
			double polar1 = Math.PI * (i + 1) / VOID_SPHERE_LATITUDES;
			for (int j = 0; j < VOID_SPHERE_LONGITUDES; j++) {
				double around0 = Math.PI * 2 * j / VOID_SPHERE_LONGITUDES;
				double around1 = Math.PI * 2 * (j + 1) / VOID_SPHERE_LONGITUDES;
				LightningDraw.drawFlatQuad(pose, buffer, onSphere(center, radius, polar0, around0), onSphere(center, radius, polar0, around1),
					onSphere(center, radius, polar1, around1), onSphere(center, radius, polar1, around0), 0.0f, 0.0f, 0.02f, alpha);
			}
		}
	}

	/** Specks of light scattered over the inside of the ball, in a few groups that twinkle out of step. */
	private static List<List<Segment>> stars(Vec3 center, double radius) {
		RandomSource random = RandomSource.create(VOID_STAR_SEED);
		List<List<Segment>> groups = new ArrayList<>();
		for (int group = 0; group < VOID_STAR_GROUPS; group++) {
			List<Segment> specks = new ArrayList<>();
			for (int i = 0; i < VOID_STARS_PER_GROUP; i++) {
				Vec3 direction = LightningDraw.randomDirection(random);
				Vec3 at = center.add(direction.scale(radius));
				Vec3 helper = Math.abs(direction.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
				Vec3 along = direction.cross(helper).normalize().scale(VOID_STAR_SIZE * (0.5 + random.nextDouble()));
				specks.add(new Segment(at.subtract(along), at.add(along)));
			}
			groups.add(specks);
		}
		return groups;
	}

	/** A circle in a plane tilted around the x axis, turned by the angle. */
	private static Vec3 onRing(Vec3 center, double radius, double tilt, double angle) {
		double a = Math.cos(angle) * radius;
		double b = Math.sin(angle) * radius;
		return center.add(a, b * Math.sin(tilt), b * Math.cos(tilt));
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
		if (current.kind() == Kind.VOID) {
			// Dark space with static flashing white through it.
			Minecraft minecraft = Minecraft.getInstance();
			boolean flash = minecraft.level != null && (minecraft.level.getGameTime() * 7919L) % VOID_FLICKER_ODDS == 0;
			int color = flash ? VOID_FLICKER_COLOR : VOID_DARK_COLOR;
			float amount = flash ? VOID_FLICKER_ALPHA : VOID_DARKNESS;
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (int) (amount * 255) << 24 | color);
			return;
		}
		int alpha = (int) (Mth.clamp(MAX_DARKNESS, 0, 1) * 255);
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | DARKNESS_COLOR);
	}
}
