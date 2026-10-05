package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.network.JujutsuPayload;
import com.ronaldw07.deku.network.JujutsuPayload.Move;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * Client side of Gojo, used while his item is in hand: hold right-click for Blue, hold V to charge Red,
 * hold X to charge Hollow Purple, Z to switch Infinity on and off, and C for Infinite Void.
 */
public final class GojoClient {
	private static final int FULL_BLUE_TICKS = 60;
	private static final int FULL_RED_TICKS = 40;
	private static final int FULL_PURPLE_TICKS = 60;
	private static final int CHARGE_SOUND_INTERVAL = 10;
	private static final int POSE_REPEAT_TICKS = 8;
	private static final int POSE_TICKS = 10;
	private static final int VOID_POSE_TICKS = 40;

	private static int blueTicks;
	private static int redTicks;
	private static int purpleTicks;
	private static boolean infinity;

	private GojoClient() {
	}

	/** How far Blue is wound up, 0-100; 0 when not charging. */
	public static int blueCharge() {
		return blueTicks == 0 ? 0 : Math.min(100, 1 + blueTicks * 100 / FULL_BLUE_TICKS);
	}

	/** How far Red is wound up, 0-100; 0 when not charging. */
	public static int redCharge() {
		return redTicks == 0 ? 0 : Math.min(100, 1 + redTicks * 100 / FULL_RED_TICKS);
	}

	/** How far Hollow Purple is wound up, 0-100; 0 when not charging. */
	public static int purpleCharge() {
		return purpleTicks == 0 ? 0 : Math.min(100, 1 + purpleTicks * 100 / FULL_PURPLE_TICKS);
	}

	public static boolean infinity() {
		return infinity;
	}

	static void tick(LocalPlayer player, boolean holding, boolean useDown, boolean redDown, boolean purpleDown, boolean infinityPressed,
			boolean voidPressed) {
		if (player == null) {
			blueTicks = 0;
			redTicks = 0;
			purpleTicks = 0;
			infinity = false;
			return;
		}
		boolean able = holding && !player.isDeadOrDying();

		blueTicks = charge(player, able && useDown, blueTicks, FULL_BLUE_TICKS, Cooldowns.Ability.GOJO_BLUE, Move.BLUE, Poses.Pose.AIM_RIGHT, 1.0f);
		redTicks = charge(player, able && redDown, redTicks, FULL_RED_TICKS, Cooldowns.Ability.GOJO_RED, Move.RED, Poses.Pose.AIM_LEFT, 0.8f);
		purpleTicks = charge(player, able && purpleDown, purpleTicks, FULL_PURPLE_TICKS, Cooldowns.Ability.GOJO_PURPLE, Move.PURPLE, Poses.Pose.AIM_BOTH, 0.5f);

		if (able && voidPressed && Cooldowns.ready(Cooldowns.Ability.INFINITE_VOID)) {
			send(Move.INFINITE_VOID, true, 0);
			Cooldowns.start(Cooldowns.Ability.INFINITE_VOID);
			Poses.play(Poses.Pose.CROSS, VOID_POSE_TICKS);
		}

		boolean was = infinity;
		if (!able) {
			infinity = false;
		} else if (infinityPressed) {
			infinity = !infinity;
		}
		if (infinity != was) {
			send(Move.INFINITY, infinity, 0);
		}
	}

	/** Winds a move up while its key is held, and throws it at the charge reached once it is let go. */
	private static int charge(LocalPlayer player, boolean down, int ticks, int fullTicks, Cooldowns.Ability ability, Move move,
			Poses.Pose pose, float basePitch) {
		if (down && (ticks > 0 || Cooldowns.ready(ability))) {
			if (ticks % CHARGE_SOUND_INTERVAL == 0) {
				int percent = Math.min(100, 1 + ticks * 100 / fullTicks);
				player.level().playLocalSound(player, DekuSounds.EXPLOSION_CHARGE, SoundSource.PLAYERS, 1.0f, basePitch + 0.8f * percent / 100f);
			}
			if (ticks % POSE_REPEAT_TICKS == 0) {
				Poses.play(pose, POSE_TICKS);
			}
			return Math.min(fullTicks, ticks + 1);
		}
		if (ticks > 0) {
			send(move, true, Math.min(100, 1 + ticks * 100 / fullTicks));
			Cooldowns.start(ability);
			Poses.play(pose, POSE_TICKS);
		}
		return 0;
	}

	private static void send(Move move, boolean active, int charge) {
		if (ClientPlayNetworking.canSend(JujutsuPayload.TYPE)) {
			ClientPlayNetworking.send(new JujutsuPayload(move, active, charge));
		}
	}
}
