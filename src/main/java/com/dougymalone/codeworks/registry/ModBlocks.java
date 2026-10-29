package com.dougymalone.codeworks.registry;

import com.dougymalone.codeworks.Codeworks;
import com.dougymalone.codeworks.block.BreakerBlock;
import com.dougymalone.codeworks.block.CentralCommandBlock;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final Block CENTRAL_COMMAND = register("central_command", CentralCommandBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BLUE)
		.strength(3.5F)
		.requiresCorrectToolForDrops()
		.sound(SoundType.METAL));

	public static final Block BREAKER = register("breaker", BreakerBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.METAL)
		.strength(3.5F)
		.requiresCorrectToolForDrops()
		.sound(SoundType.METAL));

	private ModBlocks() {
	}

	public static void register() {
		// static init
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Codeworks.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}
}
