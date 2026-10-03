package com.ronaldw07.deku.test;

import com.ronaldw07.deku.DekuMod;
import com.ronaldw07.deku.FullCowling;
import com.ronaldw07.deku.client.DangerSenseClient;
import com.ronaldw07.deku.client.DekuModClient;
import com.ronaldw07.deku.client.DekuSettings;
import com.ronaldw07.deku.client.FullCowlingClient;
import com.ronaldw07.deku.client.SettingsScreen;
import com.ronaldw07.deku.client.SmashClient;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.Vec3;

/**
 * Launches the real game, uses each ability, and saves screenshots to check by eye.
 * Sections run in order in one world, each cleaning up what would get in the next one's way.
 */
public class DekuClientGameTest implements FabricClientGameTest {
	private static final int HUSK_NOTICE_TICKS = 100;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientLevel().waitForChunksRender();
			checkSoundsResolve(context);
			settings(context);
			fullCowling(context, singleplayer);
			smash(context, singleplayer);
			blackwhip(context, singleplayer);
			smokescreen(context, singleplayer);
			floatQuirk(context, singleplayer);
			dangerSense(context, singleplayer);
		}
	}

	private static void settings(ClientGameTestContext context) {
		context.getInput().pressKey(DekuModClient.SETTINGS_KEY);
		context.waitForScreen(SettingsScreen.class);
		context.takeScreenshot("settings-screen");
		context.setScreen(() -> null);
	}

	private static void fullCowling(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
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
		command(singleplayer, "time set midnight");
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.waitTicks(5);
		context.takeScreenshot("lightning-100");

		DekuSettings.set(DekuSettings.get().withCowlingPower(20));
		context.waitTicks(2);
		context.takeScreenshot("lightning-20");
		DekuSettings.set(DekuSettings.get().withCowlingPower(100));
		camera(context, CameraType.FIRST_PERSON);

		context.getInput().pressKey(DekuModClient.COWLING_KEY);
		context.waitTicks(2);
		check(context.computeOnClient(client -> FullCowlingClient.percent()) == 0, "cowling should turn off instantly");
		check(!hasSpeedBoost(singleplayer), "server should remove the speed boost when cowling turns off");
		command(singleplayer, "time set day");
	}

	private static void smash(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Face south with a golem 45 degrees off to the side, outside the blast cone, so it only
		// gets hit if the punch locks onto it. Charge for half a second (50%) and let go.
		command(singleplayer, "execute as @p at @p run tp @s ~ ~ ~ 0 0");
		command(singleplayer, "execute at @p run summon minecraft:iron_golem ~3 ~ ~3");
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(5);
		Vec3 golemStart = singleplayer.getServer().computeOnServer(server -> golem(server).position());

		context.getInput().holdKey(DekuModClient.SMASH_KEY);
		context.waitTicks(10);
		double charge = context.computeOnClient(client -> SmashClient.charge());
		check(charge > 30 && charge < 70, "smash should be about half charged after 10 ticks, was " + charge);
		context.takeScreenshot("smash-charging");
		context.getInput().releaseKey(DekuModClient.SMASH_KEY);
		context.waitTicks(2);
		context.takeScreenshot("smash-lightning");
		camera(context, CameraType.FIRST_PERSON);
		context.waitTicks(2);
		context.takeScreenshot("smash-blast");

		float health = singleplayer.getServer().computeOnServer(server -> golem(server).getHealth());
		double moved = singleplayer.getServer().computeOnServer(server -> golem(server).position().distanceTo(golemStart));
		check(health < 100, "smash should damage the golem, health was " + health);
		check(moved > 1, "smash should launch the golem, it moved " + moved);
		command(singleplayer, "kill @e[type=minecraft:iron_golem]");
	}

	private static void blackwhip(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// On a mob: a pig 10 blocks ahead gets reeled in.
		command(singleplayer, "execute at @p run summon minecraft:pig ~ ~ ~10");
		command(singleplayer, "execute as @p at @p anchored eyes run tp @s ~ ~ ~ facing entity @e[type=minecraft:pig,limit=1] eyes");
		context.waitTicks(5);
		context.getInput().pressKey(DekuModClient.BLACKWHIP_KEY);
		context.waitTicks(4);
		context.takeScreenshot("blackwhip-grab");
		context.waitTicks(16);
		double pigDistance = singleplayer.getServer().computeOnServer(server ->
			server.overworld().getEntities(EntityTypes.PIG, pig -> true).getFirst().distanceTo(player(server)));
		check(pigDistance < 4, "blackwhip should reel the pig in, it's still " + pigDistance + " away");
		command(singleplayer, "kill @e[type=minecraft:pig]");

		// On a block: aim up at a wall 8 blocks ahead and get pulled up toward it.
		command(singleplayer, "execute as @p at @p run tp @s ~ ~ ~ 0 0");
		command(singleplayer, "execute at @p run fill ~-2 ~ ~8 ~2 ~12 ~8 minecraft:stone");
		command(singleplayer, "execute as @p at @p run tp @s ~ ~ ~ 0 -30");
		context.waitTicks(5);
		double groundY = context.computeOnClient(client -> client.player.getY());
		context.getInput().pressKey(DekuModClient.BLACKWHIP_KEY);
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(6);
		context.takeScreenshot("blackwhip-swing");
		double climbed = context.computeOnClient(client -> client.player.getY()) - groundY;
		check(climbed > 2, "blackwhip should pull the player up toward the wall, climbed " + climbed);
		camera(context, CameraType.FIRST_PERSON);
		context.waitTicks(40);
		command(singleplayer, "execute at @p run fill ~-6 ~-2 ~-6 ~6 ~14 ~12 minecraft:air replace minecraft:stone");
		command(singleplayer, "execute as @p at @p run tp @s ~ ~ ~ 0 0");
	}

	private static void smokescreen(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// A husk (doesn't burn in daylight) hunts the player until the smoke goes up.
		command(singleplayer, "difficulty normal");
		command(singleplayer, "execute at @p run summon minecraft:husk ~ ~ ~8");
		// Mobs only look for targets every so often, so give it a few seconds to notice the player.
		for (int tick = 0; tick < HUSK_NOTICE_TICKS && !huskHuntsPlayer(singleplayer); tick++) {
			context.waitTick();
		}
		check(huskHuntsPlayer(singleplayer), "husk should be hunting the player before the smoke");
		context.getInput().pressKey(DekuModClient.SMOKESCREEN_KEY);
		context.waitTicks(5);
		check(!huskHuntsPlayer(singleplayer), "husk should lose the player inside the smoke");
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(15);
		context.takeScreenshot("smokescreen");
		camera(context, CameraType.FIRST_PERSON);
		command(singleplayer, "kill @e[type=minecraft:husk]");
	}

	private static void floatQuirk(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// From 6 blocks up, hold the key and stay put; let go and drop.
		command(singleplayer, "execute as @p at @p run tp @s ~ ~6 ~");
		context.getInput().holdKey(DekuModClient.FLOAT_KEY);
		context.waitTicks(2);
		double startY = context.computeOnClient(client -> client.player.getY());
		context.waitTicks(40);
		double hoverDrift = Math.abs(context.computeOnClient(client -> client.player.getY()) - startY);
		check(hoverDrift < 0.6, "float should hold the player in the air, drifted " + hoverDrift);
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.waitTicks(2);
		context.takeScreenshot("float");
		context.getInput().releaseKey(DekuModClient.FLOAT_KEY);
		context.waitTicks(30);
		double dropped = startY - context.computeOnClient(client -> client.player.getY());
		check(dropped > 3, "releasing float should drop the player, dropped " + dropped);
		camera(context, CameraType.FIRST_PERSON);
	}

	private static void dangerSense(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Well clear of the earlier smokescreen, which would stop mobs hunting the player.
		command(singleplayer, "execute as @p at @p run tp @s ~30 ~ ~ 0 0");
		context.waitTicks(5);

		// A husk 5 blocks away and hunting the player is very close.
		command(singleplayer, "execute at @p run summon minecraft:husk ~3 ~ ~4");
		for (int tick = 0; tick < HUSK_NOTICE_TICKS && dangerLevel(context) < DangerSenseClient.VERY_CLOSE; tick++) {
			context.waitTick();
		}
		check(dangerLevel(context) >= DangerSenseClient.VERY_CLOSE, "danger sense should flare for a husk 5 blocks away, was " + dangerLevel(context));
		context.takeScreenshot("danger-sense-very-close");
		command(singleplayer, "kill @e[type=minecraft:husk]");
		context.waitTicks(3);
		check(dangerLevel(context) == 0, "danger sense should calm down once the husk is gone, was " + dangerLevel(context));

		// An arrow flying straight at the player from 10 blocks ahead.
		command(singleplayer, "execute at @p run summon minecraft:arrow ~ ~1.5 ~10 {Motion:[0.0d,0.0d,-2.0d],NoGravity:1b}");
		context.waitTicks(1);
		check(dangerLevel(context) > 0.5, "danger sense should pick up an incoming arrow, was " + dangerLevel(context));
		context.takeScreenshot("danger-sense-arrow");
		context.waitTicks(10);
		command(singleplayer, "kill @e[type=minecraft:arrow]");

		// Switched off, a hunting husk raises nothing.
		context.getInput().pressKey(DekuModClient.DANGER_SENSE_KEY);
		command(singleplayer, "execute at @p run summon minecraft:husk ~ ~ ~4");
		context.waitTicks(40);
		check(dangerLevel(context) == 0, "danger sense should stay quiet while switched off, was " + dangerLevel(context));
		context.getInput().pressKey(DekuModClient.DANGER_SENSE_KEY);
		command(singleplayer, "kill @e[type=minecraft:husk]");
	}

	private static float dangerLevel(ClientGameTestContext context) {
		return context.computeOnClient(client -> DangerSenseClient.level());
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

	private static void command(TestSingleplayerContext singleplayer, String command) {
		singleplayer.getServer().runCommand(command);
	}

	private static void camera(ClientGameTestContext context, CameraType type) {
		context.runOnClient(client -> client.options.setCameraType(type));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static boolean huskHuntsPlayer(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(server -> {
			LivingEntity target = server.overworld().getEntities(EntityTypes.HUSK, husk -> true).getFirst().getTarget();
			return target != null && target == player(server);
		});
	}

	private static IronGolem golem(MinecraftServer server) {
		return server.overworld().getEntities(EntityTypes.IRON_GOLEM, golem -> true).getFirst();
	}

	private static boolean hasSpeedBoost(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(server ->
			player(server).getAttribute(Attributes.MOVEMENT_SPEED).getModifier(FullCowling.SPEED_ID) != null);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
