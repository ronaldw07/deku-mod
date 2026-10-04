package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuParticles;
import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.network.ExplosionCowlingPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the Explosion quirk's Full Cowling: the toggle, and the embers that rise off
 * the player while it burns. The glow itself is drawn by ExplosionCowlingFx.
 */
public final class ExplosionCowlingClient {
	private static final int POSE_TICKS = 12;
	private static final int ON_RING_PUFFS = 24;
	private static final double ON_RING_SPEED = 0.35;
	private static final int OFF_SOOT_PUFFS = 4;
	private static final float OFF_SOOT_SCALE = 0.6f;
	private static final int EMBER_EVERY_TICKS = 1;
	private static final double EMBER_RADIUS = 0.5;
	private static final double EMBER_RISE = 0.06;
	private static final float ON_SOUND_PITCH = 0.7f;

	private static boolean active;

	private ExplosionCowlingClient() {
	}

	public static boolean active() {
		return active;
	}

	/**
	 * @param holdingQuirk whether Explosion is in hand; putting it away switches the Cowling off
	 * @param togglePressed whether the toggle key was pressed since last tick
	 */
	static void tick(LocalPlayer player, boolean holdingQuirk, boolean togglePressed) {
		boolean was = active;
		if (player == null || !holdingQuirk || player.isDeadOrDying()) {
			active = false;
		} else if (togglePressed) {
			active = !active;
		}

		if (active != was) {
			if (ClientPlayNetworking.canSend(ExplosionCowlingPayload.TYPE)) {
				ClientPlayNetworking.send(new ExplosionCowlingPayload(active));
			}
			if (player != null) {
				if (active) {
					igniteBurst(player);
				} else {
					snuffOut(player);
				}
			}
		}
		if (active && player != null && player.tickCount % EMBER_EVERY_TICKS == 0) {
			embers(player);
		}
	}

	/** A ring of flame spreading out from the feet as the Cowling lights. */
	private static void igniteBurst(LocalPlayer player) {
		Poses.play(Poses.Pose.POWER_UP, POSE_TICKS);
		Vec3 feet = player.position().add(0, 0.2, 0);
		for (int i = 0; i < ON_RING_PUFFS; i++) {
			double angle = Math.PI * 2 * i / ON_RING_PUFFS;
			player.level().addParticle(ParticleTypes.FLAME, feet.x, feet.y, feet.z,
				Math.cos(angle) * ON_RING_SPEED, 0.05, Math.sin(angle) * ON_RING_SPEED);
		}
		player.level().playLocalSound(player, DekuSounds.COWLING_ACTIVATE, SoundSource.PLAYERS, 1.0f, ON_SOUND_PITCH);
	}

	private static void snuffOut(LocalPlayer player) {
		Vec3 chest = player.position().add(0, player.getBbHeight() * 0.5, 0);
		for (int i = 0; i < OFF_SOOT_PUFFS; i++) {
			var puff = Minecraft.getInstance().particleEngine.createParticle(DekuParticles.SOOT_SMOKE,
				chest.x, chest.y, chest.z, 0, 0.05, 0);
			if (puff != null) {
				puff.scale(OFF_SOOT_SCALE);
			}
		}
	}

	/** Orange sparks and a lick of flame drifting up off the body. */
	private static void embers(LocalPlayer player) {
		RandomSource random = player.getRandom();
		int count = Math.max(1, (int) Math.round(DekuSettings.get().detailScale() * 2));
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			Vec3 at = player.position().add(Math.cos(angle) * EMBER_RADIUS, random.nextDouble() * player.getBbHeight(),
				Math.sin(angle) * EMBER_RADIUS);
			player.level().addParticle(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 0, EMBER_RISE, 0);
		}
	}
}
