package com.dougymalone.codeworks.client.network;

import com.dougymalone.codeworks.client.screen.BreakerChannelScreen;
import com.dougymalone.codeworks.client.screen.CentralCommandScreen;
import com.dougymalone.codeworks.network.OpenBreakerChannelPayload;
import com.dougymalone.codeworks.network.OpenCentralCommandPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class ClientNetworking {
	private ClientNetworking() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(OpenCentralCommandPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				Minecraft minecraft = context.client();
				minecraft.setScreenAndShow(new CentralCommandScreen(
					payload.pos(),
					payload.script(),
					payload.status(),
					payload.channelsCsv(),
					payload.breakerCountsCsv()
				));
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(OpenBreakerChannelPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				Minecraft minecraft = context.client();
				minecraft.setScreenAndShow(new BreakerChannelScreen(
					payload.pos(),
					payload.channel(),
					payload.availableChannelsCsv()
				));
			});
		});
	}
}
