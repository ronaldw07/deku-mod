package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server tells nearby clients to draw green lightning from one point to another. Heavy is the
 * Smash punch itself: denser bolts with arcs leaping off them. Everything else is light.
 */
public record SmashFxPayload(Vec3 from, Vec3 to, float power, boolean heavy) implements CustomPacketPayload {
	public static final Type<SmashFxPayload> TYPE = new Type<>(DekuMod.id("smash_fx"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SmashFxPayload> CODEC = StreamCodec.composite(
		Vec3.STREAM_CODEC, SmashFxPayload::from,
		Vec3.STREAM_CODEC, SmashFxPayload::to,
		ByteBufCodecs.FLOAT, SmashFxPayload::power,
		ByteBufCodecs.BOOL, SmashFxPayload::heavy,
		SmashFxPayload::new);

	@Override
	public Type<SmashFxPayload> type() {
		return TYPE;
	}
}
