package com.dougymalone.codeworks.network;

import com.dougymalone.codeworks.Codeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenChannelAdapterPayload(BlockPos pos, String channel, String displayName, String status)
	implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenChannelAdapterPayload> TYPE =
		new CustomPacketPayload.Type<>(Codeworks.id("open_channel_adapter"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenChannelAdapterPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		OpenChannelAdapterPayload::pos,
		ByteBufCodecs.STRING_UTF8,
		OpenChannelAdapterPayload::channel,
		ByteBufCodecs.STRING_UTF8,
		OpenChannelAdapterPayload::displayName,
		ByteBufCodecs.STRING_UTF8,
		OpenChannelAdapterPayload::status,
		OpenChannelAdapterPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
