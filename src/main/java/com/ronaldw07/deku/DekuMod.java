package com.ronaldw07.deku;

import com.ronaldw07.deku.network.CowlingPayload;
import com.ronaldw07.deku.network.SmashPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DekuMod implements ModInitializer {
	public static final String MOD_ID = "deku";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		DekuSounds.init();

		PayloadTypeRegistry.serverboundPlay().register(CowlingPayload.TYPE, CowlingPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CowlingPayload.TYPE,
			(payload, context) -> FullCowling.apply(context.player(), payload.percent()));

		PayloadTypeRegistry.serverboundPlay().register(SmashPayload.TYPE, SmashPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SmashPayload.TYPE,
			(payload, context) -> Smash.perform(context.player(), payload.percent()));

		LOGGER.info("One For All loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
