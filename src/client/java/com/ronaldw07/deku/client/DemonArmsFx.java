package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.LightningDraw.Segment;
import com.ronaldw07.deku.network.DemonArmsFxPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Sukuna's Demon Arms: four extra human arms, pale-skinned and jointed like the real ones, with a
 * shoulder, elbow and a hand with fingers, held out in front of him; and the black marks that
 * run over his face, chest, arms and legs.
 */
final class DemonArmsFx {
	private static final double UPPER_ARM = 0.36;
	private static final double FOREARM = 0.34;
	private static final double SHOULDER_SIDE = 0.3;
	private static final double SHOULDER_BACK = 0.0;
	private static final double PALM = 0.1;
	private static final double FINGER = 0.12;
	private static final double FINGER_SPREAD = 0.045;
	private static final float ARM_WIDTH = 0.1f;
	private static final float MARK_WIDTH = 0.012f;
	private static final double SWAY = 0.06;
	private static final double FIRST_PERSON_DROP = 0.45;
	// Pairs: height up the body, how far out to the side the elbow goes, how far forward the hand reaches, how high the hand rises.
	private static final double[][] PAIRS = {{1.2, 0.55, 0.45, 0.25}, {0.95, 0.5, 0.55, -0.05}};
	private static final float[] SKIN = {0.86f, 0.68f, 0.58f};

	private static Set<UUID> others = Set.of();

	private DemonArmsFx() {
	}

	static void add(DemonArmsFxPayload fx) {
		Set<UUID> next = new HashSet<>(others);
		if (fx.on()) {
			next.add(fx.player());
		} else {
			next.remove(fx.player());
		}
		others = Set.copyOf(next);
	}

	static void reset() {
		others = Set.of();
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		List<Segment> skin = new ArrayList<>();
		List<Segment> black = new ArrayList<>();
		if (DemonArmsClient.active()) {
			draw(skin, black, minecraft.player, partialTick, camera, minecraft.options.getCameraType().isFirstPerson());
		}
		for (UUID id : others) {
			Player other = minecraft.level.getPlayerByUUID(id);
			if (other != null && other != minecraft.player) {
				draw(skin, black, other, partialTick, camera, false);
			}
		}
		if (skin.isEmpty() && black.isEmpty()) {
			return;
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), SolidRender.darkType(), (pose, buffer) -> {
			LightningDraw.drawFlat(pose.pose(), buffer, skin, ARM_WIDTH, SKIN[0], SKIN[1], SKIN[2], 1f);
			LightningDraw.drawFlat(pose.pose(), buffer, black, MARK_WIDTH, 0.02f, 0.02f, 0.03f, 1f);
		});
	}

	/** A point in the player's own space: x to their right, y up, z ahead, turned to the way their body faces. */
	private record Frame(Vec3 base, Vec3 right, Vec3 forward) {
		Vec3 at(double x, double y, double z) {
			return base.add(right.scale(x)).add(forward.scale(z)).add(0, y, 0);
		}
	}

	private static void draw(List<Segment> skin, List<Segment> black, Player player, float partialTick, Vec3 camera, boolean firstPerson) {
		double yaw = Math.toRadians(player instanceof LivingEntity living ? living.yBodyRot : player.getYRot());
		Frame frame = new Frame(player.getPosition(partialTick).subtract(camera), new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)), new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)));
		double time = player.tickCount + partialTick;
		double drop = firstPerson ? FIRST_PERSON_DROP : 0;
		for (int side = -1; side <= 1; side += 2) {
			for (int pair = 0; pair < PAIRS.length; pair++) {
				double[] shape = PAIRS[pair];
				double sway = Math.sin(time * 0.1 + side * 1.9 + pair * 2.6) * SWAY;
				Vec3 shoulder = frame.at(side * SHOULDER_SIDE, shape[0] - drop, SHOULDER_BACK);
				Vec3 elbow = frame.at(side * (SHOULDER_SIDE + shape[1] * 0.8), shape[0] - drop - 0.1 + sway, 0.15 + sway);
				Vec3 wrist = frame.at(side * (SHOULDER_SIDE + shape[1] * 0.45), shape[0] - drop + shape[3] + sway, shape[2] + 0.55);
				skin.add(new Segment(shoulder, elbow));
				skin.add(new Segment(elbow, wrist));
				// Black bands round the upper arm and forearm, like his markings.
				black.add(new Segment(shoulder.lerp(elbow, 0.35), shoulder.lerp(elbow, 0.45)));
				black.add(new Segment(elbow.lerp(wrist, 0.3), elbow.lerp(wrist, 0.4)));
				Vec3 along = wrist.subtract(elbow).normalize();
				Vec3 spread = along.cross(new Vec3(0, 1, 0)).normalize();
				Vec3 palm = wrist.add(along.scale(PALM));
				skin.add(new Segment(wrist, palm));
				for (int finger = 0; finger < 4; finger++) {
					double offset = (finger - 1.5) * FINGER_SPREAD;
					double curl = finger == 0 || finger == 3 ? 0.8 : 1.0;
					skin.add(new Segment(palm.add(spread.scale(offset)), palm.add(spread.scale(offset * 1.4)).add(along.scale(FINGER * curl))));
				}
			}
		}
		marks(black, frame);
	}

	/** The black markings: bands round the chest, arms and legs, a line down the chest and stripes on the face. */
	private static void marks(List<Segment> black, Frame frame) {
		double[][] boxes = {{0, 0.25, 0.125}, {-0.375, 0.125, 0.125}, {0.375, 0.125, 0.125}, {-0.125, 0.125, 0.125}, {0.125, 0.125, 0.125}};
		double[][] heights = {{1.0, 1.12, 1.24, 1.36}, {1.2, 1.3, 1.4}, {1.2, 1.3, 1.4}, {0.15, 0.3, 0.45, 0.6}, {0.15, 0.3, 0.45, 0.6}};
		for (int part = 0; part < boxes.length; part++) {
			double cx = boxes[part][0];
			double hx = boxes[part][1] + 0.012;
			double hz = boxes[part][2] + 0.012;
			for (double y : heights[part]) {
				Vec3 a = frame.at(cx - hx, y, hz);
				Vec3 b = frame.at(cx + hx, y, hz);
				Vec3 c = frame.at(cx + hx, y, -hz);
				Vec3 d = frame.at(cx - hx, y, -hz);
				black.add(new Segment(a, b));
				black.add(new Segment(b, c));
				black.add(new Segment(c, d));
				black.add(new Segment(d, a));
			}
		}
		// Down the chest and the length of each limb's front.
		black.add(new Segment(frame.at(0, 1.42, 0.14), frame.at(0, 0.78, 0.14)));
		black.add(new Segment(frame.at(-0.375, 1.42, 0.14), frame.at(-0.375, 0.85, 0.14)));
		black.add(new Segment(frame.at(0.375, 1.42, 0.14), frame.at(0.375, 0.85, 0.14)));
		black.add(new Segment(frame.at(-0.125, 0.72, 0.14), frame.at(-0.125, 0.05, 0.14)));
		black.add(new Segment(frame.at(0.125, 0.72, 0.14), frame.at(0.125, 0.05, 0.14)));
		// Face: a stripe under each eye, a bar over the brow and a line down the chin.
		for (int side = -1; side <= 1; side += 2) {
			black.add(new Segment(frame.at(side * 0.07, 1.6, 0.262), frame.at(side * 0.2, 1.58, 0.262)));
			black.add(new Segment(frame.at(side * 0.07, 1.55, 0.262), frame.at(side * 0.2, 1.52, 0.262)));
			black.add(new Segment(frame.at(side * 0.2, 1.74, 0.262), frame.at(side * 0.06, 1.72, 0.262)));
		}
		black.add(new Segment(frame.at(0, 1.5, 0.262), frame.at(0, 1.42, 0.262)));
	}
}
