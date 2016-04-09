package net.sandius.rembulan.core;

public abstract class Dispatch {

	private Dispatch() {
		// not to be instantiated or extended
	}

	public static Invokable callTarget(LuaState state, Object target) {
		if (target instanceof Invokable) {
			return (Invokable) target;
		}
		else {
			Object handler = Metatables.getMetamethod(state, Metatables.MT_CALL, target);

			if (handler instanceof Invokable) {
				return (Invokable) handler;
			}
			else {
				throw new IllegalOperationAttemptException("call", Value.typeOf(target).name);
			}
		}
	}

	public static void mt_invoke(LuaState state, ObjectSink result, Object target) throws ControlThrowable {
		Invokable fn = callTarget(state, target);
		if (fn == target) fn.invoke(state, result);
		else fn.invoke(state, result, target);
	}

	public static void mt_invoke(LuaState state, ObjectSink result, Object target, Object arg1) throws ControlThrowable {
		Invokable fn = callTarget(state, target);
		if (fn == target) fn.invoke(state, result, arg1);
		else fn.invoke(state, result, target, arg1);
	}

	public static void mt_invoke(LuaState state, ObjectSink result, Object target, Object arg1, Object arg2) throws ControlThrowable {
		Invokable fn = callTarget(state, target);
		if (fn == target) fn.invoke(state, result, arg1, arg2);
		else fn.invoke(state, result, target, arg1, arg2);
	}

	public static void mt_invoke(LuaState state, ObjectSink result, Object target, Object arg1, Object arg2, Object arg3) throws ControlThrowable {
		Invokable fn = callTarget(state, target);
		if (fn == target) fn.invoke(state, result, arg1, arg2, arg3);
		else fn.invoke(state, result, target, arg1, arg2, arg3);
	}

	public static void mt_invoke(LuaState state, ObjectSink result, Object target, Object arg1, Object arg2, Object arg3, Object arg4) throws ControlThrowable {
		Invokable fn = callTarget(state, target);
		if (fn == target) fn.invoke(state, result, arg1, arg2, arg3, arg4);
		else fn.invoke(state, result, target, arg1, arg2, arg3, arg4);
	}

	public static void mt_invoke(LuaState state, ObjectSink result, Object target, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) throws ControlThrowable {
		Invokable fn = callTarget(state, target);
		if (fn == target) fn.invoke(state, result, arg1, arg2, arg3, arg4, arg5);
		else fn.invoke(state, result, new Object[] { target, arg1, arg2, arg3, arg4, arg5 });
	}

	public static void mt_invoke(LuaState state, ObjectSink result, Object target, Object[] args) throws ControlThrowable {
		Invokable fn = callTarget(state, target);
		if (fn == target) {
			fn.invoke(state, result, args);
		}
		else {
			Object[] mtArgs = new Object[args.length + 1];
			mtArgs[0] = target;
			System.arraycopy(args, 0, mtArgs, 1, args.length);
			fn.invoke(state, result, mtArgs);
		}
	}

	public static void evaluateTailCalls(LuaState state, ObjectSink r) throws ControlThrowable {
		while (r.isTailCall()) {
			switch (r.size()) {
				case 0: throw new IllegalStateException();
				case 1: mt_invoke(state, r, r._0()); break;
				case 2: mt_invoke(state, r, r._0(), r._1()); break;
				case 3: mt_invoke(state, r, r._0(), r._1(), r._2()); break;
				case 4: mt_invoke(state, r, r._0(), r._1(), r._2(), r._3()); break;
				case 5: mt_invoke(state, r, r._0(), r._1(), r._2(), r._3(), r._4()); break;
				default: mt_invoke(state, r, r._0(), r.tailAsArray()); break;
			}
		}
	}

	public static void call(LuaState state, ObjectSink result, Object target) throws ControlThrowable {
		mt_invoke(state, result, target);
		evaluateTailCalls(state, result);
	}

	public static void call(LuaState state, ObjectSink result, Object target, Object arg1) throws ControlThrowable {
		mt_invoke(state, result, target, arg1);
		evaluateTailCalls(state, result);
	}

	public static void call(LuaState state, ObjectSink result, Object target, Object arg1, Object arg2) throws ControlThrowable {
		mt_invoke(state, result, target, arg1, arg2);
		evaluateTailCalls(state, result);
	}

	public static void call(LuaState state, ObjectSink result, Object target, Object arg1, Object arg2, Object arg3) throws ControlThrowable {
		mt_invoke(state, result, target, arg1, arg2, arg3);
		evaluateTailCalls(state, result);
	}

	public static void call(LuaState state, ObjectSink result, Object target, Object arg1, Object arg2, Object arg3, Object arg4) throws ControlThrowable {
		mt_invoke(state, result, target, arg1, arg2, arg3, arg4);
		evaluateTailCalls(state, result);
	}

	public static void call(LuaState state, ObjectSink result, Object target, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) throws ControlThrowable {
		mt_invoke(state, result, target, arg1, arg2, arg3, arg4, arg5);
		evaluateTailCalls(state, result);
	}

	public static void call(LuaState state, ObjectSink result, Object target, Object[] args) throws ControlThrowable {
		mt_invoke(state, result, target, args);
		evaluateTailCalls(state, result);
	}

	public static void index(LuaState state, ObjectSink result, Object table, Object key) throws ControlThrowable {
		if (table instanceof Table) {
			Table t = (Table) table;
			Object value = t.rawget(key);

			if (value != null) {
				result.setTo(value);
				return;
			}
		}

		Object handler = Metatables.getMetamethod(state, Metatables.MT_INDEX, table);

		if (handler == null && table instanceof Table) {
			// key not found and no index metamethod, returning nil
			result.setTo(null);
			return;
		}
		if (handler instanceof Invokable) {
			// call the handler
			Invokable fn = (Invokable) handler;

			fn.invoke(state, result, handler, table, key);
			evaluateTailCalls(state, result);
		}
		else if (handler instanceof Table) {
			// TODO: protect against infinite loops
			index(state, result, handler, key);
		}
		else {
			throw new IllegalOperationAttemptException("index", Value.typeOf(table).name);
		}
	}

	private static void try_mt_arithmetic(LuaState state, ObjectSink result, String event, Object a, Object b) throws ControlThrowable {
		Object handler = Metatables.binaryHandlerFor(state, Metatables.MT_ADD, a, b);

		if (handler != null) {
			call(state, result, handler, a, b);
		}
		else {
			String typeName = Value.typeOf(Conversions.objectAsNumber(a) == null ? a : b).name;
			throw new IllegalOperationAttemptException("perform arithmetic on", typeName);
		}
	}

	public static Number add(Number a, Number b) {
		return MathImplementation.arithmetic(a, b).do_add(a, b);
	}

	public static Number add_integer(Number a, Number b) {
		return MathImplementation.INTEGER_MATH.do_add(a, b);
	}

	public static Number add_float(Number a, Number b) {
		return MathImplementation.FLOAT_MATH.do_add(a, b);
	}

	public static void mt_add(LuaState state, ObjectSink result, Object a, Object b) throws ControlThrowable {
		MathImplementation math = MathImplementation.arithmetic(a, b);
		if (math != null) {
			result.setTo(math.do_add(Conversions.objectAsNumber(a), Conversions.objectAsNumber(b)));
		}
		else {
			try_mt_arithmetic(state, result, Metatables.MT_ADD, a, b);
		}
	}

	public static void sub(LuaState state, ObjectSink result, Object a, Object b) throws ControlThrowable {
		MathImplementation m = MathImplementation.arithmetic(a, b);
		if (m != null) {
			result.setTo(m.do_sub(Conversions.objectAsNumber(a), Conversions.objectAsNumber(b)));
		}
		else {
			try_mt_arithmetic(state, result, Metatables.MT_SUB, a, b);
		}
	}

	public static void mul(LuaState state, ObjectSink result, Object a, Object b) throws ControlThrowable {
		MathImplementation m = MathImplementation.arithmetic(a, b);
		if (m != null) {
			result.setTo(m.do_mul(Conversions.objectAsNumber(a), Conversions.objectAsNumber(b)));
		}
		else {
			try_mt_arithmetic(state, result, Metatables.MT_MUL, a, b);
		}
	}

	public static void div(LuaState state, ObjectSink result, Object a, Object b) throws ControlThrowable {
		MathImplementation m = MathImplementation.arithmetic(a, b);
		if (m != null) {
			result.setTo(m.do_div(Conversions.objectAsNumber(a), Conversions.objectAsNumber(b)));
		}
		else {
			try_mt_arithmetic(state, result, Metatables.MT_DIV, a, b);
		}
	}

	public static void mod(LuaState state, ObjectSink result, Object a, Object b) throws ControlThrowable {
		MathImplementation m = MathImplementation.arithmetic(a, b);
		if (m != null) {
			result.setTo(m.do_mod(Conversions.objectAsNumber(a), Conversions.objectAsNumber(b)));
		}
		else {
			try_mt_arithmetic(state, result, Metatables.MT_MOD, a, b);
		}
	}

	public static void idiv(LuaState state, ObjectSink result, Object a, Object b) throws ControlThrowable {
		MathImplementation m = MathImplementation.arithmetic(a, b);
		if (m != null) {
			result.setTo(m.do_idiv(Conversions.objectAsNumber(a), Conversions.objectAsNumber(b)));
		}
		else {
			try_mt_arithmetic(state, result, Metatables.MT_IDIV, a, b);
		}
	}

	public static void pow(LuaState state, ObjectSink result, Object a, Object b) throws ControlThrowable {
		MathImplementation m = MathImplementation.arithmetic(a, b);
		if (m != null) {
			result.setTo(m.do_pow(Conversions.objectAsNumber(a), Conversions.objectAsNumber(b)));
		}
		else {
			try_mt_arithmetic(state, result, Metatables.MT_POW, a, b);
		}
	}

	public static void eq(LuaState state, ObjectSink result, Object a, Object b) throws ControlThrowable {
		throw new UnsupportedOperationException("not implemented");
	}

	public static void le(LuaState state, ObjectSink result, Object a, Object b) throws ControlThrowable {
		throw new UnsupportedOperationException("not implemented");
	}

	public static void lt(LuaState state, ObjectSink result, Object a, Object b) throws ControlThrowable {
		throw new UnsupportedOperationException("not implemented");
	}

}
