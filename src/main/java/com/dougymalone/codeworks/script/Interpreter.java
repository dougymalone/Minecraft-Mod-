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
	public static final int DEFAULT_OPCODE_BUDGET = 768;
	public static final int DEFAULT_API_BUDGET = 32;
	public static final int MAX_LOOP_ITERATIONS = 128;
	public static final int MAX_CALL_DEPTH = 16;

	private static final Set<String> HOST_METHODS = Set.of(
		"Harvest", "BreakFront", "Place", "Log", "Sense", "HasBlock", "HasStoredBlock", "HasItem",
		"Extended", "IsPistonExtended", "Move", "Wait", "Send", "Receive"
	);
	private static final Set<String> MATH_METHODS = Set.of("Min", "Max", "Abs", "Clamp");
	private static final Set<String> SETTINGS = Set.of("speed", "range", "channel");

	private final Ast.Program program;
	private final HostApi host;
	private final Map<String, Ast.FunctionDef> functions = new HashMap<>();
	private final Deque<Map<String, Object>> scopes = new ArrayDeque<>();
	private final Deque<Frame> stack = new ArrayDeque<>();
	private Object lastReturnValue;
	private int opcodesRemaining;
	private int apiCallsRemaining;
	private int callDepth;
	private int nextCallId;
	private boolean finished = true;

	public Interpreter(Ast.Program program, HostApi host) {
		this.program = program;
		this.host = host;
		for (Ast.FunctionDef fn : program.functions()) {
			if (functions.put(fn.name(), fn) != null) {
				// keep last definition silently
			}
		}
	}

	public static Ast.Program compile(String source) throws ScriptException {
		return new Parser(new Lexer(source).scanTokens()).parse();
	}

	public boolean isFinished() {
		return finished;
	}

	public void start() throws ScriptException, ScriptYield {
		start(DEFAULT_OPCODE_BUDGET, DEFAULT_API_BUDGET);
	}

	public void start(int opcodeBudget, int apiBudget) throws ScriptException, ScriptYield {
		opcodesRemaining = opcodeBudget;
		apiCallsRemaining = apiBudget;
		scopes.clear();
		scopes.push(new HashMap<>());
		stack.clear();
		lastReturnValue = null;
		callDepth = 0;
		finished = false;
		stack.push(Frame.seq(program.statements()));
		resume();
	}

	public void resume() throws ScriptException, ScriptYield {
		resume(DEFAULT_OPCODE_BUDGET, DEFAULT_API_BUDGET);
	}

	public void resume(int opcodeBudget, int apiBudget) throws ScriptException, ScriptYield {
		if (finished) {
			return;
		}
		opcodesRemaining = opcodeBudget;
		apiCallsRemaining = apiBudget;
		while (!stack.isEmpty()) {
			step();
		}
		finished = true;
	}

	private void step() throws ScriptException, ScriptYield {
		Frame frame = stack.peek();
		if (frame == null) {
			return;
		}
		try {
			switch (frame.kind) {
				case SEQ -> stepSeq(frame);
				case WHILE -> stepWhile(frame);
				case FOR -> stepFor(frame);
				case CALL -> stepCall(frame);
			}
		} catch (ScriptBreak ignored) {
			handleBreak();
		} catch (ScriptContinue ignored) {
			handleContinue();
		} catch (ScriptReturn ret) {
			handleReturn(ret.value);
		}
	}

	private void handleBreak() throws ScriptException {
		while (!stack.isEmpty()) {
			Frame frame = stack.pop();
			if (frame.kind == Frame.Kind.WHILE || frame.kind == Frame.Kind.FOR) {
				return;
			}
			if (frame.kind == Frame.Kind.CALL) {
				scopes.pop();
				callDepth--;
				throw new ScriptException("break outside of loop");
			}
		}
		throw new ScriptException("break outside of loop");
	}

	private void handleContinue() throws ScriptException {
		while (!stack.isEmpty()) {
			Frame frame = stack.peek();
			if (frame.kind == Frame.Kind.WHILE) {
				return;
			}
			if (frame.kind == Frame.Kind.FOR) {
				if (frame.forStmt.increment() != null) {
					stack.push(Frame.seq(List.of(frame.forStmt.increment())));
				}
				return;
			}
			if (frame.kind == Frame.Kind.CALL) {
				throw new ScriptException("continue outside of loop");
			}
			stack.pop();
		}
		throw new ScriptException("continue outside of loop");
	}

	private void handleReturn(Object value) {
		lastReturnValue = value;
		while (!stack.isEmpty()) {
			Frame frame = stack.pop();
			if (frame.kind == Frame.Kind.CALL) {
				scopes.pop();
				callDepth--;
				return;
			}
		}
		stack.clear();
		finished = true;
	}

	private void stepSeq(Frame frame) throws ScriptException, ScriptYield, ScriptBreak, ScriptContinue, ScriptReturn {
		if (frame.index >= frame.statements.size()) {
			stack.pop();
			return;
		}
		Ast.Stmt stmt = frame.statements.get(frame.index++);
		enter(stmt);
	}

	private void stepCall(Frame frame) {
		// Body finished without return — end call.
		stack.pop();
		scopes.pop();
		callDepth--;
		lastReturnValue = null;
	}

	private void enter(Ast.Stmt stmt) throws ScriptException, ScriptYield, ScriptBreak, ScriptContinue, ScriptReturn {
		switch (stmt) {
			case Ast.Stmt.Block block -> stack.push(Frame.seq(block.statements()));
			case Ast.Stmt.VarDecl decl -> {
				bump();
				Object value = evaluate(decl.initializer());
				if (decl.isArray()) {
					if (!(value instanceof ScriptArray array) || !array.elementType.equals(decl.typeName())) {
						throw new ScriptException("Expected new " + decl.typeName() + "[n]", decl.line());
					}
					writeVar(decl.name(), array, decl.line(), true);
				} else {
					writeVar(decl.name(), coerce(decl.typeName(), value, decl.line()), decl.line(), true);
				}
			}
			case Ast.Stmt.Assign assign -> {
				bump();
				applyAssign(assign.target(), assign.op(), evaluate(assign.value()), assign.line());
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
			case Ast.Stmt.Switch switchStmt -> {
				bump();
				Object disc = evaluate(switchStmt.discriminant());
				List<Ast.Stmt> chosen = switchStmt.defaultBody();
				for (Ast.SwitchCase caze : switchStmt.cases()) {
					if (valuesEqual(disc, evaluate(caze.value()))) {
						chosen = caze.body();
						break;
					}
				}
				if (chosen != null && !chosen.isEmpty()) {
					stack.push(Frame.seq(chosen));
				}
			}
			case Ast.Stmt.Break ignored -> throw new ScriptBreak();
			case Ast.Stmt.Continue ignored -> throw new ScriptContinue();
			case Ast.Stmt.Return returnStmt -> {
				Object value = returnStmt.value() == null ? null : evaluate(returnStmt.value());
				throw new ScriptReturn(value);
			}
			case Ast.Stmt.Expression expression -> {
				bump();
				evaluate(expression.expression());
			}
		}
	}

	private void stepWhile(Frame frame) throws ScriptException, ScriptYield, ScriptBreak, ScriptContinue, ScriptReturn {
		bump();
		if (frame.iterations >= MAX_LOOP_ITERATIONS) {
			throw new ScriptException("while loop iteration limit (" + MAX_LOOP_ITERATIONS + ")", frame.whileStmt.line());
		}
		if (!isTruthy(evaluate(frame.whileStmt.condition()))) {
			stack.pop();
			return;
		}
		frame.iterations++;
		enter(frame.whileStmt.body());
	}

	private void stepFor(Frame frame) throws ScriptException, ScriptYield, ScriptBreak, ScriptContinue, ScriptReturn {
		bump();
		if (frame.iterations >= MAX_LOOP_ITERATIONS) {
			throw new ScriptException("for loop iteration limit (" + MAX_LOOP_ITERATIONS + ")", frame.forStmt.line());
		}
		boolean run = frame.forStmt.condition() == null || isTruthy(evaluate(frame.forStmt.condition()));
		if (!run) {
			stack.pop();
			return;
		}
		frame.iterations++;
		if (frame.forStmt.increment() != null) {
			stack.push(Frame.seq(List.of(frame.forStmt.increment())));
		}
		enter(frame.forStmt.body());
	}

	private Object evaluate(Ast.Expr expr) throws ScriptException, ScriptYield {
		bump();
		return switch (expr) {
			case Ast.Expr.Literal literal -> literal.value();
			case Ast.Expr.Variable variable -> readVar(variable.name(), variable.line());
			case Ast.Expr.Unary unary -> switch (unary.op()) {
				case BANG -> !isTruthy(evaluate(unary.right()));
				case MINUS -> -asInt(evaluate(unary.right()), unary.line());
				default -> throw new ScriptException("Unknown unary operator", unary.line());
			};
			case Ast.Expr.Binary binary -> evalBinary(binary);
			case Ast.Expr.Call call -> evaluateCall(call);
			case Ast.Expr.Index index -> {
				Object array = evaluate(index.array());
				int i = asInt(evaluate(index.index()), index.line());
				if (!(array instanceof ScriptArray scriptArray)) {
					throw new ScriptException("Indexing requires an array", index.line());
				}
				yield scriptArray.get(i, index.line());
			}
			case Ast.Expr.Member member -> {
				Object object = evaluate(member.object());
				if (!"Length".equals(member.name())) {
					throw new ScriptException("Unknown member '" + member.name() + "' (try Method() for helpers)", member.line());
				}
				if (object instanceof String string) {
					yield string.length();
				}
				if (object instanceof ScriptArray array) {
					yield array.length();
				}
				throw new ScriptException(".Length requires string or array", member.line());
			}
			case Ast.Expr.NewArray newArray -> new ScriptArray(newArray.elementType(), asInt(evaluate(newArray.length()), newArray.line()));
			case Ast.Expr.Prefix prefix -> {
				int next = asInt(readAssignable(prefix.target(), prefix.line()), prefix.line())
					+ (prefix.op() == TokenType.PLUSPLUS ? 1 : -1);
				writeAssignable(prefix.target(), next, prefix.line(), false);
				yield next;
			}
			case Ast.Expr.Postfix postfix -> {
				int value = asInt(readAssignable(postfix.target(), postfix.line()), postfix.line());
				writeAssignable(postfix.target(), value + (postfix.op() == TokenType.PLUSPLUS ? 1 : -1), postfix.line(), false);
				yield value;
			}
			case Ast.Expr.AssignExpr assign -> applyAssign(assign.target(), assign.op(), evaluate(assign.value()), assign.line());
		};
	}

	private Object evalBinary(Ast.Expr.Binary binary) throws ScriptException, ScriptYield {
		if (binary.op() == TokenType.AMPAMP) {
			Object left = evaluate(binary.left());
			return isTruthy(left) && isTruthy(evaluate(binary.right()));
		}
		if (binary.op() == TokenType.PIPEPIPE) {
			Object left = evaluate(binary.left());
			return isTruthy(left) || isTruthy(evaluate(binary.right()));
		}
		Object left = evaluate(binary.left());
		Object right = evaluate(binary.right());
		return switch (binary.op()) {
			case PLUS -> (left instanceof String || right instanceof String)
				? String.valueOf(left) + String.valueOf(right)
				: asInt(left, binary.line()) + asInt(right, binary.line());
			case MINUS -> asInt(left, binary.line()) - asInt(right, binary.line());
			case STAR -> asInt(left, binary.line()) * asInt(right, binary.line());
			case SLASH -> {
				int d = asInt(right, binary.line());
				if (d == 0) {
					throw new ScriptException("Division by zero", binary.line());
				}
				yield asInt(left, binary.line()) / d;
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

	private Object evaluateCall(Ast.Expr.Call call) throws ScriptException, ScriptYield {
		List<Object> args = new ArrayList<>(call.args().size());
		for (Ast.Expr arg : call.args()) {
			args.add(evaluate(arg));
		}

		if (call.callee() instanceof Ast.Expr.Member member) {
			return evalInstanceMethod(evaluate(member.object()), member.name(), args, call.line());
		}
		if (!(call.callee() instanceof Ast.Expr.Variable variable)) {
			throw new ScriptException("Invalid call target", call.line());
		}
		String name = variable.name();
		if (MATH_METHODS.contains(name)) {
			return evalMath(name, args, call.line());
		}
		if (HOST_METHODS.contains(name)) {
			return evalHost(name, args, call.line());
		}
		Ast.FunctionDef fn = functions.get(name);
		if (fn == null) {
			throw new ScriptException("Unknown method '" + name + "'", call.line());
		}
		return callUserFunction(fn, args, call.line());
	}

	private Object callUserFunction(Ast.FunctionDef fn, List<Object> args, int line) throws ScriptException, ScriptYield {
		if (args.size() != fn.params().size()) {
			throw new ScriptException("'" + fn.name() + "' expects " + fn.params().size() + " argument(s)", line);
		}
		if (callDepth >= MAX_CALL_DEPTH) {
			throw new ScriptException("Call depth exceeded (" + MAX_CALL_DEPTH + ")", line);
		}
		Map<String, Object> frameLocals = new HashMap<>();
		for (int i = 0; i < fn.params().size(); i++) {
			Ast.Param param = fn.params().get(i);
			frameLocals.put(param.name(), coerce(param.typeName(), args.get(i), line));
		}
		int callId = ++nextCallId;
		scopes.push(frameLocals);
		callDepth++;
		stack.push(Frame.call(fn, callId));
		stack.push(Frame.seq(fn.body().statements()));

		try {
			while (hasCall(callId)) {
				step();
			}
		} catch (ScriptYield yield) {
			throw yield;
		}

		Object result = lastReturnValue;
		lastReturnValue = null;
		if ("void".equals(fn.returnType())) {
			return null;
		}
		if (result == null) {
			throw new ScriptException("Function '" + fn.name() + "' must return " + fn.returnType(), line);
		}
		return coerce(fn.returnType(), result, line);
	}

	private boolean hasCall(int callId) {
		for (Frame frame : stack) {
			if (frame.kind == Frame.Kind.CALL && frame.callId == callId) {
				return true;
			}
		}
		return false;
	}

	private Object evalInstanceMethod(Object object, String name, List<Object> args, int line) throws ScriptException {
		if (object instanceof String string) {
			return switch (name) {
				case "Length" -> requireNoArgs(name, args, line, string.length());
				case "Contains" -> {
					requireArgCount(name, args, 1, line);
					yield string.contains(String.valueOf(args.getFirst()));
				}
				case "Equals" -> {
					requireArgCount(name, args, 1, line);
					yield string.equals(String.valueOf(args.getFirst()));
				}
				case "EqualsIgnoreCase" -> {
					requireArgCount(name, args, 1, line);
					yield string.equalsIgnoreCase(String.valueOf(args.getFirst()));
				}
				default -> throw new ScriptException("Unknown string method '" + name + "'", line);
			};
		}
		if (object instanceof ScriptArray array) {
			if ("Length".equals(name)) {
				return requireNoArgs(name, args, line, array.length());
			}
			throw new ScriptException("Unknown array method '" + name + "'", line);
		}
		throw new ScriptException("Cannot call '" + name + "' on this value", line);
	}

	private Object evalMath(String name, List<Object> args, int line) throws ScriptException {
		return switch (name) {
			case "Min" -> {
				requireArgCount(name, args, 2, line);
				yield Math.min(asInt(args.get(0), line), asInt(args.get(1), line));
			}
			case "Max" -> {
				requireArgCount(name, args, 2, line);
				yield Math.max(asInt(args.get(0), line), asInt(args.get(1), line));
			}
			case "Abs" -> {
				requireArgCount(name, args, 1, line);
				yield Math.abs(asInt(args.getFirst(), line));
			}
			case "Clamp" -> {
				requireArgCount(name, args, 3, line);
				yield Math.clamp(asInt(args.get(0), line), asInt(args.get(1), line), asInt(args.get(2), line));
			}
			default -> throw new ScriptException("Unknown Math function", line);
		};
	}

	private Object evalHost(String name, List<Object> args, int line) throws ScriptException, ScriptYield {
		if (apiCallsRemaining-- <= 0) {
			throw new ScriptException("API call budget exceeded", line);
		}
		return switch (name) {
			case "Harvest", "BreakFront" -> {
				requireArgCount(name, args, 0, line);
				yield host.harvest();
			}
			case "Place" -> {
				requireArgCount(name, args, 0, line);
				yield host.place();
			}
			case "Log" -> {
				requireArgCount(name, args, 1, line);
				host.log(String.valueOf(args.getFirst()));
				yield null;
			}
			case "Sense" -> {
				requireArgCount(name, args, 0, line);
				yield host.sense();
			}
			case "HasBlock" -> {
				requireArgCount(name, args, 0, line);
				yield host.hasBlock();
			}
			case "HasStoredBlock", "HasItem" -> {
				requireArgCount(name, args, 0, line);
				yield host.hasStoredBlock();
			}
			case "Extended", "IsPistonExtended" -> {
				requireArgCount(name, args, 0, line);
				yield host.extended();
			}
			case "Move" -> {
				requireArgCount(name, args, 1, line);
				host.move(String.valueOf(args.getFirst()));
				yield null;
			}
			case "Wait" -> {
				requireArgCount(name, args, 1, line);
				host.waitTicks(asInt(args.getFirst(), line));
				throw new ScriptYield();
			}
			case "Send" -> {
				requireArgCount(name, args, 2, line);
				host.send(String.valueOf(args.get(0)), String.valueOf(args.get(1)));
				yield null;
			}
			case "Receive" -> {
				requireArgCount(name, args, 1, line);
				yield host.receive(String.valueOf(args.getFirst()));
			}
			default -> throw new ScriptException("Unknown method '" + name + "'", line);
		};
	}

	private Object applyAssign(Ast.Expr target, TokenType op, Object value, int line) throws ScriptException, ScriptYield {
		if (op == TokenType.EQ) {
			writeAssignable(target, value, line, false);
			return value;
		}
		Object current = readAssignable(target, line);
		Object next = switch (op) {
			case PLUSEQ -> (current instanceof String || value instanceof String)
				? String.valueOf(current) + String.valueOf(value)
				: asInt(current, line) + asInt(value, line);
			case MINUSEQ -> asInt(current, line) - asInt(value, line);
			case STAREQ -> asInt(current, line) * asInt(value, line);
			case SLASHEQ -> {
				int d = asInt(value, line);
				if (d == 0) {
					throw new ScriptException("Division by zero", line);
				}
				yield asInt(current, line) / d;
			}
			default -> throw new ScriptException("Invalid compound assignment", line);
		};
		writeAssignable(target, next, line, false);
		return next;
	}

	private Object readAssignable(Ast.Expr target, int line) throws ScriptException, ScriptYield {
		return switch (target) {
			case Ast.Expr.Variable variable -> readVar(variable.name(), line);
			case Ast.Expr.Index index -> {
				Object array = evaluate(index.array());
				int i = asInt(evaluate(index.index()), line);
				if (!(array instanceof ScriptArray scriptArray)) {
					throw new ScriptException("Indexing requires an array", line);
				}
				yield scriptArray.get(i, line);
			}
			default -> throw new ScriptException("Invalid assignment target", line);
		};
	}

	private void writeAssignable(Ast.Expr target, Object value, int line, boolean declaring) throws ScriptException, ScriptYield {
		switch (target) {
			case Ast.Expr.Variable variable -> writeVar(variable.name(), value, line, declaring);
			case Ast.Expr.Index index -> {
				Object array = evaluate(index.array());
				int i = asInt(evaluate(index.index()), line);
				if (!(array instanceof ScriptArray scriptArray)) {
					throw new ScriptException("Indexing requires an array", line);
				}
				Object coerced = switch (scriptArray.elementType) {
					case "int" -> asInt(value, line);
					case "string" -> String.valueOf(value);
					case "bool" -> isTruthy(value);
					default -> value;
				};
				scriptArray.set(i, coerced, line);
			}
			default -> throw new ScriptException("Invalid assignment target", line);
		}
	}

	private Object readVar(String name, int line) throws ScriptException {
		if (SETTINGS.contains(name)) {
			return switch (name) {
				case "speed" -> host.getSpeed();
				case "range" -> host.getRange();
				case "channel" -> host.getChannel();
				default -> throw new ScriptException("Unknown setting", line);
			};
		}
		if (!scopes.peek().containsKey(name)) {
			throw new ScriptException("Undefined variable '" + name + "'", line);
		}
		return scopes.peek().get(name);
	}

	private void writeVar(String name, Object value, int line, boolean declaring) throws ScriptException {
		if (SETTINGS.contains(name)) {
			switch (name) {
				case "speed" -> host.setSpeed(asInt(value, line));
				case "range" -> host.setRange(asInt(value, line));
				case "channel" -> host.setChannel(String.valueOf(value));
				default -> throw new ScriptException("Unknown setting", line);
			}
			return;
		}
		if (!declaring && !scopes.peek().containsKey(name)) {
			throw new ScriptException("Undefined variable '" + name + "'", line);
		}
		scopes.peek().put(name, value);
	}

	private Object coerce(String typeName, Object value, int line) throws ScriptException {
		return switch (typeName) {
			case "int" -> asInt(value, line);
			case "bool" -> isTruthy(value);
			case "string" -> String.valueOf(value);
			case "void" -> null;
			default -> throw new ScriptException("Unknown type '" + typeName + "'", line);
		};
	}

	private void bump() throws ScriptException {
		if (opcodesRemaining-- <= 0) {
			throw new ScriptException("Opcode budget exceeded (loop/script too heavy for one tick)");
		}
	}

	private static void requireArgCount(String name, List<Object> args, int expected, int line) throws ScriptException {
		if (args.size() != expected) {
			throw new ScriptException(name + " expects " + expected + " argument(s)", line);
		}
	}

	private static Object requireNoArgs(String name, List<Object> args, int line, Object value) throws ScriptException {
		requireArgCount(name, args, 0, line);
		return value;
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
		if (value instanceof ScriptArray array) {
			return array.length() > 0;
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
			FOR,
			CALL
		}

		final Kind kind;
		final List<Ast.Stmt> statements;
		int index;
		final Ast.Stmt.While whileStmt;
		final Ast.Stmt.For forStmt;
		final Ast.FunctionDef function;
		final int callId;
		int iterations;

		private Frame(
			Kind kind,
			List<Ast.Stmt> statements,
			Ast.Stmt.While whileStmt,
			Ast.Stmt.For forStmt,
			Ast.FunctionDef function,
			int callId
		) {
			this.kind = kind;
			this.statements = statements;
			this.whileStmt = whileStmt;
			this.forStmt = forStmt;
			this.function = function;
			this.callId = callId;
		}

		static Frame seq(List<Ast.Stmt> statements) {
			return new Frame(Kind.SEQ, statements, null, null, null, 0);
		}

		static Frame whileLoop(Ast.Stmt.While whileStmt) {
			return new Frame(Kind.WHILE, List.of(), whileStmt, null, null, 0);
		}

		static Frame forLoop(Ast.Stmt.For forStmt) {
			return new Frame(Kind.FOR, List.of(), null, forStmt, null, 0);
		}

		static Frame call(Ast.FunctionDef function, int callId) {
			return new Frame(Kind.CALL, List.of(), null, null, function, callId);
		}
	}
}
