package com.ronaldw07.deku.test;

import com.ronaldw07.deku.FullCowling;
import com.ronaldw07.deku.client.DekuModClient;
import com.ronaldw07.deku.client.FullCowlingClient;
import com.ronaldw07.deku.client.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.ai.attributes.Attributes;

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

			// Default settings: 100% power with a 1 second ramp-up.
			context.getInput().pressKey(DekuModClient.COWLING_KEY);
			context.waitTicks(10);
			double halfway = context.computeOnClient(client -> FullCowlingClient.percent());
			check(halfway > 30 && halfway < 70, "cowling should be mid-ramp after 10 ticks, was " + halfway);

			context.waitTicks(15);
			double full = context.computeOnClient(client -> FullCowlingClient.percent());
			check(full == 100, "cowling should reach 100% after the ramp, was " + full);
			check(hasSpeedBoost(singleplayer), "server should apply the speed boost while cowling is on");
			context.takeScreenshot("cowling-on");

			context.getInput().pressKey(DekuModClient.COWLING_KEY);
			context.waitTicks(2);
			check(context.computeOnClient(client -> FullCowlingClient.percent()) == 0, "cowling should turn off instantly");
			check(!hasSpeedBoost(singleplayer), "server should remove the speed boost when cowling turns off");
		}
	}

	private static boolean hasSpeedBoost(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst()
			.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(FullCowling.SPEED_ID) != null);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
