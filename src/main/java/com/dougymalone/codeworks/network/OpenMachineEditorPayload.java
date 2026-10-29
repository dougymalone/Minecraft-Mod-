package com.dougymalone.codeworks.network;

import com.dougymalone.codeworks.Codeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenMachineEditorPayload(BlockPos pos, String script, String status, String machineType)
	implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenMachineEditorPayload> TYPE =
		new CustomPacketPayload.Type<>(Codeworks.id("open_machine_editor"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenMachineEditorPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC,
		OpenMachineEditorPayload::pos,
		ByteBufCodecs.STRING_UTF8,
		OpenMachineEditorPayload::script,
		ByteBufCodecs.STRING_UTF8,
		OpenMachineEditorPayload::status,
		ByteBufCodecs.STRING_UTF8,
		OpenMachineEditorPayload::machineType,
		OpenMachineEditorPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
