package com.dougymalone.codeworks.script;

import java.util.ArrayList;
import java.util.List;

public final class Parser {
	private final List<Token> tokens;
	private int current;

	public Parser(List<Token> tokens) {
		this.tokens = tokens;
	}

	public Ast.Program parse() throws ScriptException {
		List<Ast.Stmt> statements = new ArrayList<>();
		while (!isAtEnd()) {
			statements.add(declaration());
		}
		return new Ast.Program(statements);
	}

	private Ast.Stmt declaration() throws ScriptException {
		if (match(TokenType.INT_KW, TokenType.BOOL_KW, TokenType.STRING_KW)) {
			return varDecl();
		}
		return statement();
	}

	private Ast.Stmt varDecl() throws ScriptException {
		Token type = previous();
		Token name = consume(TokenType.IDENT, "Expected variable name");
		consume(TokenType.EQ, "Expected '=' after variable name");
		Ast.Expr initializer = expression();
		consume(TokenType.SEMI, "Expected ';' after declaration");
		return new Ast.Stmt.VarDecl(type.lexeme(), name.lexeme(), initializer, name.line());
	}

	private Ast.Stmt statement() throws ScriptException {
		if (match(TokenType.IF)) {
			return ifStatement();
		}
		if (match(TokenType.LBRACE)) {
			return new Ast.Stmt.Block(block());
		}
		return expressionStatement();
	}

	private Ast.Stmt ifStatement() throws ScriptException {
		consume(TokenType.LPAREN, "Expected '(' after if");
		Ast.Expr condition = expression();
		consume(TokenType.RPAREN, "Expected ')' after if condition");
		Ast.Stmt thenBranch = statement();
		Ast.Stmt elseBranch = null;
		if (match(TokenType.ELSE)) {
			elseBranch = statement();
		}
		return new Ast.Stmt.If(condition, thenBranch, elseBranch);
	}

	private List<Ast.Stmt> block() throws ScriptException {
		List<Ast.Stmt> statements = new ArrayList<>();
		while (!check(TokenType.RBRACE) && !isAtEnd()) {
			statements.add(declaration());
		}
		consume(TokenType.RBRACE, "Expected '}' after block");
		return statements;
	}

	private Ast.Stmt expressionStatement() throws ScriptException {
		Ast.Expr expr = expression();
		if (expr instanceof Ast.Expr.Variable variable && match(TokenType.EQ)) {
			Ast.Expr value = expression();
			consume(TokenType.SEMI, "Expected ';' after assignment");
			return new Ast.Stmt.Assign(variable.name(), value, variable.line());
		}
		consume(TokenType.SEMI, "Expected ';' after expression");
		return new Ast.Stmt.Expression(expr);
	}

	private Ast.Expr expression() throws ScriptException {
		return or();
	}

	private Ast.Expr or() throws ScriptException {
		Ast.Expr expr = and();
		while (match(TokenType.PIPEPIPE)) {
			Token op = previous();
			expr = new Ast.Expr.Binary(expr, op.type(), and(), op.line());
		}
		return expr;
	}

	private Ast.Expr and() throws ScriptException {
		Ast.Expr expr = equality();
		while (match(TokenType.AMPAMP)) {
			Token op = previous();
			expr = new Ast.Expr.Binary(expr, op.type(), equality(), op.line());
		}
		return expr;
	}

	private Ast.Expr equality() throws ScriptException {
		Ast.Expr expr = comparison();
		while (match(TokenType.EQEQ, TokenType.BANGEQ)) {
			Token op = previous();
			expr = new Ast.Expr.Binary(expr, op.type(), comparison(), op.line());
		}
		return expr;
	}

	private Ast.Expr comparison() throws ScriptException {
		Ast.Expr expr = term();
		while (match(TokenType.LT, TokenType.LTE, TokenType.GT, TokenType.GTE)) {
			Token op = previous();
			expr = new Ast.Expr.Binary(expr, op.type(), term(), op.line());
		}
		return expr;
	}

	private Ast.Expr term() throws ScriptException {
		Ast.Expr expr = factor();
		while (match(TokenType.PLUS, TokenType.MINUS)) {
			Token op = previous();
			expr = new Ast.Expr.Binary(expr, op.type(), factor(), op.line());
		}
		return expr;
	}

	private Ast.Expr factor() throws ScriptException {
		Ast.Expr expr = unary();
		while (match(TokenType.STAR, TokenType.SLASH)) {
			Token op = previous();
			expr = new Ast.Expr.Binary(expr, op.type(), unary(), op.line());
		}
		return expr;
	}

	private Ast.Expr unary() throws ScriptException {
		if (match(TokenType.BANG, TokenType.MINUS)) {
			Token op = previous();
			return new Ast.Expr.Unary(op.type(), unary(), op.line());
		}
		return call();
	}

	private Ast.Expr call() throws ScriptException {
		Ast.Expr expr = primary();
		while (match(TokenType.LPAREN)) {
			List<Ast.Expr> args = new ArrayList<>();
			if (!check(TokenType.RPAREN)) {
				do {
					if (args.size() >= 8) {
						throw new ScriptException("Too many arguments", peek().line());
					}
					args.add(expression());
				} while (match(TokenType.COMMA));
			}
			Token paren = consume(TokenType.RPAREN, "Expected ')' after arguments");
			expr = new Ast.Expr.Call(expr, args, paren.line());
		}
		return expr;
	}

	private Ast.Expr primary() throws ScriptException {
		if (match(TokenType.FALSE)) {
			return new Ast.Expr.Literal(false);
		}
		if (match(TokenType.TRUE)) {
			return new Ast.Expr.Literal(true);
		}
		if (match(TokenType.NUMBER)) {
			return new Ast.Expr.Literal(Integer.parseInt(previous().lexeme()));
		}
		if (match(TokenType.STRING)) {
			return new Ast.Expr.Literal(previous().lexeme());
		}
		if (match(TokenType.IDENT)) {
			Token name = previous();
			return new Ast.Expr.Variable(name.lexeme(), name.line());
		}
		if (match(TokenType.LPAREN)) {
			Ast.Expr expr = expression();
			consume(TokenType.RPAREN, "Expected ')' after expression");
			return expr;
		}
		throw new ScriptException("Expected expression", peek().line());
	}

	private boolean match(TokenType... types) {
		for (TokenType type : types) {
			if (check(type)) {
				advance();
				return true;
			}
		}
		return false;
	}

	private Token consume(TokenType type, String message) throws ScriptException {
		if (check(type)) {
			return advance();
		}
		throw new ScriptException(message, peek().line());
	}

	private boolean check(TokenType type) {
		return !isAtEnd() && peek().type() == type;
	}

	private Token advance() {
		if (!isAtEnd()) {
			current++;
		}
		return previous();
	}

	private boolean isAtEnd() {
		return peek().type() == TokenType.EOF;
	}

	private Token peek() {
		return tokens.get(current);
	}

	private Token previous() {
		return tokens.get(current - 1);
	}
}
