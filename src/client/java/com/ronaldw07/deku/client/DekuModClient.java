package com.ronaldw07.deku.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ronaldw07.deku.DekuMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class DekuModClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(DekuMod.id("abilities"));

	public static final KeyMapping SETTINGS_KEY = KeyMappingHelper.registerKeyMapping(
		new KeyMapping("key.deku.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));

	@Override
	public void onInitializeClient() {
		DekuSettings.load();
		ClientTickEvents.END_CLIENT_TICK.register(DekuModClient::tick);
	}

	private static void tick(Minecraft client) {
		while (SETTINGS_KEY.consumeClick()) {
			client.gui.setScreen(new SettingsScreen());
		}
	}
}
