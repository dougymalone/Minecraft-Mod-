package com.dougymalone.codeworks.network;

import com.dougymalone.codeworks.Codeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenBreakerEditorPayload(BlockPos pos, String script, String status) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenBreakerEditorPayload> TYPE =
		new CustomPacketPayload.Type<>(Codeworks.id("open_breaker_editor"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenBreakerEditorPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		OpenBreakerEditorPayload::pos,
		ByteBufCodecs.STRING_UTF8,
		OpenBreakerEditorPayload::script,
		ByteBufCodecs.STRING_UTF8,
		OpenBreakerEditorPayload::status,
		OpenBreakerEditorPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
