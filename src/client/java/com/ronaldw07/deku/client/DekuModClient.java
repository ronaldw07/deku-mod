package com.ronaldw07.deku.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ronaldw07.deku.DekuItems;
import com.ronaldw07.deku.DekuMod;
import com.ronaldw07.deku.DekuParticles;
import com.ronaldw07.deku.HeroNotebookItem;
import com.ronaldw07.deku.network.BlackwhipFxPayload;
import com.ronaldw07.deku.network.BlackwhipPayload;
import com.ronaldw07.deku.network.DangerPayload;
import com.ronaldw07.deku.network.SmashFxPayload;
import com.ronaldw07.deku.network.SmokescreenPayload;
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
		ClientPlayNetworking.registerGlobalReceiver(DangerPayload.TYPE, (payload, context) -> DangerSenseClient.receive(payload, context.player()));
		LevelRenderEvents.COLLECT_SUBMITS.register(CowlingLightning::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(CowlingAura::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(SmashLightning::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(BlackwhipTendrils::render);
		ClientPlayNetworking.registerGlobalReceiver(BlackwhipFxPayload.TYPE, (payload, context) -> BlackwhipTendrils.add(payload));
		ClientPlayNetworking.registerGlobalReceiver(SmashFxPayload.TYPE, (payload, context) -> SmashLightning.add(payload));
		ParticleProviderRegistry.getInstance().register(DekuParticles.PURPLE_SMOKE,
			sprites -> (options, level, x, y, z, xa, ya, za, random) -> new PurpleSmokeParticle(level, x, y, z, xa, ya, za, sprites.get(random)));
	}

	private static KeyMapping register(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYSYM, key, CATEGORY));
	}

	private static void tick(Minecraft client) {
		while (SETTINGS_KEY.consumeClick()) {
			client.gui.setScreen(new SettingsScreen());
		}

		// Keys are read once here, then go to whichever quirk item is in hand.
		LocalPlayer player = client.player;
		boolean oneForAll = player != null && DekuItems.isHolding(player, DekuItems.ONE_FOR_ALL);
		int cowlingClicks = countClicks(COWLING_KEY);
		int smashClicks = countClicks(SMASH_KEY);
		int smokescreenClicks = countClicks(SMOKESCREEN_KEY);
		int blackwhipClicks = countClicks(BLACKWHIP_KEY);

		FullCowlingClient.tick(player, oneForAll && cowlingClicks % 2 == 1, oneForAll);
		SmashClient.tick(player, oneForAll && SMASH_KEY.isDown(), oneForAll && smashClicks > 0);
		if (oneForAll && smokescreenClicks > 0) {
			send(SmokescreenPayload.INSTANCE);
		}
		FloatClient.tick(player, oneForAll && FLOAT_KEY.isDown());
		if (oneForAll && blackwhipClicks > 0) {
			send(BlackwhipPayload.INSTANCE);
		}

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
