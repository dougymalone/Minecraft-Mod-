package com.dougymalone.codeworks.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Lexer {
	private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

	static {
		KEYWORDS.put("int", TokenType.INT_KW);
		KEYWORDS.put("bool", TokenType.BOOL_KW);
		KEYWORDS.put("string", TokenType.STRING_KW);
		KEYWORDS.put("void", TokenType.VOID_KW);
		KEYWORDS.put("if", TokenType.IF);
		KEYWORDS.put("else", TokenType.ELSE);
		KEYWORDS.put("while", TokenType.WHILE);
		KEYWORDS.put("for", TokenType.FOR);
		KEYWORDS.put("switch", TokenType.SWITCH);
		KEYWORDS.put("case", TokenType.CASE);
		KEYWORDS.put("default", TokenType.DEFAULT);
		KEYWORDS.put("break", TokenType.BREAK);
		KEYWORDS.put("continue", TokenType.CONTINUE);
		KEYWORDS.put("return", TokenType.RETURN);
		KEYWORDS.put("new", TokenType.NEW);
		KEYWORDS.put("true", TokenType.TRUE);
		KEYWORDS.put("false", TokenType.FALSE);
	}

	private final String source;
	private final List<Token> tokens = new ArrayList<>();
	private int start;
	private int current;
	private int line = 1;

	public Lexer(String source) {
		this.source = source == null ? "" : source;
	}

	public List<Token> scanTokens() throws ScriptException {
		while (!isAtEnd()) {
			start = current;
			scanToken();
		}
		tokens.add(new Token(TokenType.EOF, "", line));
		return tokens;
	}

	private void scanToken() throws ScriptException {
		char c = advance();
		switch (c) {
			case '(' -> add(TokenType.LPAREN);
			case ')' -> add(TokenType.RPAREN);
			case '{' -> add(TokenType.LBRACE);
			case '}' -> add(TokenType.RBRACE);
			case '[' -> add(TokenType.LBRACK);
			case ']' -> add(TokenType.RBRACK);
			case ';' -> add(TokenType.SEMI);
			case ',' -> add(TokenType.COMMA);
			case '.' -> add(TokenType.DOT);
			case ':' -> add(TokenType.COLON);
			case '+' -> {
				if (match('+')) {
					add(TokenType.PLUSPLUS);
				} else if (match('=')) {
					add(TokenType.PLUSEQ);
				} else {
					add(TokenType.PLUS);
				}
			}
			case '-' -> {
				if (match('-')) {
					add(TokenType.MINUSMINUS);
				} else if (match('=')) {
					add(TokenType.MINUSEQ);
				} else {
					add(TokenType.MINUS);
				}
			}
			case '*' -> add(match('=') ? TokenType.STAREQ : TokenType.STAR);
			case '/' -> {
				if (match('/')) {
					while (peek() != '\n' && !isAtEnd()) {
						advance();
					}
				} else if (match('*')) {
					blockComment();
				} else if (match('=')) {
					add(TokenType.SLASHEQ);
				} else {
					add(TokenType.SLASH);
				}
			}
			case '!' -> add(match('=') ? TokenType.BANGEQ : TokenType.BANG);
			case '=' -> add(match('=') ? TokenType.EQEQ : TokenType.EQ);
			case '<' -> add(match('=') ? TokenType.LTE : TokenType.LT);
			case '>' -> add(match('=') ? TokenType.GTE : TokenType.GT);
			case '&' -> {
				if (match('&')) {
					add(TokenType.AMPAMP);
				} else {
					throw new ScriptException("Unexpected '&'", line);
				}
			}
			case '|' -> {
				if (match('|')) {
					add(TokenType.PIPEPIPE);
				} else {
					throw new ScriptException("Unexpected '|'", line);
				}
			}
			case '"' -> string();
			case ' ', '\r', '\t' -> {
			}
			case '\n' -> line++;
			default -> {
				if (isDigit(c)) {
					number();
				} else if (isAlpha(c)) {
					identifier();
				} else {
					throw new ScriptException("Unexpected character '" + c + "'", line);
				}
			}
		}
	}

	private void blockComment() throws ScriptException {
		while (!isAtEnd()) {
			if (peek() == '*' && peekNext() == '/') {
				advance();
				advance();
				return;
			}
			if (peek() == '\n') {
				line++;
			}
			advance();
		}
		throw new ScriptException("Unterminated block comment", line);
	}

	private void string() throws ScriptException {
		StringBuilder builder = new StringBuilder();
		while (peek() != '"' && !isAtEnd()) {
			if (peek() == '\n') {
				line++;
			}
			if (peek() == '\\') {
				advance();
				char escaped = advance();
				builder.append(switch (escaped) {
					case 'n' -> '\n';
					case 't' -> '\t';
					case '"' -> '"';
					case '\\' -> '\\';
					default -> escaped;
				});
			} else {
				builder.append(advance());
			}
		}
		if (isAtEnd()) {
			throw new ScriptException("Unterminated string", line);
		}
		advance();
		tokens.add(new Token(TokenType.STRING, builder.toString(), line));
	}

	private void number() {
		while (isDigit(peek())) {
			advance();
		}
		add(TokenType.NUMBER);
	}

	private void identifier() {
		while (isAlphaNumeric(peek())) {
			advance();
		}
		String text = source.substring(start, current);
		TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENT);
		tokens.add(new Token(type, text, line));
	}

	private void add(TokenType type) {
		tokens.add(new Token(type, source.substring(start, current), line));
	}

	private boolean match(char expected) {
		if (isAtEnd() || source.charAt(current) != expected) {
			return false;
		}
		current++;
		return true;
	}

	private char advance() {
		return source.charAt(current++);
	}

	private char peek() {
		return isAtEnd() ? '\0' : source.charAt(current);
	}

	private char peekNext() {
		return current + 1 >= source.length() ? '\0' : source.charAt(current + 1);
	}

	private boolean isAtEnd() {
		return current >= source.length();
	}

	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private static boolean isAlpha(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
	}

	private static boolean isAlphaNumeric(char c) {
		return isAlpha(c) || isDigit(c);
	}
}
