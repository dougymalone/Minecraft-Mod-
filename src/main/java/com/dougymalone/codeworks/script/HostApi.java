package com.dougymalone.codeworks.script;

public interface HostApi {
	boolean harvest() throws ScriptException;

	void log(String message) throws ScriptException;
}
