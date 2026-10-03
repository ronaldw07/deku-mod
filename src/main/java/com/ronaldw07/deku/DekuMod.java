package com.ronaldw07.deku;

import com.ronaldw07.deku.network.BlackwhipFxPayload;
import com.ronaldw07.deku.network.BlackwhipPayload;
import com.ronaldw07.deku.network.CowlingPayload;
import com.ronaldw07.deku.network.DangerPayload;
import com.ronaldw07.deku.network.DangerSenseTogglePayload;
import com.ronaldw07.deku.network.ExplosionFxPayload;
import com.ronaldw07.deku.network.ExplosionPayload;
import com.ronaldw07.deku.network.FloatPayload;
import com.ronaldw07.deku.network.LaunchPayload;
import com.ronaldw07.deku.network.SmashFxPayload;
import com.ronaldw07.deku.network.SmashPayload;
import com.ronaldw07.deku.network.SmokescreenPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DekuMod implements ModInitializer {
	public static final String MOD_ID = "deku";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		DekuSounds.init();
		DekuParticles.init();
		DekuItems.init();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> StarterKit.giveOnFirstJoin(handler.player));

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

		PayloadTypeRegistry.serverboundPlay().register(LaunchPayload.TYPE, LaunchPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(LaunchPayload.TYPE,
			(payload, context) -> Launch.perform(context.player(), payload.charge()));

		PayloadTypeRegistry.serverboundPlay().register(BlackwhipPayload.TYPE, BlackwhipPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BlackwhipPayload.TYPE, (payload, context) -> Blackwhip.lash(context.player()));
		PayloadTypeRegistry.clientboundPlay().register(BlackwhipFxPayload.TYPE, BlackwhipFxPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Blackwhip::tick);

		PayloadTypeRegistry.serverboundPlay().register(DangerSenseTogglePayload.TYPE, DangerSenseTogglePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DangerSenseTogglePayload.TYPE,
			(payload, context) -> DangerSense.setEnabled(context.player(), payload.enabled()));
		PayloadTypeRegistry.clientboundPlay().register(DangerPayload.TYPE, DangerPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(DangerSense::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DangerSense.setEnabled(handler.player, false));

		PayloadTypeRegistry.serverboundPlay().register(ExplosionPayload.TYPE, ExplosionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ExplosionPayload.TYPE,
			(payload, context) -> Bakugo.handle(context.player(), payload.move(), payload.active(), payload.charge()));
		PayloadTypeRegistry.clientboundPlay().register(ExplosionFxPayload.TYPE, ExplosionFxPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Bakugo::tick);
		ServerTickEvents.END_SERVER_TICK.register(Blasts::tick);

		// Quirk users land on their feet: no fall damage for players, ever.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof Player && source.is(DamageTypeTags.IS_FALL)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Bakugo.forget(handler.player));

		LOGGER.info("One For All loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
