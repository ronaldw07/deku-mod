package com.ronaldw07.deku;

import com.ronaldw07.deku.network.BlackwhipFxPayload;
import com.ronaldw07.deku.network.BlackwhipPayload;
import com.ronaldw07.deku.network.CowlingPayload;
import com.ronaldw07.deku.network.FloatPayload;
import com.ronaldw07.deku.network.SmashFxPayload;
import com.ronaldw07.deku.network.SmashPayload;
import com.ronaldw07.deku.network.SmokescreenPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
		DekuParticles.init();

		PayloadTypeRegistry.serverboundPlay().register(CowlingPayload.TYPE, CowlingPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CowlingPayload.TYPE,
			(payload, context) -> FullCowling.apply(context.player(), payload.percent()));

		PayloadTypeRegistry.serverboundPlay().register(SmashPayload.TYPE, SmashPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SmashPayload.TYPE,
			(payload, context) -> Smash.perform(context.player(), payload.percent()));
		PayloadTypeRegistry.clientboundPlay().register(SmashFxPayload.TYPE, SmashFxPayload.CODEC);

		PayloadTypeRegistry.serverboundPlay().register(SmokescreenPayload.TYPE, SmokescreenPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SmokescreenPayload.TYPE,
			(payload, context) -> Smokescreen.deploy(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(Smokescreen::tick);

		PayloadTypeRegistry.serverboundPlay().register(FloatPayload.TYPE, FloatPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FloatPayload.TYPE,
			(payload, context) -> FloatQuirk.apply(context.player(), payload.active()));

		PayloadTypeRegistry.serverboundPlay().register(BlackwhipPayload.TYPE, BlackwhipPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BlackwhipPayload.TYPE, (payload, context) -> Blackwhip.lash(context.player()));
		PayloadTypeRegistry.clientboundPlay().register(BlackwhipFxPayload.TYPE, BlackwhipFxPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Blackwhip::tick);

		LOGGER.info("One For All loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
