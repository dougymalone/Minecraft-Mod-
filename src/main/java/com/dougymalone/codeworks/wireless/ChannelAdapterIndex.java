package com.dougymalone.codeworks.wireless;

import com.dougymalone.codeworks.block.entity.ChannelAdapterBlockEntity;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Tracks loaded Channel Adapters so Pulse/SetPower can reach every adapter on a
 * channel (Breaker, Placer, and any future machine write through the same bus).
 * Unloaded chunks are skipped until the adapter loads and syncs from the bus.
 */
public final class ChannelAdapterIndex {
	private static final Map<ResourceKey<Level>, Map<String, Map<BlockPos, ChannelAdapterBlockEntity>>> BY_DIMENSION =
		new ConcurrentHashMap<>();

	private ChannelAdapterIndex() {
	}

	public static void register(ChannelAdapterBlockEntity adapter) {
		if (adapter.getLevel() == null || adapter.getLevel().isClientSide()) {
			return;
		}
		String channel = WirelessBus.sanitizeChannel(adapter.getChannel());
		BY_DIMENSION
			.computeIfAbsent(adapter.getLevel().dimension(), ignored -> new ConcurrentHashMap<>())
			.computeIfAbsent(channel, ignored -> new ConcurrentHashMap<>())
			.put(adapter.getBlockPos().immutable(), adapter);
	}

	public static void unregister(ChannelAdapterBlockEntity adapter) {
		if (adapter.getLevel() == null) {
			return;
		}
		Map<String, Map<BlockPos, ChannelAdapterBlockEntity>> channels =
			BY_DIMENSION.get(adapter.getLevel().dimension());
		if (channels == null) {
			return;
		}
		String channel = WirelessBus.sanitizeChannel(adapter.getChannel());
		Map<BlockPos, ChannelAdapterBlockEntity> adapters = channels.get(channel);
		if (adapters != null) {
			adapters.remove(adapter.getBlockPos());
			if (adapters.isEmpty()) {
				channels.remove(channel);
			}
		}
	}

	/** Re-index after a channel rename. */
	public static void reindex(ChannelAdapterBlockEntity adapter, String previousChannel) {
		if (adapter.getLevel() == null || adapter.getLevel().isClientSide()) {
			return;
		}
		Map<String, Map<BlockPos, ChannelAdapterBlockEntity>> channels =
			BY_DIMENSION.get(adapter.getLevel().dimension());
		if (channels != null) {
			Map<BlockPos, ChannelAdapterBlockEntity> adapters =
				channels.get(WirelessBus.sanitizeChannel(previousChannel));
			if (adapters != null) {
				adapters.remove(adapter.getBlockPos());
			}
		}
		register(adapter);
	}

	public static void pulse(Level level, String channel, int ticks) {
		if (level.isClientSide()) {
			return;
		}
		String safe = WirelessBus.sanitizeChannel(channel);
		WirelessBus.send(level, safe, WirelessKeys.CMD_PULSE, String.valueOf(Math.max(1, ticks)));
		forEach(level, safe, adapter -> adapter.beginPulse(ticks));
	}

	public static void setPower(Level level, String channel, boolean on) {
		if (level.isClientSide()) {
			return;
		}
		String safe = WirelessBus.sanitizeChannel(channel);
		WirelessBus.send(level, safe, WirelessKeys.CMD_POWER, on ? "1" : "0");
		forEach(level, safe, adapter -> adapter.setSustainedPower(on));
	}

	public static void clearDimension(ResourceKey<Level> dimension) {
		BY_DIMENSION.remove(dimension);
	}

	public static void clearAll() {
		BY_DIMENSION.clear();
	}

	private static void forEach(Level level, String channel, java.util.function.Consumer<ChannelAdapterBlockEntity> action) {
		Map<String, Map<BlockPos, ChannelAdapterBlockEntity>> channels = BY_DIMENSION.get(level.dimension());
		if (channels == null) {
			return;
		}
		Map<BlockPos, ChannelAdapterBlockEntity> adapters = channels.get(channel);
		if (adapters == null) {
			return;
		}
		for (ChannelAdapterBlockEntity adapter : Set.copyOf(adapters.values())) {
			if (adapter.isRemoved() || adapter.getLevel() != level) {
				adapters.remove(adapter.getBlockPos());
				continue;
			}
			action.accept(adapter);
		}
	}
}
