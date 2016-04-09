package net.sandius.rembulan.compiler.gen;

public abstract class CodeVisitor {

	public abstract void visitMove(Object id, SlotState st, int r_src, int r_dest);

	public abstract void visitLoadK(Object id, SlotState st, int r_dest, int constIndex);

	public abstract void visitLoadBool(Object id, SlotState st, int r_dest, boolean value);

	public abstract void visitLoadNil(Object id, SlotState st, int r_dest, int count);

	public abstract void visitGetUpVal(Object id, SlotState st, int r_dest, int upvalueIndex);

	public abstract void visitGetTabUp(Object id, SlotState st, int r_dest, int upvalueIndex, int rk_key);

	public abstract void visitGetTable(Object id, SlotState st, int r_dest, int r_tab, int rk_key);

	public abstract void visitSetTabUp(Object id, SlotState st, int upvalueIndex, int rk_key, int rk_value);

	public abstract void visitSetUpVal(Object id, SlotState st, int r_src, int upvalueIndex);

	public abstract void visitSetTable(Object id, SlotState st, int r_tab, int rk_key, int rk_value);

	public abstract void visitNewTable(Object id, SlotState st, int r_dest, int arraySize, int hashSize);

	public abstract void visitSelf(Object id, SlotState st, int r_dest, int r_self, int rk_key);

	public abstract void visitConcat(Object id, SlotState st, int r_dest, int r_begin, int r_end);

	public abstract void visitEq(Object id, SlotState st, boolean pos, int rk_left, int rk_right, Object trueBranchIdentity, Object falseBranchIdentity);

	public abstract void visitLt(Object id, SlotState st, boolean pos, int rk_left, int rk_right, Object trueBranchIdentity, Object falseBranchIdentity);

	public abstract void visitLe(Object id, SlotState st, boolean pos, int rk_left, int rk_right, Object trueBranchIdentity, Object falseBranchIdentity);

	public abstract void visitTest(Object id, SlotState st, int r_index, boolean value, Object trueBranchIdentity, Object falseBranchIdentity);

	public abstract void visitCall(Object id, SlotState st, int r_tgt, int b, int c);

	public abstract void visitTailCall(Object id, SlotState st, int r_tgt, int b);

	public abstract void visitReturn(Object id, SlotState st, int r_from, int b);

	public abstract void visitForLoop(Object id, SlotState st, int r_base);

	public abstract void visitForPrep(Object id, SlotState st, int r_base);

	// TODO: TFORCALL

	// TODO: TFORLOOP

	// TODO: SETLIST

	public abstract void visitClosure(Object id, SlotState st, int r_dest, int index);

	public abstract void visitVararg(Object id, SlotState st, int r_base, int b);

}