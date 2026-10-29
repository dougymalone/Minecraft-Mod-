package com.dougymalone.codeworks.block.entity;

import com.dougymalone.codeworks.registry.ModBlockEntities;
import com.dougymalone.codeworks.script.ScriptException;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BreakerBlockEntity extends ScriptedMachineBlockEntity {
	public static final String DEFAULT_SCRIPT = """
		// Codeworks Breaker — shares wireless bus with Placer + Channel Adapter
		channel = "line1";
		speed = 10;
		range = 1;
		if (GetExtended("gate")) {
		  Log("gate piston out — waiting");
		  Wait(20);
		}
		if (HasBlock()) {
		  string id = Sense();
		  if (Harvest()) {
		    Send("broke", id);
		    Pulse("gate");
		    Log("harvested " + id);
		  }
		}
		Wait(5);
		""";

	public BreakerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BREAKER, pos, state, DEFAULT_SCRIPT);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, BreakerBlockEntity breaker) {
		breaker.tickServer(level, pos, state);
	}

	@Override
	protected String machineTypeId() {
		return "breaker";
	}

	@Override
	protected String defaultScript() {
		return DEFAULT_SCRIPT;
	}

	@Override
	public boolean harvest() throws ScriptException {
		if (!(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		if (isOnCooldown()) {
			return false;
		}
		BlockPos targetPos = findTargetPos(serverLevel);
		if (targetPos == null) {
			return false;
		}
		BlockState target = serverLevel.getBlockState(targetPos);
		if (isUnbreakable(serverLevel, targetPos, target)) {
			setStatus("Cannot break " + target.getBlock().getName().getString());
			setChanged();
			return false;
		}
		boolean destroyed = serverLevel.destroyBlock(targetPos, true, null);
		if (destroyed) {
			startActionCooldown();
			setStatus("Harvested");
			setChanged();
		}
		return destroyed;
	}
}
