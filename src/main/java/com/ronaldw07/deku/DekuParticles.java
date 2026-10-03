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

	private DekuParticles() {
	}

	/** Forces the static fields above to register. */
	static void init() {
	}
}
