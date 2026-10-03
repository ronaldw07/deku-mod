package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client switched Danger Sense on or off. */
public record DangerSenseTogglePayload(boolean enabled) implements CustomPacketPayload {
	public static final Type<DangerSenseTogglePayload> TYPE = new Type<>(DekuMod.id("danger_sense_toggle"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DangerSenseTogglePayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.BOOL, DangerSenseTogglePayload::enabled, DangerSenseTogglePayload::new);

	@Override
	public Type<DangerSenseTogglePayload> type() {
		return TYPE;
	}
}
