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
import com.dougymalone.codeworks.script.ScriptSections;
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
 * Sole script host. Owns channel registry + multiple named script sections.
 * Every enabled, non-empty, successfully parsed section ticks each server tick.
 */
public class CentralCommandBlockEntity extends BlockEntity implements HostApi {
	public static final int MAX_SCRIPT_LENGTH = ScriptSections.MAX_SCRIPT_LENGTH;
	public static final int MAX_CHANNELS = 32;
	public static final String DEFAULT_SCRIPT = ScriptSections.DEFAULT_SCRIPT;

	private static final class Slot {
		String name;
		String source;
		boolean enabled;
		Ast.@Nullable Program compiled;
		@Nullable Interpreter interpreter;
		int waitTicks;
		boolean parseOk = true;
		@Nullable String parseError;

		Slot(ScriptSections.Section section) {
			this.name = section.name;
			this.source = section.source;
			this.enabled = section.enabled;
		}

		ScriptSections.Section toSection() {
			return new ScriptSections.Section(name, source, enabled);
		}
	}

	private final List<String> channels = new ArrayList<>();
	private final List<Slot> slots = new ArrayList<>();
	private String selectedScript = ScriptSections.MAIN;
	private String status = "Ready";
	private @Nullable Slot activeSlot;
	private boolean indexed;

	public CentralCommandBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CENTRAL_COMMAND, pos, state);
		replaceSlots(ScriptSections.defaultList(), true);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CentralCommandBlockEntity command) {
		command.tickServer();
	}

	private void tickServer() {
		ensureIndexed();
		for (Slot slot : slots) {
			if (!slot.enabled || slot.source.isBlank() || !slot.parseOk || slot.compiled == null) {
				continue;
			}
			activeSlot = slot;
			try {
				if (slot.waitTicks > 0) {
					slot.waitTicks--;
					continue;
				}
				try {
					if (slot.interpreter == null || slot.interpreter.isFinished()) {
						slot.interpreter = new Interpreter(slot.compiled, this);
						slot.interpreter.start();
					} else {
						slot.interpreter.resume();
					}
				} catch (ScriptYield ignored) {
					// keep interpreter; Wait set slot.waitTicks via host
				} catch (ScriptException exception) {
					slot.interpreter = null;
					status = "[" + slot.name + "] Error: " + exception.getMessage();
					setChanged();
					Codeworks.LOGGER.debug("Central Command script '{}' error at {}: {}", slot.name, worldPosition, exception.getMessage());
				} catch (RuntimeException exception) {
					slot.interpreter = null;
					status = "[" + slot.name + "] Error: internal fault";
					setChanged();
					Codeworks.LOGGER.warn("Central Command script '{}' crashed at {}", slot.name, worldPosition, exception);
				}
			} finally {
				activeSlot = null;
			}
		}
	}

	public void openUi(ServerPlayer player) {
		ServerPlayNetworking.send(player, new OpenCentralCommandPayload(
			worldPosition,
			status,
			String.join("\n", channels),
			countsCsv(),
			ScriptSections.pack(toSections()),
			selectedScript
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

	public void applyFromPlayer(String scriptsPack, List<String> newChannels, String selected) {
		channels.clear();
		for (String raw : newChannels) {
			String id = ChannelIds.sanitize(raw);
			if (!id.isEmpty() && !channels.contains(id) && channels.size() < MAX_CHANNELS) {
				channels.add(id);
			}
		}
		List<ScriptSections.Section> sections = ScriptSections.unpack(scriptsPack);
		for (ScriptSections.Section section : sections) {
			if (section.source.length() > MAX_SCRIPT_LENGTH) {
				status = "Error: script too long in " + section.name;
				setChanged();
				return;
			}
		}
		replaceSlots(sections, true);
		String sel = ScriptSections.sanitizeName(selected);
		if (ScriptSections.containsName(toSections(), sel)) {
			selectedScript = sel;
		} else {
			selectedScript = slots.getFirst().name;
		}
		refreshStatusSummary();
		ensureIndexed();
		setChanged();
	}

	private List<ScriptSections.Section> toSections() {
		List<ScriptSections.Section> list = new ArrayList<>(slots.size());
		for (Slot slot : slots) {
			list.add(slot.toSection());
		}
		return list;
	}

	private void replaceSlots(List<ScriptSections.Section> sections, boolean resetRuntime) {
		slots.clear();
		for (ScriptSections.Section section : ScriptSections.normalize(sections)) {
			Slot slot = new Slot(section);
			compileSlot(slot, resetRuntime);
			slots.add(slot);
		}
		if (slots.isEmpty()) {
			Slot main = new Slot(new ScriptSections.Section(ScriptSections.MAIN, DEFAULT_SCRIPT, true));
			compileSlot(main, true);
			slots.add(main);
		}
		if (!ScriptSections.containsName(toSections(), selectedScript)) {
			selectedScript = slots.getFirst().name;
		}
	}

	private void compileSlot(Slot slot, boolean resetRuntime) {
		if (slot.source.isBlank()) {
			slot.compiled = null;
			slot.parseOk = true;
			slot.parseError = null;
			slot.interpreter = null;
			slot.waitTicks = 0;
			return;
		}
		try {
			slot.compiled = Interpreter.compile(slot.source);
			slot.parseOk = true;
			slot.parseError = null;
			if (resetRuntime) {
				slot.waitTicks = 0;
				slot.interpreter = null;
			}
		} catch (ScriptException exception) {
			slot.compiled = null;
			slot.parseOk = false;
			slot.parseError = exception.getMessage();
			slot.interpreter = null;
			slot.waitTicks = 0;
		}
	}

	private void refreshStatusSummary() {
		int enabled = 0;
		int errors = 0;
		for (Slot slot : slots) {
			if (slot.enabled && !slot.source.isBlank()) {
				enabled++;
			}
			if (!slot.parseOk) {
				errors++;
			}
		}
		if (errors > 0) {
			for (Slot slot : slots) {
				if (!slot.parseOk) {
					status = "[" + slot.name + "] Parse error: " + slot.parseError;
					return;
				}
			}
		}
		String channelPart = channels.isEmpty()
			? "no channels"
			: channels.size() + " channel(s): " + String.join(", ", channels);
		status = "OK — " + slots.size() + " script(s), " + enabled + " enabled — " + channelPart;
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
		for (Slot slot : slots) {
			if (slot.name.equals(selectedScript)) {
				return slot.source;
			}
		}
		return slots.isEmpty() ? DEFAULT_SCRIPT : slots.getFirst().source;
	}

	public String getStatus() {
		return status;
	}

	@Override
	public void log(String message) {
		String body = message == null ? "" : (message.length() > 100 ? message.substring(0, 100) : message);
		String prefix = activeSlot != null ? "[" + activeSlot.name + "] " : "";
		status = prefix + body;
		setChanged();
	}

	@Override
	public void waitTicks(int ticks) throws ScriptException {
		if (ticks < 0) {
			throw new ScriptException("Wait ticks must be >= 0");
		}
		int capped = Math.min(ticks, 20 * 60 * 5);
		if (activeSlot != null) {
			activeSlot.waitTicks = capped;
		}
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
		String prefix = activeSlot != null ? "[" + activeSlot.name + "] " : "";
		status = prefix + (any ? "Break ok @" + id : "Break miss @" + id);
		setChanged();
		return any;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		status = input.getStringOr("Status", "Ready");
		selectedScript = ScriptSections.sanitizeName(input.getStringOr("SelectedScript", ScriptSections.MAIN));
		channels.clear();
		String packedChannels = input.getStringOr("Channels", "");
		if (!packedChannels.isEmpty()) {
			for (String part : packedChannels.split("\n")) {
				String id = ChannelIds.sanitize(part);
				if (!id.isEmpty() && !channels.contains(id) && channels.size() < MAX_CHANNELS) {
					channels.add(id);
				}
			}
		}

		String scriptsPack = input.getStringOr("Scripts", "");
		List<ScriptSections.Section> loaded;
		if (!scriptsPack.isBlank()) {
			loaded = ScriptSections.unpack(scriptsPack);
		} else {
			// Migrate legacy single Script field → main
			String legacy = input.getStringOr("Script", DEFAULT_SCRIPT);
			loaded = List.of(new ScriptSections.Section(ScriptSections.MAIN, legacy, true));
		}
		replaceSlots(loaded, true);
		if (!ScriptSections.containsName(toSections(), selectedScript)) {
			selectedScript = slots.getFirst().name;
		}
		indexed = false;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("Scripts", ScriptSections.pack(toSections()));
		// Legacy single-field mirror of selected / main source for older readers
		output.putString("Script", getScriptSource());
		output.putString("SelectedScript", selectedScript);
		output.putString("Status", status);
		output.putString("Channels", String.join("\n", channels));
	}
}
