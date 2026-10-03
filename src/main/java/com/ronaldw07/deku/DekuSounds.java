package com.ronaldw07.deku;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * The mod's sound effects. assets/deku/sounds.json builds each one out of vanilla
 * sounds; a resource pack can point any of them at its own .ogg file instead.
 */
public final class DekuSounds {
	public static final SoundEvent COWLING_ACTIVATE = register("cowling.activate");
	public static final SoundEvent COWLING_CRACKLE = register("cowling.crackle");
	public static final SoundEvent SMASH_WINDUP = register("smash.windup");
	public static final SoundEvent SMASH_CHARGE = register("smash.charge");
	public static final SoundEvent SMASH_BLAST = register("smash.blast");
	public static final SoundEvent SMASH_THUNDER = register("smash.thunder");
	public static final SoundEvent SMOKESCREEN = register("smokescreen");
	public static final SoundEvent FLOAT = register("float");
	public static final SoundEvent BLACKWHIP_LASH = register("blackwhip.lash");
	public static final SoundEvent BLACKWHIP_GRAB = register("blackwhip.grab");
	public static final SoundEvent DANGER_SENSE = register("danger_sense");
	public static final SoundEvent EXPLOSION_POP = register("explosion.pop");
	public static final SoundEvent EXPLOSION_CHARGE = register("explosion.charge");
	public static final SoundEvent HOWITZER_SPIN = register("explosion.howitzer_spin");

	private DekuSounds() {
	}

	/** Forces the static fields above to register. */
	static void init() {
	}

	private static SoundEvent register(String name) {
		Identifier id = DekuMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
