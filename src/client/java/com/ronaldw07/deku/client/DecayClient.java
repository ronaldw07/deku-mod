package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.network.DecayPayload;
import com.ronaldw07.deku.network.DecayPayload.Move;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ronaldw07.deku.client.LightningDraw.Segment;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the Decay quirk, used while the Decay item is in hand: right-click to decay
 * whatever you touch, hold V to wind up a wave of decay that floods out when you let go, hold
 * X for Catastrophe, and C switches Decay Cowling on and off.
 */
public final class DecayClient {
	private static final int TOUCH_POSE_TICKS = 8;
	private static final int FULL_WAVE_CHARGE_TICKS = 40;
	private static final int CHARGE_SOUND_INTERVAL = 10;
	private static final int SLAM_POSE_TICKS = 15;
	private static final int FULL_CATASTROPHE_CHARGE_TICKS = 60;
	private static final int POWER_UP_POSE_TICKS = 12;
	private static final int TICKS_PER_SHAPE = 2;
	private static final double AURA_POWER = 1.0;
	private static final int EMBERS_PER_TICK = 3;
	private static final DustParticleOptions ASH = new DustParticleOptions(0x8A8A8A, 1.0f);

	private static int waveCharge;
	private static int catastropheCharge;
	private static boolean cowling;

	private DecayClient() {
	}

	public static boolean charging() {
		return waveCharge > 0 || catastropheCharge > 0;
	}

	public static boolean cowling() {
		return cowling;
	}

	/** How far Catastrophe is wound up, 0-100. */
	public static int catastropheCharge() {
		return catastropheCharge * 100 / FULL_CATASTROPHE_CHARGE_TICKS;
	}

	public static boolean chargingCatastrophe() {
		return catastropheCharge > 0;
	}

	/** How far the wave is wound up, 0-100. */
	public static int waveCharge() {
		return waveCharge * 100 / FULL_WAVE_CHARGE_TICKS;
	}

	static void tick(LocalPlayer player, boolean holding, boolean useDown, boolean waveDown, boolean catastropheDown,
			boolean cowlingPressed) {
		if (player == null) {
			waveCharge = 0;
			catastropheCharge = 0;
			cowling = false;
			return;
		}
		boolean able = holding && !player.isDeadOrDying();
		cowling(player, able, cowlingPressed);
		catastrophe(player, able, catastropheDown);

		if (able && useDown && Cooldowns.ready(Cooldowns.Ability.DECAY_TOUCH)) {
			send(Move.TOUCH, 0);
			Cooldowns.start(Cooldowns.Ability.DECAY_TOUCH);
			Poses.play(Poses.Pose.AIM_RIGHT, TOUCH_POSE_TICKS);
		}

		if (able && waveDown && (waveCharge > 0 || Cooldowns.ready(Cooldowns.Ability.DECAY_WAVE))) {
			if (waveCharge % CHARGE_SOUND_INTERVAL == 0) {
				player.level().playLocalSound(player, DekuSounds.DECAY_CHARGE, SoundSource.PLAYERS, 1.0f, 0.8f + 0.6f * waveCharge() / 100f);
			}
			waveCharge = Math.min(FULL_WAVE_CHARGE_TICKS, waveCharge + 1);
			return;
		}
		if (waveCharge > 0) {
			if (able) {
				send(Move.WAVE, waveCharge());
				Cooldowns.start(Cooldowns.Ability.DECAY_WAVE);
				Poses.play(Poses.Pose.GROUND_TOUCH, SLAM_POSE_TICKS);
			}
			waveCharge = 0;
		}
	}

	/** Decay Cowling stays on until it's switched off or the Decay item leaves the hand. */
	private static void cowling(LocalPlayer player, boolean able, boolean pressed) {
		boolean wanted = able && (pressed ? !cowling : cowling);
		if (wanted != cowling) {
			cowling = wanted;
			send(Move.COWLING, cowling ? 1 : 0);
			if (cowling) {
				Poses.play(Poses.Pose.POWER_UP, POWER_UP_POSE_TICKS);
			}
		}
		if (cowling) {
			RandomSource random = player.getRandom();
			for (int i = 0; i < EMBERS_PER_TICK; i++) {
				Vec3 at = player.position().add((random.nextDouble() - 0.5) * 0.8, random.nextDouble() * player.getBbHeight(),
					(random.nextDouble() - 0.5) * 0.8);
				player.level().addParticle(ASH, at.x, at.y, at.z, 0, 0.03, 0);
			}
		}
	}

	/** Like the wave but held longer, and when it goes, everything all around goes with it. */
	private static void catastrophe(LocalPlayer player, boolean able, boolean down) {
		if (able && down && (catastropheCharge > 0 || Cooldowns.ready(Cooldowns.Ability.CATASTROPHE))) {
			if (catastropheCharge % CHARGE_SOUND_INTERVAL == 0) {
				player.level().playLocalSound(player, DekuSounds.DECAY_CHARGE, SoundSource.PLAYERS, 1.5f,
					0.5f + 0.8f * catastropheCharge() / 100f);
			}
			catastropheCharge = Math.min(FULL_CATASTROPHE_CHARGE_TICKS, catastropheCharge + 1);
			return;
		}
		if (catastropheCharge > 0) {
			if (able) {
				send(Move.CATASTROPHE, catastropheCharge());
				Cooldowns.start(Cooldowns.Ability.CATASTROPHE);
				Poses.play(Poses.Pose.GROUND_TOUCH, SLAM_POSE_TICKS);
			}
			catastropheCharge = 0;
		}
	}

	/** Grey and red decay lightning crawling over the body while Decay Cowling is on. */
	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !cowling) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 offset = player.getPosition(partialTick).subtract(context.levelState().cameraRenderState.pos);
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		double radius = firstPerson ? 0.5 : 0.35;
		double height = firstPerson ? player.getEyeHeight() - 0.35 : player.getBbHeight();
		float width = firstPerson ? 0.5f : 1f;
		List<Segment> segments = CowlingLightning.buildBolts(AURA_POWER, player.getId() * 53L + player.tickCount / TICKS_PER_SHAPE,
			radius, height);

		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(offset);
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lightning(),
			(pose, buffer) -> LightningDraw.draw(pose.pose(), buffer, segments, width, LightningDraw.DECAY, 1f));
		poseStack.popPose();
	}

	private static void send(Move move, int charge) {
		if (ClientPlayNetworking.canSend(DecayPayload.TYPE)) {
			ClientPlayNetworking.send(new DecayPayload(move, charge));
		}
	}
}
