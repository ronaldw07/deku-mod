package com.ronaldw07.deku.test;

import com.ronaldw07.deku.client.DekuModClient;
import com.ronaldw07.deku.client.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Launches the real game, uses each ability, and saves screenshots to check by eye. */
public class DekuClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientLevel().waitForChunksRender();

			context.getInput().pressKey(DekuModClient.SETTINGS_KEY);
			context.waitForScreen(SettingsScreen.class);
			context.takeScreenshot("settings-screen");
			context.setScreen(() -> null);
		}
	}
}
