package com.dougymalone.codeworks.script;

import java.util.List;

/** Help copy for the Central Command Help tab. */
public final class ScriptHelp {
	public record ApiEntry(String signature, String returns, String description, String example) {
	}

	public static final List<ApiEntry> HOST_API = List.of(
		new ApiEntry(
			"void Log(string message)",
			"void",
			"Show status on Central Command.",
			"Log(\"running\");"
		),
		new ApiEntry(
			"void Wait(int ticks)",
			"void",
			"Pause this Command script across game ticks.",
			"Break(\"mine\"); Wait(20);"
		),
		new ApiEntry(
			"bool Break(string channel)",
			"bool",
			"Command every loaded Breaker on that channel to break one block in front. Alias: BreakFront. True if any harvest succeeded.",
			"if (Break(\"mine\")) { Log(\"ok\"); }"
		),
		new ApiEntry(
			"bool BreakFront(string channel)",
			"bool",
			"Alias of Break(channel).",
			"BreakFront(\"mine\");"
		),
		new ApiEntry(
			"int BreakerCount(string channel)",
			"int",
			"How many loaded Breakers in this dimension are on the channel.",
			"if (BreakerCount(\"mine\") == 0) { Log(\"none\"); }"
		),
		new ApiEntry(
			"bool HasChannel(string channel)",
			"bool",
			"True if this Central Command has registered the channel (Channels tab).",
			"if (HasChannel(\"mine\")) { Break(\"mine\"); }"
		)
	);

	public static final List<ApiEntry> BUILTINS = List.of(
		new ApiEntry("int Min(int a, int b)", "int", "Smaller of two ints.", "int n = Min(3, 7);"),
		new ApiEntry("int Max(int a, int b)", "int", "Larger of two ints.", "int n = Max(1, 5);"),
		new ApiEntry("int Abs(int x)", "int", "Absolute value.", "int n = Abs(i - 3);"),
		new ApiEntry("int Clamp(int v, int lo, int hi)", "int", "Clamp into [lo, hi].", "int n = Clamp(x, 0, 10);")
	);

	public static final List<String> NOTES = List.of(
		"ALL scripts live only in Central Command — Breakers have a channel selector, not a script editor.",
		"Channels tab holds many ids at once: Add appends, click a row to select, × removes only that row.",
		"Each Breaker still picks ONE channel (Prev/Next or list from nearby Commands).",
		"Scripts may Break()/BreakerCount() many registered channels in the same tick.",
		"Break(channel) fans out to every loaded Breaker on that channel in this dimension.",
		"Placer tab / placed events come next PR — design reserved.",
		"C#-like dialect: if/else/switch, while/for, functions, arrays, Math, // and /* */ comments.",
		"Sandbox: opcode + API budgets; loops capped at " + Interpreter.MAX_LOOP_ITERATIONS + " iters/tick."
	);

	private ScriptHelp() {
	}
}
