package com.ronaldw07.deku.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ronaldw07.deku.DekuMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class DekuModClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(DekuMod.id("abilities"));

	public static final KeyMapping COWLING_KEY = register("key.deku.full_cowling", GLFW.GLFW_KEY_C);
	public static final KeyMapping SMASH_KEY = register("key.deku.smash", GLFW.GLFW_KEY_V);
	public static final KeyMapping SETTINGS_KEY = register("key.deku.settings", GLFW.GLFW_KEY_K);

	@Override
	public void onInitializeClient() {
		DekuSettings.load();
		ClientTickEvents.END_CLIENT_TICK.register(DekuModClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, DekuMod.id("power"), PowerHud::extract);
		LevelRenderEvents.COLLECT_SUBMITS.register(CowlingLightning::render);
	}

	private static KeyMapping register(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYSYM, key, CATEGORY));
	}

	private static void tick(Minecraft client) {
		while (SETTINGS_KEY.consumeClick()) {
			client.gui.setScreen(new SettingsScreen());
		}

		boolean cowlingToggled = false;
		while (COWLING_KEY.consumeClick()) {
			cowlingToggled = !cowlingToggled;
		}
		FullCowlingClient.tick(client.player, cowlingToggled);

		boolean smashPressed = false;
		while (SMASH_KEY.consumeClick()) {
			smashPressed = true;
		}
		SmashClient.tick(client.player, SMASH_KEY.isDown(), smashPressed);
	}
}
