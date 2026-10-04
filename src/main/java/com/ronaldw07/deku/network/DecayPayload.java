package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client used a Decay move. Charge is how far the wave was wound up, 0-100. */
public record DecayPayload(Move move, int charge) implements CustomPacketPayload {
	public enum Move {
		TOUCH, WAVE
	}

	public static final Type<DecayPayload> TYPE = new Type<>(DekuMod.id("decay"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DecayPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.idMapper(i -> Move.values()[i], Move::ordinal), DecayPayload::move,
		ByteBufCodecs.VAR_INT, DecayPayload::charge,
		DecayPayload::new);

	@Override
	public Type<DecayPayload> type() {
		return TYPE;
	}
}
