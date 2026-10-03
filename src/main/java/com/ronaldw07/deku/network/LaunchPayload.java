package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client let go of a charged One For All launch; charge is 0-100. */
public record LaunchPayload(int charge) implements CustomPacketPayload {
	public static final Type<LaunchPayload> TYPE = new Type<>(DekuMod.id("launch"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LaunchPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, LaunchPayload::charge, LaunchPayload::new);

	@Override
	public Type<LaunchPayload> type() {
		return TYPE;
	}
}
