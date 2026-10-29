package com.dougymalone.codeworks;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Codeworks entrypoint. Gameplay machines were soft-reset; scaffolding remains
 * for the next rebuild of programmable Create-inspired blocks.
 */
public class Codeworks implements ModInitializer {
	public static final String MOD_ID = "codeworks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Codeworks initialized (no gameplay machines — soft reset)");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
