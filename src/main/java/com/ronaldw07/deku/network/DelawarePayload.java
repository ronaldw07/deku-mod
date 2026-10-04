package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client flicked a Delaware Smash at the given power. */
public record DelawarePayload(int percent) implements CustomPacketPayload {
	public static final Type<DelawarePayload> TYPE = new Type<>(DekuMod.id("delaware"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DelawarePayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, DelawarePayload::percent, DelawarePayload::new);

	@Override
	public Type<DelawarePayload> type() {
		return TYPE;
	}
}
