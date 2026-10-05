package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client reports how far its Cluster Bomb fireball is grown, 0-100; 0 when it let go or stopped. */
public record FireballChargePayload(int charge) implements CustomPacketPayload {
	public static final Type<FireballChargePayload> TYPE = new Type<>(DekuMod.id("fireball_charge"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FireballChargePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, FireballChargePayload::charge,
		FireballChargePayload::new);

	@Override
	public Type<FireballChargePayload> type() {
		return TYPE;
	}
}
