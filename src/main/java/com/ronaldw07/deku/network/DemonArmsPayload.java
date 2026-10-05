package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client switches Sukuna's Demon Arms on or off. */
public record DemonArmsPayload(boolean on) implements CustomPacketPayload {
	public static final Type<DemonArmsPayload> TYPE = new Type<>(DekuMod.id("demon_arms"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DemonArmsPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.BOOL, DemonArmsPayload::on, DemonArmsPayload::new);

	@Override
	public Type<DemonArmsPayload> type() {
		return TYPE;
	}
}
