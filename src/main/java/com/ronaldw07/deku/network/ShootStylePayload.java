package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client threw a Shoot Style kick at the given power. */
public record ShootStylePayload(int percent) implements CustomPacketPayload {
	public static final Type<ShootStylePayload> TYPE = new Type<>(DekuMod.id("shoot_style"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShootStylePayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, ShootStylePayload::percent, ShootStylePayload::new);

	@Override
	public Type<ShootStylePayload> type() {
		return TYPE;
	}
}
