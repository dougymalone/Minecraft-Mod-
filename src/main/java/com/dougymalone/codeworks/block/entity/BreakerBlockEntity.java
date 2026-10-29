package com.dougymalone.codeworks.block.entity;

import com.dougymalone.codeworks.Codeworks;
import com.dougymalone.codeworks.block.BreakerBlock;
import com.dougymalone.codeworks.network.OpenBreakerEditorPayload;
import com.dougymalone.codeworks.registry.ModBlockEntities;
import com.dougymalone.codeworks.script.Ast;
import com.dougymalone.codeworks.script.HostApi;
import com.dougymalone.codeworks.script.Interpreter;
import com.dougymalone.codeworks.script.ScriptException;
import com.dougymalone.codeworks.script.ScriptYield;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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

public class BreakerBlockEntity extends BlockEntity implements HostApi {
	public static final String DEFAULT_SCRIPT = """
		// Codeworks Breaker
		speed = 10;
		range = 1;
		if (HasBlock()) {
		  Harvest();
		}
		""";

	public static final int MAX_SCRIPT_LENGTH = 8000;
	public static final int MIN_SPEED = 1;
	public static final int MAX_SPEED = 20;
	public static final int MIN_RANGE = 1;
	public static final int MAX_RANGE = 3;

	private String scriptSource = DEFAULT_SCRIPT;
	private String status = "Ready";
	private boolean running = true;
	private int harvestCooldown;
	private int waitTicks;
	private int speed = 10;
	private int range = 1;
	private Ast.@Nullable Program compiled;
	private @Nullable Interpreter activeInterpreter;

	public BreakerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BREAKER, pos, state);
		recompile(true);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, BreakerBlockEntity breaker) {
		breaker.tickServer(level, pos, state);
	}

	private void tickServer(Level level, BlockPos pos, BlockState state) {
		if (harvestCooldown > 0) {
			harvestCooldown--;
		}
		if (!running || compiled == null) {
			return;
		}
		if (waitTicks > 0) {
			waitTicks--;
			return;
		}
		try {
			if (activeInterpreter == null || activeInterpreter.isFinished()) {
				activeInterpreter = new Interpreter(compiled, this);
				activeInterpreter.start();
			} else {
				activeInterpreter.resume();
			}
		} catch (ScriptYield ignored) {
			// Waiting across ticks — keep activeInterpreter
		} catch (ScriptException exception) {
			running = false;
			activeInterpreter = null;
			status = "Error: " + exception.getMessage();
			setChanged();
			Codeworks.LOGGER.debug("Breaker script error at {}: {}", pos, exception.getMessage());
		} catch (RuntimeException exception) {
			running = false;
			activeInterpreter = null;
			status = "Error: internal fault";
			setChanged();
			Codeworks.LOGGER.warn("Breaker crashed at {}", pos, exception);
		}
	}

	public void openEditor(ServerPlayer player) {
		ServerPlayNetworking.send(player, new OpenBreakerEditorPayload(worldPosition, scriptSource, status));
	}

	public void setScriptFromPlayer(String script) {
		if (script.length() > MAX_SCRIPT_LENGTH) {
			status = "Error: script too long";
			setChanged();
			return;
		}
		this.scriptSource = script;
		recompile(true);
		setChanged();
	}

	private void recompile(boolean resetRuntime) {
		try {
			compiled = Interpreter.compile(scriptSource);
			running = true;
			status = "OK";
			if (resetRuntime) {
				waitTicks = 0;
				activeInterpreter = null;
			}
		} catch (ScriptException exception) {
			compiled = null;
			running = false;
			activeInterpreter = null;
			status = "Parse error: " + exception.getMessage();
		}
	}

	private int harvestCooldownForSpeed() {
		return Math.max(1, 21 - speed);
	}

	private @Nullable BlockPos findTargetPos(ServerLevel serverLevel) {
		Direction facing = getBlockState().getValue(BreakerBlock.FACING);
		for (int step = 1; step <= range; step++) {
			BlockPos targetPos = worldPosition.relative(facing, step);
			BlockState target = serverLevel.getBlockState(targetPos);
			if (!target.isAir()) {
				return targetPos;
			}
		}
		return null;
	}

	private boolean isUnbreakable(ServerLevel level, BlockPos pos, BlockState state) {
		return state.getDestroySpeed(level, pos) < 0.0F
			|| state.is(Blocks.BEDROCK)
			|| state.is(Blocks.BARRIER)
			|| state.is(BlockTags.FEATURES_CANNOT_REPLACE);
	}

	@Override
	public boolean harvest() throws ScriptException {
		if (!(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		if (harvestCooldown > 0) {
			return false;
		}
		BlockPos targetPos = findTargetPos(serverLevel);
		if (targetPos == null) {
			return false;
		}
		BlockState target = serverLevel.getBlockState(targetPos);
		if (isUnbreakable(serverLevel, targetPos, target)) {
			status = "Cannot break " + target.getBlock().getName().getString();
			setChanged();
			return false;
		}
		boolean destroyed = serverLevel.destroyBlock(targetPos, true, null);
		if (destroyed) {
			harvestCooldown = harvestCooldownForSpeed();
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

	@Override
	public String sense() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return "air";
		}
		BlockPos targetPos = findTargetPos(serverLevel);
		if (targetPos == null) {
			return "air";
		}
		BlockState state = serverLevel.getBlockState(targetPos);
		var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return id == null ? "air" : id.toString();
	}

	@Override
	public boolean hasBlock() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		BlockPos targetPos = findTargetPos(serverLevel);
		if (targetPos == null) {
			return false;
		}
		BlockState state = serverLevel.getBlockState(targetPos);
		return !state.isAir() && !isUnbreakable(serverLevel, targetPos, state);
	}

	@Override
	public void move(String direction) throws ScriptException {
		if (level == null || level.isClientSide()) {
			return;
		}
		Direction current = getBlockState().getValue(BreakerBlock.FACING);
		String key = direction == null ? "" : direction.trim().toLowerCase();
		Direction next = switch (key) {
			case "left" -> current.getCounterClockWise();
			case "right" -> current.getClockWise();
			case "back", "around", "reverse" -> current.getOpposite();
			case "north", "south", "east", "west", "up", "down" -> {
				Direction named = Direction.byName(key);
				if (named == null) {
					throw new ScriptException("Unknown Move direction '" + direction + "'");
				}
				yield named;
			}
			default -> throw new ScriptException(
				"Move expects left/right/back/north/south/east/west/up/down, got '" + direction + "'"
			);
		};
		level.setBlock(worldPosition, getBlockState().setValue(BreakerBlock.FACING, next), 3);
		status = "Facing " + next.getSerializedName();
		setChanged();
	}

	@Override
	public void waitTicks(int ticks) throws ScriptException {
		if (ticks < 0) {
			throw new ScriptException("Wait ticks must be >= 0");
		}
		waitTicks = Math.min(ticks, 20 * 60 * 5); // cap 5 minutes
	}

	@Override
	public int getSpeed() {
		return speed;
	}

	@Override
	public void setSpeed(int speed) {
		this.speed = Math.clamp(speed, MIN_SPEED, MAX_SPEED);
		setChanged();
	}

	@Override
	public int getRange() {
		return range;
	}

	@Override
	public void setRange(int range) {
		this.range = Math.clamp(range, MIN_RANGE, MAX_RANGE);
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
		harvestCooldown = input.getIntOr("HarvestCooldown", 0);
		waitTicks = input.getIntOr("WaitTicks", 0);
		speed = Math.clamp(input.getIntOr("Speed", 10), MIN_SPEED, MAX_SPEED);
		range = Math.clamp(input.getIntOr("Range", 1), MIN_RANGE, MAX_RANGE);
		recompile(true);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("Script", scriptSource);
		output.putString("Status", status);
		output.putBoolean("Running", running);
		output.putInt("HarvestCooldown", harvestCooldown);
		output.putInt("WaitTicks", waitTicks);
		output.putInt("Speed", speed);
		output.putInt("Range", range);
	}
}
