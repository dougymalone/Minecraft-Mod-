package com.dougymalone.codeworks.script;

import java.util.List;

/**
 * Player-facing docs for the in-game Coder help UI.
 * Keep in sync with {@link Interpreter} host whitelist and {@link Parser} grammar.
 */
public final class ScriptHelp {
	public record ApiEntry(String signature, String returns, String description) {
	}

	public static final List<ApiEntry> HOST_API = List.of(
		new ApiEntry(
			"Harvest()",
			"bool",
			"Break the block in front of this Coder (facing direction). Returns true if something was broken."
		),
		new ApiEntry(
			"BreakFront()",
			"bool",
			"Same as Harvest() — alternate name for breaking the front block."
		),
		new ApiEntry(
			"Log(string message)",
			"void",
			"Show a short status / debug message on the machine (also visible when you reopen the editor)."
		)
	);

	public static final List<String> LANGUAGE_NOTES = List.of(
		"This is a tiny C#-like dialect, not full C#.",
		"Types: int, bool, string. Literals: numbers, \"strings\", true/false.",
		"Supported: variable decls, assignment, if/else, { } blocks, // comments.",
		"Operators: + - * / == != < <= > >= && || !",
		"Not supported yet: while, for, methods/classes, arrays, null, switch.",
		"Scripts are top-level statements (no void Main() needed). End lines with ;",
		"Only the host API above can be called — no Java reflection or file I/O.",
		"Each run has an opcode and API-call budget; Harvest has a cooldown."
	);

	private ScriptHelp() {
	}
}
