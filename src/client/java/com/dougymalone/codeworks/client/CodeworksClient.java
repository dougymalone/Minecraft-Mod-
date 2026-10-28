package com.dougymalone.codeworks.client;

import com.dougymalone.codeworks.client.network.ClientNetworking;
import net.fabricmc.api.ClientModInitializer;

public class CodeworksClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientNetworking.register();
	}
}
