package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client left-clicked with a quirk item in hand: use that quirk's punch. */
public record MeleePayload() implements CustomPacketPayload {
	public static final MeleePayload INSTANCE = new MeleePayload();
	public static final Type<MeleePayload> TYPE = new Type<>(DekuMod.id("melee"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MeleePayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<MeleePayload> type() {
		return TYPE;
	}
}
