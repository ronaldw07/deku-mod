package com.ronaldw07.deku;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class DekuParticles {
	/** Big, slow purple smoke puffs for Smokescreen. Always shown, even on reduced particle settings. */
	public static final SimpleParticleType PURPLE_SMOKE = Registry.register(
		BuiltInRegistries.PARTICLE_TYPE, DekuMod.id("purple_smoke"), FabricParticleTypes.simple(true));

	/** Shorter-lived white cloud for the Howitzer Impact vortex. */
	public static final SimpleParticleType WHITE_SMOKE = Registry.register(
		BuiltInRegistries.PARTICLE_TYPE, DekuMod.id("white_smoke"), FabricParticleTypes.simple(true));

	/** Near-black smoke that hangs over a blast for ten seconds or more. */
	public static final SimpleParticleType SOOT_SMOKE = Registry.register(
		BuiltInRegistries.PARTICLE_TYPE, DekuMod.id("soot_smoke"), FabricParticleTypes.simple(true));

	/** A hot puff that goes white, orange, deep red and finally black as it burns out. */
	public static final SimpleParticleType FIREBALL = Registry.register(
		BuiltInRegistries.PARTICLE_TYPE, DekuMod.id("fireball"), FabricParticleTypes.simple(true));

	/** Small dark flakes that drift down after a huge blast. */
	public static final SimpleParticleType ASH_FLAKE = Registry.register(
		BuiltInRegistries.PARTICLE_TYPE, DekuMod.id("ash_flake"), FabricParticleTypes.simple(true));

	private DekuParticles() {
	}

	/** Forces the static fields above to register. */
	static void init() {
	}
}
