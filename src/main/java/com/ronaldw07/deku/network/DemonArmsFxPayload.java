package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server tells nearby players that someone's Demon Arms sprouted or went away, so they can see them too. */
public record DemonArmsFxPayload(UUID player, boolean on) implements CustomPacketPayload {
	public static final Type<DemonArmsFxPayload> TYPE = new Type<>(DekuMod.id("demon_arms_fx"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DemonArmsFxPayload> CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC, DemonArmsFxPayload::player,
		ByteBufCodecs.BOOL, DemonArmsFxPayload::on,
		DemonArmsFxPayload::new);

	@Override
	public Type<DemonArmsFxPayload> type() {
		return TYPE;
	}
}
