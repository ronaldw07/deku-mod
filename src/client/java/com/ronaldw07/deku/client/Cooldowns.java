package com.ronaldw07.deku.client;

import java.util.EnumMap;
import java.util.Map;

/** Per-move recharge times on the client. They can be switched off in settings. */
public final class Cooldowns {
	/** Every move shown on the controls panel, with its cooldown in ticks (0 = none). */
	public enum Ability {
		FULL_COWLING("Full Cowling", 0),
		SMASH("Smash", 20),
		SMOKESCREEN("Smokescreen", 100),
		FLOAT("Float", 0),
		BLACKWHIP("Blackwhip", 20),
		LAUNCH("Launch", 40),
		SHOOT_STYLE("Shoot Style", 15),
		DELAWARE("Delaware Smash", 10),
		US_SMASH("United States of Smash", 600),
		MANCHESTER("Manchester Smash", 60),
		GEARSHIFT("Gearshift", 0),
		DANGER_SENSE("Danger Sense", 0),
		AP_SHOT("AP Shot", 20),
		FLIGHT("Explosion Flight", 0),
		HOWITZER("Howitzer Impact", 160),
		GROUND_BLAST("Ground Blast", 100),
		CLUSTER("Cluster Bomb", 120),
		DECAY_TOUCH("Decay Touch", 10),
		DECAY_WAVE("Decay Wave", 100),
		DECAY_COWLING("Decay Cowling", 0),
		CATASTROPHE("Catastrophe", 400),
		ICE_WAVE("Ice Wave", 20),
		FLAMETHROWER("Flamethrower", 0),
		ICE_WALL("Ice Wall", 100),
		HEATWAVE("Flashfreeze Heatwave", 200);

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
