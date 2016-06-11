package net.sandius.rembulan.parser.ast;

import net.sandius.rembulan.parser.util.Util;
import net.sandius.rembulan.util.Check;

import java.util.List;

public class AssignStatement implements Statement {

	private final List<LValueExpr> vars;
	private final List<Expr> exprs;

	public AssignStatement(List<LValueExpr> vars, List<Expr> exprs) {
		this.vars = Check.notNull(vars);
		this.exprs = Check.notNull(exprs);
	}

	@Override
	public String toString() {
		return "(local [" + Util.listToString(vars, ", ") + "] [" + Util.listToString(exprs, ", ") + "])";
	}

	@Override
	public void accept(StatementVisitor visitor) {
		visitor.visitAssignment(vars, exprs);
	}

}