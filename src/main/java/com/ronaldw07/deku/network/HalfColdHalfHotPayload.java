package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client used a Half Cold Half Hot move. For moves you hold (flamethrower, ice slide), active is
 * true on start and false on release.
 */
public record HalfColdHalfHotPayload(Move move, boolean active) implements CustomPacketPayload {
	public enum Move {
		ICE_WAVE, FLAME, ICE_WALL, HEATWAVE, SLIDE
	}

	public static final Type<HalfColdHalfHotPayload> TYPE = new Type<>(DekuMod.id("half_cold_half_hot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HalfColdHalfHotPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.idMapper(i -> Move.values()[i], Move::ordinal), HalfColdHalfHotPayload::move,
		ByteBufCodecs.BOOL, HalfColdHalfHotPayload::active,
		HalfColdHalfHotPayload::new);

	@Override
	public Type<HalfColdHalfHotPayload> type() {
		return TYPE;
	}
}
