package com.ronaldw07.deku.test;

import com.ronaldw07.deku.Aim;
import com.ronaldw07.deku.DekuItems;
import com.ronaldw07.deku.DekuMod;
import com.ronaldw07.deku.FullCowling;
import com.ronaldw07.deku.Smokescreen;
import com.ronaldw07.deku.Sukuna;
import com.ronaldw07.deku.client.Cooldowns;
import com.ronaldw07.deku.client.DangerSenseClient;
import com.ronaldw07.deku.client.DekuModClient;
import com.ronaldw07.deku.client.DelawareClient;
import com.ronaldw07.deku.client.DekuSettings;
import com.ronaldw07.deku.client.ExplosionClient;
import com.ronaldw07.deku.client.FireballChargeFx;
import com.ronaldw07.deku.client.ExplosionCowlingClient;
import com.ronaldw07.deku.client.FullCowlingClient;
import com.ronaldw07.deku.client.GojoClient;
import com.ronaldw07.deku.client.LaunchClient;
import com.ronaldw07.deku.client.ScreenShake;
import com.ronaldw07.deku.client.SettingsScreen;
import com.ronaldw07.deku.client.SmashClient;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
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
			if (wanted("starterKit")) {
				starterKit(context, singleplayer);
			}
			if (wanted("settings")) {
				settings(context);
			}
			if (wanted("fullCowling")) {
				fullCowling(context, singleplayer);
			}
			if (wanted("smash")) {
				smash(context, singleplayer);
			}
			if (wanted("blackwhip")) {
				blackwhip(context, singleplayer);
			}
			if (wanted("smokescreen")) {
				smokescreen(context, singleplayer);
			}
			if (wanted("poses")) {
				poses(context, singleplayer);
			}
			if (wanted("launch")) {
				launch(context, singleplayer);
			}
			if (wanted("floatQuirk")) {
				floatQuirk(context, singleplayer);
			}
			if (wanted("dangerSense")) {
				dangerSense(context, singleplayer);
			}
			if (wanted("iceSlide")) {
				iceSlide(context, singleplayer);
			}
			if (wanted("heatwave")) {
				heatwave(context, singleplayer);
			}
			if (wanted("decayWave")) {
				decayWave(context, singleplayer);
			}
			if (wanted("fireballRemote")) {
				fireballRemote(context, singleplayer);
			}
			if (wanted("gojo")) {
				gojo(context, singleplayer);
			}
			if (wanted("sukuna")) {
				sukuna(context, singleplayer);
			}
			if (wanted("delaware")) {
				delaware(context, singleplayer);
			}
			if (wanted("screenEffectsKey")) {
				screenEffectsKey(context);
			}
			if (wanted("flightBoost")) {
				flightBoost(context, singleplayer);
			}
			if (wanted("smokescreenHold")) {
				smokescreenHold(context, singleplayer);
			}
			if (wanted("explosion")) {
				explosion(context, singleplayer);
			}
			if (wanted("fullPowerSmashTunnel")) {
				fullPowerSmashTunnel(context, singleplayer);
			}
		}
		if (!wanted("mountainSmash")) {
			return;
		}
		// The test world above is superflat; mountains need normal terrain.
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
				.adjustSettings(settings -> settings.setWorldType(settings.getNormalPresetList().getFirst())).create()) {
			singleplayer.getClientLevel().waitForChunksRender();
			mountainSmash(context, singleplayer);
		}
	}

	/**
	 * Runs every section unless the game is launched with -Ddeku.only=name,name, which runs just
	 * those (and the setup they depend on), so a change to one move doesn't need the whole suite.
	 */
	private static boolean wanted(String section) {
		String only = System.getProperty("deku.only");
		return only == null || only.isBlank() || List.of(only.split(",")).contains(section) || section.equals("starterKit");
	}

	private static void starterKit(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		Item[] expected = {DekuItems.ONE_FOR_ALL, DekuItems.EXPLOSION, DekuItems.HERO_NOTEBOOK};
		for (int slot = 0; slot < expected.length; slot++) {
			int index = slot;
			Item actual = singleplayer.getServer().computeOnServer(server -> player(server).getInventory().getItem(index).getItem());
			check(actual == expected[slot], "hotbar slot " + (slot + 1) + " should hold " + expected[slot] + ", had " + actual);
		}

		// The notebook opens on right-click.
		selectSlot(context, 2);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitForScreen(BookViewScreen.class);
		context.takeScreenshot("notebook");
		context.setScreen(() -> null);

		// Deku's moves need One For All in hand: with an empty hand, C does nothing.
		selectSlot(context, 5);
		context.getInput().pressKey(DekuModClient.COWLING_KEY);
		context.waitTicks(5);
		check(context.computeOnClient(client -> FullCowlingClient.percent()) == 0, "full cowling shouldn't start without One For All in hand");
		selectSlot(context, 0);
		context.takeScreenshot("hotbar");
	}

	private static void selectSlot(ClientGameTestContext context, int slot) {
		context.getInput().pressKey(options -> options.keyHotbarSlots[slot]);
		context.waitTicks(2);
	}

	private static void settings(ClientGameTestContext context) {
		context.getInput().pressKey(DekuModClient.SETTINGS_KEY);
		context.waitForScreen(SettingsScreen.class);
		context.takeScreenshot("settings-screen");
		context.setScreen(() -> null);

		double[] scales = new double[3];
		for (int detail = DekuSettings.LOW_DETAIL; detail <= DekuSettings.HIGH_DETAIL; detail++) {
			int chosen = detail;
			scales[detail - 1] = context.computeOnClient(client -> {
				DekuSettings.set(DekuSettings.get().withParticleDetail(chosen));
				return DekuSettings.get().detailScale();
			});
		}
		context.runOnClient(client -> DekuSettings.set(DekuSettings.get().withParticleDetail(DekuSettings.NORMAL_DETAIL)));
		check(scales[0] < scales[1] && scales[1] < scales[2], "particle detail should scale counts Low < Normal < High");
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

		// Hair and eye wisps, seen from the front in daylight.
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.waitTicks(5);
		context.takeScreenshot("cowling-hair-day");
		context.runOnClient(client -> client.options.fov().set(30));
		context.waitTicks(2);
		context.takeScreenshot("cowling-hair-closeup");
		context.runOnClient(client -> client.options.fov().set(70));

		// Lightning, viewed from the front at night so the glow is easy to judge.
		command(singleplayer, "time set midnight");
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

		// A slow 2 second ramp: red lightning climbs the body, then bursts at full power.
		DekuSettings.set(DekuSettings.get().withCowlingRampSeconds(2.0));
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.getInput().pressKey(DekuModClient.COWLING_KEY);
		context.waitTicks(25);
		context.takeScreenshot("cowling-charging");
		context.waitTicks(16);
		check(context.computeOnClient(client -> FullCowlingClient.percent()) == 100, "cowling should reach full power after 2 seconds");
		context.takeScreenshot("cowling-burst");
		context.getInput().pressKey(DekuModClient.COWLING_KEY);
		DekuSettings.set(DekuSettings.get().withCowlingRampSeconds(1.0));
		camera(context, CameraType.FIRST_PERSON);
		command(singleplayer, "time set day");
	}

	private static void smash(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Face south with a golem 45 degrees off to the side, outside the blast cone, so it only
		// gets hit if the punch locks onto it. Charge for half a second (50%) and let go.
		command(singleplayer, "execute as @p at @p run tp @s ~ ~ ~ 0 0");
		command(singleplayer, "execute at @p run summon minecraft:iron_golem ~3 ~ ~3");
		// It still gets knocked back, but can't wander out of reach before the punch.
		command(singleplayer, "effect give @e[type=minecraft:iron_golem] minecraft:slowness 30 255 true");
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
		// Smash carves terrain, so carry on from untouched ground.
		command(singleplayer, "execute as @p at @p run tp @s ~-30 -60 ~ 0 0");
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

	private static void smokescreenHold(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Holding Z keeps the cloud growing past its starting radius of 9, up to a cap of 30; letting go stops it.
		command(singleplayer, "kill @e[type=!minecraft:player]");
		selectSlot(context, 0);
		context.waitTicks(250); // the earlier clouds clear and the cooldown ends
		context.getInput().holdKey(DekuModClient.SMOKESCREEN_KEY);
		context.waitTicks(20);
		double early = singleplayer.getServer().computeOnServer(server -> Smokescreen.largestRadius());
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(60);
		context.takeScreenshot("smokescreen-grown");
		double grown = singleplayer.getServer().computeOnServer(server -> Smokescreen.largestRadius());
		check(grown > early && grown > 12 && grown <= 30, "a held Smokescreen should keep growing, was " + early + " then " + grown);
		context.getInput().releaseKey(DekuModClient.SMOKESCREEN_KEY);
		context.waitTicks(2);
		double released = singleplayer.getServer().computeOnServer(server -> Smokescreen.largestRadius());
		context.waitTicks(20);
		double later = singleplayer.getServer().computeOnServer(server -> Smokescreen.largestRadius());
		check(later == released, "a Smokescreen should stop growing once the key is let go, " + released + " then " + later);
		context.waitTicks(250);
		double gone = singleplayer.getServer().computeOnServer(server -> Smokescreen.largestRadius());
		check(gone == 0, "a released Smokescreen should clear after a few seconds, radius " + gone);
		camera(context, CameraType.FIRST_PERSON);
	}

	private static void iceSlide(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Sliding on ice carries the player over steps and walls instead of leaving them stuck against them.
		command(singleplayer, "kill @e[type=!minecraft:player]");
		command(singleplayer, "execute as @p run tp @s 1000 -59 1000 0 0");
		context.waitTicks(30);
		command(singleplayer, "execute at @p run fill ~-4 ~ ~6 ~4 ~ ~6 minecraft:stone");
		command(singleplayer, "execute at @p run fill ~-4 ~ ~14 ~4 ~2 ~14 minecraft:stone");
		selectSlot(context, 4);
		double startZ = context.computeOnClient(client -> client.player.getZ());
		context.getInput().holdKey(options -> options.keyJump);
		context.waitTicks(40);
		context.takeScreenshot("ice-slide");
		double slid = context.computeOnClient(client -> client.player.getZ()) - startZ;
		context.getInput().releaseKey(options -> options.keyJump);
		check(slid > 20, "the ice slide should carry the player over a step and a wall, it only got " + slid + " blocks");
		context.waitTicks(10);
	}

	private static void heatwave(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// X with Half Cold Half Hot: a giant dome of ice goes up around a spot ahead, then blows apart in a fire nova.
		command(singleplayer, "kill @e[type=!minecraft:player]");
		command(singleplayer, "execute as @p run tp @s 1800 -59 1800 0 3");
		context.waitTicks(40);
		selectSlot(context, 4);
		Vec3 target = singleplayer.getServer().computeOnServer(server -> Aim.trace(player(server), 40).getLocation());
		command(singleplayer, "summon minecraft:husk " + target.x + " " + target.y + " " + (target.z + 4) + " {NoAI:1b}");
		context.getInput().pressKey(DekuModClient.CLUSTER_KEY);
		context.waitTicks(16);
		context.takeScreenshot("heatwave-dome");
		int ice = singleplayer.getServer().computeOnServer(server -> {
			int count = 0;
			BlockPos at = BlockPos.containing(target);
			for (BlockPos pos : BlockPos.betweenClosed(at.offset(-15, 0, -15), at.offset(15, 15, 15))) {
				count += server.overworld().getBlockState(pos).is(Blocks.PACKED_ICE) || server.overworld().getBlockState(pos).is(Blocks.BLUE_ICE) ? 1 : 0;
			}
			return count;
		});
		check(ice > 150, "the Flashfreeze dome should be built out of ice, only " + ice + " ice blocks");
		context.waitTicks(14);
		context.takeScreenshot("heatwave-cracking");
		context.waitTicks(10);
		context.takeScreenshot("heatwave-nova");
		check(context.computeOnClient(client -> ScreenShake.active()), "the Flashfreeze blast should shake the screen");
		context.waitTicks(40);
		context.takeScreenshot("heatwave-aftermath");
		boolean hurt = singleplayer.getServer().computeOnServer(server -> server.overworld()
			.getEntities(EntityTypes.HUSK, husk -> husk.isAlive() && husk.getHealth() >= husk.getMaxHealth()).isEmpty());
		check(hurt, "the Flashfreeze should hurt the husk inside the dome");
		int dug = singleplayer.getServer().computeOnServer(server -> {
			int count = 0;
			BlockPos at = BlockPos.containing(target);
			for (BlockPos pos : BlockPos.betweenClosed(at.offset(-20, -4, -20), at.offset(20, 0, 20))) {
				count += server.overworld().getBlockState(pos).isAir() ? 1 : 0;
			}
			return count;
		});
		check(dug > 600, "the Flashfreeze nova should leave a big crater, only " + dug + " blocks cleared");
	}

	private static void decayWave(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Decay's wave: holding V then letting go sends a dark crumbling cloud out with the wave and shakes the screen.
		command(singleplayer, "kill @e[type=!minecraft:player]");
		command(singleplayer, "execute as @p run tp @s 5000 -59 5000 0 8");
		context.waitTicks(40);
		selectSlot(context, 3);
		check(context.computeOnClient(client -> DekuItems.isHolding(client.player, DekuItems.DECAY)), "slot 4 should hold Decay");
		context.getInput().holdKey(DekuModClient.SMASH_KEY);
		context.waitTicks(70);
		context.getInput().releaseKey(DekuModClient.SMASH_KEY);
		context.waitTicks(6);
		context.takeScreenshot("decay-wave-start");
		check(context.computeOnClient(client -> ScreenShake.active()), "the Decay wave should shake the screen");
		context.waitTicks(20);
		context.takeScreenshot("decay-wave-rolling");
		context.waitTicks(30);
		context.takeScreenshot("decay-wave-late");
	}

	private static void fireballRemote(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// The server relays how far a fireball is grown to everyone who can see the player; the charger's own client hears it too.
		command(singleplayer, "kill @e[type=!minecraft:player]");
		command(singleplayer, "execute as @p run tp @s 5400 -59 5400 0 0");
		context.waitTicks(40);
		selectSlot(context, 1);
		context.getInput().holdKey(DekuModClient.CLUSTER_KEY);
		context.waitTicks(40);
		context.takeScreenshot("fireball-charge-own");
		int heard = context.computeOnClient(client -> FireballChargeFx.remoteCharge(client.player.getUUID()));
		check(heard > 30, "the server should relay a growing fireball to nearby clients, heard " + heard);
		context.getInput().releaseKey(DekuModClient.CLUSTER_KEY);
		context.waitTicks(10);
		int after = context.computeOnClient(client -> FireballChargeFx.remoteCharge(client.player.getUUID()));
		check(after == 0, "letting go should clear the fireball for nearby clients, still " + after);
	}

	private static void gojo(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Gojo: Blue drags a pig in, Infinity stops a husk, Red blasts a pig away, Hollow Purple erases a wall and everything past it.
		command(singleplayer, "kill @e[type=!minecraft:player]");
		command(singleplayer, "execute as @p run tp @s 2200 -59 2200 0 3");
		context.waitTicks(40);
		selectSlot(context, 5);
		check(context.computeOnClient(client -> DekuItems.isHolding(client.player, DekuItems.GOJO)), "slot 6 should hold Gojo");
		Vec3 target = singleplayer.getServer().computeOnServer(server -> Aim.trace(player(server), 24).getLocation());

		command(singleplayer, "summon minecraft:pig " + (target.x + 9) + " " + target.y + " " + target.z);
		double before = singleplayer.getServer().computeOnServer(server ->
			server.overworld().getEntities(EntityTypes.PIG, pig -> true).getFirst().position().distanceTo(target));
		context.getInput().holdKeyFor(options -> options.keyUse, 2);
		context.waitTicks(25);
		context.takeScreenshot("gojo-blue");
		double after = singleplayer.getServer().computeOnServer(server ->
			server.overworld().getEntities(EntityTypes.PIG, pig -> true).getFirst().position().distanceTo(target));
		check(after < before - 3, "Blue should drag the pig toward it, " + before + " then " + after);
		context.waitTicks(60);
		command(singleplayer, "kill @e[type=minecraft:pig]");

		// Z: Infinity keeps a husk's hits off the player.
		context.getInput().pressKey(DekuModClient.SMOKESCREEN_KEY);
		context.waitTicks(5);
		check(context.computeOnClient(client -> GojoClient.infinity()), "Z should switch Infinity on");
		command(singleplayer, "difficulty normal");
		command(singleplayer, "execute at @p run summon minecraft:husk ~ ~ ~2");
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.waitTicks(80);
		context.takeScreenshot("gojo-infinity");
		float health = context.computeOnClient(client -> client.player.getHealth());
		check(health >= 20, "Infinity should keep the husk from hurting the player, health " + health);
		command(singleplayer, "kill @e[type=minecraft:husk]");
		context.getInput().pressKey(DekuModClient.SMOKESCREEN_KEY);
		context.waitTicks(5);
		camera(context, CameraType.FIRST_PERSON);

		// Hold V: Red bursts and throws a pig clear.
		command(singleplayer, "execute as @p run tp @s 2200 -59 2200 0 3");
		context.waitTicks(10);
		command(singleplayer, "summon minecraft:pig " + target.x + " " + target.y + " " + (target.z + 3));
		Vec3 pigStart = singleplayer.getServer().computeOnServer(server ->
			server.overworld().getEntities(EntityTypes.PIG, pig -> true).getFirst().position());
		context.getInput().holdKey(DekuModClient.SMASH_KEY);
		context.waitTicks(45);
		check(context.computeOnClient(client -> GojoClient.redCharge()) >= 90, "holding V should fully charge Red");
		context.takeScreenshot("gojo-red-charge");
		context.getInput().releaseKey(DekuModClient.SMASH_KEY);
		context.waitTicks(20);
		context.takeScreenshot("gojo-red");
		double thrown = singleplayer.getServer().computeOnServer(server -> {
			var pigs = server.overworld().getEntities(EntityTypes.PIG, pig -> true);
			return pigs.isEmpty() ? 99 : pigs.getFirst().position().distanceTo(pigStart);
		});
		check(thrown > 2, "Red should throw the pig away, it moved " + thrown);
		command(singleplayer, "kill @e[type=!minecraft:player]");
		context.waitTicks(100);

		// Hold X: the orbs fuse into Hollow Purple, which erases a wall in its way.
		command(singleplayer, "execute as @p run tp @s 2200 -59 2200 0 0");
		context.waitTicks(10);
		command(singleplayer, "execute at @p run fill ~-4 ~ ~30 ~4 ~5 ~30 minecraft:stone");
		BlockPos wall = singleplayer.getServer().computeOnServer(server -> BlockPos.containing(player(server).getEyePosition()).south(30));
		context.getInput().holdKey(DekuModClient.CLUSTER_KEY);
		context.waitTicks(30);
		context.takeScreenshot("gojo-purple-charging");
		context.waitTicks(40);
		check(context.computeOnClient(client -> GojoClient.purpleCharge()) >= 90, "holding X should fully charge Hollow Purple");
		context.takeScreenshot("gojo-purple-fused");
		context.getInput().releaseKey(DekuModClient.CLUSTER_KEY);
		context.waitTicks(6);
		context.takeScreenshot("gojo-purple-flight");
		context.waitTicks(20);
		boolean erased = singleplayer.getServer().computeOnServer(server -> server.overworld().getBlockState(wall).isAir());
		check(erased, "Hollow Purple should erase the wall in its path at " + wall);
		context.waitTicks(25);
		context.takeScreenshot("gojo-purple-end");
	}

	private static void sukuna(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Sukuna: Dismantle cuts through a wall and a pig, Cleave takes a big bite out of a pig, Domain Expansion shreds a husk and the terrain.
		command(singleplayer, "kill @e[type=!minecraft:player]");
		command(singleplayer, "execute as @p run tp @s 2600 -59 2600 0 0");
		context.waitTicks(40);
		selectSlot(context, 6);
		check(context.computeOnClient(client -> DekuItems.isHolding(client.player, DekuItems.SUKUNA)), "slot 7 should hold Sukuna");
		command(singleplayer, "execute at @p run fill ~-6 ~ ~14 ~6 ~7 ~14 minecraft:stone");
		command(singleplayer, "execute at @p run summon minecraft:pig ~ ~ ~8 {NoAI:1b}");
		BlockPos wall = singleplayer.getServer().computeOnServer(server -> BlockPos.containing(player(server).getEyePosition()).south(14));
		context.getInput().holdKeyFor(options -> options.keyUse, 2);
		context.waitTicks(12);
		context.takeScreenshot("sukuna-dismantle");
		int cut = singleplayer.getServer().computeOnServer(server -> {
			int count = 0;
			for (BlockPos pos : BlockPos.betweenClosed(wall.offset(-6, -3, 0), wall.offset(6, 4, 0))) {
				count += server.overworld().getBlockState(pos).isAir() ? 1 : 0;
			}
			return count;
		});
		check(cut > 20, "Dismantle should cut the wall, only " + cut + " blocks gone");
		boolean pigHurt = singleplayer.getServer().computeOnServer(server -> {
			var pigs = server.overworld().getEntities(EntityTypes.PIG, pig -> true);
			return pigs.isEmpty() || pigs.getFirst().getHealth() < pigs.getFirst().getMaxHealth();
		});
		check(pigHurt, "Dismantle should hurt the pig in front");

		command(singleplayer, "kill @e[type=minecraft:pig]");
		command(singleplayer, "execute at @p run summon minecraft:iron_golem ~ ~ ~9 {NoAI:1b}");
		context.waitTicks(40);
		command(singleplayer, "execute as @p at @p anchored eyes run tp @s ~ ~ ~ facing entity @e[type=minecraft:iron_golem,limit=1] eyes");
		context.waitTicks(3);
		context.getInput().pressKey(DekuModClient.SMASH_KEY);
		context.waitTicks(4);
		context.takeScreenshot("sukuna-cleave");
		float golemHealth = singleplayer.getServer().computeOnServer(server -> golem(server).getHealth());
		check(golemHealth < 75, "Cleave should take a big bite out of the golem, health " + golemHealth);
		command(singleplayer, "kill @e[type=!minecraft:player]");

		// C: Domain Expansion.
		command(singleplayer, "execute as @p run tp @s 2600 -59 2600 0 0");
		context.waitTicks(10);
		command(singleplayer, "execute at @p run fill ~-12 ~ ~12 ~12 ~10 ~40 minecraft:stone");
		command(singleplayer, "execute at @p run summon minecraft:husk ~ ~ ~8 {NoAI:1b}");
		context.getInput().pressKey(DekuModClient.COWLING_KEY);
		context.waitTicks(40);
		context.takeScreenshot("sukuna-domain");
		check(singleplayer.getServer().computeOnServer(server -> Sukuna.domainOpen(player(server).getUUID())), "C should open the Domain");
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(40);
		context.takeScreenshot("sukuna-domain-slashing");
		context.waitTicks(80);
		boolean huskDead = singleplayer.getServer().computeOnServer(server -> server.overworld().getEntities(EntityTypes.HUSK, husk -> husk.isAlive()).isEmpty());
		check(huskDead, "the Domain should shred the husk inside it");
		int shredded = singleplayer.getServer().computeOnServer(server -> {
			int count = 0;
			BlockPos at = player(server).blockPosition();
			for (BlockPos pos : BlockPos.betweenClosed(at.offset(-12, 0, 12), at.offset(12, 10, 40))) {
				count += server.overworld().getBlockState(pos).isAir() ? 1 : 0;
			}
			return count;
		});
		check(shredded > 400, "the Domain's slashes should cut the stone block apart, only " + shredded + " blocks gone");
		context.waitTicks(100);
		check(!singleplayer.getServer().computeOnServer(server -> Sukuna.domainOpen(player(server).getUUID())), "the Domain should close after ten seconds");
		camera(context, CameraType.FIRST_PERSON);
	}

	private static void delaware(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Holding Y winds the flick up; letting go fires it and hurts the pig in line.
		command(singleplayer, "kill @e[type=!minecraft:player]");
		command(singleplayer, "execute as @p run tp @s 3400 -59 3400 0 0");
		context.waitTicks(40);
		selectSlot(context, 0);
		command(singleplayer, "execute at @p run summon minecraft:pig ~ ~ ~12 {NoAI:1b}");
		DekuSettings.set(DekuSettings.get().withPunchPower(100));
		context.getInput().holdKey(DekuModClient.DELAWARE_KEY);
		context.waitTicks(45);
		check(context.computeOnClient(client -> DelawareClient.charge()) >= 90, "holding Y should fully charge Delaware Smash");
		context.takeScreenshot("delaware-charged");
		context.getInput().releaseKey(DekuModClient.DELAWARE_KEY);
		context.waitTicks(10);
		context.takeScreenshot("delaware-fired");
		boolean hurt = singleplayer.getServer().computeOnServer(server -> {
			var pigs = server.overworld().getEntities(EntityTypes.PIG, pig -> true);
			return pigs.isEmpty() || pigs.getFirst().getHealth() < pigs.getFirst().getMaxHealth();
		});
		check(hurt, "the charged Delaware Smash should hit the pig in line");
	}

	private static void screenEffectsKey(ClientGameTestContext context) {
		// J switches screen shake and flash off and on.
		boolean before = context.computeOnClient(client -> DekuSettings.get().noScreenEffects());
		context.getInput().pressKey(DekuModClient.SCREEN_EFFECTS_KEY);
		context.waitTicks(3);
		check(context.computeOnClient(client -> DekuSettings.get().noScreenEffects()) != before, "J should switch the screen effects");
		context.getInput().pressKey(DekuModClient.SCREEN_EFFECTS_KEY);
		context.waitTicks(3);
		check(context.computeOnClient(client -> DekuSettings.get().noScreenEffects()) == before, "J again should switch them back");
	}

	private static void flightBoost(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Holding sprint while flying doubles the speed.
		command(singleplayer, "kill @e[type=!minecraft:player]");
		command(singleplayer, "execute as @p run tp @s 3000 -59 3000 0 -20");
		context.waitTicks(40);
		selectSlot(context, 1);
		context.getInput().holdKeyFor(options -> options.keyJump, 2);
		context.waitTicks(2);
		context.getInput().holdKey(options -> options.keyJump);
		context.waitTicks(5);
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(5);
		Vec3 a = context.computeOnClient(client -> client.player.position());
		context.waitTicks(10);
		Vec3 b = context.computeOnClient(client -> client.player.position());
		context.getInput().holdKey(options -> options.keySprint);
		context.waitTicks(3);
		Vec3 c = context.computeOnClient(client -> client.player.position());
		context.waitTicks(10);
		Vec3 d = context.computeOnClient(client -> client.player.position());
		context.takeScreenshot("flight-boost");
		double normal = a.distanceTo(b);
		double boosted = c.distanceTo(d);
		context.getInput().releaseKey(options -> options.keySprint);
		context.getInput().releaseKey(options -> options.keyUp);
		context.getInput().releaseKey(options -> options.keyJump);
		check(boosted > normal * 1.5, "sprinting in flight should boost the speed, " + normal + " then " + boosted);
		context.waitTicks(40);
	}

	private static void poses(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Smokescreen was just used, so it's recharging on the controls panel and can't go off again.
		check(!context.computeOnClient(client -> Cooldowns.ready(Cooldowns.Ability.SMOKESCREEN)), "smokescreen should be on cooldown");
		context.takeScreenshot("controls-panel");

		// Face the camera, aiming up at open sky so nothing gets hit.
		command(singleplayer, "kill @e[type=!minecraft:player]");
		command(singleplayer, "execute as @p at @p run tp @s ~20 ~ ~ 0 -20");
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.waitTicks(3);
		context.getInput().pressKey(DekuModClient.COWLING_KEY);
		context.waitTicks(3);
		context.takeScreenshot("pose-power-up");
		context.getInput().pressKey(DekuModClient.COWLING_KEY);
		context.getInput().holdKey(DekuModClient.SMASH_KEY);
		context.waitTicks(10);
		context.takeScreenshot("pose-smash-charge");
		context.getInput().releaseKey(DekuModClient.SMASH_KEY);
		context.waitTicks(2);
		context.takeScreenshot("pose-punch");
		context.waitTicks(20);
		context.getInput().pressKey(DekuModClient.BLACKWHIP_KEY);
		context.waitTicks(3);
		context.takeScreenshot("pose-whip");

		selectSlot(context, 1);
		context.waitTicks(3);
		context.getInput().holdKeyFor(options -> options.keyUse, 2);
		context.waitTicks(3);
		context.takeScreenshot("pose-ap-aim");
		context.waitTicks(20);
		selectSlot(context, 0);
		camera(context, CameraType.FIRST_PERSON);
	}

	private static void launch(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Hold jump on the ground: crouch and charge without jumping, with a pig standing close by.
		command(singleplayer, "kill @e[type=!minecraft:player]");
		command(singleplayer, "execute as @p at @p run tp @s ~20 -60 ~ 0 -15"); // back up on the superflat surface, clear of Smash holes
		command(singleplayer, "execute at @p run summon minecraft:pig ~4 ~ ~");
		context.waitTicks(5);
		Vec3 start = context.computeOnClient(client -> client.player.position());
		Vec3 pigStart = singleplayer.getServer().computeOnServer(server ->
			server.overworld().getEntities(EntityTypes.PIG, pig -> true).getFirst().position());
		context.getInput().holdKey(options -> options.keyJump);
		context.waitTicks(16);
		check(context.computeOnClient(client -> client.player.isCrouching()), "charging a launch should crouch");
		check(context.computeOnClient(client -> client.player.getY()) - start.y < 0.1, "charging a launch shouldn't jump");
		int charge = context.computeOnClient(client -> LaunchClient.charge());
		check(charge > 30, "holding jump should charge the launch, was " + charge);
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.waitTicks(2);
		context.takeScreenshot("launch-charge");

		// Let go: rocket toward the crosshair, kicking off a blast and a shockwave.
		context.getInput().releaseKey(options -> options.keyJump);
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(3);
		context.takeScreenshot("launch-liftoff");
		context.waitTicks(5);
		context.takeScreenshot("launch-trail");
		double flown = context.computeOnClient(client -> client.player.position()).distanceTo(start);
		check(flown > 10, "the launch should send the player flying, moved " + flown);
		double pigThrown = singleplayer.getServer().computeOnServer(server ->
			server.overworld().getEntities(EntityTypes.PIG, pig -> true).getFirst().position().distanceTo(pigStart));
		check(pigThrown > 1, "the launch shockwave should throw the pig, it moved " + pigThrown);
		command(singleplayer, "kill @e[type=minecraft:pig]");
		context.waitTicks(60);

		// A quick tap still jumps.
		camera(context, CameraType.FIRST_PERSON);
		command(singleplayer, "execute as @p at @p run tp @s ~10 ~ ~ 0 0");
		context.waitTicks(40);
		double groundY = context.computeOnClient(client -> client.player.getY());
		context.getInput().holdKeyFor(options -> options.keyJump, 2);
		double highest = groundY;
		for (int tick = 0; tick < 8; tick++) {
			context.waitTick();
			highest = Math.max(highest, context.computeOnClient(client -> client.player.getY()));
		}
		check(highest - groundY > 0.5, "tapping jump should still jump, rose " + (highest - groundY));
		context.waitTicks(20);

		// In the air, a tap flicks the player where they look; holding flicks again and again.
		command(singleplayer, "execute as @p at @p run tp @s ~ ~12 ~ 0 0");
		context.waitTicks(2);
		Vec3 airStart = context.computeOnClient(client -> client.player.position());
		context.getInput().holdKeyFor(options -> options.keyJump, 2);
		context.waitTicks(2);
		double oneFlick = context.computeOnClient(client -> client.player.position()).subtract(airStart).horizontalDistance();
		check(oneFlick > 1, "a tap in the air should flick the player forward, moved " + oneFlick);
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(8); // long enough apart that the next press isn't a double-tap (which floats)
		context.getInput().holdKey(options -> options.keyJump);
		context.waitTicks(12);
		context.takeScreenshot("air-flick");
		context.waitTicks(12);
		context.getInput().releaseKey(options -> options.keyJump);
		double held = context.computeOnClient(client -> client.player.position()).subtract(airStart).horizontalDistance();
		check(held > 12, "holding jump in the air should keep flicking the player along, moved " + held);
		camera(context, CameraType.FIRST_PERSON);
		context.waitTicks(60);
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
		float healthBeforeDrop = singleplayer.getServer().computeOnServer(server -> player(server).getHealth());
		context.getInput().releaseKey(DekuModClient.FLOAT_KEY);
		context.waitTicks(30);
		double dropped = startY - context.computeOnClient(client -> client.player.getY());
		check(dropped > 3, "releasing float should drop the player, dropped " + dropped);
		float healthAfterDrop = singleplayer.getServer().computeOnServer(server -> player(server).getHealth());
		check(healthAfterDrop >= healthBeforeDrop, "the player shouldn't take fall damage, health went " + healthBeforeDrop + " -> " + healthAfterDrop);

		// Double-tap and hold jump also floats: the jump lifts the player off the ground and they stay up.
		double groundY = context.computeOnClient(client -> client.player.getY());
		context.getInput().holdKeyFor(options -> options.keyJump, 2);
		context.waitTicks(2);
		context.getInput().holdKey(options -> options.keyJump);
		context.waitTicks(30);
		double floating = context.computeOnClient(client -> client.player.getY()) - groundY;
		check(floating > 0.5, "double-tapping and holding jump should float the player, height " + floating);
		context.getInput().releaseKey(options -> options.keyJump);
		context.waitTicks(30);
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

	private static void explosion(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		command(singleplayer, "kill @e[type=!minecraft:player]");
		command(singleplayer, "effect give @p minecraft:resistance 600 4 true");
		command(singleplayer, "execute as @p at @p run tp @s ~40 ~ ~ 0 0");
		selectSlot(context, 1);

		// Tap right-click: a moment to load, then one big AP Shot at a golem 8 blocks ahead.
		command(singleplayer, "execute at @p run summon minecraft:iron_golem ~ ~ ~8 {NoAI:1b}");
		command(singleplayer, "execute as @p at @p anchored eyes run tp @s ~ ~ ~ facing entity @e[type=minecraft:iron_golem,limit=1] eyes");
		context.waitTicks(3);
		context.getInput().holdKeyFor(options -> options.keyUse, 2);
		context.waitTicks(11);
		context.takeScreenshot("ap-shot-big");
		float golemHealth = singleplayer.getServer().computeOnServer(server -> golem(server).getHealth());
		check(golemHealth < 100, "a big AP Shot should hurt the golem, health was " + golemHealth);
		context.waitTicks(50);
		context.takeScreenshot("ap-shot-smoke");
		command(singleplayer, "kill @e[type=minecraft:iron_golem]");

		// Hold right-click: rapid fire at untouched ground ahead blasts a hole in it.
		command(singleplayer, "execute as @p at @p run tp @s ~30 ~ ~ 0 35");
		context.waitTicks(3);
		BlockPos aimed = singleplayer.getServer().computeOnServer(server -> BlockPos.containing(
			Aim.trace(player(server), 48).getLocation().add(player(server).getLookAngle().scale(0.1))));
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(20);
		context.takeScreenshot("ap-rapid-fire");
		context.getInput().releaseKey(options -> options.keyUse);
		boolean blasted = singleplayer.getServer().computeOnServer(server -> server.overworld().getBlockState(aimed).isAir());
		check(blasted, "rapid fire should blast away the ground it hits at " + aimed);

		// Double-tap and hold jump to fly where you look.
		command(singleplayer, "execute as @p at @p run tp @s ~ ~ ~ 0 -20");
		context.waitTicks(3);
		Vec3 flightStart = context.computeOnClient(client -> client.player.position());
		context.getInput().holdKeyFor(options -> options.keyJump, 2);
		context.waitTicks(2);
		context.getInput().holdKey(options -> options.keyJump);
		context.waitTicks(3);
		check(context.computeOnClient(client -> ExplosionClient.flying()), "double-tapping jump should start explosion flight");
		// With no movement keys the player hovers in place.
		Vec3 hoverStart = context.computeOnClient(client -> client.player.position());
		context.waitTicks(10);
		double hoverDrift = context.computeOnClient(client -> client.player.position().distanceTo(hoverStart));
		check(hoverDrift < 1, "explosion flight should hover without movement keys, drifted " + hoverDrift);
		check(!context.computeOnClient(client -> ExplosionClient.flightMoving()), "hovering in place should leave the player standing, not lying flat");
		context.takeScreenshot("explosion-hover");
		// Holding W blasts them toward where they're looking.
		context.getInput().holdKey(options -> options.keyUp);
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.waitTicks(15);
		check(context.computeOnClient(client -> ExplosionClient.flightMoving()), "flying somewhere should lay the player flat");
		context.takeScreenshot("explosion-flight");
		double flown = context.computeOnClient(client -> client.player.position().distanceTo(flightStart));
		check(flown > 8, "explosion flight should carry the player, moved " + flown);
		context.getInput().releaseKey(options -> options.keyUp);
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.getInput().releaseKey(options -> options.keyJump);
		context.waitTicks(40);

		// Hold V: spiral toward the aim inside a spinning cloud, then let go to explode.
		command(singleplayer, "execute as @p at @p run tp @s ~ ~ ~ 0 0");
		context.waitTicks(3);
		Vec3 howitzerStart = context.computeOnClient(client -> client.player.position());
		command(singleplayer, "execute at @p run summon minecraft:pig ~60 ~ ~");
		context.getInput().holdKey(DekuModClient.SMASH_KEY);
		context.waitTicks(65);
		check(context.computeOnClient(client -> ExplosionClient.spinning()), "holding V with Explosion should start Howitzer Impact");
		check(context.computeOnClient(client -> ExplosionClient.howitzerCharge()) >= 90, "holding V for three seconds should fully charge the Howitzer");
		context.takeScreenshot("howitzer-spin");
		double spiralled = context.computeOnClient(client -> client.player.position().distanceTo(howitzerStart));
		check(spiralled > 5, "Howitzer Impact should carry the player forward, moved " + spiralled);
		Vec3 pigStart = singleplayer.getServer().computeOnServer(server ->
			server.overworld().getEntities(EntityTypes.PIG, pig -> true).getFirst().position());
		context.getInput().releaseKey(DekuModClient.SMASH_KEY);
		context.waitTicks(2);
		context.takeScreenshot("howitzer-impact");
		check(context.computeOnClient(client -> ScreenShake.active()), "a Howitzer Impact right next to the player should shake the screen");
		float wobble = 0;
		for (int i = 0; i < 4; i++) {
			wobble += context.computeOnClient(client -> Math.abs(ScreenShake.yaw()) + Math.abs(ScreenShake.pitch()));
			context.waitTicks(1);
		}
		check(wobble > 0, "the shaking screen should twist the camera, total twist " + wobble);
		context.waitTicks(10);
		context.takeScreenshot("howitzer-shockwave");
		double pigThrown = singleplayer.getServer().computeOnServer(server ->
			server.overworld().getEntities(EntityTypes.PIG, pig -> true).getFirst().position().distanceTo(pigStart));
		check(pigThrown > 3, "the Howitzer shockwave should throw a pig 60 blocks away, it moved " + pigThrown);
		command(singleplayer, "kill @e[type=minecraft:pig]");
		context.waitTicks(30);
		context.takeScreenshot("howitzer-column");
		check(!context.computeOnClient(client -> ScreenShake.active()), "the screen shake should settle within a couple of seconds");
		context.waitTicks(60);
		context.takeScreenshot("howitzer-smoke");

		// Hold C: arms up in a cross, charging; let go and the ground ahead erupts and takes out a husk.
		command(singleplayer, "execute as @p at @p run tp @s ~ ~ ~ 0 0");
		command(singleplayer, "execute at @p run summon minecraft:husk ~ ~ ~6 {NoAI:1b}");
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.waitTicks(3);
		context.getInput().holdKey(DekuModClient.COWLING_KEY);
		context.waitTicks(30);
		check(context.computeOnClient(client -> ExplosionClient.armsCrossed()), "holding C should cross the arms and charge");
		context.takeScreenshot("ground-blast-windup");
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.getInput().releaseKey(DekuModClient.COWLING_KEY);
		context.waitTicks(8);
		context.takeScreenshot("ground-blast");
		boolean huskDown = singleplayer.getServer().computeOnServer(server -> server.overworld()
			.getEntities(EntityTypes.HUSK, husk -> husk.isAlive() && husk.getHealth() >= husk.getMaxHealth()).isEmpty());
		check(huskDown, "the ground blast should hurt the husk in front");
		camera(context, CameraType.FIRST_PERSON);
		clusterBomb(context, singleplayer);
		crater(context, singleplayer);
		explosionCowling(context, singleplayer);
	}

	private static void clusterBomb(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Hold X: a red fireball grows in front of the player; let go and it flies to the crosshair spot and
		// lands as a nuke, with a crater, a mushroom cloud, a shaking screen and a husk caught in it.
		command(singleplayer, "kill @e[type=!minecraft:player]");
		command(singleplayer, "execute as @p run tp @s 1400 -59 1400 0 3");
		context.waitTicks(40);
		Vec3 hit = singleplayer.getServer().computeOnServer(server -> Aim.trace(player(server), 150).getLocation());
		command(singleplayer, "summon minecraft:husk " + hit.x + " " + hit.y + " " + (hit.z + 5) + " {NoAI:1b}");
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.getInput().holdKey(DekuModClient.CLUSTER_KEY);
		context.waitTicks(30);
		context.takeScreenshot("fireball-charging");
		context.waitTicks(35);
		check(context.computeOnClient(client -> ExplosionClient.fireballCharge()) >= 90, "holding X for three seconds should fully grow the fireball");
		context.takeScreenshot("fireball-full");
		context.getInput().releaseKey(DekuModClient.CLUSTER_KEY);
		camera(context, CameraType.FIRST_PERSON); // looking down the flight path, the ball is dead ahead
		context.waitTicks(2);
		context.takeScreenshot("fireball-flight-start");
		context.waitTicks(3);
		context.takeScreenshot("fireball-flight");
		context.waitTicks(3);
		context.takeScreenshot("fireball-midair");
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(11);
		check(context.computeOnClient(client -> ScreenShake.active()), "the nuke landing 30 blocks away should shake the screen");
		context.takeScreenshot("nuke-flash");
		command(singleplayer, "execute as @p at @p run tp @s ~ ~ ~ 0 -30");
		context.waitTicks(30);
		context.takeScreenshot("nuke-cloud");
		// From a hundred blocks back, the whole cloud shows as a stem with a wide cap.
		command(singleplayer, "execute as @p run tp @s 1400 -59 1350 0 -30");
		context.waitTicks(40);
		context.takeScreenshot("nuke-cloud-mid");
		command(singleplayer, "execute as @p run tp @s 1400 -59 1290 0 -22");
		context.waitTicks(30);
		context.takeScreenshot("nuke-cloud-far");
		context.waitTicks(60);
		context.takeScreenshot("nuke-cloud-late");
		boolean caught = singleplayer.getServer().computeOnServer(server -> server.overworld()
			.getEntities(EntityTypes.HUSK, husk -> husk.isAlive() && husk.getHealth() >= husk.getMaxHealth()).isEmpty());
		check(caught, "the nuke should hurt the husk at the target");
		int dug = singleplayer.getServer().computeOnServer(server -> {
			int count = 0;
			BlockPos at = BlockPos.containing(hit);
			for (BlockPos pos : BlockPos.betweenClosed(at.offset(-20, -4, -20), at.offset(20, 0, 20))) {
				count += server.overworld().getBlockState(pos).isAir() ? 1 : 0;
			}
			return count;
		});
		check(dug > 800, "the nuke should leave a huge crater, only " + dug + " blocks cleared");
		camera(context, CameraType.FIRST_PERSON);
	}

	private static void crater(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// A big AP Shot into the ground leaves a crater as big as its fireball: nothing breakable left inside.
		// Fresh ground far from every earlier blast, so there's grass left to scorch.
		command(singleplayer, "execute as @p run tp @s 400 -59 400 0 40");
		context.waitTicks(30);
		Vec3 hit = singleplayer.getServer().computeOnServer(server -> Aim.trace(player(server), 48).getLocation());
		context.getInput().holdKeyFor(options -> options.keyUse, 2);
		context.waitTicks(14);
		int left = singleplayer.getServer().computeOnServer(server -> {
			int count = 0;
			for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(hit).offset(-5, -5, -5), BlockPos.containing(hit).offset(5, 5, 5))) {
				BlockState state = server.overworld().getBlockState(pos);
				boolean breakable = !state.isAir() && !state.is(Blocks.FIRE) && state.getDestroySpeed(server.overworld(), pos) >= 0;
				count += breakable && Vec3.atCenterOf(pos).distanceTo(hit) < 5 ? 1 : 0;
			}
			return count;
		});
		check(left == 0, "the AP Shot crater should be cleared out, " + left + " blocks left inside");
		int scorched = singleplayer.getServer().computeOnServer(server -> {
			int count = 0;
			for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(hit).offset(-11, -11, -11), BlockPos.containing(hit).offset(11, 11, 11))) {
				BlockState state = server.overworld().getBlockState(pos);
				count += state.is(Blocks.BLACKSTONE) || state.is(Blocks.COARSE_DIRT) ? 1 : 0;
			}
			return count;
		});
		check(scorched > 0, "the AP Shot crater's rim should be scorched, found " + scorched + " burnt blocks");
		context.takeScreenshot("crater-scorched");
	}

	private static void explosionCowling(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Z lights the Explosion Cowling, and a big AP Shot then digs a bigger crater than the same shot without it.
		int plain = dugBySmallShot(context, singleplayer, 600);
		context.getInput().pressKey(DekuModClient.SMOKESCREEN_KEY);
		context.waitTicks(5);
		check(context.computeOnClient(client -> ExplosionCowlingClient.active()), "pressing Z with Explosion should light the Cowling");
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.waitTicks(10);
		context.takeScreenshot("explosion-cowling");
		camera(context, CameraType.FIRST_PERSON);
		int boosted = dugBySmallShot(context, singleplayer, 800);
		check(boosted > plain, "the Cowling should make blasts bigger, dug " + plain + " blocks without it and " + boosted + " with it");
		context.getInput().pressKey(DekuModClient.SMOKESCREEN_KEY);
		context.waitTicks(5);
		check(!context.computeOnClient(client -> ExplosionCowlingClient.active()), "pressing Z again should put the Cowling out");
	}

	/** Fires a big AP Shot at fresh ground and counts the ground blocks it removed. */
	private static int dugBySmallShot(ClientGameTestContext context, TestSingleplayerContext singleplayer, int x) {
		command(singleplayer, "execute as @p run tp @s " + x + " -59 400 0 40");
		context.waitTicks(30);
		Vec3 hit = singleplayer.getServer().computeOnServer(server -> Aim.trace(player(server), 48).getLocation());
		context.getInput().holdKeyFor(options -> options.keyUse, 2);
		context.waitTicks(20);
		return singleplayer.getServer().computeOnServer(server -> {
			int count = 0;
			BlockPos at = BlockPos.containing(hit);
			for (BlockPos pos : BlockPos.betweenClosed(at.offset(-14, -4, -14), at.offset(14, 0, 14))) {
				count += server.overworld().getBlockState(pos).isAir() ? 1 : 0;
			}
			return count;
		});
	}

	private static void fullPowerSmashTunnel(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// A 100% Smash, released instantly, blasts through a stone wall 5 blocks ahead.
		command(singleplayer, "execute as @p at @p run tp @s ~40 ~ ~ 0 0");
		command(singleplayer, "execute at @p run fill ~-2 ~ ~5 ~2 ~4 ~5 minecraft:stone");
		command(singleplayer, "execute at @p run fill ~-2 ~ ~40 ~2 ~4 ~40 minecraft:stone");
		selectSlot(context, 0);
		DekuSettings.set(DekuSettings.get().withPunchPower(100).withPunchChargeSeconds(0));
		context.waitTicks(3);
		BlockPos wallCenter = singleplayer.getServer().computeOnServer(server -> BlockPos.containing(player(server).getEyePosition()).south(5));
		context.getInput().holdKeyFor(DekuModClient.SMASH_KEY, 2);
		context.waitTicks(5);
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(2);
		context.takeScreenshot("smash-tunnel");
		boolean holed = singleplayer.getServer().computeOnServer(server -> server.overworld().getBlockState(wallCenter).isAir());
		check(holed, "a 100% Smash should blast through the wall at " + wallCenter);
		context.waitTicks(10);
		boolean farHoled = singleplayer.getServer().computeOnServer(server -> server.overworld().getBlockState(wallCenter.south(35)).isAir());
		check(farHoled, "a 100% Smash should tunnel through a wall 40 blocks away too");

		// With the max range slider turned down to 20 blocks, the same punch stops well short of the far wall.
		command(singleplayer, "execute at @p run fill ~-2 ~ ~40 ~2 ~4 ~40 minecraft:stone");
		DekuSettings.set(DekuSettings.get().withSmashMaxRange(20));
		context.waitTicks(60);
		context.getInput().holdKeyFor(DekuModClient.SMASH_KEY, 2);
		context.waitTicks(15);
		boolean farStillThere = singleplayer.getServer().computeOnServer(server -> !server.overworld().getBlockState(wallCenter.south(35)).isAir());
		check(farStillThere, "a Smash limited to 20 blocks shouldn't reach a wall 40 blocks away");
		DekuSettings.set(DekuSettings.get().withSmashMaxRange(DekuSettings.MAX_SMASH_RANGE));
		camera(context, CameraType.FIRST_PERSON);
	}

	private static void mountainSmash(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Find real mountains, stand 40 blocks off on the lowest side, and punch into the peak.
		command(singleplayer, "effect give @p minecraft:resistance 600 4 true");
		command(singleplayer, "time set noon");
		selectSlot(context, 0);
		BlockPos peak = singleplayer.getServer().computeOnServer(server -> {
			var found = server.overworld().findClosestBiome3d(biome -> biome.is(Biomes.JAGGED_PEAKS) || biome.is(Biomes.STONY_PEAKS)
				|| biome.is(Biomes.FROZEN_PEAKS), player(server).blockPosition(), 6400, 32, 64);
			return found == null ? null : found.getFirst();
		});
		check(peak != null, "there should be mountains to punch");
		BlockPos stand = singleplayer.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos best = null;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos spot = peak.relative(side, 40);
				BlockPos surface = spot.atY(surfaceY(level, spot));
				if (best == null || surface.getY() < best.getY()) {
					best = surface;
				}
			}
			return best;
		});
		int peakTop = singleplayer.getServer().computeOnServer(server -> surfaceY(server.overworld(), peak));
		Vec3 target = new Vec3(peak.getX() + 0.5, peakTop - 8, peak.getZ() + 0.5);
		command(singleplayer, "tp @p " + stand.getX() + " " + stand.getY() + " " + stand.getZ());
		command(singleplayer, "execute as @p at @p anchored eyes run tp @s ~ ~ ~ facing " + target.x + " " + target.y + " " + target.z);
		singleplayer.getClientLevel().waitForChunksRender();
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(20);
		context.takeScreenshot("mountain-before");

		Vec3 eye = singleplayer.getServer().computeOnServer(server -> player(server).getEyePosition());
		int solidBefore = solidAlong(singleplayer, eye, target);
		check(solidBefore > 5, "the punch line should run through the mountain, only " + solidBefore + " solid blocks");
		context.getInput().holdKeyFor(DekuModClient.SMASH_KEY, 2);
		context.waitTicks(8);
		context.takeScreenshot("mountain-smash");
		context.waitTicks(30);
		context.takeScreenshot("mountain-tunnel");
		int solidAfter = solidAlong(singleplayer, eye, target);
		check(solidAfter < solidBefore / 3, "a 100% Smash should tunnel through the mountain: " + solidBefore + " -> " + solidAfter);
		camera(context, CameraType.FIRST_PERSON);
	}

	/** Top of the ground at a spot, generating its chunk first (unloaded chunks report the bottom of the world). */
	private static int surfaceY(ServerLevel level, BlockPos pos) {
		level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
	}

	/** Solid blocks on the straight line from one point to another, sampled every half block. */
	private static int solidAlong(TestSingleplayerContext singleplayer, Vec3 from, Vec3 to) {
		return singleplayer.getServer().computeOnServer(server -> {
			Set<BlockPos> solid = new HashSet<>();
			double length = from.distanceTo(to);
			for (double distance = 2; distance <= length; distance += 0.5) {
				BlockPos pos = BlockPos.containing(from.add(to.subtract(from).normalize().scale(distance)));
				if (!server.overworld().getBlockState(pos).isAir()) {
					solid.add(pos);
				}
			}
			return solid.size();
		});
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
