package com.dougymalone.codeworks.wireless;

/**
 * Reserved wireless-bus keys used by Channel Adapters and script helpers.
 * <p>
 * Any scripted machine (Breaker, Placer, future blocks) sharing a channel can
 * {@code Receive} these, or call the dedicated host APIs that read/write them.
 * Custom gameplay keys should avoid the {@code __} prefix.
 */
public final class WirelessKeys {
	/** "1" / "0" — host piston extended (published by adapters). */
	public static final String EXTENDED = "extended";
	/** "0"–"15" — best neighbor redstone at the host (published by adapters). */
	public static final String POWER = "power";
	/** Registry id of the attached host block (published by adapters). */
	public static final String HOST = "host";
	/** "1" / "0" — whether the adapter is currently emitting power. */
	public static final String POWERED = "powered";
	/** Sustained emit command: "1" on / "0" off (written by SetPower). */
	public static final String CMD_POWER = "__power";
	/** Pulse command: remaining ticks as decimal string (written by Pulse). */
	public static final String CMD_PULSE = "__pulse";

	public static final int DEFAULT_PULSE_TICKS = 4;

	private WirelessKeys() {
	}
}
