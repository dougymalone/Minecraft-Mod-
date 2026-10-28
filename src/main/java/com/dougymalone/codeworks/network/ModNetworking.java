package com.dougymalone.codeworks.network;

import com.dougymalone.codeworks.block.entity.CoderBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void registerCommon() {
		PayloadTypeRegistry.clientboundPlay().register(OpenCoderEditorPayload.TYPE, OpenCoderEditorPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SaveCoderScriptPayload.TYPE, SaveCoderScriptPayload.CODEC);
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(SaveCoderScriptPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				if (!(context.player().level() instanceof ServerLevel level)) {
					return;
				}
				if (!level.isLoaded(payload.pos()) || context.player().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0 * 64.0) {
					return;
				}
				BlockEntity blockEntity = level.getBlockEntity(payload.pos());
				if (blockEntity instanceof CoderBlockEntity coder) {
					coder.setScriptFromPlayer(payload.script());
				}
			});
		});
	}
}
