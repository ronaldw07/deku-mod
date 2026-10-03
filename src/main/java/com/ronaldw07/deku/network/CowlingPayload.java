package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client tells the server its current Full Cowling power (0 = off). */
public record CowlingPayload(int percent) implements CustomPacketPayload {
	public static final Type<CowlingPayload> TYPE = new Type<>(DekuMod.id("cowling"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CowlingPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, CowlingPayload::percent, CowlingPayload::new);

	@Override
	public Type<CowlingPayload> type() {
		return TYPE;
	}
}
