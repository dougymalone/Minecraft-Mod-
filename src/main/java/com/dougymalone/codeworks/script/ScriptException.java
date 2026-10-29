package com.dougymalone.codeworks.script;

public class ScriptException extends Exception {
	public ScriptException(String message) {
		super(message);
	}

	public ScriptException(String message, int line) {
		super(message + " (line " + line + ")");
	}
}
