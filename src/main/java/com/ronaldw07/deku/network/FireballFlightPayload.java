package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server tells nearby clients a Cluster Bomb fireball was thrown: where from, where to, how big the blast will be and its speed in blocks a tick. */
public record FireballFlightPayload(Vec3 start, Vec3 end, float blastRadius, float speed) implements CustomPacketPayload {
	public static final Type<FireballFlightPayload> TYPE = new Type<>(DekuMod.id("fireball_flight"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FireballFlightPayload> CODEC = StreamCodec.composite(
		Vec3.STREAM_CODEC, FireballFlightPayload::start,
		Vec3.STREAM_CODEC, FireballFlightPayload::end,
		ByteBufCodecs.FLOAT, FireballFlightPayload::blastRadius,
		ByteBufCodecs.FLOAT, FireballFlightPayload::speed,
		FireballFlightPayload::new);

	@Override
	public Type<FireballFlightPayload> type() {
		return TYPE;
	}
}
