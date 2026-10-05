package com.ronaldw07.deku.client;

import com.ronaldw07.deku.client.ExplosionFx.Blast;
import com.ronaldw07.deku.network.ExplosionFxPayload.Style;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * What a big blast does to the screen of anyone near it: the camera shakes, and when it is very
 * close the whole view flashes dark red. Both fade out within a second or two and can be
 * switched off in the K menu.
 */
public final class ScreenShake {
	private static final double MIN_BLAST_RADIUS = 6.0;
	private static final double REACH_PER_RADIUS = 3.0;
	private static final double CORE_REACH = 250.0;
	private static final double FULL_SHAKE_RADIUS = 18.0;
	private static final float MAX_TRAUMA = 1.0f;
	private static final float CORE_TRAUMA_BOOST = 1.2f;
	private static final float TRAUMA_DECAY_PER_TICK = 0.04f;
	private static final float MAX_SHAKE_DEGREES = 3.0f;
	private static final double FLASH_REACH_PER_RADIUS = 1.2;
	private static final float MAX_FLASH = 0.55f;
	private static final float FLASH_DECAY_PER_TICK = 0.06f;
	private static final int FLASH_COLOR = 0x330000;

	private static float trauma;
	private static float flash;

	private ScreenShake() {
	}

	/** Called when a blast goes off; shakes and flashes the screen if it is close enough. */
	static void blast(Blast blast, Vec3 camera) {
		if (DekuSettings.get().noScreenEffects() || blast.radius() < MIN_BLAST_RADIUS || blast.style() == Style.SHOT
				|| blast.style() == Style.GROUND || blast.style() == Style.ICE_DOME) {
			return;
		}
		boolean core = blast.style() == Style.HOWITZER_CORE || blast.style() == Style.NUKE || blast.style() == Style.HEATWAVE || blast.style() == Style.PURPLE
			|| ExplosionFx.isDecay(blast.style()) || blast.style() == Style.FUGA;
		double distance = camera.distanceTo(blast.center());
		double reach = core ? CORE_REACH : blast.radius() * REACH_PER_RADIUS;
		double strength = Mth.clamp((reach - distance) / reach, 0, 1) * Math.min(1, blast.radius() / FULL_SHAKE_RADIUS);
		if (core) {
			strength *= CORE_TRAUMA_BOOST;
		}
		trauma = Math.min(MAX_TRAUMA, trauma + (float) strength);

		double flashReach = blast.radius() * FLASH_REACH_PER_RADIUS;
		if (distance < flashReach && !ExplosionFx.isDecay(blast.style())) {
			flash = Math.max(flash, MAX_FLASH * (float) (1 - distance / flashReach));
		}
	}

	/** A steady rumble while something is charging; it lingers briefly after. */
	static void rumble(float amount) {
		if (!DekuSettings.get().noScreenEffects()) {
			trauma = Math.max(trauma, Math.min(MAX_TRAUMA, amount));
		}
	}

	/** A red tint over the screen that holds while something is charging. */
	static void glow(float amount) {
		if (!DekuSettings.get().noScreenEffects()) {
			flash = Math.max(flash, Math.min(MAX_FLASH, amount));
		}
	}

	static void tick() {
		trauma = Math.max(0, trauma - TRAUMA_DECAY_PER_TICK);
		flash = Math.max(0, flash - FLASH_DECAY_PER_TICK);
	}

	public static boolean active() {
		return trauma > 0 && !DekuSettings.get().noScreenEffects();
	}

	/** How far the camera is twisted left and right, in degrees. */
	public static float yaw() {
		return offset(37, 23);
	}

	/** How far the camera is twisted up and down, in degrees. */
	public static float pitch() {
		return offset(29, 41);
	}

	// Smooth, unpredictable wobble: two sines of different speeds multiplied together.
	private static float offset(double speedA, double speedB) {
		Minecraft minecraft = Minecraft.getInstance();
		double time = minecraft.level == null ? 0 : minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float shake = trauma * trauma;
		return (float) (MAX_SHAKE_DEGREES * shake * Math.sin(time * speedA * 0.1) * Math.cos(time * speedB * 0.1));
	}

	static void extractFlash(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (flash <= 0 || DekuSettings.get().noScreenEffects()) {
			return;
		}
		int alpha = (int) (Mth.clamp(flash, 0, 1) * 255);
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | FLASH_COLOR);
	}
}
