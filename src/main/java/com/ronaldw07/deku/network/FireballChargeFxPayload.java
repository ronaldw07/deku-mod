package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server tells nearby players how far someone's Cluster Bomb fireball is grown, so they can see it too. */
public record FireballChargeFxPayload(UUID player, int charge) implements CustomPacketPayload {
	public static final Type<FireballChargeFxPayload> TYPE = new Type<>(DekuMod.id("fireball_charge_fx"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FireballChargeFxPayload> CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC, FireballChargeFxPayload::player,
		ByteBufCodecs.VAR_INT, FireballChargeFxPayload::charge,
		FireballChargeFxPayload::new);

	@Override
	public Type<FireballChargeFxPayload> type() {
		return TYPE;
	}
}
