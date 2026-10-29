package com.dougymalone.codeworks.script;

import java.util.List;

/**
 * Player-facing docs for the Breaker Help UI.
 * Keep in sync with {@link Interpreter}, {@link Parser}, and {@link HostApi}.
 */
public final class ScriptHelp {
	public record ApiEntry(String signature, String returns, String description, String example) {
	}

	public record SettingEntry(String name, String type, String description, String example) {
	}

	public static final List<ApiEntry> HOST_API = List.of(
		new ApiEntry(
			"bool Harvest()",
			"bool",
			"Break the nearest breakable non-air block within range in front of the Breaker. Honors speed cooldown; returns true if a block was broken.",
			"if (HasBlock()) { Harvest(); }"
		),
		new ApiEntry(
			"bool BreakFront()",
			"bool",
			"Alias of Harvest(). Same behavior and return value.",
			"BreakFront();"
		),
		new ApiEntry(
			"void Log(string message)",
			"void",
			"Show a short status / debug message on the machine (truncated if very long).",
			"Log(\"hello\");"
		),
		new ApiEntry(
			"string Sense()",
			"string",
			"Registry id of the block in front within range, or \"air\" if none.",
			"if (Sense() == \"minecraft:oak_log\") { Harvest(); }"
		),
		new ApiEntry(
			"bool HasBlock()",
			"bool",
			"True if a breakable non-air block is within range in front.",
			"while (HasBlock()) { Harvest(); Wait(5); }"
		),
		new ApiEntry(
			"void Move(string dir)",
			"void",
			"Turn the Breaker. dir: left, right, back, north, south, east, west, up, down.",
			"Move(\"left\");"
		),
		new ApiEntry(
			"void Wait(int ticks)",
			"void",
			"Pause the script across game ticks (cooperative; safe). Prefer as a statement; avoid inside value-returning function calls.",
			"Harvest(); Wait(40);"
		)
	);

	public static final List<ApiEntry> BUILTINS = List.of(
		new ApiEntry("int Min(int a, int b)", "int", "Smaller of two ints.", "int n = Min(3, 7);"),
		new ApiEntry("int Max(int a, int b)", "int", "Larger of two ints.", "int n = Max(speed, 5);"),
		new ApiEntry("int Abs(int x)", "int", "Absolute value of x.", "int n = Abs(i - 3);"),
		new ApiEntry(
			"int Clamp(int v, int lo, int hi)",
			"int",
			"Clamp v into the inclusive range [lo, hi].",
			"speed = Clamp(speed, 1, 20);"
		)
	);

	public static final List<SettingEntry> SETTINGS = List.of(
		new SettingEntry(
			"speed",
			"int 1–20",
			"Break rate. Cooldown ticks ≈ 21 − speed (minimum 1). Read/write like a variable; persisted on the machine.",
			"speed = 15;"
		),
		new SettingEntry(
			"range",
			"int 1–3",
			"How many blocks ahead Harvest / Sense / HasBlock reach. Read/write like a variable; persisted on the machine.",
			"range = 2;"
		)
	);

	public static final List<String> LANGUAGE_NOTES = List.of(
		"C#-like dialect — not full C# / .NET. Statements end with ;",
		"Types: int, bool, string. Arrays: int[] / string[] via new int[n] / new string[n].",
		"Comments: // line and /* block */.",
		"Control: if / else / else if, switch / case / default, while, for, break, continue, return.",
		"switch example: switch (Sense()) { case \"minecraft:dirt\": Harvest(); break; default: Wait(10); break; }",
		"Loops: for (int i = 0; i < 3; i++) { ... } — i++ / ++i / += supported.",
		"Compound ops: += -= *= /= and ++ -- (prefix and postfix).",
		"Arrays: int[] a = new int[3]; a[0] = 1; int n = a.Length;",
		"Strings: s.Length, s.Contains(\"x\"), s.Equals(\"y\"), s.EqualsIgnoreCase(\"Y\").",
		"Functions: void Dig() { Harvest(); }  int Add(int a, int b) { return a + b; }  then Dig(); or int x = Add(1, 2);",
		"Math builtins: Min, Max, Abs, Clamp (pure — no harvest side effects).",
		"Settings speed and range act like variables and are persisted on the machine.",
		"Sandbox: opcode + API budgets each tick; loops capped at "
			+ Interpreter.MAX_LOOP_ITERATIONS
			+ " iters/tick; call depth max "
			+ Interpreter.MAX_CALL_DEPTH
			+ ". while(true) cannot freeze the server.",
		"Only the host API + Math + your functions can be called — no Java escape."
	);

	private ScriptHelp() {
	}
}
