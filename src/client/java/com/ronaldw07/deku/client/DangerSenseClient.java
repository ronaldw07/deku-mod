package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.network.DangerPayload;
import com.ronaldw07.deku.network.DangerSenseTogglePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Client side of Danger Sense: the on/off toggle, the latest danger from the server, and the warning chime. */
public final class DangerSenseClient {
	/** At or above this the threat is "very close": full lightning on screen and a chime. */
	public static final float VERY_CLOSE = 0.75f;
	private static final int CHIME_COOLDOWN_TICKS = 30;

	private static float level;
	private static Vec3 source = Vec3.ZERO;
	private static boolean synced;
	private static boolean lastSent;
	private static int chimeCooldown;

	private DangerSenseClient() {
	}

	public static float level() {
		return DekuSettings.get().dangerSense() ? level : 0;
	}

	static Vec3 source() {
		return source;
	}

	static void receive(DangerPayload payload, LocalPlayer player) {
		boolean wasVeryClose = level >= VERY_CLOSE;
		level = payload.level();
		source = payload.source();
		if (!wasVeryClose && level() >= VERY_CLOSE && chimeCooldown == 0 && player != null) {
			player.level().playLocalSound(player, DekuSounds.DANGER_SENSE, SoundSource.PLAYERS, 1.0f, 1.0f);
			chimeCooldown = CHIME_COOLDOWN_TICKS;
		}
	}

	static void tick(LocalPlayer player, boolean togglePressed) {
		if (player == null) {
			synced = false;
			level = 0;
			return;
		}

		if (togglePressed) {
			DekuSettings.set(DekuSettings.get().withDangerSense(!DekuSettings.get().dangerSense()));
			DekuSettings.save();
			player.sendOverlayMessage(Component.literal("Danger Sense: " + (DekuSettings.get().dangerSense() ? "ON" : "OFF")));
		}

		boolean on = DekuSettings.get().dangerSense();
		if ((!synced || on != lastSent) && ClientPlayNetworking.canSend(DangerSenseTogglePayload.TYPE)) {
			ClientPlayNetworking.send(new DangerSenseTogglePayload(on));
			synced = true;
			lastSent = on;
			level = 0;
		}

		if (chimeCooldown > 0) {
			chimeCooldown--;
		}
	}
}
