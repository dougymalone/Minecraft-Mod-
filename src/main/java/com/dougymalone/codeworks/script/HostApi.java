package com.dougymalone.codeworks.script;

public interface HostApi {
	/** Breaker: break front block. Other machines: return false. */
	boolean harvest() throws ScriptException;

	/** Placer: place stored block in front. Other machines: return false. */
	boolean place() throws ScriptException;

	void log(String message) throws ScriptException;

	String sense() throws ScriptException;

	boolean hasBlock() throws ScriptException;

	/** Placer: true if the 1-slot inventory holds a placeable block item. */
	boolean hasStoredBlock() throws ScriptException;

	/** True if a vanilla piston in front (within range) is extended. */
	boolean extended() throws ScriptException;

	void move(String direction) throws ScriptException;

	/** Schedule a cooperative wait; interpreter should ScriptYield afterward. */
	void waitTicks(int ticks) throws ScriptException;

	/** Publish a string value on this machine's wireless channel (same dimension). */
	void send(String key, String value) throws ScriptException;

	/** Read the last published value for key on this machine's channel, or "". */
	String receive(String key) throws ScriptException;

	/**
	 * Briefly power every Channel Adapter on {@code channel} (shared bus — works from
	 * Breaker, Placer, and any future scripted machine).
	 */
	void pulse(String channel) throws ScriptException;

	/** Hold or release redstone emit on all Channel Adapters on {@code channel}. */
	void setPower(String channel, boolean on) throws ScriptException;

	/** Read adapter-published piston extended state for {@code channel} ("1" on the bus). */
	boolean getExtended(String channel) throws ScriptException;

	/** Read adapter-published redstone level 0–15 for {@code channel}. */
	int getPower(String channel) throws ScriptException;

	/** Read adapter-published host block id for {@code channel}, or "". */
	String getHost(String channel) throws ScriptException;

	int getSpeed();

	void setSpeed(int speed) throws ScriptException;

	int getRange();

	void setRange(int range) throws ScriptException;

	String getChannel();

	void setChannel(String channel) throws ScriptException;
}
