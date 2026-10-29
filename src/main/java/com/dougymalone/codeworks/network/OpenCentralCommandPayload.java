package com.dougymalone.codeworks.network;

import com.dougymalone.codeworks.Codeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenCentralCommandPayload(
	BlockPos pos,
	String status,
	String channelsCsv,
	String breakerCountsCsv,
	String scriptsPack,
	String selectedScript
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenCentralCommandPayload> TYPE =
		new CustomPacketPayload.Type<>(Codeworks.id("open_central_command"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenCentralCommandPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		OpenCentralCommandPayload::pos,
		ByteBufCodecs.STRING_UTF8,
		OpenCentralCommandPayload::status,
		ByteBufCodecs.STRING_UTF8,
		OpenCentralCommandPayload::channelsCsv,
		ByteBufCodecs.STRING_UTF8,
		OpenCentralCommandPayload::breakerCountsCsv,
		ByteBufCodecs.STRING_UTF8,
		OpenCentralCommandPayload::scriptsPack,
		ByteBufCodecs.STRING_UTF8,
		OpenCentralCommandPayload::selectedScript,
		OpenCentralCommandPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
