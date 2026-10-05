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

	/** One glow layer; a palette is several, drawn outside-in. */
	record Layer(float halfWidth, float red, float green, float blue, float alpha) {
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

	// Explosion palettes: orange-yellow fire, and the deep red of the ground blast.
	static final Layer[] FIRE = {
		new Layer(0.12f, 1.0f, 0.18f, 0.02f, 0.25f),
		new Layer(0.06f, 1.0f, 0.4f, 0.05f, 0.5f),
		new Layer(0.025f, 1.0f, 0.85f, 0.5f, 0.9f),
	};
	// The cluster bomb's deep crimson: red all the way to the core.
	static final Layer[] CRIMSON = {
		new Layer(0.14f, 1.0f, 0.0f, 0.0f, 0.45f),
		new Layer(0.07f, 1.0f, 0.08f, 0.04f, 0.7f),
		new Layer(0.03f, 1.0f, 0.35f, 0.25f, 0.9f),
	};
	// Decay: ashen grey crackle around a dried-blood red core.
	static final Layer[] DECAY = {
		new Layer(0.06f, 0.55f, 0.52f, 0.6f, 0.35f),
		new Layer(0.03f, 0.75f, 0.1f, 0.1f, 0.7f),
		new Layer(0.012f, 1.0f, 0.55f, 0.5f, 0.9f),
	};
	// Frost: pale blue to white.
	static final Layer[] ICE = {
		new Layer(0.1f, 0.3f, 0.6f, 1.0f, 0.25f),
		new Layer(0.05f, 0.6f, 0.85f, 1.0f, 0.5f),
		new Layer(0.02f, 0.95f, 1.0f, 1.0f, 0.9f),
	};
	// Gojo's Blue, and the violet of Hollow Purple.
	static final Layer[] BLUE = {
		new Layer(0.12f, 0.05f, 0.25f, 1.0f, 0.3f),
		new Layer(0.06f, 0.2f, 0.5f, 1.0f, 0.55f),
		new Layer(0.025f, 0.8f, 0.95f, 1.0f, 0.9f),
	};
	static final Layer[] PURPLE = {
		new Layer(0.14f, 0.5f, 0.0f, 0.9f, 0.35f),
		new Layer(0.07f, 0.7f, 0.2f, 1.0f, 0.6f),
		new Layer(0.03f, 1.0f, 0.8f, 1.0f, 0.9f),
	};
	// Sukuna's cuts: a blood red edge around a white-hot line.
	static final Layer[] SLASH = {
		new Layer(0.08f, 0.8f, 0.0f, 0.1f, 0.4f),
		new Layer(0.04f, 1.0f, 0.2f, 0.25f, 0.7f),
		new Layer(0.015f, 1.0f, 0.95f, 0.9f, 1.0f),
	};
	static final Layer[] RED = {
		new Layer(0.12f, 0.9f, 0.05f, 0.05f, 0.3f),
		new Layer(0.06f, 1.0f, 0.2f, 0.1f, 0.55f),
		new Layer(0.025f, 1.0f, 0.75f, 0.6f, 0.9f),
	};

	static void drawGreen(Matrix4fc pose, VertexConsumer buffer, List<Segment> segments, float widthScale) {
		draw(pose, buffer, segments, widthScale, GREEN, 1f);
	}

	static void draw(Matrix4fc pose, VertexConsumer buffer, List<Segment> segments, float widthScale, Layer[] palette,
			float alphaScale) {
		for (Layer layer : palette) {
			Layer faded = new Layer(layer.halfWidth(), layer.red(), layer.green(), layer.blue(), layer.alpha() * alphaScale);
			for (Segment segment : segments) {
				drawSegment(pose, buffer, segment, layer.halfWidth() * widthScale, faded);
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

	/** One flat-colored four-sided face, visible from both sides. */
	static void drawFlatQuad(Matrix4fc pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float red, float green, float blue, float alpha) {
		quad(pose, buffer, a, b, c, d, new Layer(0, red, green, blue, alpha));
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
