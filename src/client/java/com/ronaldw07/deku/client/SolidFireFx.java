package com.ronaldw07.deku.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

/**
 * Fire drawn as solid shapes instead of sparks: a burning sun (a white-hot core inside orange
 * and red shells, ringed with licking tongues of flame) and a roaring cone of flame tongues.
 */
final class SolidFireFx {
	private static final int SUN_TONGUES = 22;
	private static final int TONGUE_STEPS = 6;
	private static final double SUN_TONGUE_REACH = 0.75; // in sun radii
	private static final int CONE_TONGUES = 14;
	private static final int CONE_STEPS = 10;
	// Root to tip: white-gold, yellow-orange, orange, red, dark red.
	private static final float[][] HEAT = {
		{1.0f, 0.97f, 0.7f},
		{1.0f, 0.75f, 0.2f},
		{1.0f, 0.48f, 0.05f},
		{0.9f, 0.2f, 0.04f},
		{0.55f, 0.05f, 0.02f},
	};

	private SolidFireFx() {
	}

	/** A ball of fire like a small sun: solid shells from white to red with flames licking off it. */
	static void sun(Matrix4fc pose, VertexConsumer buffer, Vec3 center, double radius, double time) {
		FireballChargeFx.drawSolidSphere(pose, buffer, center, radius * 1.08, 0.85f, 0.12f, 0.03f, 0.8f);
		FireballChargeFx.drawSolidSphere(pose, buffer, center, radius * 0.92, 1.0f, 0.45f, 0.05f, 0.95f);
		FireballChargeFx.drawSolidSphere(pose, buffer, center, radius * 0.62, 1.0f, 0.88f, 0.45f, 1f);
		for (int i = 0; i < SUN_TONGUES; i++) {
			// Spread evenly over the ball (golden spiral), each swaying in its own rhythm.
			double y = 1 - 2 * (i + 0.5) / SUN_TONGUES;
			double ring = Math.sqrt(1 - y * y);
			double around = i * 2.39996 + time * 0.05;
			Vec3 out = new Vec3(Math.cos(around) * ring, y, Math.sin(around) * ring);
			double reach = radius * SUN_TONGUE_REACH * (0.6 + 0.4 * Math.sin(time * 0.4 + i * 1.7));
			tongue(pose, buffer, center.add(out.scale(radius * 0.85)), out, reach, radius * 0.32, time, i, TONGUE_STEPS, 0.25);
		}
	}

	/** A roaring cone of fire pouring from a point along a direction, widening as it goes. */
	static void cone(Matrix4fc pose, VertexConsumer buffer, Vec3 origin, Vec3 direction, double length, double spread, double time) {
		Vec3 side = direction.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 up = side.cross(direction).normalize();
		for (int i = 0; i < CONE_TONGUES; i++) {
			double angle = i * 2.39996;
			double tilt = spread * (0.2 + 0.8 * ((i * 37) % 10) / 10.0);
			Vec3 heading = direction.add(side.scale(Math.cos(angle) * tilt)).add(up.scale(Math.sin(angle) * tilt)).normalize();
			double reach = length * (0.65 + 0.35 * Math.sin(time * 0.6 + i * 2.1) * 0.5 + 0.35 * ((i * 13) % 10) / 10.0);
			tongue(pose, buffer, origin, heading, reach, 0.1 + length * 0.035, time, i, CONE_STEPS, 0.6);
		}
	}

	/**
	 * One tongue of flame: two crossed ribbons, narrow at the root, swelling, then tapering to a
	 * point, wavering as it goes and shading from white-gold at the root to dark red at the tip.
	 */
	private static void tongue(Matrix4fc pose, VertexConsumer buffer, Vec3 root, Vec3 heading, double reach, double width, double time, int index,
			int steps, double waver) {
		Vec3 helper = Math.abs(heading.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 sideA = heading.cross(helper).normalize();
		Vec3 sideB = heading.cross(sideA).normalize();
		for (Vec3 side : new Vec3[] {sideA, sideB}) {
			Vec3 previousLeft = null;
			Vec3 previousRight = null;
			float[] previousColor = null;
			for (int step = 0; step <= steps; step++) {
				double t = (double) step / steps;
				double wobble = Math.sin(t * 6 + time * 0.5 + index * 1.3) * reach * waver * 0.2 * t;
				Vec3 center = root.add(heading.scale(reach * t)).add(sideA.scale(wobble)).add(sideB.scale(wobble * 0.6));
				double half = width * Math.sin(Math.PI * Math.min(1, t * 1.4 + 0.15)) * (1 - t * 0.6);
				Vec3 left = center.subtract(side.scale(half));
				Vec3 right = center.add(side.scale(half));
				float[] color = heat(t);
				if (previousLeft != null) {
					LightningDraw.drawGradientQuad(pose, buffer, new Vec3[] {previousLeft, previousRight, right, left},
						new float[][] {previousColor, previousColor, color, color}, 0.95f);
				}
				previousLeft = left;
				previousRight = right;
				previousColor = color;
			}
		}
	}

	private static float[] heat(double along) {
		double scaled = Mth.clamp(along, 0, 1) * (HEAT.length - 1);
		int low = (int) Math.floor(scaled);
		int high = Math.min(HEAT.length - 1, low + 1);
		float mix = (float) (scaled - low);
		return new float[] {Mth.lerp(mix, HEAT[low][0], HEAT[high][0]), Mth.lerp(mix, HEAT[low][1], HEAT[high][1]), Mth.lerp(mix, HEAT[low][2], HEAT[high][2])};
	}
}
