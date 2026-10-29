package com.dougymalone.codeworks.script;

import java.util.List;
import org.jspecify.annotations.Nullable;

public final class Ast {
	private Ast() {
	}

	public record Param(String typeName, String name) {
	}

	public record FunctionDef(String returnType, String name, List<Param> params, Stmt.Block body, int line) {
	}

	public record SwitchCase(Expr value, List<Stmt> body) {
	}

	public sealed interface Stmt {
		record Block(List<Stmt> statements) implements Stmt {
		}

		record VarDecl(String typeName, boolean isArray, String name, Expr initializer, int line) implements Stmt {
		}

		record Assign(Expr target, TokenType op, Expr value, int line) implements Stmt {
		}

		record If(Expr condition, Stmt thenBranch, @Nullable Stmt elseBranch) implements Stmt {
		}

		record While(Expr condition, Stmt body, int line) implements Stmt {
		}

		record For(@Nullable Stmt init, @Nullable Expr condition, @Nullable Stmt increment, Stmt body, int line) implements Stmt {
		}

		record Switch(Expr discriminant, List<SwitchCase> cases, @Nullable List<Stmt> defaultBody, int line) implements Stmt {
		}

		record Break(int line) implements Stmt {
		}

		record Continue(int line) implements Stmt {
		}

		record Return(@Nullable Expr value, int line) implements Stmt {
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

		record Index(Expr array, Expr index, int line) implements Expr {
		}

		record Member(Expr object, String name, int line) implements Expr {
		}

		record NewArray(String elementType, Expr length, int line) implements Expr {
		}

		record Prefix(TokenType op, Expr target, int line) implements Expr {
		}

		record Postfix(TokenType op, Expr target, int line) implements Expr {
		}

		record AssignExpr(Expr target, TokenType op, Expr value, int line) implements Expr {
		}
	}

	public record Program(List<FunctionDef> functions, List<Stmt> statements) {
	}
}
