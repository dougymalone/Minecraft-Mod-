package com.dougymalone.codeworks.script;

final class ScriptContinue extends Exception {
	ScriptContinue() {
		super("continue", null, false, false);
	}
}
