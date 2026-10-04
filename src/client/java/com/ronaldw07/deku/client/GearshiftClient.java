package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.Gearshift;
import com.ronaldw07.deku.client.LightningDraw.Segment;
import com.ronaldw07.deku.network.GearshiftPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of Gearshift: switched on, it shifts up a gear every couple of seconds you keep
 * moving, up to fifth, and drops back to first when you stand still. Green lightning streams
 * off behind you, more of it the higher the gear.
 */
public final class GearshiftClient {
	private static final int TICKS_PER_GEAR = 40;
	private static final int STILL_TICKS_TO_DROP = 15;
	private static final double MOVING_SPEED = 0.05; // blocks per tick, sideways
	private static final int BOLTS_PER_GEAR = 2;
	private static final double TRAIL_LENGTH_PER_GEAR = 0.8;
	private static final double TRAIL_JAG = 0.25;
	private static final int TRAIL_STEPS = 5;
	private static final int TICKS_PER_SHAPE = 2;

	private static boolean active;
	private static int gear;
	private static int movingTicks;
	private static int stillTicks;
	private static int lastSent;

	private GearshiftClient() {
	}

	/** The current gear, 0 when off. */
	static int gear() {
		return gear;
	}

	/** @param holdingQuirk whether One For All is in hand; letting go of it switches Gearshift off */
	static void tick(LocalPlayer player, boolean togglePressed, boolean holdingQuirk) {
		if (player == null) {
			active = false;
			gear = 0;
			lastSent = 0;
			return;
		}

		if (togglePressed) {
			active = !active;
			if (active) {
				player.level().playLocalSound(player, DekuSounds.GEARSHIFT_SHIFT, SoundSource.PLAYERS, 1.0f, 0.8f);
			}
		}
		if (!holdingQuirk || player.isDeadOrDying()) {
			active = false;
		}

		if (!active) {
			gear = 0;
			movingTicks = 0;
			stillTicks = 0;
		} else {
			gear = Math.max(gear, 1);
			if (player.getDeltaMovement().horizontalDistance() > MOVING_SPEED) {
				stillTicks = 0;
				if (gear < Gearshift.MAX_GEAR && ++movingTicks >= TICKS_PER_GEAR) {
					gear++;
					movingTicks = 0;
					player.level().playLocalSound(player, DekuSounds.GEARSHIFT_SHIFT, SoundSource.PLAYERS, 1.0f, 0.8f + gear * 0.2f);
				}
			} else if (++stillTicks > STILL_TICKS_TO_DROP) {
				gear = 1;
				movingTicks = 0;
			}
		}

		if (gear != lastSent && ClientPlayNetworking.canSend(GearshiftPayload.TYPE)) {
			ClientPlayNetworking.send(new GearshiftPayload(gear));
			lastSent = gear;
		}
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || gear == 0) {
			return;
		}
		Vec3 velocity = player.getDeltaMovement();
		Vec3 flat = new Vec3(velocity.x, 0, velocity.z);
		if (flat.horizontalDistance() <= MOVING_SPEED) {
			return;
		}

		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Vec3 back = flat.normalize().scale(-1);
		Vec3 body = player.getPosition(partialTick).subtract(camera);
		RandomSource random = RandomSource.create(player.getId() * 17L + player.tickCount / TICKS_PER_SHAPE);
		List<Segment> segments = new ArrayList<>();
		for (int i = 0; i < gear * BOLTS_PER_GEAR; i++) {
			Vec3 from = body.add((random.nextDouble() - 0.5) * 0.6, random.nextDouble() * player.getBbHeight(), (random.nextDouble() - 0.5) * 0.6);
			Vec3 to = from.add(back.scale(gear * TRAIL_LENGTH_PER_GEAR * (0.6 + random.nextDouble() * 0.8)));
			Vec3 previous = from;
			for (int step = 1; step <= TRAIL_STEPS; step++) {
				Vec3 next = from.lerp(to, (double) step / TRAIL_STEPS);
				if (step < TRAIL_STEPS) {
					next = next.add(LightningDraw.randomDirection(random).scale(TRAIL_JAG));
				}
				segments.add(new Segment(previous, next));
				previous = next;
			}
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(),
			(pose, buffer) -> LightningDraw.drawGreen(pose.pose(), buffer, segments, 1.5f));
	}
}
