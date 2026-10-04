package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client's Gearshift changed gear; 0 means off. */
public record GearshiftPayload(int gear) implements CustomPacketPayload {
	public static final Type<GearshiftPayload> TYPE = new Type<>(DekuMod.id("gearshift"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GearshiftPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, GearshiftPayload::gear, GearshiftPayload::new);

	@Override
	public Type<GearshiftPayload> type() {
		return TYPE;
	}
}
