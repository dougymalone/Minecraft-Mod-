package com.dougymalone.codeworks.network;

import com.dougymalone.codeworks.Codeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SaveCentralCommandPayload(
	BlockPos pos,
	String channelsCsv,
	String scriptsPack,
	String selectedScript
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SaveCentralCommandPayload> TYPE =
		new CustomPacketPayload.Type<>(Codeworks.id("save_central_command"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SaveCentralCommandPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		SaveCentralCommandPayload::pos,
		ByteBufCodecs.STRING_UTF8,
		SaveCentralCommandPayload::channelsCsv,
		ByteBufCodecs.STRING_UTF8,
		SaveCentralCommandPayload::scriptsPack,
		ByteBufCodecs.STRING_UTF8,
		SaveCentralCommandPayload::selectedScript,
		SaveCentralCommandPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
