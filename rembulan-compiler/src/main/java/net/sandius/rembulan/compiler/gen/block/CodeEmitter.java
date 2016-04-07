package net.sandius.rembulan.compiler.gen.block;

import net.sandius.rembulan.compiler.gen.PrototypeContext;
import net.sandius.rembulan.compiler.gen.SlotState;
import net.sandius.rembulan.core.ControlThrowable;
import net.sandius.rembulan.core.Dispatch;
import net.sandius.rembulan.core.LuaState;
import net.sandius.rembulan.core.ObjectSink;
import net.sandius.rembulan.core.Resumable;
import net.sandius.rembulan.core.ResumeInfo;
import net.sandius.rembulan.core.Table;
import net.sandius.rembulan.core.Upvalue;
import net.sandius.rembulan.util.Check;
import net.sandius.rembulan.util.asm.ASMUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

public class CodeEmitter {

	public final int REGISTER_OFFSET = 4;

	public final int LV_STATE = 1;
	public final int LV_OBJECTSINK = 2;
	public final int LV_RESUME = 3;

	private final ClassEmitter parent;
	private final PrototypeContext context;

	private final MethodNode methodNode;
	private final MethodNode resumeMethodNode;
	private MethodNode saveStateNode;

	private final Map<Object, LabelNode> labels;
	private final ArrayList<LabelNode> resumptionPoints;

	private final InsnList resumeSwitch;
	private final InsnList code;
	private final InsnList errorState;
	private final InsnList resumeHandler;

	public CodeEmitter(ClassEmitter parent, PrototypeContext context) {
		this.parent = Check.notNull(parent);
		this.context = Check.notNull(context);
		this.labels = new HashMap<>();
		this.resumptionPoints = new ArrayList<>();

		this.methodNode = new MethodNode(
				ACC_PRIVATE,
				methodName(),
				methodType().getDescriptor(),
				null,
				exceptions());

		this.resumeMethodNode = new MethodNode(
				ACC_PUBLIC,
				"resume",
				Type.getMethodType(
						Type.VOID_TYPE,
						Type.getType(LuaState.class),
						Type.getType(ObjectSink.class),
						Type.getType(Object.class)).getDescriptor(),
						null,
				exceptions());

		this.saveStateNode = null;

		resumeSwitch = new InsnList();
		code = new InsnList();
		errorState = new InsnList();
		resumeHandler = new InsnList();
	}

	public MethodNode node() {
		return methodNode;
	}

	public MethodNode resumeNode() {
		return resumeMethodNode;
	}

	public MethodNode saveNode() {
		return saveStateNode;
	}

	private String methodName() {
		return "run";
	}

	private Type methodType() {
		Type[] args = new Type[3 + numOfRegisters()];
		args[0] = Type.getType(LuaState.class);
		args[1] = Type.getType(ObjectSink.class);
		args[2] = Type.INT_TYPE;
		for (int i = 3; i < args.length; i++) {
			args[i] = Type.getType(Object.class);
		}
		return Type.getMethodType(Type.VOID_TYPE, args);
	}

	private String[] exceptions() {
		return new String[] { Type.getInternalName(ControlThrowable.class) };
	}

	protected LabelNode _l(Object key) {
		LabelNode l = labels.get(key);

		if (l != null) {
			return l;
		}
		else {
			LabelNode nl = new LabelNode();
			labels.put(key, nl);
			return nl;
		}
	}

	protected Type thisType() {
		return Type.getType("L" + thisClassName().replace('.', '/') + ";");
	}

	protected String thisClassName() {
		return context.className();
	}

	public void _note(String text) {
		System.out.println("// " + text);
	}

	public void _dup() {
		code.add(new InsnNode(DUP));
	}

	public void _swap() {
		code.add(new InsnNode(SWAP));
	}

	public void _push_this() {
		code.add(new VarInsnNode(ALOAD, 0));
	}

	public void _push_null() {
		code.add(new InsnNode(ACONST_NULL));
	}

	public void _load_k(int idx, Class castTo) {
		Object k = context.getConst(idx);

		if (k == null) {
			_push_null();
		}
		else if (k instanceof Boolean) {
			code.add(ASMUtils.loadInt((Boolean) k ? 1 : 0));
			code.add(ASMUtils.box(Type.BOOLEAN_TYPE, Type.getType(Boolean.class)));
		}
		else if (k instanceof Double || k instanceof Float) {
			code.add(ASMUtils.loadDouble(((Number) k).doubleValue()));
			code.add(ASMUtils.box(Type.DOUBLE_TYPE, Type.getType(Double.class)));
		}
		else if (k instanceof Number) {
			code.add(ASMUtils.loadLong(((Number) k).longValue()));
			code.add(ASMUtils.box(Type.LONG_TYPE, Type.getType(Long.class)));
		}
		else if (k instanceof String) {
			code.add(new LdcInsnNode((String) k));
		}
		else {
			throw new UnsupportedOperationException("Illegal const type: " + k.getClass());
		}

		if (castTo != null) {
			if (!castTo.isAssignableFrom(k.getClass())) {
				_checkCast(castTo);
			}
		}
	}

	public void _load_k(int idx) {
		_load_k(idx, null);
	}

	public void _load_reg_value(int idx) {
		code.add(new VarInsnNode(ALOAD, REGISTER_OFFSET + idx));
	}

	public void _load_reg_value(int idx, Class clazz) {
		_load_reg_value(idx);
		_checkCast(clazz);
	}

	public void _load_reg(int idx, SlotState slots, Class castTo) {
		Check.notNull(slots);
		Check.nonNegative(idx);

		if (slots.isCaptured(idx)) {
			_get_downvalue(idx);
			_get_upvalue_value();
		}
		else {
			_load_reg_value(idx);
		}

		Class clazz = Object.class;

		if (castTo != null) {
			if (!castTo.isAssignableFrom(clazz)) {
				_checkCast(castTo);
			}
		}
	}

	public void _load_reg(int idx, SlotState slots) {
		_load_reg(idx, slots, null);
	}

	public void _load_regs(int firstIdx, SlotState slots, int num) {
		for (int i = 0; i < num; i++) {
			_load_reg(firstIdx + i, slots);
		}
	}

	public void _get_downvalue(int idx) {
		code.add(new VarInsnNode(ALOAD, REGISTER_OFFSET + idx));
		_checkCast(Upvalue.class);
	}

	public void _load_reg_or_const(int rk, SlotState slots, Class castTo) {
		Check.notNull(slots);

		if (rk < 0) {
			// it's a constant
			_load_k(-rk - 1, castTo);
		}
		else {
			_load_reg(rk, slots, castTo);
		}
	}

	public void _load_reg_or_const(int rk, SlotState slots) {
		_load_reg_or_const(rk, slots, null);
	}

	private void _store_reg_value(int r) {
		code.add(new VarInsnNode(ASTORE, REGISTER_OFFSET + r));
	}

	public void _store(int r, SlotState slots) {
		Check.notNull(slots);

		if (slots.isCaptured(r)) {
			_get_downvalue(r);
			_swap();
			_set_upvalue_value();
		}
		else {
			_store_reg_value(r);
		}
	}

	@Deprecated
	public static String _className(String cn) {
		return cn.replace('.', '/');
	}

	@Deprecated
	public static String _classDesc(String cn) {
		return "L" + _className(cn) + ";";
	}

	public void _invokeStatic(Class clazz, String methodName, Type methodSignature) {
		code.add(new MethodInsnNode(
				INVOKESTATIC,
				Type.getInternalName(clazz),
				methodName,
				methodSignature.getDescriptor(),
				false
		));
	}

	public void _invokeVirtual(Class clazz, String methodName, Type methodSignature) {
		code.add(new MethodInsnNode(
				INVOKEVIRTUAL,
				Type.getInternalName(clazz),
				methodName,
				methodSignature.getDescriptor(),
				false
		));
	}

	public void _invokeInterface(Class clazz, String methodName, Type methodSignature) {
		code.add(new MethodInsnNode(
				INVOKEINTERFACE,
				Type.getInternalName(clazz),
				methodName,
				methodSignature.getDescriptor(),
				true
		));
	}

	public void _invokeSpecial(String className, String methodName, Type methodSignature) {
		code.add(new MethodInsnNode(
				INVOKESPECIAL,
				_className(className),
				methodName,
				methodSignature.getDescriptor(),
				false
		));
	}

	public void _dispatch_binop(String name, Class clazz) {
		Type t = Type.getType(clazz);
		_invokeStatic(Dispatch.class, name, Type.getMethodType(t, t, t));
	}

	public void _dispatch_generic_mt_2(String name) {
		_invokeStatic(
				Dispatch.class,
				name,
				Type.getMethodType(
					Type.VOID_TYPE,
					Type.getType(LuaState.class),
					Type.getType(ObjectSink.class),
					Type.getType(Object.class),
					Type.getType(Object.class)
			)
		);
	}

	public void _dispatch_index() {
		_dispatch_generic_mt_2("index");
	}

	public void _checkCast(Class clazz) {
		code.add(new TypeInsnNode(CHECKCAST, Type.getInternalName(clazz)));
	}

	public void _loadState() {
		withLuaState(code)
				.push();
	}

	public void _loadObjectSink() {
		withObjectSink(code)
				.push();
	}

	public void _retrieve_1() {
		withObjectSink(code)
				.push()
				.call_get(1);
	}

	public void _save_pc(Object o) {
		LabelNode rl = _l(o);

		int idx = resumptionPoints.size();
		resumptionPoints.add(rl);

		code.add(ASMUtils.loadInt(idx));
		code.add(new VarInsnNode(ISTORE, LV_RESUME));
	}

	public void _resumptionPoint(Object label) {
		_label_here(label);
	}

	private LabelNode l_insns_begin;
	private LabelNode l_body_begin;
	private LabelNode l_error_state;

	private LabelNode l_handler_begin;

	public void begin() {
		l_insns_begin = new LabelNode();
		methodNode.instructions.add(l_insns_begin);

		l_body_begin = new LabelNode();
		l_error_state = new LabelNode();

		l_handler_begin = new LabelNode();

		resumptionPoints.add(l_body_begin);

		code.add(l_body_begin);
		_frame_same(code);
	}

	public void end() {
		if (isResumable()) {
			_error_state();
		}
		_dispatch_table();
		if (isResumable()) {
			_resumption_handler();
		}

		// local variable declaration

		LabelNode l_insns_end = new LabelNode();

		List<LocalVariableNode> locals = methodNode.localVariables;
		locals.add(new LocalVariableNode("this", parent.thisClassType().getDescriptor(), null, l_insns_begin, l_insns_end, 0));
		locals.add(new LocalVariableNode("state", Type.getDescriptor(LuaState.class), null, l_insns_begin, l_insns_end, LV_STATE));
		locals.add(new LocalVariableNode("sink", Type.getDescriptor(ObjectSink.class), null, l_insns_begin, l_insns_end, LV_OBJECTSINK));
		locals.add(new LocalVariableNode("rp", Type.INT_TYPE.getDescriptor(), null, l_insns_begin, l_insns_end, LV_RESUME));

		for (int i = 0; i < numOfRegisters(); i++) {
			locals.add(new LocalVariableNode("r_" + i, Type.getDescriptor(Object.class), null, l_insns_begin, l_insns_end, REGISTER_OFFSET + i));
		}

//		if (isResumable()) {
//			locals.add(new LocalVariableNode("ct", Type.getDescriptor(ControlThrowable.class), null, l_handler_begin, l_handler_end, REGISTER_OFFSET + numOfRegisters()));
//		}

		// TODO: check these
//		methodNode.maxLocals = numOfRegisters() + 4;
//		methodNode.maxStack = numOfRegisters() + 5;

		methodNode.maxLocals = locals.size();
		methodNode.maxStack = 4 + numOfRegisters() + 5;

		methodNode.instructions.add(resumeSwitch);
		methodNode.instructions.add(code);
		methodNode.instructions.add(errorState);
		methodNode.instructions.add(resumeHandler);

		methodNode.instructions.add(l_insns_end);

		emitResumeNode();

		MethodNode save = saveStateNode();
		if (save != null) {
			parent.node().methods.add(save);
		}
	}

	private void emitResumeNode() {
//		if (isResumable()) {
//
//		}
//		else
		{
			InsnList il = resumeMethodNode.instructions;
			List<LocalVariableNode> locals = resumeMethodNode.localVariables;

			LabelNode begin = new LabelNode();
			LabelNode end = new LabelNode();

			il.add(begin);
			il.add(new TypeInsnNode(NEW, Type.getInternalName(UnsupportedOperationException.class)));
			il.add(new InsnNode(DUP));
			il.add(ASMUtils.ctor(UnsupportedOperationException.class));
			il.add(new InsnNode(ATHROW));
			il.add(end);

			locals.add(new LocalVariableNode("this", parent.thisClassType().getDescriptor(), null, begin, end, 0));
			locals.add(new LocalVariableNode("state", Type.getDescriptor(LuaState.class), null, begin, end, 1));
			locals.add(new LocalVariableNode("sink", Type.getDescriptor(ObjectSink.class), null, begin, end, 2));
			locals.add(new LocalVariableNode("suspendedState", Type.getDescriptor(Object.class), null, begin, end, 3));

			resumeMethodNode.maxStack = 2;
			resumeMethodNode.maxLocals = 4;
		}
	}

	protected void _error_state() {
		errorState.add(l_error_state);
		errorState.add(new TypeInsnNode(NEW, Type.getInternalName(IllegalStateException.class)));
		errorState.add(new InsnNode(DUP));
		errorState.add(ASMUtils.ctor(IllegalStateException.class));
		errorState.add(new InsnNode(ATHROW));
	}

	protected boolean isResumable() {
		return resumptionPoints.size() > 1;
	}

	protected void _dispatch_table() {
		if (isResumable()) {
			LabelNode[] labels = resumptionPoints.toArray(new LabelNode[0]);

			resumeSwitch.add(new VarInsnNode(ILOAD, LV_RESUME));
			resumeSwitch.add(new TableSwitchInsnNode(0, resumptionPoints.size() - 1, l_error_state, labels));
		}
	}

	protected int numOfRegisters() {
		return context.prototype().getMaximumStackSize();
	}

	private Type saveStateType() {
		Type[] args = new Type[1 + numOfRegisters()];
		args[0] = Type.INT_TYPE;
		for (int i = 1; i < args.length; i++) {
			args[i] = Type.getType(Object.class);
		}
		return Type.getMethodType(Type.getType(ResumeInfo.class), args);
	}

	private String saveStateName() {
		return "resumeInfo";
	}

	private MethodNode saveStateNode() {
		if (isResumable()) {
			MethodNode saveNode = new MethodNode(
					ACC_PRIVATE,
					saveStateName(),
					saveStateType().getDescriptor(),
					null,
					null);

			InsnList il = saveNode.instructions;
			LabelNode begin = new LabelNode();
			LabelNode end = new LabelNode();

			il.add(begin);

			il.add(new TypeInsnNode(NEW, Type.getInternalName(ResumeInfo.class)));
			il.add(new InsnNode(DUP));

			il.add(new VarInsnNode(ALOAD, 0));

			il.add(new TypeInsnNode(NEW, Type.getInternalName(ResumeInfo.SavedState.class)));
			il.add(new InsnNode(DUP));

			// resumption point
			il.add(new VarInsnNode(ILOAD, 1));

			// registers
			int numRegs = numOfRegisters();
			il.add(ASMUtils.loadInt(numRegs));
			il.add(new TypeInsnNode(ANEWARRAY, Type.getInternalName(Object.class)));
			for (int i = 0; i < numRegs; i++) {
				il.add(new InsnNode(DUP));
				il.add(ASMUtils.loadInt(i));
				il.add(new VarInsnNode(ALOAD, 2 + i));
				il.add(new InsnNode(AASTORE));
			}

			// TODO: varargs

			il.add(ASMUtils.ctor(
					Type.getType(ResumeInfo.SavedState.class),
					Type.INT_TYPE,
					ASMUtils.arrayTypeFor(Object.class)));

			il.add(ASMUtils.ctor(
					Type.getType(ResumeInfo.class),
					Type.getType(Resumable.class),
					Type.getType(Object.class)));

			il.add(new InsnNode(ARETURN));

			il.add(end);

			List<LocalVariableNode> locals = saveNode.localVariables;

			locals.add(new LocalVariableNode("this", parent.thisClassType().getDescriptor(), null, begin, end, 0));
			locals.add(new LocalVariableNode("rp", Type.INT_TYPE.getDescriptor(), null, begin, end, 1));
			for (int i = 0; i < numOfRegisters(); i++) {
				locals.add(new LocalVariableNode("r_" + i, Type.getDescriptor(Object.class), null, begin, end, 2 + i));
			}

			saveNode.maxLocals = 2 + numOfRegisters();
			saveNode.maxStack = 7 + 3;  // 7 to get register array at top, +3 to add element to it

			return saveNode;
		}
		else {
			return null;
		}
	}

	protected void _resumption_handler() {
		resumeHandler.add(l_handler_begin);
		resumeHandler.add(new FrameNode(F_SAME1, 0, null, 1, new Object[] { Type.getInternalName(ControlThrowable.class) }));

		resumeHandler.add(new InsnNode(DUP));

		resumeHandler.add(new VarInsnNode(ALOAD, 0));
		resumeHandler.add(new VarInsnNode(ILOAD, LV_RESUME));
		for (int i = 0; i < numOfRegisters(); i++) {
			resumeHandler.add(new VarInsnNode(ALOAD, REGISTER_OFFSET + i));
		}

		resumeHandler.add(new MethodInsnNode(
				INVOKESPECIAL,
				_className(thisClassName()),
				saveStateName(),
				saveStateType().getDescriptor(),
				false));

		resumeHandler.add(new MethodInsnNode(
				INVOKEVIRTUAL,
				Type.getInternalName(ControlThrowable.class),
				"push",
				Type.getMethodType(
						Type.VOID_TYPE,
						Type.getType(ResumeInfo.class)).getDescriptor(),
				false));

		// rethrow
		resumeHandler.add(new InsnNode(ATHROW));

		methodNode.tryCatchBlocks.add(new TryCatchBlockNode(l_insns_begin, l_handler_begin, l_handler_begin, Type.getInternalName(ControlThrowable.class)));
	}

	public void _get_upvalue_ref(int idx) {
		code.add(new VarInsnNode(ALOAD, 0));
		code.add(new FieldInsnNode(
				GETFIELD,
				_className(thisClassName()),
				parent.getUpvalueFieldName(idx),
				Type.getDescriptor(Upvalue.class)));
	}

	public void _get_upvalue_value() {
		_invokeVirtual(Upvalue.class, "get", Type.getMethodType(Type.getType(Object.class)));
	}

	public void _set_upvalue_value() {
		_invokeVirtual(Upvalue.class, "set", Type.getMethodType(Type.VOID_TYPE, Type.getType(Object.class)));
	}

	public void _return() {
		code.add(new InsnNode(RETURN));
	}

	public void _new(String className) {
		code.add(new TypeInsnNode(NEW, _className(className)));
	}

	public void _new(Class clazz) {
		_new(clazz.getName());
	}

	public void _closure_ctor(String className, int numUpvalues) {
		Type[] argTypes = new Type[numUpvalues];
		Arrays.fill(argTypes, Type.getType(Upvalue.class));
		code.add(ASMUtils.ctor(Type.getType(_classDesc(className)), argTypes));
	}

	public void _capture(int idx) {
		LuaState_prx state = withLuaState(code);
		state.push();
		_load_reg_value(idx);
		state.call_newUpvalue();
		_store_reg_value(idx);
	}

	public void _uncapture(int idx) {
		_load_reg_value(idx);
		_get_upvalue_value();
		_store_reg_value(idx);
	}

	private void _frame_same(InsnList il) {
		il.add(new FrameNode(F_SAME, 0, null, 0, null));
	}

	public void _label_here(Object identity) {
		LabelNode l = _l(identity);
		code.add(l);
		_frame_same(code);
	}

	public void _goto(Object l) {
		code.add(new JumpInsnNode(GOTO, _l(l)));
	}

	public void _next_insn(Target t) {
		_goto(t);
//
//		if (t.inSize() < 2) {
//			// can be inlined, TODO: check this again
//			_note("goto ignored: " + t.toString());
//		}
//		else {
//			_goto(t);
//		}
	}

	public void _new_table(int array, int hash) {
		withLuaState(code)
				.push()
				.do_newTable(array, hash);
	}

	public void _if_null(Object target) {
		code.add(new JumpInsnNode(IFNULL, _l(target)));
	}

	public void _if_nonnull(Object target) {
		code.add(new JumpInsnNode(IFNONNULL, _l(target)));
	}

	public void _tailcall(int numArgs) {
		withObjectSink(code)
				.call_tailCall(numArgs);
	}

	public void _setret(int num) {
		withObjectSink(code)
				.call_setTo(num);
	}

	public void _line_here(int line) {
		LabelNode l = _l(new Object());
		code.add(l);
		code.add(new LineNumberNode(line, l));
	}

	private LuaState_prx withLuaState(InsnList il) {
		return new LuaState_prx(LV_STATE, il);
	}

	private ObjectSink_prx withObjectSink(InsnList il) {
		return new ObjectSink_prx(LV_OBJECTSINK, il);
	}

	private static class LuaState_prx {

		private final int selfIndex;
		private final InsnList il;

		public LuaState_prx(int selfIndex, InsnList il) {
			this.selfIndex = selfIndex;
			this.il = il;
		}

		private Type selfTpe() {
			return Type.getType(LuaState.class);
		}

		public LuaState_prx push() {
			il.add(new VarInsnNode(ALOAD, selfIndex));
			return this;
		}

		public LuaState_prx do_newTable(int array, int hash) {
			push();

			il.add(ASMUtils.loadInt(array));
			il.add(ASMUtils.loadInt(hash));

			il.add(new MethodInsnNode(
					INVOKEVIRTUAL,
					selfTpe().getInternalName(),
					"newTable",
					Type.getMethodType(
							Type.getType(Table.class),
							Type.INT_TYPE,
							Type.INT_TYPE).getDescriptor(),
					false));

			return this;
		}

		public LuaState_prx call_newUpvalue() {
			il.add(new MethodInsnNode(
					INVOKEVIRTUAL,
					selfTpe().getInternalName(),
					"newUpvalue",
					Type.getMethodType(
							Type.getType(Upvalue.class),
							Type.getType(Object.class)).getDescriptor(),
					false));
			return this;
		}

	}

	private static class ObjectSink_prx {

		private final int selfIndex;
		private final InsnList il;

		public ObjectSink_prx(int selfIndex, InsnList il) {
			this.selfIndex = selfIndex;
			this.il = il;
		}

		private Type selfTpe() {
			return Type.getType(ObjectSink.class);
		}

		public ObjectSink_prx push() {
			il.add(new VarInsnNode(ALOAD, selfIndex));
			return this;
		}

		public ObjectSink_prx call_get(int index) {
			Check.nonNegative(index);
			if (index <= 4) {
				String methodName = "_" + index;
				il.add(new MethodInsnNode(
						INVOKEVIRTUAL,
						selfTpe().getInternalName(),
						methodName,
						Type.getMethodType(
								Type.getType(Object.class)).getDescriptor(),
						true));
			}
			else {
				il.add(ASMUtils.loadInt(index));
				il.add(new MethodInsnNode(
						INVOKEVIRTUAL,
						selfTpe().getInternalName(),
						"get",
						Type.getMethodType(
								Type.getType(Object.class),
								Type.INT_TYPE).getDescriptor(),
						true));
			}
			return this;
		}

		public ObjectSink_prx call_setTo(int numValues) {
			Check.nonNegative(numValues);
			if (numValues == 0) {
				il.add(new MethodInsnNode(
						INVOKEVIRTUAL,
						selfTpe().getInternalName(),
						"reset",
						Type.getMethodType(
								Type.VOID_TYPE).getDescriptor(),
						true));
			}
			else {
				// TODO: determine this by reading the ObjectSink interface?
				if (numValues <= 5) {
					Type[] argTypes = new Type[numValues];
					Arrays.fill(argTypes, Type.getType(Object.class));

					il.add(new MethodInsnNode(
							INVOKEVIRTUAL,
							selfTpe().getInternalName(),
							"setTo",
							Type.getMethodType(
									Type.VOID_TYPE,
									argTypes).getDescriptor(),
							true));
				}
				else {
					// TODO: iterate and push
					throw new UnsupportedOperationException("Return " + numValues + " values");
				}
			}
			return this;
		}

		public ObjectSink_prx call_tailCall(int numCallArgs) {
			Check.nonNegative(numCallArgs);
			// TODO: determine this by reading the ObjectSink interface?
			if (numCallArgs <= 4) {
				Type[] callArgTypes = new Type[numCallArgs + 1];  // don't forget the call target
				Arrays.fill(callArgTypes, Type.getType(Object.class));

				il.add(new MethodInsnNode(
						INVOKEVIRTUAL,
						selfTpe().getInternalName(),
						"tailCall",
						Type.getMethodType(
								Type.VOID_TYPE,
								callArgTypes).getDescriptor(),
						true));
			}
			else {
				// TODO: iterate and push
				throw new UnsupportedOperationException("Tail call with " + numCallArgs + " arguments");
			}
			return this;
		}

	}

}