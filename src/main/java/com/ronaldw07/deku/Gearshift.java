package com.ronaldw07.deku;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Server side of Gearshift: each gear up adds more speed, on top of Full Cowling. */
public final class Gearshift {
	public static final int MAX_GEAR = 5;
	private static final Identifier SPEED_ID = DekuMod.id("gearshift_speed");
	private static final double SPEED_PER_GEAR = 0.3; // +30% of base walk speed per gear

	private Gearshift() {
	}

	/** Gear 0 is off. */
	public static void apply(ServerPlayer player, int gear) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		int clamped = Mth.clamp(gear, 0, MAX_GEAR);
		if (clamped == 0) {
			speed.removeModifier(SPEED_ID);
		} else {
			speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, SPEED_PER_GEAR * clamped,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}
}
