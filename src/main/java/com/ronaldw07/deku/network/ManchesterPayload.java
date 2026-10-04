package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client's Manchester Smash axe kick hit the ground at the given power. */
public record ManchesterPayload(int percent) implements CustomPacketPayload {
	public static final Type<ManchesterPayload> TYPE = new Type<>(DekuMod.id("manchester"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ManchesterPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, ManchesterPayload::percent, ManchesterPayload::new);

	@Override
	public Type<ManchesterPayload> type() {
		return TYPE;
	}
}
