package com.ronaldw07.deku.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ronaldw07.deku.DekuItems;
import com.ronaldw07.deku.DekuMod;
import com.ronaldw07.deku.DekuParticles;
import com.ronaldw07.deku.HeroNotebookItem;
import com.ronaldw07.deku.network.BlackwhipFxPayload;
import com.ronaldw07.deku.network.BlackwhipPayload;
import com.ronaldw07.deku.network.DelawarePayload;
import com.ronaldw07.deku.network.DangerPayload;
import com.ronaldw07.deku.network.ExplosionFxPayload;
import com.ronaldw07.deku.network.SmashFxPayload;
import com.ronaldw07.deku.network.ShootStylePayload;
import com.ronaldw07.deku.network.SmokescreenPayload;
import com.ronaldw07.deku.network.TornadoFxPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.lwjgl.glfw.GLFW;

public class DekuModClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(DekuMod.id("abilities"));

	public static final KeyMapping COWLING_KEY = register("key.deku.full_cowling", GLFW.GLFW_KEY_C);
	public static final KeyMapping SMASH_KEY = register("key.deku.smash", GLFW.GLFW_KEY_V);
	public static final KeyMapping SMOKESCREEN_KEY = register("key.deku.smokescreen", GLFW.GLFW_KEY_Z);
	public static final KeyMapping FLOAT_KEY = register("key.deku.float", GLFW.GLFW_KEY_R);
	public static final KeyMapping BLACKWHIP_KEY = register("key.deku.blackwhip", GLFW.GLFW_KEY_B);
	public static final KeyMapping CLUSTER_KEY = register("key.deku.cluster", GLFW.GLFW_KEY_X);
	public static final KeyMapping MANCHESTER_KEY = register("key.deku.manchester", GLFW.GLFW_KEY_G);
	public static final KeyMapping GEARSHIFT_KEY = register("key.deku.gearshift", GLFW.GLFW_KEY_N);
	public static final KeyMapping DELAWARE_KEY = register("key.deku.delaware", GLFW.GLFW_KEY_Y);
	public static final KeyMapping US_SMASH_KEY = register("key.deku.us_smash", GLFW.GLFW_KEY_U);
	public static final KeyMapping DANGER_SENSE_KEY = register("key.deku.danger_sense", GLFW.GLFW_KEY_H);
	public static final KeyMapping SETTINGS_KEY = register("key.deku.settings", GLFW.GLFW_KEY_K);

	@Override
	public void onInitializeClient() {
		DekuSettings.load();
		HeroNotebookItem.opener = player ->
			Minecraft.getInstance().gui.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(HeroNotebook.pages())));
		ClientTickEvents.END_CLIENT_TICK.register(DekuModClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, DekuMod.id("power"), PowerHud::extract);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, DekuMod.id("danger_sense"), DangerSenseHud::extract);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, DekuMod.id("controls"), ControlsHud::extract);
		ClientPlayNetworking.registerGlobalReceiver(DangerPayload.TYPE, (payload, context) -> DangerSenseClient.receive(payload, context.player()));
		LevelRenderEvents.COLLECT_SUBMITS.register(CowlingLightning::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(CowlingAura::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(CowlingFx::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(FaJinFx::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(GearshiftClient::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(DecayClient::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(SmashLightning::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(BlackwhipTendrils::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(ExplosionFx::render);
		ClientPlayNetworking.registerGlobalReceiver(ExplosionFxPayload.TYPE, (payload, context) -> ExplosionFx.add(payload));
		ClientPlayNetworking.registerGlobalReceiver(BlackwhipFxPayload.TYPE, (payload, context) -> BlackwhipTendrils.add(payload));
		ClientPlayNetworking.registerGlobalReceiver(SmashFxPayload.TYPE, (payload, context) -> SmashLightning.add(payload));
		ClientPlayNetworking.registerGlobalReceiver(TornadoFxPayload.TYPE, (payload, context) -> TornadoFx.add(payload));
		ParticleProviderRegistry.getInstance().register(DekuParticles.PURPLE_SMOKE, sprites -> (options, level, x, y, z, xa, ya, za, random) ->
			new SmokePuffParticle(level, x, y, z, xa, ya, za, sprites.get(random), SmokePuffParticle.SMOKESCREEN));
		ParticleProviderRegistry.getInstance().register(DekuParticles.WHITE_SMOKE, sprites -> (options, level, x, y, z, xa, ya, za, random) ->
			new SmokePuffParticle(level, x, y, z, xa, ya, za, sprites.get(random), SmokePuffParticle.HOWITZER_CLOUD));
		ParticleProviderRegistry.getInstance().register(DekuParticles.SOOT_SMOKE, sprites -> (options, level, x, y, z, xa, ya, za, random) ->
			new SmokePuffParticle(level, x, y, z, xa, ya, za, sprites.get(random), SmokePuffParticle.SOOT));
		ParticleProviderRegistry.getInstance().register(DekuParticles.FIREBALL, sprites -> (options, level, x, y, z, xa, ya, za, random) ->
			new SmokePuffParticle(level, x, y, z, xa, ya, za, sprites.get(random), SmokePuffParticle.FIREBALL));
		ParticleProviderRegistry.getInstance().register(DekuParticles.ASH_FLAKE, sprites -> (options, level, x, y, z, xa, ya, za, random) ->
			new SmokePuffParticle(level, x, y, z, xa, ya, za, sprites.get(random), SmokePuffParticle.ASH));
	}

	private static final DoubleTapHold floatTap = new DoubleTapHold();
	private static final int SMOKESCREEN_POSE_TICKS = 12;
	private static final int WHIP_POSE_TICKS = 14;
	private static final int KICK_POSE_TICKS = 8;
	private static final int FLICK_POSE_TICKS = 6;

	private static KeyMapping register(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYSYM, key, CATEGORY));
	}

	private static void tick(Minecraft client) {
		while (SETTINGS_KEY.consumeClick()) {
			client.gui.setScreen(new SettingsScreen());
		}

		// Keys are read once here, then go to whichever quirk item is in hand.
		LocalPlayer player = client.player;
		if (player == null) {
			Cooldowns.reset();
		}
		Cooldowns.tick();
		Poses.tick();
		boolean oneForAll = player != null && DekuItems.isHolding(player, DekuItems.ONE_FOR_ALL);
		boolean explosion = player != null && DekuItems.isHolding(player, DekuItems.EXPLOSION);
		boolean decay = player != null && DekuItems.isHolding(player, DekuItems.DECAY);
		boolean halfColdHalfHot = player != null && DekuItems.isHolding(player, DekuItems.HALF_COLD_HALF_HOT);
		int cowlingClicks = countClicks(COWLING_KEY);
		int smashClicks = countClicks(SMASH_KEY);
		int smokescreenClicks = countClicks(SMOKESCREEN_KEY);
		int blackwhipClicks = countClicks(BLACKWHIP_KEY);
		int clusterClicks = countClicks(CLUSTER_KEY);
		int manchesterClicks = countClicks(MANCHESTER_KEY);
		int gearshiftClicks = countClicks(GEARSHIFT_KEY);
		int delawareClicks = countClicks(DELAWARE_KEY);
		int usSmashClicks = countClicks(US_SMASH_KEY);

		FullCowlingClient.tick(player, oneForAll && cowlingClicks % 2 == 1, oneForAll);
		SmashClient.tick(player, oneForAll && SMASH_KEY.isDown(), oneForAll && smashClicks > 0);
		if (oneForAll && smokescreenClicks > 0 && Cooldowns.ready(Cooldowns.Ability.SMOKESCREEN)) {
			send(SmokescreenPayload.INSTANCE);
			Cooldowns.start(Cooldowns.Ability.SMOKESCREEN);
			Poses.play(Poses.Pose.SMOKESCREEN, SMOKESCREEN_POSE_TICKS);
		}
		boolean floatTapped = floatTap.tick(client.options.keyJump.isDown(), oneForAll);
		FloatClient.tick(player, oneForAll && (FLOAT_KEY.isDown() || floatTapped), FLOAT_KEY.isDown());
		if (oneForAll && blackwhipClicks > 0 && Cooldowns.ready(Cooldowns.Ability.BLACKWHIP)) {
			send(BlackwhipPayload.INSTANCE);
			Cooldowns.start(Cooldowns.Ability.BLACKWHIP);
			Poses.play(Poses.Pose.WHIP, WHIP_POSE_TICKS);
		}
		// X is Shoot Style with One For All in hand, and the cluster bomb with Explosion.
		if (oneForAll && clusterClicks > 0 && Cooldowns.ready(Cooldowns.Ability.SHOOT_STYLE)) {
			send(new ShootStylePayload(DekuSettings.get().punchPower()));
			Cooldowns.start(Cooldowns.Ability.SHOOT_STYLE);
			Poses.play(Poses.Pose.KICK, KICK_POSE_TICKS);
		}
		if (oneForAll && delawareClicks > 0 && Cooldowns.ready(Cooldowns.Ability.DELAWARE)) {
			send(new DelawarePayload(DekuSettings.get().punchPower()));
			Cooldowns.start(Cooldowns.Ability.DELAWARE);
			Poses.play(Poses.Pose.AIM_RIGHT, FLICK_POSE_TICKS);
		}
		ManchesterClient.tick(player, oneForAll && manchesterClicks > 0);
		UnitedStatesClient.tick(player, oneForAll && usSmashClicks > 0);
		GearshiftClient.tick(player, oneForAll && gearshiftClicks % 2 == 1, oneForAll);

		ExplosionClient.tick(player, explosion, client.options.keyUse.isDown(), client.options.keyJump.isDown(),
			SMASH_KEY.isDown(), COWLING_KEY.isDown(), clusterClicks > 0);
		DecayClient.tick(player, decay, client.options.keyUse.isDown(), SMASH_KEY.isDown(), CLUSTER_KEY.isDown(),
			decay && cowlingClicks % 2 == 1);
		HalfColdHalfHotClient.tick(player, halfColdHalfHot, client.options.keyUse.isDown(), SMASH_KEY.isDown(),
			halfColdHalfHot && cowlingClicks > 0, halfColdHalfHot && clusterClicks > 0, client.options.keyJump.isDown());
		TornadoFx.tick(client.level);
		ExplosionFx.tick(client.level);
		FaJinFx.tick(player);

		DangerSenseClient.tick(player, countClicks(DANGER_SENSE_KEY) % 2 == 1);
	}

	private static int countClicks(KeyMapping key) {
		int clicks = 0;
		while (key.consumeClick()) {
			clicks++;
		}
		return clicks;
	}

	private static void send(CustomPacketPayload payload) {
		if (ClientPlayNetworking.canSend(payload.type())) {
			ClientPlayNetworking.send(payload);
		}
	}
}
