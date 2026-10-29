package com.dougymalone.codeworks.network;

import com.dougymalone.codeworks.Codeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SaveBreakerChannelPayload(BlockPos pos, String channel) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SaveBreakerChannelPayload> TYPE =
		new CustomPacketPayload.Type<>(Codeworks.id("save_breaker_channel"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SaveBreakerChannelPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		SaveBreakerChannelPayload::pos,
		ByteBufCodecs.STRING_UTF8,
		SaveBreakerChannelPayload::channel,
		SaveBreakerChannelPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
