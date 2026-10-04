package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server tells nearby clients to draw an Explosion blast; shots also streak in from the shooter's hand. */
public record ExplosionFxPayload(Vec3 center, float radius, Style style, Vec3 from) implements CustomPacketPayload {
	public enum Style {
		SHOT, BIG_SHOT, HOWITZER, GROUND, NUKE, HOWITZER_CORE, HOWITZER_RING, ICE_DOME, HEATWAVE
	}

	public static final Type<ExplosionFxPayload> TYPE = new Type<>(DekuMod.id("explosion_fx"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ExplosionFxPayload> CODEC = StreamCodec.composite(
		Vec3.STREAM_CODEC, ExplosionFxPayload::center,
		ByteBufCodecs.FLOAT, ExplosionFxPayload::radius,
		ByteBufCodecs.idMapper(i -> Style.values()[i], Style::ordinal), ExplosionFxPayload::style,
		Vec3.STREAM_CODEC, ExplosionFxPayload::from,
		ExplosionFxPayload::new);

	@Override
	public Type<ExplosionFxPayload> type() {
		return TYPE;
	}
}
