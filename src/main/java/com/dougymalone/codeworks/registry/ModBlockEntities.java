package com.dougymalone.codeworks.registry;

import com.dougymalone.codeworks.Codeworks;
import com.dougymalone.codeworks.block.entity.BreakerBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<BreakerBlockEntity> BREAKER = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		Codeworks.id("breaker"),
		FabricBlockEntityTypeBuilder.create(BreakerBlockEntity::new, ModBlocks.BREAKER).build()
	);

	private ModBlockEntities() {
	}

	public static void register() {
		// static init
	}
}
