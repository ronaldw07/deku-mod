package com.ronaldw07.deku.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

/** Draws glowing lightning segments; meant for the additive lightning render type. */
final class LightningDraw {
	record Segment(Vec3 from, Vec3 to) {
	}

	private record Layer(float halfWidth, float red, float green, float blue, float alpha) {
	}

	// Glow layers, drawn outside-in. Blending is additive, so where they overlap the
	// core burns almost white.
	private static final Layer[] GREEN = {
		new Layer(0.045f, 0.1f, 0.9f, 0.2f, 0.25f),
		new Layer(0.022f, 0.3f, 1.0f, 0.4f, 0.5f),
		new Layer(0.009f, 0.9f, 1.0f, 0.9f, 0.9f),
	};

	private LightningDraw() {
	}

	static void drawGreen(Matrix4fc pose, VertexConsumer buffer, List<Segment> segments, float widthScale) {
		for (Layer layer : GREEN) {
			for (Segment segment : segments) {
				drawSegment(pose, buffer, segment, layer.halfWidth() * widthScale, layer);
			}
		}
	}

	static Vec3 randomDirection(RandomSource random) {
		return new Vec3(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1).normalize();
	}

	/** Two crossed ribbons along the segment, each drawn from both sides so it shows from any angle. */
	private static void drawSegment(Matrix4fc pose, VertexConsumer buffer, Segment segment, float halfWidth, Layer layer) {
		Vec3 along = segment.to().subtract(segment.from());
		Vec3 side = along.cross(new Vec3(0, 1, 0));
		if (side.lengthSqr() < 1.0E-6) {
			side = along.cross(new Vec3(1, 0, 0));
		}
		Vec3 u = side.normalize().scale(halfWidth);
		Vec3 v = along.cross(u).normalize().scale(halfWidth);

		ribbon(pose, buffer, segment, u, layer);
		ribbon(pose, buffer, segment, v, layer);
	}

	private static void ribbon(Matrix4fc pose, VertexConsumer buffer, Segment segment, Vec3 offset, Layer layer) {
		Vec3 a = segment.from().add(offset);
		Vec3 b = segment.to().add(offset);
		Vec3 c = segment.to().subtract(offset);
		Vec3 d = segment.from().subtract(offset);

		vertex(pose, buffer, a, layer);
		vertex(pose, buffer, b, layer);
		vertex(pose, buffer, c, layer);
		vertex(pose, buffer, d, layer);

		vertex(pose, buffer, d, layer);
		vertex(pose, buffer, c, layer);
		vertex(pose, buffer, b, layer);
		vertex(pose, buffer, a, layer);
	}

	private static void vertex(Matrix4fc pose, VertexConsumer buffer, Vec3 point, Layer layer) {
		buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
			.setColor(layer.red(), layer.green(), layer.blue(), layer.alpha());
	}
}
