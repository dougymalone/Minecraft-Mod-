package com.dougymalone.codeworks.script;

public interface HostApi {
	boolean harvest() throws ScriptException;

	void log(String message) throws ScriptException;

	String sense() throws ScriptException;

	boolean hasBlock() throws ScriptException;

	void move(String direction) throws ScriptException;

	/** Schedule a cooperative wait; interpreter should ScriptYield afterward. */
	void waitTicks(int ticks) throws ScriptException;

	int getSpeed();

	void setSpeed(int speed) throws ScriptException;

	int getRange();

	void setRange(int range) throws ScriptException;
}
