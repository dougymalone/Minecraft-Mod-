package com.dougymalone.codeworks.script;

import java.util.List;

/**
 * Player-facing docs for the machine Help UI.
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
			"Breaker only: break the nearest breakable non-air block within range in front. Honors speed cooldown. Returns false on Placer.",
			"if (HasBlock()) { Harvest(); }"
		),
		new ApiEntry(
			"bool BreakFront()",
			"bool",
			"Alias of Harvest().",
			"BreakFront();"
		),
		new ApiEntry(
			"bool Place()",
			"bool",
			"Placer only: place the stored block item into the air block directly in front. Needs a stored block and replaceable front. Honors speed cooldown. Returns false on Breaker.",
			"if (HasStoredBlock() && Sense() == \"air\") { Place(); }"
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
			"Registry id of the first non-air block in front within range, or \"air\".",
			"if (Sense() == \"minecraft:oak_log\") { Harvest(); }"
		),
		new ApiEntry(
			"bool HasBlock()",
			"bool",
			"True if a breakable non-air block is within range in front (facing block).",
			"while (HasBlock()) { Harvest(); Wait(5); }"
		),
		new ApiEntry(
			"bool HasStoredBlock()",
			"bool",
			"Placer only: true if the 1-slot inventory holds a placeable block item. Alias: HasItem(). Always false on Breaker.",
			"if (HasStoredBlock()) { Place(); }"
		),
		new ApiEntry(
			"bool HasItem()",
			"bool",
			"Alias of HasStoredBlock().",
			"if (HasItem()) { Place(); }"
		),
		new ApiEntry(
			"bool Extended()",
			"bool",
			"True if a vanilla piston / sticky piston in front within range is extended, or a piston head is in front.",
			"if (Extended()) { Wait(20); }"
		),
		new ApiEntry(
			"bool IsPistonExtended()",
			"bool",
			"Alias of Extended().",
			"if (IsPistonExtended()) { Log(\"out\"); }"
		),
		new ApiEntry(
			"void Move(string dir)",
			"void",
			"Turn the machine. dir: left, right, back, north, south, east, west, up, down.",
			"Move(\"left\");"
		),
		new ApiEntry(
			"void Wait(int ticks)",
			"void",
			"Pause the script across game ticks (cooperative; safe). Prefer as a statement.",
			"Harvest(); Wait(40);"
		),
		new ApiEntry(
			"void Send(string key, string value)",
			"void",
			"Publish a string on this machine's wireless channel. Same bus as Channel Adapters, Breakers, Placers, and any future machines. Same dimension; in-memory for the server session.",
			"Send(\"ready\", \"1\");"
		),
		new ApiEntry(
			"string Receive(string key)",
			"string",
			"Last value on this machine's channel (including adapter keys extended/power/host/powered), or \"\".",
			"if (Receive(\"extended\") == \"1\") { Log(\"adapter says out\"); }"
		),
		new ApiEntry(
			"void Pulse(string channel)",
			"void",
			"Briefly power every Channel Adapter on that channel (any scripted machine can call this — Breaker, Placer, future blocks). Triggers attached pistons/redstone.",
			"Pulse(\"gate\");"
		),
		new ApiEntry(
			"void SetPower(string channel, bool on)",
			"void",
			"Hold (true) or release (false) redstone emit on all Channel Adapters on that channel. Shared bus — usable from every scripted machine.",
			"SetPower(\"gate\", true); Wait(40); SetPower(\"gate\", false);"
		),
		new ApiEntry(
			"bool GetExtended(string channel)",
			"bool",
			"Read piston-extended state published by Channel Adapters on that channel (bus key \"extended\").",
			"if (GetExtended(\"gate\")) { Log(\"piston out\"); }"
		),
		new ApiEntry(
			"int GetPower(string channel)",
			"int",
			"Read 0–15 redstone level published by Channel Adapters on that channel (bus key \"power\").",
			"int p = GetPower(\"gate\");"
		),
		new ApiEntry(
			"string GetHost(string channel)",
			"string",
			"Registry id of the block a Channel Adapter is attached to on that channel, or \"\".",
			"Log(GetHost(\"gate\"));"
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
			"Action rate for Harvest/Place. Cooldown ticks ≈ 21 − speed (minimum 1). Persisted.",
			"speed = 15;"
		),
		new SettingEntry(
			"range",
			"int 1–3",
			"How many blocks ahead Sense / HasBlock / Extended / Harvest reach. Place always uses the adjacent front cell. Persisted.",
			"range = 2;"
		),
		new SettingEntry(
			"channel",
			"string",
			"Wireless network id. Breakers, Placers, Channel Adapters, and future machines on the same id share one bus. Same dimension only. Persisted.",
			"channel = \"line1\";"
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
		"Functions: void Dig() { Harvest(); }  int Add(int a, int b) { return a + b; }",
		"Math builtins: Min, Max, Abs, Clamp (pure — no world side effects).",
		"Settings speed, range, and channel act like variables and are persisted on the machine.",
		"SHARED WIRELESS BUS: Breaker, Placer, Channel Adapter, and any future scripted machine all use the same Send/Receive/Pulse bus. Set matching channel ids.",
		"Channel Adapter: place on the back/face of a piston or redstone device; open GUI to set channel. Publishes extended/power/host; Pulse/SetPower emit redstone into the host.",
		"Example — Placer waits for adapter piston, then places: channel=\"gate\"; if (GetExtended(\"gate\")) { Wait(5); } else { Pulse(\"gate\"); Wait(10); } if (HasStoredBlock()) { Place(); }",
		"Example — Breaker harvests then pulses a gate adapter: channel=\"line1\"; if (Harvest()) { Pulse(\"gate\"); }",
		"Adapter bus keys: extended, power, host, powered (avoid custom keys starting with __).",
		"Multiple adapters on different channels never collide; same channel shares state/commands.",
		"Wireless scope: same dimension, global within that dimension, in-memory for the server session (not disk-persisted).",
		"Machines: Breaker breaks; Placer stores one block; Channel Adapter bridges redstone ↔ scripts.",
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
