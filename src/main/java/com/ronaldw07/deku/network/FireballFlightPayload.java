package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server tells nearby clients a glowing orb was thrown: where from, where to, how big the ball
 * is, its speed in blocks a tick, what kind it is, and how many ticks it hangs at the end.
 */
public record FireballFlightPayload(Vec3 start, Vec3 end, float ballRadius, float speed, Kind kind, int holdTicks)
		implements CustomPacketPayload {
	public enum Kind {
		FIRE, BLUE, RED, PURPLE, ARROW
	}

	public static final Type<FireballFlightPayload> TYPE = new Type<>(DekuMod.id("fireball_flight"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FireballFlightPayload> CODEC = StreamCodec.composite(
		Vec3.STREAM_CODEC, FireballFlightPayload::start,
		Vec3.STREAM_CODEC, FireballFlightPayload::end,
		ByteBufCodecs.FLOAT, FireballFlightPayload::ballRadius,
		ByteBufCodecs.FLOAT, FireballFlightPayload::speed,
		ByteBufCodecs.idMapper(i -> Kind.values()[i], Kind::ordinal), FireballFlightPayload::kind,
		ByteBufCodecs.VAR_INT, FireballFlightPayload::holdTicks,
		FireballFlightPayload::new);

	@Override
	public Type<FireballFlightPayload> type() {
		return TYPE;
	}
}
