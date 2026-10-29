package com.dougymalone.codeworks.script;

/**
 * Host surface for Central Command scripts only.
 * Actuator blocks (Breaker, future Placer) have no local scripts.
 */
public interface HostApi {
	void log(String message) throws ScriptException;

	/** Schedule a cooperative wait; interpreter should ScriptYield afterward. */
	void waitTicks(int ticks) throws ScriptException;

	/** True if this Command has registered the channel id. */
	boolean hasChannel(String channel) throws ScriptException;

	/** Number of loaded Breakers in this dimension on the channel. */
	int breakerCount(String channel) throws ScriptException;

	/**
	 * Command every loaded Breaker on {@code channel} to attempt one break-front.
	 * @return true if at least one Breaker harvested a block
	 */
	boolean breakFront(String channel) throws ScriptException;
}
