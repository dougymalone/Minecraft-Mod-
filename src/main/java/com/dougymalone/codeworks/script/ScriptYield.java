package com.dougymalone.codeworks.script;

/** Cooperative yield — script pauses across ticks (e.g. Wait). Not an error. */
public final class ScriptYield extends Exception {
	public ScriptYield() {
		super("yield", null, false, false);
	}
}
