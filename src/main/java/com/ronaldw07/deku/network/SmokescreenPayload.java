package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client released a Smokescreen. */
public record SmokescreenPayload() implements CustomPacketPayload {
	public static final SmokescreenPayload INSTANCE = new SmokescreenPayload();
	public static final Type<SmokescreenPayload> TYPE = new Type<>(DekuMod.id("smokescreen"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SmokescreenPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<SmokescreenPayload> type() {
		return TYPE;
	}
}
