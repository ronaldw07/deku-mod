package com.ronaldw07.deku.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ronaldw07.deku.DekuMod;
import com.ronaldw07.deku.DekuParticles;
import com.ronaldw07.deku.network.BlackwhipFxPayload;
import com.ronaldw07.deku.network.BlackwhipPayload;
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
import org.lwjgl.glfw.GLFW;

public class DekuModClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(DekuMod.id("abilities"));

	public static final KeyMapping COWLING_KEY = register("key.deku.full_cowling", GLFW.GLFW_KEY_C);
	public static final KeyMapping SMASH_KEY = register("key.deku.smash", GLFW.GLFW_KEY_V);
	public static final KeyMapping SMOKESCREEN_KEY = register("key.deku.smokescreen", GLFW.GLFW_KEY_Z);
	public static final KeyMapping FLOAT_KEY = register("key.deku.float", GLFW.GLFW_KEY_R);
	public static final KeyMapping BLACKWHIP_KEY = register("key.deku.blackwhip", GLFW.GLFW_KEY_B);
	public static final KeyMapping SETTINGS_KEY = register("key.deku.settings", GLFW.GLFW_KEY_K);

	@Override
	public void onInitializeClient() {
		DekuSettings.load();
		ClientTickEvents.END_CLIENT_TICK.register(DekuModClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, DekuMod.id("power"), PowerHud::extract);
		LevelRenderEvents.COLLECT_SUBMITS.register(CowlingLightning::render);
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

		while (SMOKESCREEN_KEY.consumeClick()) {
			if (ClientPlayNetworking.canSend(SmokescreenPayload.TYPE)) {
				ClientPlayNetworking.send(SmokescreenPayload.INSTANCE);
			}
		}

		FloatClient.tick(client.player, FLOAT_KEY.isDown());

		while (BLACKWHIP_KEY.consumeClick()) {
			if (ClientPlayNetworking.canSend(BlackwhipPayload.TYPE)) {
				ClientPlayNetworking.send(BlackwhipPayload.INSTANCE);
			}
		}
	}
}
