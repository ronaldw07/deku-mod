package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server tells nearby clients to draw a slash: a thin sheet that cuts forward from origin along
 * aim for length blocks, spanning halfHeight either side of its middle along blade.
 */
public record SlashFxPayload(Vec3 origin, Vec3 aim, Vec3 blade, float length, float halfHeight, boolean big)
		implements CustomPacketPayload {
	public static final Type<SlashFxPayload> TYPE = new Type<>(DekuMod.id("slash_fx"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SlashFxPayload> CODEC = StreamCodec.composite(
		Vec3.STREAM_CODEC, SlashFxPayload::origin,
		Vec3.STREAM_CODEC, SlashFxPayload::aim,
		Vec3.STREAM_CODEC, SlashFxPayload::blade,
		ByteBufCodecs.FLOAT, SlashFxPayload::length,
		ByteBufCodecs.FLOAT, SlashFxPayload::halfHeight,
		ByteBufCodecs.BOOL, SlashFxPayload::big,
		SlashFxPayload::new);

	@Override
	public Type<SlashFxPayload> type() {
		return TYPE;
	}
}
