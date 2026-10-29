package com.dougymalone.codeworks.network;

import com.dougymalone.codeworks.Codeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SaveChannelAdapterPayload(BlockPos pos, String channel, String displayName)
	implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SaveChannelAdapterPayload> TYPE =
		new CustomPacketPayload.Type<>(Codeworks.id("save_channel_adapter"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SaveChannelAdapterPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		SaveChannelAdapterPayload::pos,
		ByteBufCodecs.STRING_UTF8,
		SaveChannelAdapterPayload::channel,
		ByteBufCodecs.STRING_UTF8,
		SaveChannelAdapterPayload::displayName,
		SaveChannelAdapterPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
