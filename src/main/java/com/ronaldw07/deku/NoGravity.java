package com.ronaldw07.deku;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Switches a player's gravity off for a move that keeps them airborne. With zero gravity
 * the server also stops treating the hover as cheating, so they aren't kicked for flying.
 * Each move uses its own id so one ending doesn't cancel another.
 */
public final class NoGravity {
	private NoGravity() {
	}

	public static void set(ServerPlayer player, Identifier moveId, boolean on) {
		AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
		if (gravity == null) {
			return;
		}

		if (on) {
			gravity.addOrUpdateTransientModifier(new AttributeModifier(moveId, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			gravity.removeModifier(moveId);
		}
		player.resetFallDistance();
	}
}
