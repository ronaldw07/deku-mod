package com.ronaldw07.deku.test;

import com.ronaldw07.deku.DekuMod;
import com.ronaldw07.deku.FullCowling;
import com.ronaldw07.deku.client.DekuModClient;
import com.ronaldw07.deku.client.DekuSettings;
import com.ronaldw07.deku.client.FullCowlingClient;
import com.ronaldw07.deku.client.SettingsScreen;
import com.ronaldw07.deku.client.SmashClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.Vec3;

/** Launches the real game, uses each ability, and saves screenshots to check by eye. */
public class DekuClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientLevel().waitForChunksRender();
			checkSoundsResolve(context);

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

			// Lightning, viewed from the front at night so the glow is easy to judge.
			singleplayer.getServer().runCommand("time set midnight");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("lightning-100");

			DekuSettings.set(DekuSettings.get().withCowlingPower(20));
			context.waitTicks(2);
			context.takeScreenshot("lightning-20");
			DekuSettings.set(DekuSettings.get().withCowlingPower(100));
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			context.getInput().pressKey(DekuModClient.COWLING_KEY);
			context.waitTicks(2);
			check(context.computeOnClient(client -> FullCowlingClient.percent()) == 0, "cowling should turn off instantly");
			check(!hasSpeedBoost(singleplayer), "server should remove the speed boost when cowling turns off");

			// Smash: face south with a golem 4 blocks ahead, charge for half a second (50%) and let go.
			singleplayer.getServer().runCommand("time set day");
			singleplayer.getServer().runCommand("execute as @p at @p run tp @s ~ ~ ~ 0 0");
			singleplayer.getServer().runCommand("execute at @p run summon minecraft:iron_golem ~ ~ ~4");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(5);
			Vec3 golemStart = singleplayer.getServer().computeOnServer(server -> golem(server).position());

			context.getInput().holdKey(DekuModClient.SMASH_KEY);
			context.waitTicks(10);
			double charge = context.computeOnClient(client -> SmashClient.charge());
			check(charge > 30 && charge < 70, "smash should be about half charged after 10 ticks, was " + charge);
			context.takeScreenshot("smash-charging");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.getInput().releaseKey(DekuModClient.SMASH_KEY);
			context.waitTicks(4);
			context.takeScreenshot("smash-blast");

			float health = singleplayer.getServer().computeOnServer(server -> golem(server).getHealth());
			double moved = singleplayer.getServer().computeOnServer(server -> golem(server).position().distanceTo(golemStart));
			check(health < 100, "smash should damage the golem, health was " + health);
			check(moved > 1, "smash should launch the golem, it moved " + moved);
		}
	}

	/** Every deku: sound must point at a real sound, so a typo in sounds.json fails here instead of going silent. */
	private static void checkSoundsResolve(ClientGameTestContext context) {
		List<Identifier> sounds = BuiltInRegistries.SOUND_EVENT.keySet().stream()
			.filter(id -> id.getNamespace().equals(DekuMod.MOD_ID))
			.toList();
		check(!sounds.isEmpty(), "mod should register sounds");
		for (Identifier id : sounds) {
			boolean resolves = context.computeOnClient(client -> {
				WeighedSoundEvents event = client.getSoundManager().getSoundEvent(id);
				return event != null && event.getSound(RandomSource.create()) != SoundManager.EMPTY_SOUND;
			});
			check(resolves, "sound " + id + " should resolve to a real sound");
		}
	}

	private static IronGolem golem(MinecraftServer server) {
		return server.overworld().getEntities(EntityTypes.IRON_GOLEM, golem -> true).getFirst();
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
