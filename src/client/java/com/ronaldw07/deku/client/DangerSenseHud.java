package com.ronaldw07.deku.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Yellow lightning on the edge of the screen facing the danger. A faint flicker when
 * something's coming; when it's very close, big bolts crackle in from every side.
 */
final class DangerSenseHud {
	private static final float FAINT_THRESHOLD = 0.3f;
	private static final int TICKS_PER_SHAPE = 2;
	private static final int FAINT_BOLTS = 2;
	private static final int CLOSE_BOLTS = 7;
	private static final int SEGMENTS_PER_BOLT = 6;
	private static final float FAINT_REACH = 0.18f; // fraction of the smaller screen side
	private static final float CLOSE_REACH = 0.35f;
	private static final float EDGE_SPREAD = 0.25f;
	private static final float JAG = 0.6f; // sideways kink per segment, as a fraction of segment length
	private static final double BRANCH_CHANCE = 0.25;
	private static final int CORE_COLOR = 0xFFFFF7A0;
	private static final int GLOW_COLOR = 0x80FFC400;
	private static final int FAINT_CORE_COLOR = 0xB0FFF7A0;
	private static final int FAINT_GLOW_COLOR = 0x40FFC400;

	private record Line(float x0, float y0, float x1, float y1) {
	}

	private DangerSenseHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		float level = DangerSenseClient.level();
		if (player == null || level < FAINT_THRESHOLD) {
			return;
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		float size = Math.min(width, height);
		boolean veryClose = level >= DangerSenseClient.VERY_CLOSE;
		RandomSource random = RandomSource.create(player.tickCount / TICKS_PER_SHAPE);
		Vec2 toward = screenDirection(player, DangerSenseClient.source(), delta.getGameTimeDeltaPartialTick(false));

		List<Line> lines = new ArrayList<>();
		float reach = size * (veryClose ? CLOSE_REACH : FAINT_REACH) * level;
		int bolts = veryClose ? CLOSE_BOLTS : FAINT_BOLTS;
		for (int i = 0; i < bolts; i++) {
			Vec2 start = edgePoint(width, height, toward, (random.nextFloat() * 2 - 1) * EDGE_SPREAD * size);
			addBolt(lines, random, start, new Vec2(width / 2f, height / 2f), reach);
		}
		if (veryClose) {
			// The tingle spreads: smaller bolts from the other three sides too.
			for (Vec2 side : new Vec2[] {new Vec2(-toward.y, toward.x), new Vec2(toward.y, -toward.x), toward.negated()}) {
				addBolt(lines, random, edgePoint(width, height, side, 0), new Vec2(width / 2f, height / 2f), reach * 0.6f);
			}
		}

		int glow = veryClose ? GLOW_COLOR : FAINT_GLOW_COLOR;
		int core = veryClose ? CORE_COLOR : FAINT_CORE_COLOR;
		lines.forEach(line -> drawLine(graphics, line, 3, glow));
		lines.forEach(line -> drawLine(graphics, line, 1, core));
	}

	/** Which way the danger is on screen: ahead is up, behind is down, right is right. */
	private static Vec2 screenDirection(LocalPlayer player, Vec3 source, float partialTick) {
		Vec3 offset = source.subtract(player.getEyePosition(partialTick));
		double yaw = Math.toRadians(player.getViewYRot(partialTick));
		double ahead = offset.x * -Math.sin(yaw) + offset.z * Math.cos(yaw);
		double right = offset.x * -Math.cos(yaw) + offset.z * -Math.sin(yaw);
		Vec2 direction = new Vec2((float) right, (float) -ahead);
		return direction.lengthSquared() < 1.0E-4f ? new Vec2(0, -1) : direction.normalized();
	}

	/** Where a ray from the screen's center in the given direction meets the edge, slid along it by offset. */
	private static Vec2 edgePoint(int width, int height, Vec2 direction, float offset) {
		float halfWidth = width / 2f;
		float halfHeight = height / 2f;
		float scale = Math.min(
			Math.abs(direction.x) < 1.0E-4f ? Float.MAX_VALUE : halfWidth / Math.abs(direction.x),
			Math.abs(direction.y) < 1.0E-4f ? Float.MAX_VALUE : halfHeight / Math.abs(direction.y));
		float x = halfWidth + direction.x * scale - direction.y * offset;
		float y = halfHeight + direction.y * scale + direction.x * offset;
		return new Vec2(Math.clamp(x, 0, width), Math.clamp(y, 0, height));
	}

	private static void addBolt(List<Line> lines, RandomSource random, Vec2 start, Vec2 center, float length) {
		Vec2 forward = center.add(start.negated()).normalized();
		Vec2 sideways = new Vec2(-forward.y, forward.x);
		float step = length / SEGMENTS_PER_BOLT;
		Vec2 point = start;
		for (int i = 0; i < SEGMENTS_PER_BOLT; i++) {
			Vec2 next = point.add(forward.scale(step)).add(sideways.scale((random.nextFloat() * 2 - 1) * JAG * step));
			lines.add(new Line(point.x, point.y, next.x, next.y));
			if (random.nextDouble() < BRANCH_CHANCE) {
				Vec2 branch = next.add(forward.scale(step * 0.7f)).add(sideways.scale((random.nextFloat() * 2 - 1) * step * 1.5f));
				lines.add(new Line(next.x, next.y, branch.x, branch.y));
			}
			point = next;
		}
	}

	private static void drawLine(GuiGraphicsExtractor graphics, Line line, int thickness, int color) {
		float dx = line.x1() - line.x0();
		float dy = line.y1() - line.y0();
		int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
		int half = thickness / 2;
		for (int i = 0; i <= steps; i++) {
			int x = Math.round(line.x0() + dx * i / steps);
			int y = Math.round(line.y0() + dy * i / steps);
			graphics.fill(x - half, y - half, x - half + thickness, y - half + thickness, color);
		}
	}
}
