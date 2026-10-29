package com.dougymalone.codeworks.script;

import java.util.List;

/**
 * Player-facing docs for the Breaker Help UI.
 * Keep in sync with {@link Interpreter} and {@link HostApi}.
 */
public final class ScriptHelp {
	public record ApiEntry(String signature, String returns, String description, String example) {
	}

	public record SettingEntry(String name, String type, String description, String example) {
	}

	public static final List<ApiEntry> HOST_API = List.of(
		new ApiEntry(
			"Harvest()",
			"bool",
			"Break the nearest breakable block within range in front of the Breaker. Honors speed cooldown. Returns true if a block broke.",
			"if (HasBlock()) { Harvest(); }"
		),
		new ApiEntry(
			"BreakFront()",
			"bool",
			"Alias of Harvest() — same break-in-front behavior.",
			"BreakFront();"
		),
		new ApiEntry(
			"Log(string message)",
			"void",
			"Show a short status / debug message on the machine (visible in the editor status line).",
			"Log(\"hello\");"
		),
		new ApiEntry(
			"Sense()",
			"string",
			"Id of the block in front within range (e.g. minecraft:dirt), or \"air\" if none.",
			"if (Sense() == \"minecraft:oak_log\") { Harvest(); }"
		),
		new ApiEntry(
			"HasBlock()",
			"bool",
			"True if there is a non-air, breakable block within range in front.",
			"while (HasBlock()) { Harvest(); Wait(5); }"
		),
		new ApiEntry(
			"Move(string dir)",
			"void",
			"Turn the Breaker. dir: left, right, back, north, south, east, west, up, down.",
			"Move(\"left\"); Harvest();"
		),
		new ApiEntry(
			"Wait(int ticks)",
			"void",
			"Pause this script for N game ticks without freezing the server, then continue.",
			"Harvest(); Wait(40); Move(\"right\");"
		)
	);

	public static final List<SettingEntry> SETTINGS = List.of(
		new SettingEntry(
			"speed",
			"int 1–20",
			"Break rate. Higher = shorter cooldown between Harvests. Cooldown ticks ≈ 21 − speed (min 1).",
			"speed = 15;"
		),
		new SettingEntry(
			"range",
			"int 1–3",
			"How many blocks ahead Harvest/Sense/HasBlock can reach.",
			"range = 2;"
		),
		new SettingEntry(
			"autoPickup",
			"bool",
			"When true, after a break, suck nearby drops into a chest/container behind the Breaker (opposite facing).",
			"autoPickup = true;"
		)
	);

	public static final List<String> LANGUAGE_NOTES = List.of(
		"C#-like dialect — not full C# / .NET.",
		"Types: int, bool, string. // line comments. Statements end with ;",
		"Supported: decls, assignment, if/else, while, for, { } blocks, operators + - * / == != < <= > >= && || !",
		"for example: for (int i = 0; i < 3; i = i + 1) { Harvest(); }",
		"No methods/classes/arrays/switch. Only the host API above may be called.",
		"speed / range / autoPickup are special settings (assign or read like variables; persisted on the machine).",
		"Safety: each tick has an opcode + API budget; while/for stop after "
			+ Interpreter.MAX_LOOP_ITERATIONS
			+ " iterations per tick slice. while(true) cannot freeze the server — you get a budget/limit error.",
		"Wait(n) yields across ticks (safe). Prefer Wait or speed cooldown over tight empty loops."
	);

	private ScriptHelp() {
	}
}
