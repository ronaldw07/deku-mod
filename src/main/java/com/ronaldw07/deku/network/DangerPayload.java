package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server tells a player how close the worst danger is (0 = none, 1 = about to hit) and where it is. */
public record DangerPayload(float level, Vec3 source) implements CustomPacketPayload {
	public static final Type<DangerPayload> TYPE = new Type<>(DekuMod.id("danger"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DangerPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.FLOAT, DangerPayload::level,
		Vec3.STREAM_CODEC, DangerPayload::source,
		DangerPayload::new);

	@Override
	public Type<DangerPayload> type() {
		return TYPE;
	}
}
