package com.dougymalone.codeworks.client.network;

import com.dougymalone.codeworks.client.screen.BreakerEditScreen;
import com.dougymalone.codeworks.network.OpenBreakerEditorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class ClientNetworking {
	private ClientNetworking() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(OpenBreakerEditorPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				Minecraft minecraft = context.client();
				minecraft.setScreenAndShow(new BreakerEditScreen(payload.pos(), payload.script(), payload.status()));
			});
		});
	}
}
