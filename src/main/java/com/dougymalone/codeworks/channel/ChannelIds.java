package com.dougymalone.codeworks.channel;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class ChannelIds {
	public static final int MAX_LENGTH = 32;

	private ChannelIds() {
	}

	public static String sanitize(@Nullable String raw) {
		String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
		if (value.length() > MAX_LENGTH) {
			value = value.substring(0, MAX_LENGTH);
		}
		return value.replaceAll("[^a-z0-9_\\-./]", "");
	}
}
