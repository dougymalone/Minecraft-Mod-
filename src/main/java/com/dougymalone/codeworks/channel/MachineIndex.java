package com.dougymalone.codeworks.channel;

import com.dougymalone.codeworks.block.entity.BreakerBlockEntity;
import com.dougymalone.codeworks.block.entity.CentralCommandBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Tracks loaded actuator machines and Central Commands by dimension for fan-out / discovery.
 */
public final class MachineIndex {
	private static final Map<ResourceKey<Level>, Map<String, Map<BlockPos, BreakerBlockEntity>>> BREAKERS =
		new ConcurrentHashMap<>();
	private static final Map<ResourceKey<Level>, Map<BlockPos, CentralCommandBlockEntity>> COMMANDS =
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

	public static void registerCommand(CentralCommandBlockEntity command) {
		if (command.getLevel() == null || command.getLevel().isClientSide()) {
			return;
		}
		COMMANDS
			.computeIfAbsent(command.getLevel().dimension(), ignored -> new ConcurrentHashMap<>())
			.put(command.getBlockPos().immutable(), command);
	}

	public static void unregisterCommand(CentralCommandBlockEntity command) {
		if (command.getLevel() == null) {
			return;
		}
		Map<BlockPos, CentralCommandBlockEntity> map = COMMANDS.get(command.getLevel().dimension());
		if (map != null) {
			map.remove(command.getBlockPos());
		}
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

	/** Union of channel ids registered on every loaded Central Command in this dimension (stable order). */
	public static List<String> knownChannels(Level level) {
		List<String> out = new ArrayList<>();
		if (level.isClientSide()) {
			return out;
		}
		Map<BlockPos, CentralCommandBlockEntity> map = COMMANDS.get(level.dimension());
		if (map == null) {
			return out;
		}
		Set<String> seen = new LinkedHashSet<>();
		for (CentralCommandBlockEntity command : List.copyOf(map.values())) {
			if (command.isRemoved() || command.getLevel() != level) {
				map.remove(command.getBlockPos());
				continue;
			}
			for (String channel : command.getChannels()) {
				seen.add(channel);
			}
		}
		out.addAll(seen);
		return out;
	}

	public static void clearAll() {
		BREAKERS.clear();
		COMMANDS.clear();
	}
}
