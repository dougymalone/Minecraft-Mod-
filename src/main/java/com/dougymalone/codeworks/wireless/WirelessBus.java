package com.dougymalone.codeworks.wireless;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * In-memory wireless signal bus for Codeworks machines.
 * <p>
 * Scope (v1): same dimension / world only, global within that dimension for the
 * lifetime of the server process (not persisted to disk). Empty channel id
 * isolates a machine onto a private empty-channel namespace.
 */
public final class WirelessBus {
	public static final int MAX_CHANNEL_LENGTH = 32;
	public static final int MAX_KEY_LENGTH = 32;
	public static final int MAX_VALUE_LENGTH = 256;

	private static final Map<ResourceKey<Level>, Map<String, Map<String, String>>> BY_DIMENSION =
		new ConcurrentHashMap<>();

	private WirelessBus() {
	}

	public static String sanitizeChannel(@Nullable String raw) {
		String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
		if (value.length() > MAX_CHANNEL_LENGTH) {
			value = value.substring(0, MAX_CHANNEL_LENGTH);
		}
		return value.replaceAll("[^a-z0-9_\\-./]", "");
	}

	public static String sanitizeKey(@Nullable String raw) {
		String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
		if (value.isEmpty()) {
			return "";
		}
		if (value.length() > MAX_KEY_LENGTH) {
			value = value.substring(0, MAX_KEY_LENGTH);
		}
		return value.replaceAll("[^a-z0-9_\\-./]", "");
	}

	public static String sanitizeValue(@Nullable String raw) {
		String value = raw == null ? "" : raw;
		if (value.length() > MAX_VALUE_LENGTH) {
			return value.substring(0, MAX_VALUE_LENGTH);
		}
		return value;
	}

	public static void send(Level level, String channel, String key, String value) {
		if (level.isClientSide()) {
			return;
		}
		String safeKey = sanitizeKey(key);
		if (safeKey.isEmpty()) {
			return;
		}
		String safeChannel = sanitizeChannel(channel);
		String safeValue = sanitizeValue(value);
		BY_DIMENSION
			.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
			.computeIfAbsent(safeChannel, ignored -> new ConcurrentHashMap<>())
			.put(safeKey, safeValue);
	}

	public static String receive(Level level, String channel, String key) {
		if (level.isClientSide()) {
			return "";
		}
		String safeKey = sanitizeKey(key);
		if (safeKey.isEmpty()) {
			return "";
		}
		Map<String, Map<String, String>> channels = BY_DIMENSION.get(level.dimension());
		if (channels == null) {
			return "";
		}
		Map<String, String> values = channels.get(sanitizeChannel(channel));
		if (values == null) {
			return "";
		}
		return values.getOrDefault(safeKey, "");
	}

	/** Clears all signals for a dimension (e.g. on server stop). */
	public static void clearDimension(ResourceKey<Level> dimension) {
		BY_DIMENSION.remove(dimension);
	}

	public static void clearAll() {
		BY_DIMENSION.clear();
	}
}
