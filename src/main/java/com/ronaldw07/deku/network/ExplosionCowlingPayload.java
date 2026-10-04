package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client switches the Explosion quirk's Full Cowling on or off. */
public record ExplosionCowlingPayload(boolean on) implements CustomPacketPayload {
	public static final Type<ExplosionCowlingPayload> TYPE = new Type<>(DekuMod.id("explosion_cowling"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ExplosionCowlingPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.BOOL, ExplosionCowlingPayload::on, ExplosionCowlingPayload::new);

	@Override
	public Type<ExplosionCowlingPayload> type() {
		return TYPE;
	}
}
