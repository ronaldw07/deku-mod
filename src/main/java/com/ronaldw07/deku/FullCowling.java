package com.ronaldw07.deku;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Server side of Full Cowling: stat boosts that scale with the power percentage. */
public final class FullCowling {
	public static final Identifier SPEED_ID = DekuMod.id("full_cowling_speed");
	private static final Identifier JUMP_ID = DekuMod.id("full_cowling_jump");
	private static final Identifier SAFE_FALL_ID = DekuMod.id("full_cowling_safe_fall");
	private static final Identifier ATTACK_ID = DekuMod.id("full_cowling_attack");

	// Bonuses at 100%; lower percentages get a proportional share.
	private static final double MAX_SPEED_BONUS = 1.0; // +100% of base walk speed
	private static final double MAX_JUMP_BONUS = 0.5; // vanilla jump strength is 0.42
	private static final double MAX_SAFE_FALL_BONUS = 20.0; // blocks
	private static final double MAX_ATTACK_BONUS = 10.0; // half-hearts

	private FullCowling() {
	}

	public static void apply(ServerPlayer player, int percent) {
		double power = Mth.clamp(percent, 0, 100) / 100.0;
		setBonus(player, Attributes.MOVEMENT_SPEED, SPEED_ID, MAX_SPEED_BONUS * power, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		setBonus(player, Attributes.JUMP_STRENGTH, JUMP_ID, MAX_JUMP_BONUS * power, AttributeModifier.Operation.ADD_VALUE);
		setBonus(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID, MAX_SAFE_FALL_BONUS * power, AttributeModifier.Operation.ADD_VALUE);
		setBonus(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, MAX_ATTACK_BONUS * power, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void setBonus(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount,
			AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}

		if (amount == 0) {
			instance.removeModifier(id);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}
}
