package com.dougymalone.codeworks.block.entity;

import com.dougymalone.codeworks.Codeworks;
import com.dougymalone.codeworks.channel.ChannelIds;
import com.dougymalone.codeworks.channel.MachineIndex;
import com.dougymalone.codeworks.network.OpenCentralCommandPayload;
import com.dougymalone.codeworks.registry.ModBlockEntities;
import com.dougymalone.codeworks.script.Ast;
import com.dougymalone.codeworks.script.HostApi;
import com.dougymalone.codeworks.script.Interpreter;
import com.dougymalone.codeworks.script.ScriptException;
import com.dougymalone.codeworks.script.ScriptYield;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Sole script host. Owns the channel registry and commands Breakers (and later Placers).
 */
public class CentralCommandBlockEntity extends BlockEntity implements HostApi {
	public static final int MAX_SCRIPT_LENGTH = 12000;
	public static final int MAX_CHANNELS = 32;

	public static final String DEFAULT_SCRIPT = """
		// Central Command — only place scripts live
		// 1) Channels tab: add several ids (e.g. mine + quarry)
		// 2) Point each Breaker at ONE of those channels
		// 3) This script can drive many channels in one tick
		if (HasChannel("mine") && BreakerCount("mine") > 0) {
		  if (Break("mine")) {
		    Log("mine ok");
		  }
		}
		if (HasChannel("quarry") && BreakerCount("quarry") > 0) {
		  if (Break("quarry")) {
		    Log("quarry ok");
		  }
		}
		Wait(40);
		""";

	private final List<String> channels = new ArrayList<>();
	private String scriptSource = DEFAULT_SCRIPT;
	private String status = "Ready";
	private boolean running = true;
	private int waitTicks;
	private Ast.@Nullable Program compiled;
	private @Nullable Interpreter activeInterpreter;
	private boolean indexed;

	public CentralCommandBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CENTRAL_COMMAND, pos, state);
		recompile(true);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CentralCommandBlockEntity command) {
		command.tickServer();
	}

	private void tickServer() {
		ensureIndexed();
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
			// keep interpreter
		} catch (ScriptException exception) {
			running = false;
			activeInterpreter = null;
			status = "Error: " + exception.getMessage();
			setChanged();
			Codeworks.LOGGER.debug("Central Command script error at {}: {}", worldPosition, exception.getMessage());
		} catch (RuntimeException exception) {
			running = false;
			activeInterpreter = null;
			status = "Error: internal fault";
			setChanged();
			Codeworks.LOGGER.warn("Central Command crashed at {}", worldPosition, exception);
		}
	}

	public void openUi(ServerPlayer player) {
		ServerPlayNetworking.send(player, new OpenCentralCommandPayload(
			worldPosition,
			scriptSource,
			status,
			String.join("\n", channels),
			countsCsv()
		));
	}

	private String countsCsv() {
		if (level == null) {
			return "";
		}
		List<String> counts = new ArrayList<>(channels.size());
		for (String channel : channels) {
			counts.add(Integer.toString(MachineIndex.breakerCount(level, channel)));
		}
		return String.join("\n", counts);
	}

	public void applyFromPlayer(String script, List<String> newChannels) {
		if (script.length() > MAX_SCRIPT_LENGTH) {
			status = "Error: script too long";
			setChanged();
			return;
		}
		channels.clear();
		for (String raw : newChannels) {
			String id = ChannelIds.sanitize(raw);
			if (!id.isEmpty() && !channels.contains(id) && channels.size() < MAX_CHANNELS) {
				channels.add(id);
			}
		}
		scriptSource = script;
		recompile(true);
		ensureIndexed();
		setChanged();
	}

	private void ensureIndexed() {
		if (!indexed && level != null && !level.isClientSide()) {
			MachineIndex.registerCommand(this);
			indexed = true;
		}
	}

	@Override
	public void setRemoved() {
		MachineIndex.unregisterCommand(this);
		indexed = false;
		super.setRemoved();
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		indexed = false;
	}

	public List<String> getChannels() {
		return List.copyOf(channels);
	}

	public String getScriptSource() {
		return scriptSource;
	}

	public String getStatus() {
		return status;
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

	@Override
	public void log(String message) {
		status = message == null ? "" : (message.length() > 120 ? message.substring(0, 120) : message);
		setChanged();
	}

	@Override
	public void waitTicks(int ticks) throws ScriptException {
		if (ticks < 0) {
			throw new ScriptException("Wait ticks must be >= 0");
		}
		waitTicks = Math.min(ticks, 20 * 60 * 5);
	}

	@Override
	public boolean hasChannel(String channel) {
		String id = ChannelIds.sanitize(channel);
		return !id.isEmpty() && channels.contains(id);
	}

	@Override
	public int breakerCount(String channel) {
		if (level == null) {
			return 0;
		}
		return MachineIndex.breakerCount(level, channel);
	}

	@Override
	public boolean breakFront(String channel) throws ScriptException {
		String id = ChannelIds.sanitize(channel);
		if (id.isEmpty()) {
			throw new ScriptException("Break channel must be non-empty");
		}
		if (!channels.contains(id)) {
			status = "Unknown channel " + id;
			setChanged();
			return false;
		}
		if (level == null) {
			return false;
		}
		boolean any = false;
		for (BreakerBlockEntity breaker : MachineIndex.breakersOn(level, id)) {
			if (breaker.tryHarvest()) {
				any = true;
			}
		}
		status = any ? "Break ok @" + id : "Break miss @" + id;
		setChanged();
		return any;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		scriptSource = input.getStringOr("Script", DEFAULT_SCRIPT);
		status = input.getStringOr("Status", "Ready");
		running = input.getBooleanOr("Running", true);
		waitTicks = input.getIntOr("WaitTicks", 0);
		channels.clear();
		String packed = input.getStringOr("Channels", "");
		if (!packed.isEmpty()) {
			for (String part : packed.split("\n")) {
				String id = ChannelIds.sanitize(part);
				if (!id.isEmpty() && !channels.contains(id) && channels.size() < MAX_CHANNELS) {
					channels.add(id);
				}
			}
		}
		indexed = false;
		recompile(true);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("Script", scriptSource);
		output.putString("Status", status);
		output.putBoolean("Running", running);
		output.putInt("WaitTicks", waitTicks);
		output.putString("Channels", String.join("\n", channels));
	}
}
