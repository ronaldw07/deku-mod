package com.ronaldw07.deku;

import com.ronaldw07.deku.network.BlackwhipFxPayload;
import com.ronaldw07.deku.network.BlackwhipPayload;
import com.ronaldw07.deku.network.CowlingPayload;
import com.ronaldw07.deku.network.DecayPayload;
import com.ronaldw07.deku.network.DelawarePayload;
import com.ronaldw07.deku.network.DangerPayload;
import com.ronaldw07.deku.network.DangerSenseTogglePayload;
import com.ronaldw07.deku.network.ExplosionCowlingFxPayload;
import com.ronaldw07.deku.network.ExplosionCowlingPayload;
import com.ronaldw07.deku.network.FireballChargeFxPayload;
import com.ronaldw07.deku.network.FireballChargePayload;
import com.ronaldw07.deku.network.DomainPayload;
import com.ronaldw07.deku.network.JujutsuPayload;
import com.ronaldw07.deku.network.SlashFxPayload;
import com.ronaldw07.deku.network.ExplosionFxPayload;
import com.ronaldw07.deku.network.FireballFlightPayload;
import com.ronaldw07.deku.network.ExplosionPayload;
import com.ronaldw07.deku.network.FloatPayload;
import com.ronaldw07.deku.network.GearshiftPayload;
import com.ronaldw07.deku.network.HalfColdHalfHotPayload;
import com.ronaldw07.deku.network.LaunchPayload;
import com.ronaldw07.deku.network.ManchesterPayload;
import com.ronaldw07.deku.network.ShootStylePayload;
import com.ronaldw07.deku.network.SmashFxPayload;
import com.ronaldw07.deku.network.SmashPayload;
import com.ronaldw07.deku.network.SmokescreenPayload;
import com.ronaldw07.deku.network.TornadoFxPayload;
import com.ronaldw07.deku.network.UnitedStatesSmashPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerPlayer;
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
			(payload, context) -> Smash.perform(context.player(), payload.percent(), payload.maxRange()));
		PayloadTypeRegistry.clientboundPlay().register(SmashFxPayload.TYPE, SmashFxPayload.CODEC);

		PayloadTypeRegistry.serverboundPlay().register(SmokescreenPayload.TYPE, SmokescreenPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SmokescreenPayload.TYPE,
			(payload, context) -> {
				if (payload.holding()) {
					Smokescreen.deploy(context.player());
				} else {
					Smokescreen.release(context.player());
				}
			});
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
		PayloadTypeRegistry.clientboundPlay().register(FireballFlightPayload.TYPE, FireballFlightPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Bakugo::tick);
		ServerTickEvents.END_SERVER_TICK.register(Blasts::tick);

		PayloadTypeRegistry.serverboundPlay().register(ShootStylePayload.TYPE, ShootStylePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ShootStylePayload.TYPE,
			(payload, context) -> ShootStyle.kick(context.player(), payload.percent()));

		PayloadTypeRegistry.serverboundPlay().register(DelawarePayload.TYPE, DelawarePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DelawarePayload.TYPE,
			(payload, context) -> Delaware.flick(context.player(), payload.percent()));

		PayloadTypeRegistry.serverboundPlay().register(ManchesterPayload.TYPE, ManchesterPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ManchesterPayload.TYPE,
			(payload, context) -> Manchester.slam(context.player(), payload.percent()));

		PayloadTypeRegistry.serverboundPlay().register(GearshiftPayload.TYPE, GearshiftPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GearshiftPayload.TYPE,
			(payload, context) -> Gearshift.apply(context.player(), payload.gear()));

		PayloadTypeRegistry.serverboundPlay().register(DecayPayload.TYPE, DecayPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DecayPayload.TYPE,
			(payload, context) -> Decay.handle(context.player(), payload.move(), payload.charge()));
		ServerTickEvents.END_SERVER_TICK.register(Decay::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Decay.forget(handler.player));

		PayloadTypeRegistry.serverboundPlay().register(UnitedStatesSmashPayload.TYPE, UnitedStatesSmashPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(UnitedStatesSmashPayload.TYPE,
			(payload, context) -> UnitedStatesSmash.land(context.player(), payload.percent()));
		PayloadTypeRegistry.clientboundPlay().register(TornadoFxPayload.TYPE, TornadoFxPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(UnitedStatesSmash::tick);

		PayloadTypeRegistry.serverboundPlay().register(HalfColdHalfHotPayload.TYPE, HalfColdHalfHotPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(HalfColdHalfHotPayload.TYPE,
			(payload, context) -> HalfColdHalfHot.handle(context.player(), payload.move(), payload.active()));
		ServerTickEvents.END_SERVER_TICK.register(HalfColdHalfHot::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HalfColdHalfHot.forget(handler.player));

		ServerLifecycleEvents.SERVER_STARTED.register(DekuMod::keepInventory);

		// Quirk users land on their feet: no fall damage for players, ever.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof Player && source.is(DamageTypeTags.IS_FALL))
				&& !(entity instanceof ServerPlayer player && Gojo.blocks(player, source)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Bakugo.forget(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Smokescreen.forget(handler.player));

		PayloadTypeRegistry.serverboundPlay().register(FireballChargePayload.TYPE, FireballChargePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FireballChargeFxPayload.TYPE, FireballChargeFxPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FireballChargePayload.TYPE,
			(payload, context) -> BlastFx.showFireballCharge(context.player(), payload.charge()));

		PayloadTypeRegistry.serverboundPlay().register(ExplosionCowlingPayload.TYPE, ExplosionCowlingPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ExplosionCowlingFxPayload.TYPE, ExplosionCowlingFxPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ExplosionCowlingPayload.TYPE,
			(payload, context) -> ExplosionCowling.set(context.player(), payload.on()));
		ServerTickEvents.END_SERVER_TICK.register(ExplosionCowling::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ExplosionCowling.forget(handler.player));

		PayloadTypeRegistry.serverboundPlay().register(JujutsuPayload.TYPE, JujutsuPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(JujutsuPayload.TYPE,
			(payload, context) -> {
				Gojo.handle(context.player(), payload.move(), payload.active(), payload.charge());
				Sukuna.handle(context.player(), payload.move());
			});
		PayloadTypeRegistry.clientboundPlay().register(SlashFxPayload.TYPE, SlashFxPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DomainPayload.TYPE, DomainPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Gojo::tick);
		ServerTickEvents.END_SERVER_TICK.register(Sukuna::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Sukuna.forget(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Gojo.forget(handler.player));

		LOGGER.info("One For All loaded");
	}

	/** Heroes keep their gear: dying never drops the inventory, in any world the mod runs in. */
	private static void keepInventory(MinecraftServer server) {
		CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
		// The rule was renamed to keep_inventory; trying both covers either name, and the unknown one fails quietly.
		server.getCommands().performPrefixedCommand(source, "gamerule keep_inventory true");
		server.getCommands().performPrefixedCommand(source, "gamerule keepInventory true");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
