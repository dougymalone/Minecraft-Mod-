package com.dougymalone.codeworks.script;

final class ScriptBreak extends Exception {
	ScriptBreak() {
		super("break", null, false, false);
	}
}
