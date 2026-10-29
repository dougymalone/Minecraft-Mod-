package com.dougymalone.codeworks.block.entity;

import com.dougymalone.codeworks.block.ChannelAdapterBlock;
import com.dougymalone.codeworks.network.OpenChannelAdapterPayload;
import com.dougymalone.codeworks.registry.ModBlockEntities;
import com.dougymalone.codeworks.wireless.ChannelAdapterIndex;
import com.dougymalone.codeworks.wireless.WirelessBus;
import com.dougymalone.codeworks.wireless.WirelessKeys;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Reads host piston/redstone state onto the shared {@link WirelessBus} and
 * emits redstone when scripts (Breaker, Placer, or any future machine) call
 * Pulse/SetPower on this adapter's channel.
 */
public class ChannelAdapterBlockEntity extends BlockEntity {
	public static final int MAX_NAME_LENGTH = 32;

	private String channel = "";
	private String displayName = "";
	private boolean sustainedPower;
	private int pulseTicks;
	private boolean indexed;
	private String lastPublishedExtended = "";
	private String lastPublishedPower = "";
	private String lastPublishedHost = "";

	public ChannelAdapterBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CHANNEL_ADAPTER, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, ChannelAdapterBlockEntity adapter) {
		adapter.tickServer(level, pos, state);
	}

	private void tickServer(Level level, BlockPos pos, BlockState state) {
		if (!indexed) {
			ChannelAdapterIndex.register(this);
			indexed = true;
			syncCommandsFromBus();
		}

		if (pulseTicks > 0) {
			pulseTicks--;
			if (pulseTicks == 0 && !sustainedPower) {
				setPoweredState(false);
			}
		}

		publishHostState();
	}

	public void onNeighborChanged() {
		publishHostState();
	}

	public void openConfigurator(ServerPlayer player) {
		ServerPlayNetworking.send(player, new OpenChannelAdapterPayload(worldPosition, channel, displayName, statusLine()));
	}

	public void applyConfigFromPlayer(String newChannel, String newDisplayName) {
		String previous = channel;
		channel = WirelessBus.sanitizeChannel(newChannel);
		displayName = sanitizeName(newDisplayName);
		if (!previous.equals(channel)) {
			ChannelAdapterIndex.reindex(this, previous);
		}
		setChanged();
		publishHostState();
	}

	public String getChannel() {
		return channel;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void beginPulse(int ticks) {
		pulseTicks = Math.max(1, ticks);
		setPoweredState(true);
	}

	public void setSustainedPower(boolean on) {
		sustainedPower = on;
		if (on) {
			setPoweredState(true);
		} else if (pulseTicks <= 0) {
			setPoweredState(false);
		}
		setChanged();
	}

	private void syncCommandsFromBus() {
		if (level == null) {
			return;
		}
		String powerCmd = WirelessBus.receive(level, channel, WirelessKeys.CMD_POWER);
		sustainedPower = "1".equals(powerCmd);
		String pulseCmd = WirelessBus.receive(level, channel, WirelessKeys.CMD_PULSE);
		if (!pulseCmd.isEmpty()) {
			try {
				int ticks = Integer.parseInt(pulseCmd);
				if (ticks > 0) {
					beginPulse(ticks);
				}
			} catch (NumberFormatException ignored) {
				// ignore malformed bus values
			}
		} else if (sustainedPower) {
			setPoweredState(true);
		}
	}

	private void publishHostState() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		Direction towardHost = getBlockState().getValue(ChannelAdapterBlock.FACING);
		BlockPos hostPos = worldPosition.relative(towardHost);
		BlockState host = serverLevel.getBlockState(hostPos);

		boolean extended = false;
		if (host.is(Blocks.PISTON) || host.is(Blocks.STICKY_PISTON)) {
			extended = host.hasProperty(PistonBaseBlock.EXTENDED) && host.getValue(PistonBaseBlock.EXTENDED);
		} else if (host.is(Blocks.PISTON_HEAD)) {
			extended = true;
		}

		int power = serverLevel.getBestNeighborSignal(hostPos);
		var hostId = BuiltInRegistries.BLOCK.getKey(host.getBlock());
		String hostKey = hostId == null ? "air" : hostId.toString();
		String extendedValue = extended ? "1" : "0";
		String powerValue = Integer.toString(Math.clamp(power, 0, 15));
		boolean emitting = getBlockState().getValue(ChannelAdapterBlock.POWERED);

		if (!extendedValue.equals(lastPublishedExtended)) {
			WirelessBus.send(serverLevel, channel, WirelessKeys.EXTENDED, extendedValue);
			lastPublishedExtended = extendedValue;
		}
		if (!powerValue.equals(lastPublishedPower)) {
			WirelessBus.send(serverLevel, channel, WirelessKeys.POWER, powerValue);
			lastPublishedPower = powerValue;
		}
		if (!hostKey.equals(lastPublishedHost)) {
			WirelessBus.send(serverLevel, channel, WirelessKeys.HOST, hostKey);
			lastPublishedHost = hostKey;
		}
		WirelessBus.send(serverLevel, channel, WirelessKeys.POWERED, emitting ? "1" : "0");
	}

	private void setPoweredState(boolean powered) {
		if (level == null || level.isClientSide()) {
			return;
		}
		BlockState state = getBlockState();
		if (state.getValue(ChannelAdapterBlock.POWERED) == powered) {
			return;
		}
		level.setBlock(worldPosition, state.setValue(ChannelAdapterBlock.POWERED, powered), 3);
		ChannelAdapterBlock.updateNeighbors(level, worldPosition, level.getBlockState(worldPosition));
		setChanged();
	}

	private String statusLine() {
		String label = displayName.isEmpty() ? "Adapter" : displayName;
		return label + " @" + (channel.isEmpty() ? "(no channel)" : channel);
	}

	private static String sanitizeName(String raw) {
		String value = raw == null ? "" : raw.trim();
		if (value.length() > MAX_NAME_LENGTH) {
			return value.substring(0, MAX_NAME_LENGTH);
		}
		return value;
	}

	@Override
	public void setRemoved() {
		ChannelAdapterIndex.unregister(this);
		indexed = false;
		super.setRemoved();
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		indexed = false;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		channel = WirelessBus.sanitizeChannel(input.getStringOr("Channel", ""));
		displayName = sanitizeName(input.getStringOr("DisplayName", ""));
		sustainedPower = input.getBooleanOr("SustainedPower", false);
		pulseTicks = input.getIntOr("PulseTicks", 0);
		indexed = false;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("Channel", channel);
		output.putString("DisplayName", displayName);
		output.putBoolean("SustainedPower", sustainedPower);
		output.putInt("PulseTicks", pulseTicks);
	}
}
