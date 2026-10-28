package com.dougymalone.codeworks.script;

import java.util.List;

public final class Ast {
	private Ast() {
	}

	public sealed interface Stmt {
		record Block(List<Stmt> statements) implements Stmt {
		}

		record VarDecl(String typeName, String name, Expr initializer, int line) implements Stmt {
		}

		record Assign(String name, Expr value, int line) implements Stmt {
		}

		record If(Expr condition, Stmt thenBranch, Stmt elseBranch) implements Stmt {
		}

		record Expression(Expr expression) implements Stmt {
		}
	}

	public sealed interface Expr {
		record Literal(Object value) implements Expr {
		}

		record Variable(String name, int line) implements Expr {
		}

		record Unary(TokenType op, Expr right, int line) implements Expr {
		}

		record Binary(Expr left, TokenType op, Expr right, int line) implements Expr {
		}

		record Call(Expr callee, List<Expr> args, int line) implements Expr {
		}
	}

	public record Program(List<Stmt> statements) {
	}
}
