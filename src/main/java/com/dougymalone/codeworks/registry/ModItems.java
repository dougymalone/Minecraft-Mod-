package com.dougymalone.codeworks.registry;

import com.dougymalone.codeworks.Codeworks;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final Item BREAKER = registerBlockItem("breaker", ModBlocks.BREAKER);
	public static final Item PLACER = registerBlockItem("placer", ModBlocks.PLACER);

	private ModItems() {
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> {
			entries.accept(BREAKER);
			entries.accept(PLACER);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
			entries.accept(BREAKER);
			entries.accept(PLACER);
		});
	}

	private static Item registerBlockItem(String name, net.minecraft.world.level.block.Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Codeworks.id(name));
		return Registry.register(
			BuiltInRegistries.ITEM,
			key,
			new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix())
		);
	}
}
