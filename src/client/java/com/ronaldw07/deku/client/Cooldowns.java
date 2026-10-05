package com.ronaldw07.deku.client;

import java.util.EnumMap;
import java.util.Map;

/** Per-move recharge times on the client. They can be switched off in settings. */
public final class Cooldowns {
	/** Every move shown on the controls panel, with its cooldown in ticks (0 = none). */
	public enum Ability {
		FULL_COWLING("Full Cowling", 0),
		SMASH("Smash", 8),
		SMOKESCREEN("Smokescreen", 30),
		FLOAT("Float", 0),
		BLACKWHIP("Blackwhip", 8),
		LAUNCH("Launch", 15),
		SHOOT_STYLE("Shoot Style", 6),
		DELAWARE("Delaware Smash", 4),
		US_SMASH("United States of Smash", 100),
		MANCHESTER("Manchester Smash", 20),
		GEARSHIFT("Gearshift", 0),
		DANGER_SENSE("Danger Sense", 0),
		AP_SHOT("AP Shot", 8),
		FLIGHT("Explosion Flight", 0),
		HOWITZER("Howitzer Impact", 40),
		GROUND_BLAST("Ground Blast", 30),
		CLUSTER("Cluster Bomb", 100),
		EXPLOSION_COWLING("Explosion Cowling", 0),
		DECAY_TOUCH("Decay Touch", 4),
		DECAY_WAVE("Decay Wave", 30),
		DECAY_COWLING("Decay Cowling", 0),
		CATASTROPHE("Catastrophe", 100),
		ICE_WAVE("Ice Wave", 8),
		FLAMETHROWER("Flamethrower", 0),
		ICE_SLIDE("Ice Slide", 0),
		ICE_WALL("Ice Wall", 30),
		HEATWAVE("Flashfreeze Heatwave", 50),
		GOJO_BLUE("Blue", 40),
		GOJO_RED("Red", 60),
		GOJO_PURPLE("Hollow Purple", 200),
		INFINITY("Infinity", 0),
		INFINITE_VOID("Infinite Void", 600),
		DISMANTLE("Dismantle", 20),
		CLEAVE("Cleave", 40),
		DOMAIN("Domain Expansion", 600);

		final String label;
		final int ticks;

		Ability(String label, int ticks) {
			this.label = label;
			this.ticks = ticks;
		}
	}

	private static final Map<Ability, Integer> remaining = new EnumMap<>(Ability.class);

	private Cooldowns() {
	}

	public static boolean ready(Ability ability) {
		return !enabled() || remaining.getOrDefault(ability, 0) == 0;
	}

	static void start(Ability ability) {
		if (ability.ticks > 0) {
			remaining.put(ability, ability.ticks);
		}
	}

	/** How much of the cooldown is left, from 1 (just used) to 0 (ready). */
	static float fractionLeft(Ability ability) {
		return ability.ticks == 0 || !enabled() ? 0 : remaining.getOrDefault(ability, 0) / (float) ability.ticks;
	}

	static void tick() {
		remaining.replaceAll((ability, ticks) -> Math.max(0, ticks - 1));
	}

	static void reset() {
		remaining.clear();
	}

	private static boolean enabled() {
		return !DekuSettings.get().noCooldowns();
	}
}
