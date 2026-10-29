package com.dougymalone.codeworks.network;

import com.dougymalone.codeworks.Codeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenBreakerChannelPayload(BlockPos pos, String channel, String availableChannelsCsv) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenBreakerChannelPayload> TYPE =
		new CustomPacketPayload.Type<>(Codeworks.id("open_breaker_channel"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenBreakerChannelPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		OpenBreakerChannelPayload::pos,
		ByteBufCodecs.STRING_UTF8,
		OpenBreakerChannelPayload::channel,
		ByteBufCodecs.STRING_UTF8,
		OpenBreakerChannelPayload::availableChannelsCsv,
		OpenBreakerChannelPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
