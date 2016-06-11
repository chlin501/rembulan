package net.sandius.rembulan.parser.ast;

import net.sandius.rembulan.util.Check;

public class FunctionDefExpr implements RValueExpr {

	private final FunctionLiteral body;

	public FunctionDefExpr(FunctionLiteral body) {
		this.body = Check.notNull(body);
	}

	@Override
	public String toString() {
		return "(fndef " + body + ")";
	}

}