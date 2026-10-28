package com.dougymalone.codeworks.block.entity;

import com.dougymalone.codeworks.Codeworks;
import com.dougymalone.codeworks.block.CoderBlock;
import com.dougymalone.codeworks.network.OpenCoderEditorPayload;
import com.dougymalone.codeworks.registry.ModBlockEntities;
import com.dougymalone.codeworks.script.Ast;
import com.dougymalone.codeworks.script.HostApi;
import com.dougymalone.codeworks.script.Interpreter;
import com.dougymalone.codeworks.script.ScriptException;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class CoderBlockEntity extends BlockEntity implements HostApi {
	public static final String DEFAULT_SCRIPT = """
		// Codeworks Coder — break the block in front
		Harvest();
		""";

	public static final int RUN_INTERVAL_TICKS = 20;
	public static final int HARVEST_COOLDOWN_TICKS = 40;
	public static final int MAX_SCRIPT_LENGTH = 8000;

	private String scriptSource = DEFAULT_SCRIPT;
	private String status = "Ready";
	private boolean running = true;
	private int tickCounter;
	private int harvestCooldown;
	private Ast.@Nullable Program compiled;

	public CoderBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CODER, pos, state);
		recompile();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CoderBlockEntity coder) {
		coder.tickServer(level, pos, state);
	}

	private void tickServer(Level level, BlockPos pos, BlockState state) {
		if (harvestCooldown > 0) {
			harvestCooldown--;
		}
		if (!running || compiled == null) {
			return;
		}
		tickCounter++;
		if (tickCounter < RUN_INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;
		try {
			new Interpreter(compiled, this).run();
		} catch (ScriptException exception) {
			running = false;
			status = "Error: " + exception.getMessage();
			setChanged();
			Codeworks.LOGGER.debug("Coder script error at {}: {}", pos, exception.getMessage());
		} catch (RuntimeException exception) {
			running = false;
			status = "Error: internal fault";
			setChanged();
			Codeworks.LOGGER.warn("Coder crashed at {}", pos, exception);
		}
	}

	public void openEditor(ServerPlayer player) {
		ServerPlayNetworking.send(player, new OpenCoderEditorPayload(worldPosition, scriptSource, status));
	}

	public void setScriptFromPlayer(String script) {
		if (script.length() > MAX_SCRIPT_LENGTH) {
			status = "Error: script too long";
			setChanged();
			return;
		}
		this.scriptSource = script;
		recompile();
		setChanged();
	}

	private void recompile() {
		try {
			compiled = Interpreter.compile(scriptSource);
			running = true;
			status = "OK";
			tickCounter = 0;
		} catch (ScriptException exception) {
			compiled = null;
			running = false;
			status = "Parse error: " + exception.getMessage();
		}
	}

	@Override
	public boolean harvest() throws ScriptException {
		if (!(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		if (harvestCooldown > 0) {
			return false;
		}
		Direction facing = getBlockState().getValue(CoderBlock.FACING);
		BlockPos targetPos = worldPosition.relative(facing);
		BlockState target = serverLevel.getBlockState(targetPos);
		if (target.isAir()) {
			return false;
		}
		if (target.getDestroySpeed(serverLevel, targetPos) < 0.0F
			|| target.is(Blocks.BEDROCK)
			|| target.is(Blocks.BARRIER)
			|| target.is(BlockTags.FEATURES_CANNOT_REPLACE)) {
			status = "Cannot break " + target.getBlock().getName().getString();
			setChanged();
			return false;
		}
		boolean destroyed = serverLevel.destroyBlock(targetPos, true, null);
		if (destroyed) {
			harvestCooldown = HARVEST_COOLDOWN_TICKS;
			status = "Harvested";
			setChanged();
		}
		return destroyed;
	}

	@Override
	public void log(String message) {
		status = message == null ? "" : (message.length() > 120 ? message.substring(0, 120) : message);
		setChanged();
	}

	public String getScriptSource() {
		return scriptSource;
	}

	public String getStatus() {
		return status;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		scriptSource = input.getStringOr("Script", DEFAULT_SCRIPT);
		status = input.getStringOr("Status", "Ready");
		running = input.getBooleanOr("Running", true);
		tickCounter = input.getIntOr("TickCounter", 0);
		harvestCooldown = input.getIntOr("HarvestCooldown", 0);
		recompile();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("Script", scriptSource);
		output.putString("Status", status);
		output.putBoolean("Running", running);
		output.putInt("TickCounter", tickCounter);
		output.putInt("HarvestCooldown", harvestCooldown);
	}
}
