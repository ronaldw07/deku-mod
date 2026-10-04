package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.network.DelawarePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;

/** Client side of Delaware Smash: hold Y to wind up the finger flick, let go to fire the air bullet. */
public final class DelawareClient {
	private static final int FULL_CHARGE_TICKS = 40;
	private static final double TAP_POWER = 0.3; // share of the punch power a quick tap fires with
	private static final int SOUND_INTERVAL = 10;
	private static final int POSE_REPEAT_TICKS = 8;
	private static final int POSE_TICKS = 10;

	private static int ticks;

	private DelawareClient() {
	}

	/** How far the flick is wound up, 0-100; 0 when not charging. */
	public static int charge() {
		return ticks == 0 ? 0 : (int) Math.round(100 * (TAP_POWER + (1 - TAP_POWER) * Math.min(1.0, ticks / (double) FULL_CHARGE_TICKS)));
	}

	static void tick(LocalPlayer player, boolean down) {
		if (player == null) {
			ticks = 0;
			return;
		}
		if (down && (ticks > 0 || Cooldowns.ready(Cooldowns.Ability.DELAWARE))) {
			if (ticks % SOUND_INTERVAL == 0) {
				player.level().playLocalSound(player, DekuSounds.EXPLOSION_CHARGE, SoundSource.PLAYERS, 0.6f, 1.2f + charge() / 200f);
			}
			if (ticks % POSE_REPEAT_TICKS == 0) {
				Poses.play(Poses.Pose.AIM_RIGHT, POSE_TICKS);
			}
			ticks = Math.min(FULL_CHARGE_TICKS, ticks + 1);
			return;
		}
		if (ticks > 0) {
			int power = Math.max(1, DekuSettings.get().punchPower() * charge() / 100);
			if (ClientPlayNetworking.canSend(DelawarePayload.TYPE)) {
				ClientPlayNetworking.send(new DelawarePayload(power));
			}
			Cooldowns.start(Cooldowns.Ability.DELAWARE);
			Poses.play(Poses.Pose.AIM_RIGHT, POSE_TICKS);
			ticks = 0;
		}
	}
}
