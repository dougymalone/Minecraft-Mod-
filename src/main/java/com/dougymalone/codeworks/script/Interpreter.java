package com.dougymalone.codeworks.script;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Interpreter {
	public static final int DEFAULT_OPCODE_BUDGET = 512;
	public static final int DEFAULT_API_BUDGET = 32;
	public static final int MAX_LOOP_ITERATIONS = 128;

	private static final Set<String> HOST_METHODS = Set.of(
		"Harvest", "BreakFront", "Log", "Sense", "HasBlock", "Move", "Wait"
	);
	private static final Set<String> SETTINGS = Set.of("speed", "range", "autoPickup");

	private final Ast.Program program;
	private final HostApi host;
	private final Map<String, Object> locals = new HashMap<>();
	private final Deque<Frame> stack = new ArrayDeque<>();
	private int opcodesRemaining;
	private int apiCallsRemaining;
	private boolean finished = true;

	public Interpreter(Ast.Program program, HostApi host) {
		this.program = program;
		this.host = host;
	}

	public static Ast.Program compile(String source) throws ScriptException {
		List<Token> tokens = new Lexer(source).scanTokens();
		return new Parser(tokens).parse();
	}

	public boolean isFinished() {
		return finished;
	}

	/** Start a fresh run from the top of the program. */
	public void start() throws ScriptException, ScriptYield {
		start(DEFAULT_OPCODE_BUDGET, DEFAULT_API_BUDGET);
	}

	public void start(int opcodeBudget, int apiBudget) throws ScriptException, ScriptYield {
		opcodesRemaining = opcodeBudget;
		apiCallsRemaining = apiBudget;
		locals.clear();
		stack.clear();
		finished = false;
		stack.push(Frame.seq(program.statements()));
		resume();
	}

	/** Continue after Wait or mid-run; refills budgets for this tick slice. */
	public void resume() throws ScriptException, ScriptYield {
		resume(DEFAULT_OPCODE_BUDGET, DEFAULT_API_BUDGET);
	}

	public void resume(int opcodeBudget, int apiBudget) throws ScriptException, ScriptYield {
		if (finished) {
			return;
		}
		opcodesRemaining = opcodeBudget;
		apiCallsRemaining = apiBudget;
		try {
			while (!stack.isEmpty()) {
				step();
			}
			finished = true;
		} catch (ScriptYield yield) {
			// stack preserved for later resume
			throw yield;
		}
	}

	private void step() throws ScriptException, ScriptYield {
		Frame frame = stack.peek();
		if (frame == null) {
			return;
		}
		switch (frame.kind) {
			case SEQ -> stepSeq(frame);
			case WHILE -> stepWhile(frame);
			case FOR -> stepFor(frame);
		}
	}

	private void stepSeq(Frame frame) throws ScriptException, ScriptYield {
		if (frame.index >= frame.statements.size()) {
			stack.pop();
			return;
		}
		Ast.Stmt stmt = frame.statements.get(frame.index);
		frame.index++;
		enter(stmt);
	}

	private void enter(Ast.Stmt stmt) throws ScriptException, ScriptYield {
		switch (stmt) {
			case Ast.Stmt.Block block -> stack.push(Frame.seq(block.statements()));
			case Ast.Stmt.VarDecl decl -> {
				bump();
				Object value = coerce(decl.typeName(), evaluate(decl.initializer()), decl.line());
				writeVar(decl.name(), value, decl.line(), true);
			}
			case Ast.Stmt.Assign assign -> {
				bump();
				Object value = evaluate(assign.value());
				writeVar(assign.name(), value, assign.line(), false);
			}
			case Ast.Stmt.If ifStmt -> {
				bump();
				if (isTruthy(evaluate(ifStmt.condition()))) {
					enter(ifStmt.thenBranch());
				} else if (ifStmt.elseBranch() != null) {
					enter(ifStmt.elseBranch());
				}
			}
			case Ast.Stmt.While whileStmt -> {
				bump();
				stack.push(Frame.whileLoop(whileStmt));
			}
			case Ast.Stmt.For forStmt -> {
				bump();
				if (forStmt.init() != null) {
					enter(forStmt.init());
				}
				stack.push(Frame.forLoop(forStmt));
			}
			case Ast.Stmt.Expression expression -> {
				bump();
				evaluate(expression.expression());
			}
		}
	}

	private void stepWhile(Frame frame) throws ScriptException, ScriptYield {
		Ast.Stmt.While whileStmt = frame.whileStmt;
		bump();
		if (frame.iterations >= MAX_LOOP_ITERATIONS) {
			throw new ScriptException("while loop iteration limit (" + MAX_LOOP_ITERATIONS + ")", whileStmt.line());
		}
		if (!isTruthy(evaluate(whileStmt.condition()))) {
			stack.pop();
			return;
		}
		frame.iterations++;
		enter(whileStmt.body());
	}

	private void stepFor(Frame frame) throws ScriptException, ScriptYield {
		Ast.Stmt.For forStmt = frame.forStmt;
		bump();
		if (frame.iterations >= MAX_LOOP_ITERATIONS) {
			throw new ScriptException("for loop iteration limit (" + MAX_LOOP_ITERATIONS + ")", forStmt.line());
		}
		boolean run = forStmt.condition() == null || isTruthy(evaluate(forStmt.condition()));
		if (!run) {
			stack.pop();
			return;
		}
		frame.iterations++;
		// Body then increment: push increment as a one-shot seq after body returns
		if (forStmt.increment() != null) {
			stack.push(Frame.seq(List.of(forStmt.increment())));
		}
		enter(forStmt.body());
	}

	private Object evaluate(Ast.Expr expr) throws ScriptException, ScriptYield {
		bump();
		return switch (expr) {
			case Ast.Expr.Literal literal -> literal.value();
			case Ast.Expr.Variable variable -> readVar(variable.name(), variable.line());
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

	private Object evaluateCall(Ast.Expr.Call call) throws ScriptException, ScriptYield {
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

		List<Object> args = new ArrayList<>(call.args().size());
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
			case "Sense" -> {
				if (!args.isEmpty()) {
					throw new ScriptException("Sense() takes no arguments", call.line());
				}
				yield host.sense();
			}
			case "HasBlock" -> {
				if (!args.isEmpty()) {
					throw new ScriptException("HasBlock() takes no arguments", call.line());
				}
				yield host.hasBlock();
			}
			case "Move" -> {
				if (args.size() != 1) {
					throw new ScriptException("Move(string) expects one argument", call.line());
				}
				host.move(String.valueOf(args.getFirst()));
				yield null;
			}
			case "Wait" -> {
				if (args.size() != 1) {
					throw new ScriptException("Wait(int) expects one argument", call.line());
				}
				host.waitTicks(asInt(args.getFirst(), call.line()));
				throw new ScriptYield();
			}
			default -> throw new ScriptException("Unknown method '" + name + "'", call.line());
		};
	}

	private Object readVar(String name, int line) throws ScriptException {
		if (SETTINGS.contains(name)) {
			return switch (name) {
				case "speed" -> host.getSpeed();
				case "range" -> host.getRange();
				case "autoPickup" -> host.getAutoPickup();
				default -> throw new ScriptException("Unknown setting '" + name + "'", line);
			};
		}
		if (!locals.containsKey(name)) {
			throw new ScriptException("Undefined variable '" + name + "'", line);
		}
		return locals.get(name);
	}

	private void writeVar(String name, Object value, int line, boolean declaring) throws ScriptException {
		if (SETTINGS.contains(name)) {
			switch (name) {
				case "speed" -> host.setSpeed(asInt(value, line));
				case "range" -> host.setRange(asInt(value, line));
				case "autoPickup" -> host.setAutoPickup(isTruthy(value));
				default -> throw new ScriptException("Unknown setting '" + name + "'", line);
			}
			return;
		}
		if (!declaring && !locals.containsKey(name)) {
			throw new ScriptException("Undefined variable '" + name + "'", line);
		}
		locals.put(name, value);
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
			throw new ScriptException("Opcode budget exceeded (loop/script too heavy for one tick)");
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

	private static final class Frame {
		enum Kind {
			SEQ,
			WHILE,
			FOR
		}

		final Kind kind;
		final List<Ast.Stmt> statements;
		int index;
		final Ast.Stmt.While whileStmt;
		final Ast.Stmt.For forStmt;
		int iterations;

		private Frame(Kind kind, List<Ast.Stmt> statements, Ast.Stmt.While whileStmt, Ast.Stmt.For forStmt) {
			this.kind = kind;
			this.statements = statements;
			this.whileStmt = whileStmt;
			this.forStmt = forStmt;
		}

		static Frame seq(List<Ast.Stmt> statements) {
			return new Frame(Kind.SEQ, statements, null, null);
		}

		static Frame whileLoop(Ast.Stmt.While whileStmt) {
			return new Frame(Kind.WHILE, List.of(), whileStmt, null);
		}

		static Frame forLoop(Ast.Stmt.For forStmt) {
			return new Frame(Kind.FOR, List.of(), null, forStmt);
		}
	}
}
