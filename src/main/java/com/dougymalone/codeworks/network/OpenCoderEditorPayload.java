package com.dougymalone.codeworks.network;

import com.dougymalone.codeworks.Codeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenCoderEditorPayload(BlockPos pos, String script, String status) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenCoderEditorPayload> TYPE =
		new CustomPacketPayload.Type<>(Codeworks.id("open_coder_editor"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenCoderEditorPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		OpenCoderEditorPayload::pos,
		ByteBufCodecs.STRING_UTF8,
		OpenCoderEditorPayload::script,
		ByteBufCodecs.STRING_UTF8,
		OpenCoderEditorPayload::status,
		OpenCoderEditorPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
