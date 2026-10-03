package com.ronaldw07.deku.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shows the current ability power just below the crosshair. */
final class PowerHud {
	private static final int COWLING_COLOR = 0xFF55FF55;
	private static final int OFFSET_BELOW_CROSSHAIR = 12;

	private PowerHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		int cowling = (int) Math.round(FullCowlingClient.percent());
		if (cowling == 0) {
			return;
		}

		graphics.centeredText(Minecraft.getInstance().font, "Full Cowling " + cowling + "%",
			graphics.guiWidth() / 2, graphics.guiHeight() / 2 + OFFSET_BELOW_CROSSHAIR, COWLING_COLOR);
	}
}
