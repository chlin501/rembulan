package net.sandius.rembulan.parser.ast;

import net.sandius.rembulan.parser.util.Util;
import net.sandius.rembulan.util.Check;

import java.util.List;

public abstract class CallExpr implements RValueExpr {

	private final List<Expr> args;

	public CallExpr(List<Expr> args) {
		this.args = Check.notNull(args);
	}

	public List<Expr> args() {
		return args;
	}

	public static class FunctionCallExpr extends CallExpr {

		private final Expr fn;

		public FunctionCallExpr(Expr fn, List<Expr> args) {
			super(args);
			this.fn = Check.notNull(fn);
		}

		@Override
		public String toString() {
			return "(call-fn " + fn + " [" + Util.listToString(args(), ", ") + "])";
		}

	}

	public static class MethodCallExpr extends CallExpr {

		private final Expr target;
		private final Name methodName;

		public MethodCallExpr(Expr target, Name methodName, List<Expr> args) {
			super(args);
			this.target = Check.notNull(target);
			this.methodName = Check.notNull(methodName);
		}

		@Override
		public String toString() {
			return "(call-method " + target + " " + methodName + " [" + Util.listToString(args(), ", ") + "])";
		}

	}

}