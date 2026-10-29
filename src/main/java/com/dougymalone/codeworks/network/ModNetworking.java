package com.dougymalone.codeworks.network;

import com.dougymalone.codeworks.block.entity.BreakerBlockEntity;
import com.dougymalone.codeworks.block.entity.CentralCommandBlockEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void registerCommon() {
		PayloadTypeRegistry.clientboundPlay().register(OpenCentralCommandPayload.TYPE, OpenCentralCommandPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SaveCentralCommandPayload.TYPE, SaveCentralCommandPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenBreakerChannelPayload.TYPE, OpenBreakerChannelPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SaveBreakerChannelPayload.TYPE, SaveBreakerChannelPayload.CODEC);
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(SaveCentralCommandPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				if (!(context.player().level() instanceof ServerLevel level)) {
					return;
				}
				if (!level.isLoaded(payload.pos()) || context.player().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0 * 64.0) {
					return;
				}
				BlockEntity blockEntity = level.getBlockEntity(payload.pos());
				if (blockEntity instanceof CentralCommandBlockEntity command) {
					List<String> channels = new ArrayList<>();
					if (payload.channelsCsv() != null && !payload.channelsCsv().isBlank()) {
						channels.addAll(Arrays.asList(payload.channelsCsv().split("\n")));
					}
					command.applyFromPlayer(payload.scriptsPack(), channels, payload.selectedScript());
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(SaveBreakerChannelPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				if (!(context.player().level() instanceof ServerLevel level)) {
					return;
				}
				if (!level.isLoaded(payload.pos()) || context.player().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0 * 64.0) {
					return;
				}
				BlockEntity blockEntity = level.getBlockEntity(payload.pos());
				if (blockEntity instanceof BreakerBlockEntity breaker) {
					breaker.setChannelFromPlayer(payload.channel());
				}
			});
		});
	}
}
