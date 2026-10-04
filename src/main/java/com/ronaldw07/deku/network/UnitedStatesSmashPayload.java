package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client hit the ground with United States of Smash at the given power. */
public record UnitedStatesSmashPayload(int percent) implements CustomPacketPayload {
	public static final Type<UnitedStatesSmashPayload> TYPE = new Type<>(DekuMod.id("united_states_smash"));
	public static final StreamCodec<RegistryFriendlyByteBuf, UnitedStatesSmashPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, UnitedStatesSmashPayload::percent, UnitedStatesSmashPayload::new);

	@Override
	public Type<UnitedStatesSmashPayload> type() {
		return TYPE;
	}
}
