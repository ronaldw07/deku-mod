package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server tells nearby clients a tornado is spinning at base for the given number of ticks. */
public record TornadoFxPayload(Vec3 base, int ticks) implements CustomPacketPayload {
	public static final Type<TornadoFxPayload> TYPE = new Type<>(DekuMod.id("tornado_fx"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TornadoFxPayload> CODEC = StreamCodec.composite(
		Vec3.STREAM_CODEC, TornadoFxPayload::base,
		ByteBufCodecs.VAR_INT, TornadoFxPayload::ticks,
		TornadoFxPayload::new);

	@Override
	public Type<TornadoFxPayload> type() {
		return TYPE;
	}
}
