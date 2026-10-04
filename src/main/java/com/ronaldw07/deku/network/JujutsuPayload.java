package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client used a Gojo or Sukuna move. For toggles (Infinity), active says whether it is now on;
 * charge is how far a charged move (Red, Hollow Purple) was wound up, 0-100.
 */
public record JujutsuPayload(Move move, boolean active, int charge) implements CustomPacketPayload {
	public enum Move {
		BLUE, RED, PURPLE, INFINITY, DISMANTLE, CLEAVE, DOMAIN
	}

	public static final Type<JujutsuPayload> TYPE = new Type<>(DekuMod.id("jujutsu"));
	public static final StreamCodec<RegistryFriendlyByteBuf, JujutsuPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.idMapper(i -> Move.values()[i], Move::ordinal), JujutsuPayload::move,
		ByteBufCodecs.BOOL, JujutsuPayload::active,
		ByteBufCodecs.VAR_INT, JujutsuPayload::charge,
		JujutsuPayload::new);

	@Override
	public Type<JujutsuPayload> type() {
		return TYPE;
	}
}
