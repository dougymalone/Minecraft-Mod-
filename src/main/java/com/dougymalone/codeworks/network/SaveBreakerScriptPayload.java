package com.dougymalone.codeworks.network;

import com.dougymalone.codeworks.Codeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SaveBreakerScriptPayload(BlockPos pos, String script) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SaveBreakerScriptPayload> TYPE =
		new CustomPacketPayload.Type<>(Codeworks.id("save_breaker_script"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SaveBreakerScriptPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		SaveBreakerScriptPayload::pos,
		ByteBufCodecs.STRING_UTF8,
		SaveBreakerScriptPayload::script,
		SaveBreakerScriptPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
