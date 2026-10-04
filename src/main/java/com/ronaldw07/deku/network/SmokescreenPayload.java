package com.ronaldw07.deku.network;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client started holding the Smokescreen key (true) or let go of it (false). */
public record SmokescreenPayload(boolean holding) implements CustomPacketPayload {
	public static final Type<SmokescreenPayload> TYPE = new Type<>(DekuMod.id("smokescreen"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SmokescreenPayload> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, SmokescreenPayload::holding, SmokescreenPayload::new);

	@Override
	public Type<SmokescreenPayload> type() {
		return TYPE;
	}
}
