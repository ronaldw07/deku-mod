package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client used an Explosion move. For moves you hold (flight, Howitzer), active is true on
 * start and false on release. Charge is how far a charged move was wound up, 0-100.
 */
public record ExplosionPayload(Move move, boolean active, int charge) implements CustomPacketPayload {
	public enum Move {
		AP_SHOT, AP_SHOT_BIG, FLIGHT, HOWITZER, GROUND_BLAST
	}

	public static final Type<ExplosionPayload> TYPE = new Type<>(DekuMod.id("explosion"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ExplosionPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.idMapper(i -> Move.values()[i], Move::ordinal), ExplosionPayload::move,
		ByteBufCodecs.BOOL, ExplosionPayload::active,
		ByteBufCodecs.VAR_INT, ExplosionPayload::charge,
		ExplosionPayload::new);

	@Override
	public Type<ExplosionPayload> type() {
		return TYPE;
	}
}
