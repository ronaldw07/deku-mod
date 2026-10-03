package com.ronaldw07.deku.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

/**
 * Draws chains of segments as thin crossed ribbons: the green glow for lightning (meant for
 * the additive lightning render type) or any flat color, like Blackwhip's tendrils.
 */
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

	static void drawFlat(Matrix4fc pose, VertexConsumer buffer, List<Segment> segments, float halfWidth,
			float red, float green, float blue, float alpha) {
		Layer layer = new Layer(halfWidth, red, green, blue, alpha);
		for (Segment segment : segments) {
			drawSegment(pose, buffer, segment, halfWidth, layer);
		}
	}

	/**
	 * Glowing edges only: strips from innerHalfWidth out to outerHalfWidth on both sides,
	 * leaving the middle clear so a separately drawn dark core stays dark.
	 */
	static void drawEdges(Matrix4fc pose, VertexConsumer buffer, List<Segment> segments, float innerHalfWidth,
			float outerHalfWidth, float red, float green, float blue, float alpha) {
		Layer layer = new Layer(outerHalfWidth, red, green, blue, alpha);
		for (Segment segment : segments) {
			Vec3 along = segment.to().subtract(segment.from());
			for (Vec3 side : sides(along)) {
				for (int sign = -1; sign <= 1; sign += 2) {
					Vec3 inner = side.scale(sign * innerHalfWidth);
					Vec3 outer = side.scale(sign * outerHalfWidth);
					quad(pose, buffer, segment.from().add(inner), segment.to().add(inner),
						segment.to().add(outer), segment.from().add(outer), layer);
				}
			}
		}
	}

	static Vec3 randomDirection(RandomSource random) {
		return new Vec3(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1).normalize();
	}

	/** Two crossed ribbons along the segment, each drawn from both sides so it shows from any angle. */
	private static void drawSegment(Matrix4fc pose, VertexConsumer buffer, Segment segment, float halfWidth, Layer layer) {
		for (Vec3 side : sides(segment.to().subtract(segment.from()))) {
			Vec3 offset = side.scale(halfWidth);
			quad(pose, buffer, segment.from().add(offset), segment.to().add(offset),
				segment.to().subtract(offset), segment.from().subtract(offset), layer);
		}
	}

	/** Two unit vectors at right angles to each other and to the segment: the ribbons' widths. */
	private static Vec3[] sides(Vec3 along) {
		Vec3 side = along.cross(new Vec3(0, 1, 0));
		if (side.lengthSqr() < 1.0E-6) {
			side = along.cross(new Vec3(1, 0, 0));
		}
		Vec3 u = side.normalize();
		return new Vec3[] {u, along.cross(u).normalize()};
	}

	private static void quad(Matrix4fc pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Layer layer) {
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
