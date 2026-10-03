package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server tells nearby clients to draw a Smash blast's lightning from one point to another. */
public record SmashFxPayload(Vec3 from, Vec3 to, float power) implements CustomPacketPayload {
	public static final Type<SmashFxPayload> TYPE = new Type<>(DekuMod.id("smash_fx"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SmashFxPayload> CODEC = StreamCodec.composite(
		Vec3.STREAM_CODEC, SmashFxPayload::from,
		Vec3.STREAM_CODEC, SmashFxPayload::to,
		ByteBufCodecs.FLOAT, SmashFxPayload::power,
		SmashFxPayload::new);

	@Override
	public Type<SmashFxPayload> type() {
		return TYPE;
	}
}
