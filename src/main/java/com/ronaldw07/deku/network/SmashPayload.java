package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client threw a Smash punch charged to the given power. */
public record SmashPayload(int percent) implements CustomPacketPayload {
	public static final Type<SmashPayload> TYPE = new Type<>(DekuMod.id("smash"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SmashPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, SmashPayload::percent, SmashPayload::new);

	@Override
	public Type<SmashPayload> type() {
		return TYPE;
	}
}
