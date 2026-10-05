package com.ronaldw07.deku;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;

/**
 * What every cowling gives on top of its own boosts: immunity to bad effects, a heavy shield of
 * extra health, much less damage taken, and extra speed and jump. Full, Explosion and Decay
 * Cowling and Sukuna's Demon Arms all share it.
 */
public final class CowlingGuard {
	private static final int REFRESH_TICKS = 40;
	private static final int RESISTANCE_LEVEL = 3; // level IV: 80% less damage
	private static final int SHIELD_LEVEL = 4; // five absorption hearts per level above zero: 10 hearts at level III, more here
	private static final int SPEED_LEVEL = 1;
	private static final int JUMP_LEVEL = 2;
	private static final Identifier SPEED_ID = DekuMod.id("cowling_guard_speed");
	private static final double EXTRA_SPEED = 0.35;

	private CowlingGuard() {
	}

	public static boolean cowled(ServerPlayer player) {
		return FullCowling.active(player) || ExplosionCowling.active(player) || Decay.cowlingActive(player) || DemonArms.active(player);
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
			boolean on = cowled(player) && player.isAlive();
			if (speed != null) {
				if (on) {
					speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, EXTRA_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
				} else {
					speed.removeModifier(SPEED_ID);
				}
			}
			if (!on) {
				continue;
			}
			for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
				MobEffect type = effect.getEffect().value();
				if (type.getCategory() == MobEffectCategory.HARMFUL) {
					player.removeEffect(effect.getEffect());
				}
			}
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, REFRESH_TICKS, RESISTANCE_LEVEL, false, false));
			if (player.getAbsorptionAmount() < SHIELD_LEVEL * 4) {
				player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, REFRESH_TICKS, SHIELD_LEVEL, false, false));
			}
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, REFRESH_TICKS, SPEED_LEVEL, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, REFRESH_TICKS, JUMP_LEVEL, false, false));
		}
	}
}
