package com.dougymalone.codeworks;

import com.dougymalone.codeworks.network.ModNetworking;
import com.dougymalone.codeworks.registry.ModBlockEntities;
import com.dougymalone.codeworks.registry.ModBlocks;
import com.dougymalone.codeworks.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Codeworks implements ModInitializer {
	public static final String MOD_ID = "codeworks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModBlockEntities.register();
		ModItems.register();
		ModNetworking.registerCommon();
		ModNetworking.registerServerReceivers();
		LOGGER.info("Codeworks initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
