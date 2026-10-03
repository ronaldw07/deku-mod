package com.ronaldw07.deku.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shows the current ability power just below the crosshair. */
final class PowerHud {
	private static final int COWLING_COLOR = 0xFF55FF55;
	private static final int SMASH_COLOR = 0xFFFFAA00;
	private static final int GROUND_BLAST_COLOR = 0xFFFF4030;
	private static final int OFFSET_BELOW_CROSSHAIR = 12;
	private static final int LINE_HEIGHT = 10;

	private PowerHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		int x = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2 + OFFSET_BELOW_CROSSHAIR;

		int cowling = (int) Math.round(FullCowlingClient.percent());
		if (cowling > 0) {
			graphics.centeredText(Minecraft.getInstance().font, "Full Cowling " + cowling + "%", x, y, COWLING_COLOR);
			y += LINE_HEIGHT;
		}

		int smash = (int) Math.round(SmashClient.charge());
		if (smash > 0) {
			graphics.centeredText(Minecraft.getInstance().font, "Smash " + smash + "%", x, y, SMASH_COLOR);
			y += LINE_HEIGHT;
		}

		if (LaunchClient.charging()) {
			graphics.centeredText(Minecraft.getInstance().font, "Launch " + LaunchClient.charge() + "%", x, y, COWLING_COLOR);
			y += LINE_HEIGHT;
		}

		if (ExplosionClient.armsCrossed()) {
			graphics.centeredText(Minecraft.getInstance().font, "Ground Blast " + ExplosionClient.groundBlastCharge() + "%", x, y,
				GROUND_BLAST_COLOR);
		}
	}
}
