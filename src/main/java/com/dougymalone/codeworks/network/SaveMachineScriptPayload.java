package com.dougymalone.codeworks.network;

import com.dougymalone.codeworks.Codeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SaveMachineScriptPayload(BlockPos pos, String script) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SaveMachineScriptPayload> TYPE =
		new CustomPacketPayload.Type<>(Codeworks.id("save_machine_script"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SaveMachineScriptPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		SaveMachineScriptPayload::pos,
		ByteBufCodecs.STRING_UTF8,
		SaveMachineScriptPayload::script,
		SaveMachineScriptPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
