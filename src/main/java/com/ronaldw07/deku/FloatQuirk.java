package com.ronaldw07.deku;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Server side of Float: switches the player's gravity off. With zero gravity the
 * server also stops treating the hover as cheating, so the player isn't kicked for flying.
 */
public final class FloatQuirk {
	public static final Identifier NO_GRAVITY_ID = DekuMod.id("float_no_gravity");
	private static final AttributeModifier NO_GRAVITY =
		new AttributeModifier(NO_GRAVITY_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

	private FloatQuirk() {
	}

	public static void apply(ServerPlayer player, boolean active) {
		AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
		if (gravity == null) {
			return;
		}

		if (!active) {
			gravity.removeModifier(NO_GRAVITY_ID);
			return;
		}

		gravity.addOrUpdateTransientModifier(NO_GRAVITY);
		player.resetFallDistance();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), DekuSounds.FLOAT, SoundSource.PLAYERS, 1.0f, 1.0f);
	}
}
