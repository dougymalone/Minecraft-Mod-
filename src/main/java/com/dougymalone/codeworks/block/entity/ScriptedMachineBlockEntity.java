package com.dougymalone.codeworks.block.entity;

import com.dougymalone.codeworks.Codeworks;
import com.dougymalone.codeworks.network.OpenMachineEditorPayload;
import com.dougymalone.codeworks.script.Ast;
import com.dougymalone.codeworks.script.HostApi;
import com.dougymalone.codeworks.script.Interpreter;
import com.dougymalone.codeworks.script.ScriptException;
import com.dougymalone.codeworks.script.ScriptYield;
import com.dougymalone.codeworks.wireless.ChannelAdapterIndex;
import com.dougymalone.codeworks.wireless.WirelessBus;
import com.dougymalone.codeworks.wireless.WirelessKeys;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Shared scripted machine runtime: interpreter tick loop, facing helpers,
 * sensing, wireless, and common settings.
 */
public abstract class ScriptedMachineBlockEntity extends BlockEntity implements HostApi {
	public static final int MAX_SCRIPT_LENGTH = 8000;
	public static final int MIN_SPEED = 1;
	public static final int MAX_SPEED = 20;
	public static final int MIN_RANGE = 1;
	public static final int MAX_RANGE = 3;

	private String scriptSource;
	private String status = "Ready";
	private boolean running = true;
	private int actionCooldown;
	private int waitTicks;
	private int speed = 10;
	private int range = 1;
	private String channel = "";
	private Ast.@Nullable Program compiled;
	private @Nullable Interpreter activeInterpreter;

	protected ScriptedMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, String defaultScript) {
		super(type, pos, state);
		this.scriptSource = defaultScript;
		recompile(true);
	}

	protected abstract String machineTypeId();

	protected abstract String defaultScript();

	protected void tickServer(Level level, BlockPos pos, BlockState state) {
		if (actionCooldown > 0) {
			actionCooldown--;
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
			Codeworks.LOGGER.debug("{} script error at {}: {}", machineTypeId(), pos, exception.getMessage());
		} catch (RuntimeException exception) {
			running = false;
			activeInterpreter = null;
			status = "Error: internal fault";
			setChanged();
			Codeworks.LOGGER.warn("{} crashed at {}", machineTypeId(), pos, exception);
		}
	}

	public void openEditor(ServerPlayer player) {
		ServerPlayNetworking.send(
			player,
			new OpenMachineEditorPayload(worldPosition, scriptSource, status, machineTypeId())
		);
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

	protected int cooldownForSpeed() {
		return Math.max(1, 21 - speed);
	}

	protected boolean isOnCooldown() {
		return actionCooldown > 0;
	}

	protected void startActionCooldown() {
		actionCooldown = cooldownForSpeed();
	}

	protected Direction facing() {
		return getBlockState().getValue(DirectionalBlock.FACING);
	}

	protected @Nullable BlockPos findTargetPos(ServerLevel serverLevel) {
		Direction facing = facing();
		for (int step = 1; step <= range; step++) {
			BlockPos targetPos = worldPosition.relative(facing, step);
			BlockState target = serverLevel.getBlockState(targetPos);
			if (!target.isAir()) {
				return targetPos;
			}
		}
		return null;
	}

	protected BlockPos frontPos() {
		return worldPosition.relative(facing());
	}

	protected boolean isUnbreakable(ServerLevel level, BlockPos pos, BlockState state) {
		return state.getDestroySpeed(level, pos) < 0.0F
			|| state.is(Blocks.BEDROCK)
			|| state.is(Blocks.BARRIER)
			|| state.is(BlockTags.FEATURES_CANNOT_REPLACE);
	}

	@Override
	public boolean harvest() throws ScriptException {
		return false;
	}

	@Override
	public boolean place() throws ScriptException {
		return false;
	}

	@Override
	public boolean hasStoredBlock() throws ScriptException {
		return false;
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
	public boolean extended() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		Direction facing = facing();
		for (int step = 1; step <= range; step++) {
			BlockPos targetPos = worldPosition.relative(facing, step);
			BlockState state = serverLevel.getBlockState(targetPos);
			if (state.is(Blocks.PISTON) || state.is(Blocks.STICKY_PISTON)) {
				return state.hasProperty(PistonBaseBlock.EXTENDED) && state.getValue(PistonBaseBlock.EXTENDED);
			}
			if (state.is(Blocks.PISTON_HEAD)) {
				return true;
			}
			if (!state.isAir()) {
				return false;
			}
		}
		return false;
	}

	@Override
	public void move(String direction) throws ScriptException {
		if (level == null || level.isClientSide()) {
			return;
		}
		Direction current = facing();
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
		level.setBlock(worldPosition, getBlockState().setValue(DirectionalBlock.FACING, next), 3);
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
	public void send(String key, String value) throws ScriptException {
		if (level == null) {
			return;
		}
		String safeKey = WirelessBus.sanitizeKey(key);
		if (safeKey.isEmpty()) {
			throw new ScriptException("Send key must be non-empty (a-z, 0-9, _ - . /)");
		}
		WirelessBus.send(level, channel, safeKey, value);
		status = "Sent " + safeKey;
		setChanged();
	}

	@Override
	public String receive(String key) throws ScriptException {
		if (level == null) {
			return "";
		}
		String safeKey = WirelessBus.sanitizeKey(key);
		if (safeKey.isEmpty()) {
			throw new ScriptException("Receive key must be non-empty (a-z, 0-9, _ - . /)");
		}
		return WirelessBus.receive(level, channel, safeKey);
	}

	@Override
	public void pulse(String targetChannel) throws ScriptException {
		if (level == null) {
			return;
		}
		String safe = WirelessBus.sanitizeChannel(targetChannel);
		if (safe.isEmpty()) {
			throw new ScriptException("Pulse channel must be non-empty");
		}
		ChannelAdapterIndex.pulse(level, safe, WirelessKeys.DEFAULT_PULSE_TICKS);
		status = "Pulse " + safe;
		setChanged();
	}

	@Override
	public void setPower(String targetChannel, boolean on) throws ScriptException {
		if (level == null) {
			return;
		}
		String safe = WirelessBus.sanitizeChannel(targetChannel);
		if (safe.isEmpty()) {
			throw new ScriptException("SetPower channel must be non-empty");
		}
		ChannelAdapterIndex.setPower(level, safe, on);
		status = (on ? "Power on " : "Power off ") + safe;
		setChanged();
	}

	@Override
	public boolean getExtended(String targetChannel) throws ScriptException {
		if (level == null) {
			return false;
		}
		String safe = WirelessBus.sanitizeChannel(targetChannel);
		return "1".equals(WirelessBus.receive(level, safe, WirelessKeys.EXTENDED));
	}

	@Override
	public int getPower(String targetChannel) throws ScriptException {
		if (level == null) {
			return 0;
		}
		String safe = WirelessBus.sanitizeChannel(targetChannel);
		String raw = WirelessBus.receive(level, safe, WirelessKeys.POWER);
		if (raw.isEmpty()) {
			return 0;
		}
		try {
			return Math.clamp(Integer.parseInt(raw), 0, 15);
		} catch (NumberFormatException exception) {
			return 0;
		}
	}

	@Override
	public String getHost(String targetChannel) throws ScriptException {
		if (level == null) {
			return "";
		}
		String safe = WirelessBus.sanitizeChannel(targetChannel);
		return WirelessBus.receive(level, safe, WirelessKeys.HOST);
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

	@Override
	public String getChannel() {
		return channel;
	}

	@Override
	public void setChannel(String channel) {
		this.channel = WirelessBus.sanitizeChannel(channel);
		setChanged();
	}

	public String getScriptSource() {
		return scriptSource;
	}

	public String getStatus() {
		return status;
	}

	protected void setStatus(String status) {
		this.status = status;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		scriptSource = input.getStringOr("Script", defaultScript());
		status = input.getStringOr("Status", "Ready");
		running = input.getBooleanOr("Running", true);
		actionCooldown = input.getIntOr("ActionCooldown", input.getIntOr("HarvestCooldown", 0));
		waitTicks = input.getIntOr("WaitTicks", 0);
		speed = Math.clamp(input.getIntOr("Speed", 10), MIN_SPEED, MAX_SPEED);
		range = Math.clamp(input.getIntOr("Range", 1), MIN_RANGE, MAX_RANGE);
		channel = WirelessBus.sanitizeChannel(input.getStringOr("Channel", ""));
		recompile(true);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("Script", scriptSource);
		output.putString("Status", status);
		output.putBoolean("Running", running);
		output.putInt("ActionCooldown", actionCooldown);
		output.putInt("WaitTicks", waitTicks);
		output.putInt("Speed", speed);
		output.putInt("Range", range);
		output.putString("Channel", channel);
	}
}
