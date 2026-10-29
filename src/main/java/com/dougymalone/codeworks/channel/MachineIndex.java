package com.dougymalone.codeworks.channel;

import com.dougymalone.codeworks.block.entity.BreakerBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Tracks loaded actuator machines by dimension + channel for Central Command fan-out.
 */
public final class MachineIndex {
	private static final Map<ResourceKey<Level>, Map<String, Map<BlockPos, BreakerBlockEntity>>> BREAKERS =
		new ConcurrentHashMap<>();

	private MachineIndex() {
	}

	public static void registerBreaker(BreakerBlockEntity breaker) {
		if (breaker.getLevel() == null || breaker.getLevel().isClientSide()) {
			return;
		}
		String channel = ChannelIds.sanitize(breaker.getChannel());
		if (channel.isEmpty()) {
			return;
		}
		BREAKERS
			.computeIfAbsent(breaker.getLevel().dimension(), ignored -> new ConcurrentHashMap<>())
			.computeIfAbsent(channel, ignored -> new ConcurrentHashMap<>())
			.put(breaker.getBlockPos().immutable(), breaker);
	}

	public static void unregisterBreaker(BreakerBlockEntity breaker) {
		if (breaker.getLevel() == null) {
			return;
		}
		Map<String, Map<BlockPos, BreakerBlockEntity>> byChannel = BREAKERS.get(breaker.getLevel().dimension());
		if (byChannel == null) {
			return;
		}
		for (Map<BlockPos, BreakerBlockEntity> map : byChannel.values()) {
			map.remove(breaker.getBlockPos());
		}
	}

	public static void reindexBreaker(BreakerBlockEntity breaker) {
		unregisterBreaker(breaker);
		registerBreaker(breaker);
	}

	public static int breakerCount(Level level, String channel) {
		if (level.isClientSide()) {
			return 0;
		}
		Map<String, Map<BlockPos, BreakerBlockEntity>> byChannel = BREAKERS.get(level.dimension());
		if (byChannel == null) {
			return 0;
		}
		Map<BlockPos, BreakerBlockEntity> map = byChannel.get(ChannelIds.sanitize(channel));
		if (map == null) {
			return 0;
		}
		map.entrySet().removeIf(entry -> entry.getValue().isRemoved() || entry.getValue().getLevel() != level);
		return map.size();
	}

	public static List<BreakerBlockEntity> breakersOn(Level level, String channel) {
		List<BreakerBlockEntity> out = new ArrayList<>();
		if (level.isClientSide()) {
			return out;
		}
		Map<String, Map<BlockPos, BreakerBlockEntity>> byChannel = BREAKERS.get(level.dimension());
		if (byChannel == null) {
			return out;
		}
		Map<BlockPos, BreakerBlockEntity> map = byChannel.get(ChannelIds.sanitize(channel));
		if (map == null) {
			return out;
		}
		for (BreakerBlockEntity breaker : List.copyOf(map.values())) {
			if (breaker.isRemoved() || breaker.getLevel() != level) {
				map.remove(breaker.getBlockPos());
				continue;
			}
			out.add(breaker);
		}
		return out;
	}

	public static void clearAll() {
		BREAKERS.clear();
	}
}
