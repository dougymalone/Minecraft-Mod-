package com.dougymalone.codeworks.client.network;

import com.dougymalone.codeworks.client.screen.ChannelAdapterScreen;
import com.dougymalone.codeworks.client.screen.MachineEditScreen;
import com.dougymalone.codeworks.network.OpenChannelAdapterPayload;
import com.dougymalone.codeworks.network.OpenMachineEditorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class ClientNetworking {
	private ClientNetworking() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(OpenMachineEditorPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				Minecraft minecraft = context.client();
				minecraft.setScreenAndShow(new MachineEditScreen(
					payload.pos(),
					payload.script(),
					payload.status(),
					payload.machineType()
				));
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(OpenChannelAdapterPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				Minecraft minecraft = context.client();
				minecraft.setScreenAndShow(new ChannelAdapterScreen(
					payload.pos(),
					payload.channel(),
					payload.displayName(),
					payload.status()
				));
			});
		});
	}
}
