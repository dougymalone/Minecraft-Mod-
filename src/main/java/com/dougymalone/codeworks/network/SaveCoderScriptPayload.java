package com.dougymalone.codeworks.network;

import com.dougymalone.codeworks.Codeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SaveCoderScriptPayload(BlockPos pos, String script) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SaveCoderScriptPayload> TYPE =
		new CustomPacketPayload.Type<>(Codeworks.id("save_coder_script"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SaveCoderScriptPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		SaveCoderScriptPayload::pos,
		ByteBufCodecs.STRING_UTF8,
		SaveCoderScriptPayload::script,
		SaveCoderScriptPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
