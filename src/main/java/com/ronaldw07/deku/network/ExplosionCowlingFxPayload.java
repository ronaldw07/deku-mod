package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server tells nearby players that someone's Explosion Cowling lit or went out, so they can see the glow too. */
public record ExplosionCowlingFxPayload(UUID player, boolean on) implements CustomPacketPayload {
	public static final Type<ExplosionCowlingFxPayload> TYPE = new Type<>(DekuMod.id("explosion_cowling_fx"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ExplosionCowlingFxPayload> CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC, ExplosionCowlingFxPayload::player,
		ByteBufCodecs.BOOL, ExplosionCowlingFxPayload::on,
		ExplosionCowlingFxPayload::new);

	@Override
	public Type<ExplosionCowlingFxPayload> type() {
		return TYPE;
	}
}
