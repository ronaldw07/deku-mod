package com.ronaldw07.deku.client;

import com.ronaldw07.deku.network.JujutsuPayload;
import com.ronaldw07.deku.network.JujutsuPayload.Move;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.ronaldw07.deku.DekuSounds;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;

/** Client side of Sukuna, used while his item is in hand: right-click Dismantle, V Cleave, hold X to draw Fuga, C Domain Expansion. */
public final class SukunaClient {
	private static final int SLASH_POSE_TICKS = 8;
	private static final int DOMAIN_POSE_TICKS = 40;

	private static final int FULL_FUGA_TICKS = 50;
	private static final int FUGA_SOUND_INTERVAL = 10;
	private static final int FUGA_POSE_REPEAT_TICKS = 8;
	private static final int FUGA_POSE_TICKS = 10;

	private static boolean wasUsing;
	private static int fugaTicks;

	/** How far the Fuga arrow is drawn, 0-100; 0 when not drawing. */
	public static int fugaCharge() {
		return fugaTicks == 0 ? 0 : Math.min(100, 1 + fugaTicks * 100 / FULL_FUGA_TICKS);
	}

	private SukunaClient() {
	}

	static void tick(LocalPlayer player, boolean holding, boolean useDown, boolean cleavePressed, boolean fugaDown,
			boolean domainPressed) {
		if (player == null) {
			wasUsing = false;
			fugaTicks = 0;
			return;
		}
		boolean able = holding && !player.isDeadOrDying();
		if (able && useDown && !wasUsing && Cooldowns.ready(Cooldowns.Ability.DISMANTLE)) {
			send(Move.DISMANTLE);
			Cooldowns.start(Cooldowns.Ability.DISMANTLE);
			Poses.play(Poses.Pose.AIM_RIGHT, SLASH_POSE_TICKS);
		}
		wasUsing = useDown;
		if (able && cleavePressed && Cooldowns.ready(Cooldowns.Ability.CLEAVE)) {
			send(Move.CLEAVE);
			Cooldowns.start(Cooldowns.Ability.CLEAVE);
			Poses.play(Poses.Pose.WHIP, SLASH_POSE_TICKS);
		}
		fuga(player, able && fugaDown);
		if (able && domainPressed && Cooldowns.ready(Cooldowns.Ability.DOMAIN)) {
			send(Move.DOMAIN);
			Cooldowns.start(Cooldowns.Ability.DOMAIN);
			Poses.play(Poses.Pose.CROSS, DOMAIN_POSE_TICKS);
		}
	}

	/** Holding X draws a burning arrow; letting go looses it at the crosshair. */
	private static void fuga(LocalPlayer player, boolean down) {
		if (down && (fugaTicks > 0 || Cooldowns.ready(Cooldowns.Ability.FUGA))) {
			if (fugaTicks % FUGA_SOUND_INTERVAL == 0) {
				player.level().playLocalSound(player, DekuSounds.EXPLOSION_CHARGE, SoundSource.PLAYERS, 1.0f, 0.6f + 0.8f * fugaCharge() / 100f);
			}
			if (fugaTicks % FUGA_POSE_REPEAT_TICKS == 0) {
				Poses.play(Poses.Pose.AIM_BOTH, FUGA_POSE_TICKS);
			}
			fugaTicks = Math.min(FULL_FUGA_TICKS, fugaTicks + 1);
			return;
		}
		if (fugaTicks > 0) {
			send(Move.FUGA, fugaCharge());
			fugaTicks = 0;
			Cooldowns.start(Cooldowns.Ability.FUGA);
			Poses.play(Poses.Pose.AIM_BOTH, FUGA_POSE_TICKS);
		}
	}

	private static void send(Move move) {
		send(move, 0);
	}

	private static void send(Move move, int charge) {
		if (ClientPlayNetworking.canSend(JujutsuPayload.TYPE)) {
			ClientPlayNetworking.send(new JujutsuPayload(move, true, charge));
		}
	}
}
