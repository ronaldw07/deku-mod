package com.ronaldw07.deku.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shows the current ability power just above the hotbar, stacking upward. */
final class PowerHud {
	private static final int COWLING_COLOR = 0xFF55FF55;
	private static final int SMASH_COLOR = 0xFFFFAA00;
	private static final int GROUND_BLAST_COLOR = 0xFFFF4030;
	private static final int EXPLOSION_COWLING_COLOR = 0xFFFF7030;
	private static final int DECAY_COLOR = 0xFFB8B0D0;
	// Clear of the hotbar, health and armor rows, and the held item's name.
	private static final int BOTTOM_LINE_ABOVE_SCREEN_BOTTOM = 72;
	private static final int LINE_HEIGHT = 10;

	private PowerHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		int x = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() - BOTTOM_LINE_ABOVE_SCREEN_BOTTOM;

		int cowling = (int) Math.round(FullCowlingClient.percent());
		if (cowling > 0) {
			graphics.centeredText(Minecraft.getInstance().font, "Full Cowling " + cowling + "%", x, y, COWLING_COLOR);
			y -= LINE_HEIGHT;
		}

		int smash = (int) Math.round(SmashClient.charge());
		if (smash > 0) {
			graphics.centeredText(Minecraft.getInstance().font, "Smash " + smash + "%", x, y, SMASH_COLOR);
			y -= LINE_HEIGHT;
		}

		if (LaunchClient.charging()) {
			graphics.centeredText(Minecraft.getInstance().font, "Launch " + LaunchClient.charge() + "%", x, y, COWLING_COLOR);
			y -= LINE_HEIGHT;
		}

		if (GearshiftClient.gear() > 0) {
			graphics.centeredText(Minecraft.getInstance().font, "Gearshift: gear " + GearshiftClient.gear(), x, y, COWLING_COLOR);
			y -= LINE_HEIGHT;
		}

		if (ExplosionClient.fireballCharge() > 0) {
			graphics.centeredText(Minecraft.getInstance().font, "Fireball " + ExplosionClient.fireballCharge() + "%", x, y,
				GROUND_BLAST_COLOR);
			y -= LINE_HEIGHT;
		}

		if (ExplosionClient.armsCrossed()) {
			graphics.centeredText(Minecraft.getInstance().font, "Ground Blast " + ExplosionClient.groundBlastCharge() + "%", x, y,
				GROUND_BLAST_COLOR);
			y -= LINE_HEIGHT;
		}

		if (ExplosionCowlingClient.active()) {
			graphics.centeredText(Minecraft.getInstance().font, "Explosion Cowling", x, y, EXPLOSION_COWLING_COLOR);
			y -= LINE_HEIGHT;
		}

		if (DecayClient.cowling()) {
			graphics.centeredText(Minecraft.getInstance().font, "Decay Cowling", x, y, DECAY_COLOR);
			y -= LINE_HEIGHT;
		}

		if (DecayClient.chargingCatastrophe()) {
			graphics.centeredText(Minecraft.getInstance().font, "Catastrophe " + DecayClient.catastropheCharge() + "%", x, y, DECAY_COLOR);
		} else if (DecayClient.charging()) {
			graphics.centeredText(Minecraft.getInstance().font, "Decay Wave " + DecayClient.waveCharge() + "%", x, y, DECAY_COLOR);
		}
	}
}
