package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server tells nearby clients a Domain Expansion opened around a point, lasting the given ticks (0 = it ended). */
public record DomainPayload(Vec3 center, float radius, int ticks, Kind kind) implements CustomPacketPayload {
	public enum Kind {
		SHRINE, VOID
	}

	public static final Type<DomainPayload> TYPE = new Type<>(DekuMod.id("domain"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DomainPayload> CODEC = StreamCodec.composite(
		Vec3.STREAM_CODEC, DomainPayload::center,
		ByteBufCodecs.FLOAT, DomainPayload::radius,
		ByteBufCodecs.VAR_INT, DomainPayload::ticks,
		ByteBufCodecs.idMapper(i -> Kind.values()[i], Kind::ordinal), DomainPayload::kind,
		DomainPayload::new);

	@Override
	public Type<DomainPayload> type() {
		return TYPE;
	}
}
