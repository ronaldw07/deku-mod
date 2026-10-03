package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client started or stopped Float. */
public record FloatPayload(boolean active) implements CustomPacketPayload {
	public static final Type<FloatPayload> TYPE = new Type<>(DekuMod.id("float"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FloatPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.BOOL, FloatPayload::active, FloatPayload::new);

	@Override
	public Type<FloatPayload> type() {
		return TYPE;
	}
}
