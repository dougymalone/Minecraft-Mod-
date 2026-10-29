package com.dougymalone.codeworks.script;

import java.util.List;
import org.jspecify.annotations.Nullable;

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

		record If(Expr condition, Stmt thenBranch, @Nullable Stmt elseBranch) implements Stmt {
		}

		record While(Expr condition, Stmt body, int line) implements Stmt {
		}

		record For(@Nullable Stmt init, @Nullable Expr condition, @Nullable Stmt increment, Stmt body, int line) implements Stmt {
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
