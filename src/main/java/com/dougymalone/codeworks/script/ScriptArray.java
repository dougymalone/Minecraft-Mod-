package com.dougymalone.codeworks.script;

final class ScriptArray {
	final String elementType;
	final Object[] items;

	ScriptArray(String elementType, int length) throws ScriptException {
		if (length < 0 || length > 256) {
			throw new ScriptException("Array length must be 0–256");
		}
		this.elementType = elementType;
		this.items = new Object[length];
		for (int i = 0; i < length; i++) {
			items[i] = switch (elementType) {
				case "int" -> 0;
				case "string" -> "";
				case "bool" -> false;
				default -> null;
			};
		}
	}

	int length() {
		return items.length;
	}

	Object get(int index, int line) throws ScriptException {
		if (index < 0 || index >= items.length) {
			throw new ScriptException("Array index out of range: " + index, line);
		}
		return items[index];
	}

	void set(int index, Object value, int line) throws ScriptException {
		if (index < 0 || index >= items.length) {
			throw new ScriptException("Array index out of range: " + index, line);
		}
		items[index] = value;
	}
}
