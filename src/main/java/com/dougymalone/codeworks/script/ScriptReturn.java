package com.dougymalone.codeworks.script;

final class ScriptReturn extends Exception {
	final Object value;

	ScriptReturn(Object value) {
		super("return", null, false, false);
		this.value = value;
	}
}
