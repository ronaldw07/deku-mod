package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client lashed out with Blackwhip. */
public record BlackwhipPayload() implements CustomPacketPayload {
	public static final BlackwhipPayload INSTANCE = new BlackwhipPayload();
	public static final Type<BlackwhipPayload> TYPE = new Type<>(DekuMod.id("blackwhip"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BlackwhipPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<BlackwhipPayload> type() {
		return TYPE;
	}
}
