package com.ronaldw07.deku;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/** Server side of Float: gravity off while the key is held. */
public final class FloatQuirk {
	public static final Identifier NO_GRAVITY_ID = DekuMod.id("float_no_gravity");

	private FloatQuirk() {
	}

	public static void apply(ServerPlayer player, boolean active) {
		NoGravity.set(player, NO_GRAVITY_ID, active);
		if (active) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.FLOAT, SoundSource.PLAYERS, 1.0f, 1.0f);
		}
	}
}
