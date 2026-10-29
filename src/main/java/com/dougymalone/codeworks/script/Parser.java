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
		List<Ast.FunctionDef> functions = new ArrayList<>();
		List<Ast.Stmt> statements = new ArrayList<>();
		while (!isAtEnd()) {
			if (isFunctionStart()) {
				functions.add(functionDecl());
			} else {
				statements.add(declaration());
			}
		}
		return new Ast.Program(functions, statements);
	}

	private boolean isFunctionStart() {
		if (!check(TokenType.VOID_KW) && !check(TokenType.INT_KW) && !check(TokenType.BOOL_KW) && !check(TokenType.STRING_KW)) {
			return false;
		}
		// type IDENT (
		if (current + 2 >= tokens.size()) {
			return false;
		}
		Token t1 = tokens.get(current + 1);
		Token t2 = tokens.get(current + 2);
		// allow type[] IDENT (  for return arrays? skip — only scalar/void returns
		if (t1.type() == TokenType.LBRACK) {
			return false;
		}
		return t1.type() == TokenType.IDENT && t2.type() == TokenType.LPAREN;
	}

	private Ast.FunctionDef functionDecl() throws ScriptException {
		Token typeTok = advance();
		String returnType = typeTok.lexeme();
		Token name = consume(TokenType.IDENT, "Expected function name");
		consume(TokenType.LPAREN, "Expected '(' after function name");
		List<Ast.Param> params = new ArrayList<>();
		if (!check(TokenType.RPAREN)) {
			do {
				if (params.size() >= 8) {
					throw new ScriptException("Too many parameters", peek().line());
				}
				Token pType = consumeType("Expected parameter type");
				Token pName = consume(TokenType.IDENT, "Expected parameter name");
				params.add(new Ast.Param(pType.lexeme(), pName.lexeme()));
			} while (match(TokenType.COMMA));
		}
		consume(TokenType.RPAREN, "Expected ')' after parameters");
		consume(TokenType.LBRACE, "Expected '{' before function body");
		Ast.Stmt.Block body = new Ast.Stmt.Block(block());
		return new Ast.FunctionDef(returnType, name.lexeme(), params, body, name.line());
	}

	private Ast.Stmt declaration() throws ScriptException {
		if (check(TokenType.INT_KW) || check(TokenType.BOOL_KW) || check(TokenType.STRING_KW)) {
			return varDecl(true);
		}
		return statement();
	}

	private Ast.Stmt varDecl(boolean requireSemicolon) throws ScriptException {
		Token type = advance();
		boolean isArray = false;
		if (match(TokenType.LBRACK)) {
			consume(TokenType.RBRACK, "Expected ']' after '[' in array type");
			isArray = true;
			if (type.type() == TokenType.BOOL_KW) {
				throw new ScriptException("bool[] not supported; use int[] or string[]", type.line());
			}
		}
		Token name = consume(TokenType.IDENT, "Expected variable name");
		consume(TokenType.EQ, "Expected '=' after variable name");
		Ast.Expr initializer = expression();
		if (requireSemicolon) {
			consume(TokenType.SEMI, "Expected ';' after declaration");
		}
		return new Ast.Stmt.VarDecl(type.lexeme(), isArray, name.lexeme(), initializer, name.line());
	}

	private Ast.Stmt statement() throws ScriptException {
		if (match(TokenType.IF)) {
			return ifStatement();
		}
		if (match(TokenType.WHILE)) {
			return whileStatement();
		}
		if (match(TokenType.FOR)) {
			return forStatement();
		}
		if (match(TokenType.SWITCH)) {
			return switchStatement();
		}
		if (match(TokenType.BREAK)) {
			Token tok = previous();
			consume(TokenType.SEMI, "Expected ';' after break");
			return new Ast.Stmt.Break(tok.line());
		}
		if (match(TokenType.CONTINUE)) {
			Token tok = previous();
			consume(TokenType.SEMI, "Expected ';' after continue");
			return new Ast.Stmt.Continue(tok.line());
		}
		if (match(TokenType.RETURN)) {
			Token tok = previous();
			Ast.Expr value = null;
			if (!check(TokenType.SEMI)) {
				value = expression();
			}
			consume(TokenType.SEMI, "Expected ';' after return");
			return new Ast.Stmt.Return(value, tok.line());
		}
		if (match(TokenType.LBRACE)) {
			return new Ast.Stmt.Block(block());
		}
		return expressionStatement(true);
	}

	private Ast.Stmt ifStatement() throws ScriptException {
		consume(TokenType.LPAREN, "Expected '(' after if");
		Ast.Expr condition = expression();
		consume(TokenType.RPAREN, "Expected ')' after if condition");
		Ast.Stmt thenBranch = statement();
		Ast.Stmt elseBranch = null;
		if (match(TokenType.ELSE)) {
			elseBranch = statement(); // else if works naturally
		}
		return new Ast.Stmt.If(condition, thenBranch, elseBranch);
	}

	private Ast.Stmt whileStatement() throws ScriptException {
		Token keyword = previous();
		consume(TokenType.LPAREN, "Expected '(' after while");
		Ast.Expr condition = expression();
		consume(TokenType.RPAREN, "Expected ')' after while condition");
		Ast.Stmt body = statement();
		return new Ast.Stmt.While(condition, body, keyword.line());
	}

	private Ast.Stmt forStatement() throws ScriptException {
		Token keyword = previous();
		consume(TokenType.LPAREN, "Expected '(' after for");

		Ast.Stmt init = null;
		if (!check(TokenType.SEMI)) {
			if (check(TokenType.INT_KW) || check(TokenType.BOOL_KW) || check(TokenType.STRING_KW)) {
				init = varDecl(false);
			} else {
				init = expressionStatement(false);
			}
		}
		consume(TokenType.SEMI, "Expected ';' after for initializer");

		Ast.Expr condition = null;
		if (!check(TokenType.SEMI)) {
			condition = expression();
		}
		consume(TokenType.SEMI, "Expected ';' after for condition");

		Ast.Stmt increment = null;
		if (!check(TokenType.RPAREN)) {
			increment = expressionStatement(false);
		}
		consume(TokenType.RPAREN, "Expected ')' after for clauses");
		Ast.Stmt body = statement();
		return new Ast.Stmt.For(init, condition, increment, body, keyword.line());
	}

	private Ast.Stmt switchStatement() throws ScriptException {
		Token keyword = previous();
		consume(TokenType.LPAREN, "Expected '(' after switch");
		Ast.Expr discriminant = expression();
		consume(TokenType.RPAREN, "Expected ')' after switch value");
		consume(TokenType.LBRACE, "Expected '{' after switch");
		List<Ast.SwitchCase> cases = new ArrayList<>();
		List<Ast.Stmt> defaultBody = null;
		while (!check(TokenType.RBRACE) && !isAtEnd()) {
			if (match(TokenType.CASE)) {
				Ast.Expr value = expression();
				consume(TokenType.COLON, "Expected ':' after case value");
				List<Ast.Stmt> body = caseBody();
				cases.add(new Ast.SwitchCase(value, body));
			} else if (match(TokenType.DEFAULT)) {
				consume(TokenType.COLON, "Expected ':' after default");
				defaultBody = caseBody();
			} else {
				throw new ScriptException("Expected case or default in switch", peek().line());
			}
		}
		consume(TokenType.RBRACE, "Expected '}' after switch");
		return new Ast.Stmt.Switch(discriminant, cases, defaultBody, keyword.line());
	}

	private List<Ast.Stmt> caseBody() throws ScriptException {
		List<Ast.Stmt> body = new ArrayList<>();
		while (!check(TokenType.CASE) && !check(TokenType.DEFAULT) && !check(TokenType.RBRACE) && !isAtEnd()) {
			body.add(declaration());
		}
		return body;
	}

	private List<Ast.Stmt> block() throws ScriptException {
		List<Ast.Stmt> statements = new ArrayList<>();
		while (!check(TokenType.RBRACE) && !isAtEnd()) {
			statements.add(declaration());
		}
		consume(TokenType.RBRACE, "Expected '}' after block");
		return statements;
	}

	private Ast.Stmt expressionStatement(boolean requireSemicolon) throws ScriptException {
		Ast.Expr expr = expression();
		if (requireSemicolon) {
			consume(TokenType.SEMI, "Expected ';' after expression");
		}
		if (expr instanceof Ast.Expr.AssignExpr assign) {
			return new Ast.Stmt.Assign(assign.target(), assign.op(), assign.value(), assign.line());
		}
		return new Ast.Stmt.Expression(expr);
	}

	private Ast.Expr expression() throws ScriptException {
		return assignment();
	}

	private Ast.Expr assignment() throws ScriptException {
		Ast.Expr expr = or();
		if (match(TokenType.EQ, TokenType.PLUSEQ, TokenType.MINUSEQ, TokenType.STAREQ, TokenType.SLASHEQ)) {
			Token op = previous();
			Ast.Expr value = assignment();
			if (expr instanceof Ast.Expr.Variable || expr instanceof Ast.Expr.Index) {
				return new Ast.Expr.AssignExpr(expr, op.type(), value, op.line());
			}
			throw new ScriptException("Invalid assignment target", op.line());
		}
		return expr;
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
		if (match(TokenType.PLUSPLUS, TokenType.MINUSMINUS)) {
			Token op = previous();
			Ast.Expr target = unary();
			if (!(target instanceof Ast.Expr.Variable) && !(target instanceof Ast.Expr.Index)) {
				throw new ScriptException("Invalid ++/-- target", op.line());
			}
			return new Ast.Expr.Prefix(op.type(), target, op.line());
		}
		return postfix();
	}

	private Ast.Expr postfix() throws ScriptException {
		Ast.Expr expr = call();
		if (match(TokenType.PLUSPLUS, TokenType.MINUSMINUS)) {
			Token op = previous();
			if (!(expr instanceof Ast.Expr.Variable) && !(expr instanceof Ast.Expr.Index)) {
				throw new ScriptException("Invalid ++/-- target", op.line());
			}
			return new Ast.Expr.Postfix(op.type(), expr, op.line());
		}
		return expr;
	}

	private Ast.Expr call() throws ScriptException {
		Ast.Expr expr = primary();
		while (true) {
			if (match(TokenType.LPAREN)) {
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
			} else if (match(TokenType.LBRACK)) {
				Ast.Expr index = expression();
				Token bracket = consume(TokenType.RBRACK, "Expected ']' after index");
				expr = new Ast.Expr.Index(expr, index, bracket.line());
			} else if (match(TokenType.DOT)) {
				Token name = consume(TokenType.IDENT, "Expected member name after '.'");
				expr = new Ast.Expr.Member(expr, name.lexeme(), name.line());
			} else {
				break;
			}
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
		if (match(TokenType.NEW)) {
			Token type = consumeType("Expected type after new");
			consume(TokenType.LBRACK, "Expected '[' after new type");
			Ast.Expr length = expression();
			Token end = consume(TokenType.RBRACK, "Expected ']' after array length");
			return new Ast.Expr.NewArray(type.lexeme(), length, end.line());
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

	private Token consumeType(String message) throws ScriptException {
		if (match(TokenType.INT_KW, TokenType.BOOL_KW, TokenType.STRING_KW, TokenType.VOID_KW)) {
			return previous();
		}
		throw new ScriptException(message, peek().line());
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
