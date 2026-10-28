package com.dougymalone.codeworks.script;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Interpreter {
	public static final int DEFAULT_OPCODE_BUDGET = 256;
	public static final int DEFAULT_API_BUDGET = 8;

	private static final Set<String> HOST_METHODS = Set.of("Harvest", "BreakFront", "Log");

	private final Ast.Program program;
	private final HostApi host;
	private final Map<String, Object> locals = new HashMap<>();
	private int opcodesRemaining;
	private int apiCallsRemaining;

	public Interpreter(Ast.Program program, HostApi host) {
		this.program = program;
		this.host = host;
	}

	public static Ast.Program compile(String source) throws ScriptException {
		List<Token> tokens = new Lexer(source).scanTokens();
		return new Parser(tokens).parse();
	}

	public void run() throws ScriptException {
		run(DEFAULT_OPCODE_BUDGET, DEFAULT_API_BUDGET);
	}

	public void run(int opcodeBudget, int apiBudget) throws ScriptException {
		opcodesRemaining = opcodeBudget;
		apiCallsRemaining = apiBudget;
		locals.clear();
		for (Ast.Stmt stmt : program.statements()) {
			execute(stmt);
		}
	}

	private void execute(Ast.Stmt stmt) throws ScriptException {
		switch (stmt) {
			case Ast.Stmt.Block block -> {
				for (Ast.Stmt inner : block.statements()) {
					execute(inner);
				}
			}
			case Ast.Stmt.VarDecl decl -> {
				bump();
				Object value = evaluate(decl.initializer());
				value = coerce(decl.typeName(), value, decl.line());
				locals.put(decl.name(), value);
			}
			case Ast.Stmt.Assign assign -> {
				bump();
				if (!locals.containsKey(assign.name())) {
					throw new ScriptException("Undefined variable '" + assign.name() + "'", assign.line());
				}
				locals.put(assign.name(), evaluate(assign.value()));
			}
			case Ast.Stmt.If ifStmt -> {
				bump();
				Object condition = evaluate(ifStmt.condition());
				if (isTruthy(condition)) {
					execute(ifStmt.thenBranch());
				} else if (ifStmt.elseBranch() != null) {
					execute(ifStmt.elseBranch());
				}
			}
			case Ast.Stmt.Expression expression -> {
				bump();
				evaluate(expression.expression());
			}
		}
	}

	private Object evaluate(Ast.Expr expr) throws ScriptException {
		bump();
		return switch (expr) {
			case Ast.Expr.Literal literal -> literal.value();
			case Ast.Expr.Variable variable -> {
				if (!locals.containsKey(variable.name())) {
					throw new ScriptException("Undefined variable '" + variable.name() + "'", variable.line());
				}
				yield locals.get(variable.name());
			}
			case Ast.Expr.Unary unary -> {
				Object right = evaluate(unary.right());
				yield switch (unary.op()) {
					case BANG -> !isTruthy(right);
					case MINUS -> -asInt(right, unary.line());
					default -> throw new ScriptException("Unknown unary operator", unary.line());
				};
			}
			case Ast.Expr.Binary binary -> {
				if (binary.op() == TokenType.AMPAMP) {
					Object left = evaluate(binary.left());
					yield isTruthy(left) && isTruthy(evaluate(binary.right()));
				}
				if (binary.op() == TokenType.PIPEPIPE) {
					Object left = evaluate(binary.left());
					yield isTruthy(left) || isTruthy(evaluate(binary.right()));
				}
				Object left = evaluate(binary.left());
				Object right = evaluate(binary.right());
				yield switch (binary.op()) {
					case PLUS -> {
						if (left instanceof String || right instanceof String) {
							yield String.valueOf(left) + String.valueOf(right);
						}
						yield asInt(left, binary.line()) + asInt(right, binary.line());
					}
					case MINUS -> asInt(left, binary.line()) - asInt(right, binary.line());
					case STAR -> asInt(left, binary.line()) * asInt(right, binary.line());
					case SLASH -> {
						int divisor = asInt(right, binary.line());
						if (divisor == 0) {
							throw new ScriptException("Division by zero", binary.line());
						}
						yield asInt(left, binary.line()) / divisor;
					}
					case EQEQ -> valuesEqual(left, right);
					case BANGEQ -> !valuesEqual(left, right);
					case LT -> asInt(left, binary.line()) < asInt(right, binary.line());
					case LTE -> asInt(left, binary.line()) <= asInt(right, binary.line());
					case GT -> asInt(left, binary.line()) > asInt(right, binary.line());
					case GTE -> asInt(left, binary.line()) >= asInt(right, binary.line());
					default -> throw new ScriptException("Unknown binary operator", binary.line());
				};
			}
			case Ast.Expr.Call call -> evaluateCall(call);
		};
	}

	private Object evaluateCall(Ast.Expr.Call call) throws ScriptException {
		if (!(call.callee() instanceof Ast.Expr.Variable variable)) {
			throw new ScriptException("Can only call host API methods", call.line());
		}
		String name = variable.name();
		if (!HOST_METHODS.contains(name)) {
			throw new ScriptException("Unknown method '" + name + "'", call.line());
		}
		if (apiCallsRemaining-- <= 0) {
			throw new ScriptException("API call budget exceeded", call.line());
		}

		List<Object> args = new java.util.ArrayList<>(call.args().size());
		for (Ast.Expr arg : call.args()) {
			args.add(evaluate(arg));
		}

		return switch (name) {
			case "Harvest", "BreakFront" -> {
				if (!args.isEmpty()) {
					throw new ScriptException(name + "() takes no arguments", call.line());
				}
				yield host.harvest();
			}
			case "Log" -> {
				if (args.size() != 1) {
					throw new ScriptException("Log(string) expects one argument", call.line());
				}
				host.log(String.valueOf(args.getFirst()));
				yield null;
			}
			default -> throw new ScriptException("Unknown method '" + name + "'", call.line());
		};
	}

	private Object coerce(String typeName, Object value, int line) throws ScriptException {
		return switch (typeName) {
			case "int" -> asInt(value, line);
			case "bool" -> isTruthy(value);
			case "string" -> String.valueOf(value);
			default -> throw new ScriptException("Unknown type '" + typeName + "'", line);
		};
	}

	private void bump() throws ScriptException {
		if (opcodesRemaining-- <= 0) {
			throw new ScriptException("Opcode budget exceeded");
		}
	}

	private static boolean isTruthy(Object value) {
		if (value == null) {
			return false;
		}
		if (value instanceof Boolean bool) {
			return bool;
		}
		if (value instanceof Integer integer) {
			return integer != 0;
		}
		if (value instanceof String string) {
			return !string.isEmpty();
		}
		return true;
	}

	private static int asInt(Object value, int line) throws ScriptException {
		if (value instanceof Integer integer) {
			return integer;
		}
		if (value instanceof Boolean bool) {
			return bool ? 1 : 0;
		}
		throw new ScriptException("Expected int, got " + typeName(value), line);
	}

	private static boolean valuesEqual(Object left, Object right) {
		if (left == null) {
			return right == null;
		}
		return left.equals(right);
	}

	private static String typeName(Object value) {
		return value == null ? "null" : value.getClass().getSimpleName().toLowerCase(Locale.ROOT);
	}
}
