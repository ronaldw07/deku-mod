package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server tells nearby clients to draw Blackwhip tendrils from a player to either a
 * grabbed entity or, when targetId is NO_TARGET, a fixed point.
 */
public record BlackwhipFxPayload(int playerId, int targetId, Vec3 anchor, int ticks) implements CustomPacketPayload {
	public static final int NO_TARGET = -1;
	public static final Type<BlackwhipFxPayload> TYPE = new Type<>(DekuMod.id("blackwhip_fx"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BlackwhipFxPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, BlackwhipFxPayload::playerId,
		ByteBufCodecs.VAR_INT, BlackwhipFxPayload::targetId,
		Vec3.STREAM_CODEC, BlackwhipFxPayload::anchor,
		ByteBufCodecs.VAR_INT, BlackwhipFxPayload::ticks,
		BlackwhipFxPayload::new);

	@Override
	public Type<BlackwhipFxPayload> type() {
		return TYPE;
	}
}
