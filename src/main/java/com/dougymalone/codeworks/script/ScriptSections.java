package com.dougymalone.codeworks.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Named script sections for Central Command. Packed for network + NBT as a versioned string.
 * Format v1: {@code 1\ncount\n(name\nenabled\nlen\nsource\n)*}
 */
public final class ScriptSections {
	public static final String MAIN = "main";
	public static final int MAX_SCRIPTS = 8;
	public static final int MAX_NAME_LENGTH = 24;
	public static final int MAX_SCRIPT_LENGTH = 12000;

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

	public static final class Section {
		public String name;
		public String source;
		public boolean enabled;

		public Section(String name, String source, boolean enabled) {
			this.name = name;
			this.source = source == null ? "" : source;
			this.enabled = enabled;
		}

		public Section copy() {
			return new Section(name, source, enabled);
		}
	}

	private ScriptSections() {
	}

	public static List<Section> defaultList() {
		List<Section> list = new ArrayList<>(1);
		list.add(new Section(MAIN, DEFAULT_SCRIPT, true));
		return list;
	}

	public static String sanitizeName(@Nullable String raw) {
		String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
		if (value.length() > MAX_NAME_LENGTH) {
			value = value.substring(0, MAX_NAME_LENGTH);
		}
		return value.replaceAll("[^a-z0-9_\\-]", "");
	}

	public static String uniqueName(List<Section> existing, String preferred) {
		String base = sanitizeName(preferred);
		if (base.isEmpty()) {
			base = "script";
		}
		String candidate = base;
		int n = 2;
		while (containsName(existing, candidate)) {
			String suffix = Integer.toString(n++);
			int maxBase = Math.max(1, MAX_NAME_LENGTH - suffix.length());
			candidate = (base.length() > maxBase ? base.substring(0, maxBase) : base) + suffix;
		}
		return candidate;
	}

	public static boolean containsName(List<Section> sections, String name) {
		String id = sanitizeName(name);
		for (Section section : sections) {
			if (section.name.equals(id)) {
				return true;
			}
		}
		return false;
	}

	public static @Nullable Section find(List<Section> sections, String name) {
		String id = sanitizeName(name);
		for (Section section : sections) {
			if (section.name.equals(id)) {
				return section;
			}
		}
		return null;
	}

	public static String pack(List<Section> sections) {
		List<Section> safe = normalize(sections);
		StringBuilder sb = new StringBuilder();
		sb.append('1').append('\n').append(safe.size()).append('\n');
		for (Section section : safe) {
			String src = section.source;
			if (src.length() > MAX_SCRIPT_LENGTH) {
				src = src.substring(0, MAX_SCRIPT_LENGTH);
			}
			sb.append(section.name).append('\n');
			sb.append(section.enabled ? '1' : '0').append('\n');
			sb.append(src.length()).append('\n');
			sb.append(src).append('\n');
		}
		return sb.toString();
	}

	public static List<Section> unpack(@Nullable String packed) {
		if (packed == null || packed.isBlank()) {
			return defaultList();
		}
		try {
			Cursor cursor = new Cursor(packed);
			String version = cursor.readLine();
			if (!"1".equals(version)) {
				return defaultList();
			}
			int count = Integer.parseInt(cursor.readLine().trim());
			count = Math.min(Math.max(count, 0), MAX_SCRIPTS);
			List<Section> out = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				String name = sanitizeName(cursor.readLine());
				if (name.isEmpty()) {
					name = uniqueName(out, "script");
				} else if (containsName(out, name)) {
					name = uniqueName(out, name);
				}
				String enabledLine = cursor.readLine().trim();
				boolean enabled = !"0".equals(enabledLine);
				int len = Integer.parseInt(cursor.readLine().trim());
				len = Math.min(Math.max(len, 0), MAX_SCRIPT_LENGTH);
				String source = cursor.readChars(len);
				if (cursor.hasRemaining() && cursor.peek() == '\n') {
					cursor.skip(1);
				}
				out.add(new Section(name, source, enabled));
			}
			return normalize(out);
		} catch (RuntimeException ignored) {
			return defaultList();
		}
	}

	/** Ensure at least one section exists; clamp size; sanitize names. */
	public static List<Section> normalize(List<Section> sections) {
		List<Section> out = new ArrayList<>();
		if (sections != null) {
			for (Section section : sections) {
				if (out.size() >= MAX_SCRIPTS) {
					break;
				}
				String name = sanitizeName(section.name);
				if (name.isEmpty()) {
					name = uniqueName(out, out.isEmpty() ? MAIN : "script");
				} else if (containsName(out, name)) {
					name = uniqueName(out, name);
				}
				String source = section.source == null ? "" : section.source;
				if (source.length() > MAX_SCRIPT_LENGTH) {
					source = source.substring(0, MAX_SCRIPT_LENGTH);
				}
				out.add(new Section(name, source, section.enabled));
			}
		}
		if (out.isEmpty()) {
			return defaultList();
		}
		return out;
	}

	private static final class Cursor {
		private final String data;
		private int index;

		Cursor(String data) {
			this.data = data;
		}

		boolean hasRemaining() {
			return index < data.length();
		}

		char peek() {
			return data.charAt(index);
		}

		void skip(int n) {
			index = Math.min(data.length(), index + n);
		}

		String readLine() {
			int start = index;
			while (index < data.length() && data.charAt(index) != '\n') {
				index++;
			}
			String line = data.substring(start, index);
			if (index < data.length() && data.charAt(index) == '\n') {
				index++;
			}
			return line;
		}

		String readChars(int len) {
			int end = Math.min(data.length(), index + len);
			String slice = data.substring(index, end);
			index = end;
			return slice;
		}
	}
}
