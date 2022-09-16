package io.github.eutro.wasm2j.passes.convert;

import io.github.eutro.jwasm.BlockType;
import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.WasmExts;
import io.github.eutro.wasm2j.ops.WasmOps.DerefType.ExtType;
import io.github.eutro.wasm2j.ops.WasmOps.DerefType.LoadType;
import io.github.eutro.wasm2j.ops.WasmOps.StoreType;
import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.util.InsnMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.eutro.jwasm.Opcodes.*;

public class WasmToWir implements IRPass<ModuleNode, Module> {
    public static final WasmToWir INSTANCE = new WasmToWir();

    @Override
    public Module run(ModuleNode node) {
        Module module = new Module();
        module.attachExt(CommonExts.CODE_TYPE, CommonExts.CodeType.WASM);
        HashMap<ExprNode, Function> funcMap = new HashMap<>();
        module.attachExt(WasmExts.MODULE, node);
        module.attachExt(WasmExts.FUNC_MAP, funcMap);

        FullConvertState state = new FullConvertState(node);
        if (node.globals != null) {
            for (GlobalNode global : node.globals) {
                state.mapExprNode(
                        module,
                        new TypeNode(new byte[0], new byte[]{global.type.type}),
                        new byte[0],
                        global.init
                );
            }
        }

        if (node.datas != null) {
            for (DataNode data : node.datas) {
                if (data.offset != null) {
                    state.mapExprNode(
                            module,
                            new TypeNode(new byte[0], new byte[]{I32}),
                            new byte[0],
                            data.offset
                    );
                }
            }
        }

        if (node.codes != null) {
            int i = 0;
            for (CodeNode code : node.codes) {
                assert node.funcs != null && node.funcs.funcs != null;
                TypeNode type = state.funcTypes[node.funcs.funcs.get(i).type];
                state.mapExprNode(
                        module,
                        type,
                        code.locals,
                        code.expr
                );
                i++;
            }

            if (node.elems != null) {
                for (ElementNode elem : node.elems) {
                    state.mapExprNode(
                            module,
                            new TypeNode(new byte[0], new byte[]{elem.type}),
                            new byte[0],
                            elem.offset
                    );
                }
            }
        }

        return module;
    }

    public static class ConvertState {
        // validator ops

        public static class CtrlFrame {
            public int opcode;
            public TypeNode type;
            public int height;
            public boolean unreachable;

            public BasicBlock firstBb;
            public BasicBlock bb;

            @Nullable
            public BasicBlock elseBb; // only present if opcode == IF
        }

        public int vals;
        public final List<ConvertState.CtrlFrame> ctrls = new ArrayList<>();

        public ConvertState.CtrlFrame ctrlsRef(int idx) {
            return ctrls.get(ctrls.size() - idx - 1);
        }

        public int pushV() {
            return ++vals;
        }

        public Var pushVar() {
            return refVar(pushV());
        }

        public int popV() {
            ConvertState.CtrlFrame frame = ctrlsRef(0);
            if (vals == frame.height
                    && /* should be true by validation anyway */ frame.unreachable) {
                return -1;
            }
            return vals--;
        }

        public Var popVar() {
            return refVar(popV());
        }

        public void popVs(int n) {
            for (int i = 0; i < n; i++) {
                popV();
            }
        }

        public ConvertState.CtrlFrame pushC(int opcode, TypeNode type, BasicBlock bb) {
            ConvertState.CtrlFrame frame = new ConvertState.CtrlFrame();
            frame.opcode = opcode;
            frame.type = type;
            frame.height = vals;
            frame.unreachable = false;
            frame.firstBb = frame.bb = bb;
            ctrls.add(frame);
            return frame;
        }

        public ConvertState.CtrlFrame popC() {
            ConvertState.CtrlFrame frame = ctrls.remove(ctrls.size() - 1);
            if (frame.unreachable) {
                vals = frame.height + frame.type.returns.length;
            } else {
                if (frame.height != vals - frame.type.returns.length) {
                    throw new RuntimeException("Frame height mismatch, likely a bug or an unverified module");
                }
            }
            return frame;
        }

        public int labelArity(ConvertState.CtrlFrame frame) {
            return (frame.opcode == LOOP ? frame.type.params : frame.type.returns).length;
        }

        public BasicBlock labelTarget(int depth) {
            ConvertState.CtrlFrame frame = ctrlsRef(depth);
            return frame.opcode == LOOP ? frame.firstBb : ctrlsRef(depth + 1).bb;
        }

        public void unreachable() {
            ConvertState.CtrlFrame frame = ctrlsRef(0);
            vals = frame.height;
            frame.unreachable = true;
            frame.bb = newBb();
        }

        // extensions

        public final byte[] localTypes;
        public final int argC, localC;
        public final Function func;
        public final TypeNode[] funcTypes;
        public final FuncNode[] referencableFuncs;
        public final int returns;
        public final int locals;
        public final List<Var> varVals = new ArrayList<>();

        public Var refVar(int n) {
            while (n >= varVals.size()) {
                varVals.add(func.newVar("stack" + (varVals.size() - locals)));
            }
            return varVals.get(n);
        }

        public BasicBlock newBb() {
            return func.newBb();
        }

        public TypeNode expand(BlockType blockType) {
            if (blockType.isValtype()) {
                return new TypeNode(
                        new byte[0],
                        blockType.get() == EMPTY_TYPE ? new byte[0] : new byte[]{(byte) blockType.get()}
                );
            } else {
                return funcTypes[blockType.get()];
            }
        }

        public void copyStack(
                BasicBlock bb,
                int arity,
                int from,
                int to
        ) {
            if (from == to) {
                return;
            }
            for (int i = 1; i <= arity; ++i) {
                bb.addEffect(CommonOps.IDENTITY.insn(refVar(from + i))
                        .assignTo(refVar(to + i)));
            }
        }

        public ConvertState(TypeNode[] funcTypes, FuncNode[] referencableFuncs, int argC, byte[] locals, int returns) {
            this.funcTypes = funcTypes;
            this.referencableFuncs = referencableFuncs;
            this.argC = argC;
            this.localTypes = locals;
            this.localC = locals.length;
            this.vals = this.locals = argC + localC;
            this.returns = returns;
            this.func = new Function();
        }
    }

    public interface Converter {
        void convert(ConvertState cs, AbstractInsnNode node, BasicBlock topB);
    }

    private static final InsnMap<Converter> CONVERTERS = new InsnMap<>();

    static {
        CONVERTERS.put(UNREACHABLE, (cs, node, topB) -> {
            topB.setControl(CommonOps.UNREACHABLE.insn().jumpsTo());
            cs.unreachable();
        });
        CONVERTERS.put(NOP, (cs, node, topB) -> {
        });
        CONVERTERS.put(new byte[]{
                BLOCK,
                LOOP
        }, (cs, node, topB) -> {
            TypeNode type = cs.expand(((BlockInsnNode) node).blockType);
            ConvertState.CtrlFrame inFrame = cs.ctrlsRef(0);
            BasicBlock bb;
            if (node.opcode == BLOCK) {
                bb = inFrame.bb;
            } else {
                bb = cs.newBb();
                inFrame.bb.setControl(Control.br(bb));
            }
            inFrame.bb = cs.newBb();
            cs.pushC(node.opcode, type, bb);
        });
        CONVERTERS.put(IF, (cs, node, topB) -> {
            TypeNode type = cs.expand(((BlockInsnNode) node).blockType);
            ConvertState.CtrlFrame inFrame = cs.ctrlsRef(0);
            Var cond = cs.popVar();
            ConvertState.CtrlFrame newFrame = cs.pushC(node.opcode, type, cs.newBb());
            newFrame.elseBb = cs.newBb();
            inFrame.bb.setControl(WasmOps.brIf(
                    cond,
                    newFrame.bb,
                    newFrame.elseBb
            ));
            inFrame.bb = cs.newBb();
        });
        CONVERTERS.put(ELSE, (cs, node, topB) -> {
            ConvertState.CtrlFrame frame = cs.ctrlsRef(0);
            cs.popVs(frame.type.returns.length);
            cs.vals += frame.type.params.length;
            frame.opcode = node.opcode;
            frame.unreachable = false;
            frame.bb.setControl(Control.br(cs.ctrlsRef(1).bb));
            frame.bb = Objects.requireNonNull(frame.elseBb);
            frame.elseBb = null;
        });
    }

    private static void doReturn(ConvertState cs, BasicBlock bb) {
        Var[] exprs = new Var[cs.returns];
        for (int i = 0; i < exprs.length; i++) {
            exprs[i] = cs.refVar(cs.vals + i + 1);
        }
        bb.setControl(CommonOps.RETURN.insn(exprs).jumpsTo());
    }

    static {
        CONVERTERS.put(END, (cs, node, topB) -> {
            ConvertState.CtrlFrame frame = cs.popC();
            if (cs.ctrls.isEmpty()) {
                cs.vals = frame.height;
                doReturn(cs, frame.bb);
            } else {
                frame.bb.setControl(Control.br(cs.ctrlsRef(0).bb));
                if (frame.elseBb != null) {
                    frame.elseBb.setControl(Control.br(cs.ctrlsRef(0).bb));
                }
            }
            assert frame.bb.getControl() != null;
        });

        CONVERTERS.put(BR, (cs, node, topB) -> {
            ConvertState.CtrlFrame topFrame = cs.ctrlsRef(0);
            int depth = ((BreakInsnNode) node).label;
            ConvertState.CtrlFrame targetFrame = cs.ctrlsRef(depth);
            int arity = cs.labelArity(targetFrame);
            cs.popVs(arity);
            cs.copyStack(topFrame.bb, arity, cs.vals, targetFrame.height);
            topFrame.bb.setControl(Control.br(cs.labelTarget(depth)));
            cs.unreachable();
        });
        CONVERTERS.put(BR_IF, (cs, node, topB) -> {
            BasicBlock thenBb = cs.newBb();
            BasicBlock elseBb = cs.newBb();
            ConvertState.CtrlFrame topFrame = cs.ctrlsRef(0);
            topFrame.bb.setControl(WasmOps.brIf(cs.popVar(), thenBb, elseBb));
            topFrame.bb = elseBb;

            int depth = ((BreakInsnNode) node).label;
            ConvertState.CtrlFrame targetFrame = cs.ctrlsRef(depth);
            int arity = cs.labelArity(targetFrame);
            cs.copyStack(thenBb, arity, cs.vals - arity, targetFrame.height);
            thenBb.setControl(Control.br(cs.labelTarget(depth)));
        });
        CONVERTERS.put(BR_TABLE, (cs, node, topB) -> {
            TableBreakInsnNode tblBr = (TableBreakInsnNode) node;
            BasicBlock[] bbs = new BasicBlock[tblBr.labels.length + 1];
            BasicBlock elseBb = cs.newBb();

            ConvertState.CtrlFrame defaultFrame = cs.ctrlsRef(tblBr.defaultLabel);
            int arity = cs.labelArity(defaultFrame);
            Var condVal = cs.popVar();
            cs.popVs(arity);

            cs.copyStack(elseBb, arity, cs.vals, defaultFrame.height);
            elseBb.setControl(Control.br(cs.labelTarget(tblBr.defaultLabel)));
            for (int i = 0; i < bbs.length - 1; i++) {
                BasicBlock bb = cs.newBb();
                ConvertState.CtrlFrame frame = cs.ctrlsRef(tblBr.labels[i]);
                cs.copyStack(bb, arity, cs.vals, frame.height);
                bb.setControl(Control.br(cs.labelTarget(tblBr.labels[i])));
                bbs[i] = bb;
            }
            bbs[bbs.length - 1] = elseBb;

            ConvertState.CtrlFrame topFrame = cs.ctrlsRef(0);
            topFrame.bb.setControl(WasmOps.BR_TABLE
                    .insn(condVal)
                    .jumpsTo(bbs));
            cs.unreachable();
        });
        CONVERTERS.put(RETURN, (cs, node, topB) -> {
            cs.popVs(cs.returns);
            doReturn(cs, topB);
            cs.unreachable();
        });
        CONVERTERS.put(new byte[]{
                CALL,
                CALL_INDIRECT,
        }, (cs, node, topB) -> {
            TypeNode type;
            CallInsnNode callInsn = null;
            Var callee = null;
            if (node.opcode == CALL) {
                callInsn = (CallInsnNode) node;
                FuncNode func = cs.referencableFuncs[callInsn.function];
                type = cs.funcTypes[func.type];
            } else {
                CallIndirectInsnNode ciNode = (CallIndirectInsnNode) node;
                callee = cs.func.newVar("callee");
                topB.addEffect(WasmOps.TABLE_REF.create(ciNode.table).insn(cs.popVar()).assignTo(callee));
                type = cs.funcTypes[ciNode.type];
            }
            Var[] args = new Var[type.params.length];
            cs.popVs(args.length);
            for (int i = 0; i < args.length; i++) {
                args[i] = cs.refVar(cs.vals + i + 1);
            }
            Insn call;
            if (node.opcode == CALL) {
                call = WasmOps.CALL.create(new WasmOps.CallType(callInsn.function, type)).insn(args);
            } else {
                Var[] ciArgs = new Var[args.length + 1];
                ciArgs[0] = callee;
                System.arraycopy(args, 0, ciArgs, 1, args.length);
                call = WasmOps.CALL_INDIRECT.create(type).insn(ciArgs);
            }
            if (type.returns.length == 1) {
                topB.addEffect(call.assignTo(cs.pushVar()));
            } else {
                Var[] assignees = new Var[type.returns.length];
                for (int i = 0; i < assignees.length; i++) {
                    assignees[i] = cs.pushVar();
                }
                topB.addEffect(call.assignTo(assignees));
            }
        });
    }

    static {
        CONVERTERS.put(REF_NULL, (cs, node, topB) -> topB.addEffect(WasmOps.ZEROINIT
                .create(((NullInsnNode) node).type).insn().assignTo(cs.pushVar())));
        CONVERTERS.put(REF_IS_NULL, (cs, node, topB) -> topB.addEffect(WasmOps.IS_NULL
                .create().insn(cs.popVar()).assignTo(cs.pushVar())));
        CONVERTERS.put(REF_FUNC, (cs, node, topB) -> topB.addEffect(WasmOps.FUNC_REF
                .create(((FuncRefInsnNode) node).function).insn().assignTo(cs.pushVar())));
    }

    static {
        CONVERTERS.put(DROP, (cs, node, topB) -> cs.popV());
        CONVERTERS.put(new byte[]{
                SELECT,
                SELECTT
        }, (cs, node, topB) -> topB.addEffect(WasmOps.SELECT
                .create().insn(
                        cs.popVar(), // cond
                        cs.popVar(), // ift
                        cs.popVar()  // iff
                ).assignTo(cs.pushVar())));
    }

    static {
        CONVERTERS.put(LOCAL_GET, (cs, node, topB) -> topB.addEffect(CommonOps.IDENTITY
                .insn(cs.refVar(((VariableInsnNode) node).variable)).assignTo(cs.pushVar())));
        CONVERTERS.put(new byte[]{
                LOCAL_SET,
                LOCAL_TEE
        }, (cs, node, topB) -> topB.addEffect(CommonOps.IDENTITY
                .insn(cs.refVar(node.opcode == LOCAL_SET ? cs.popV() : cs.vals))
                .assignTo(cs.refVar(((VariableInsnNode) node).variable))));
        CONVERTERS.put(GLOBAL_GET, (cs, node, topB) -> topB.addEffect(WasmOps.GLOBAL_REF
                .create(((VariableInsnNode) node).variable).insn().assignTo(cs.pushVar())));
        CONVERTERS.put(GLOBAL_SET, (cs, node, topB) -> topB.addEffect(WasmOps.GLOBAL_SET
                .create(((VariableInsnNode) node).variable)
                .insn(cs.popVar())
                .assignTo()));
    }

    static {
        CONVERTERS.put(TABLE_GET, (cs, node, topB) -> topB.addEffect(WasmOps.TABLE_REF
                .create(((TableInsnNode) node).table)
                .insn(cs.popVar())
                .assignTo(cs.popVar())));
        CONVERTERS.put(TABLE_SET, (cs, node, topB) -> topB.addEffect(WasmOps.TABLE_STORE
                .create(((TableInsnNode) node).table)
                .insn(
                        cs.popVar(), // value
                        cs.popVar() // index
                )
                .assignTo()));
        CONVERTERS.putInt(new int[]{
                TABLE_INIT,
                ELEM_DROP,
                TABLE_COPY,
                TABLE_GROW,
                TABLE_SIZE,
                TABLE_FILL // TODO
        }, (cs, node, topB) -> {
            throw new UnsupportedOperationException();
        });
    }

    private static Converter makeLoadInsn(byte outType, LoadType load, ExtType ext) {
        return (cs, node, topB) -> topB.addEffect(WasmOps.MEM_LOAD
                .create(new WasmOps.WithMemArg<>(
                        new WasmOps.DerefType(outType, load, ext),
                        ((MemInsnNode) node).offset))
                .insn(cs.popVar())
                .assignTo(cs.pushVar()));
    }

    static {
        CONVERTERS.put(I32_LOAD, makeLoadInsn(I32, LoadType.I32, ExtType.NOEXT));
        CONVERTERS.put(I64_LOAD, makeLoadInsn(I64, LoadType.I64, ExtType.NOEXT));
        CONVERTERS.put(F32_LOAD, makeLoadInsn(F32, LoadType.F32, ExtType.NOEXT));
        CONVERTERS.put(F64_LOAD, makeLoadInsn(F64, LoadType.F64, ExtType.NOEXT));

        CONVERTERS.put(I32_LOAD8_S, makeLoadInsn(I32, LoadType.I8, ExtType.S8_32));
        CONVERTERS.put(I32_LOAD8_U, makeLoadInsn(I32, LoadType.I8, ExtType.U8_32));
        CONVERTERS.put(I32_LOAD16_S, makeLoadInsn(I32, LoadType.I16, ExtType.S16_32));
        CONVERTERS.put(I32_LOAD16_U, makeLoadInsn(I32, LoadType.I16, ExtType.U16_32));

        CONVERTERS.put(I64_LOAD8_S, makeLoadInsn(I64, LoadType.I8, ExtType.S8_64));
        CONVERTERS.put(I64_LOAD8_U, makeLoadInsn(I64, LoadType.I8, ExtType.U8_64));
        CONVERTERS.put(I64_LOAD16_S, makeLoadInsn(I64, LoadType.I16, ExtType.S16_64));
        CONVERTERS.put(I64_LOAD16_U, makeLoadInsn(I64, LoadType.I16, ExtType.U16_64));
        CONVERTERS.put(I64_LOAD32_S, makeLoadInsn(I64, LoadType.I32, ExtType.S32_64));
        CONVERTERS.put(I64_LOAD32_U, makeLoadInsn(I64, LoadType.I32, ExtType.U32_64));
    }

    private static Converter makeStoreInsn(StoreType storeType) {
        return (cs, node, topB) -> topB.addEffect(WasmOps.MEM_STORE
                .create(new WasmOps.WithMemArg<>(storeType, ((MemInsnNode) node).offset))
                .insn(
                        cs.popVar(), // value
                        cs.popVar() // addr
                )
                .assignTo()
        );
    }

    static {
        CONVERTERS.put(I32_STORE, makeStoreInsn(StoreType.I32));
        CONVERTERS.put(I64_STORE, makeStoreInsn(StoreType.I64));
        CONVERTERS.put(F32_STORE, makeStoreInsn(StoreType.F32));
        CONVERTERS.put(F64_STORE, makeStoreInsn(StoreType.F64));

        CONVERTERS.put(I32_STORE8, makeStoreInsn(StoreType.I32_8));
        CONVERTERS.put(I32_STORE16, makeStoreInsn(StoreType.I32_16));

        CONVERTERS.put(I64_STORE8, makeStoreInsn(StoreType.I64_8));
        CONVERTERS.put(I64_STORE16, makeStoreInsn(StoreType.I64_16));
        CONVERTERS.put(I64_STORE32, makeStoreInsn(StoreType.I64_32));
    }

    static {
        CONVERTERS.put(MEMORY_SIZE, (cs, node, topB) -> topB.addEffect(WasmOps.MEM_SIZE
                .create().insn().assignTo(cs.pushVar())));
        CONVERTERS.put(MEMORY_GROW, (cs, node, topB) -> topB.addEffect(WasmOps.MEM_GROW
                .create().insn(cs.popVar()).assignTo(cs.pushVar())));
        CONVERTERS.putInt(new int[]{
                MEMORY_INIT,
                DATA_DROP,
                MEMORY_COPY,
                MEMORY_FILL,
        }, (cs, node, topB) -> {
            throw new UnsupportedOperationException();
        });
    }

    static {
        CONVERTERS.put(new byte[]{
                I32_CONST,
                I64_CONST,
                F32_CONST,
                F64_CONST,
        }, (cs, node, topB) -> topB.addEffect(CommonOps.CONST
                .create(((ConstInsnNode) node).value)
                .insn().assignTo(cs.pushVar())));
    }

    private static Converter makeOpInsn(int arity, byte returnType) {
        return (cs, node, topB) -> {
            cs.popVs(arity);
            Var[] args = new Var[arity];
            for (int i = 0; i < args.length; i++) {
                args[i] = cs.refVar(cs.vals + i + 1);
            }
            topB.addEffect(WasmOps.OPERATOR
                    .create(new WasmOps.OperatorType(
                            node.opcode,
                            node instanceof PrefixInsnNode ? ((PrefixInsnNode) node).intOpcode : 0,
                            returnType
                    )).insn(args)
                    .assignTo(cs.pushVar()));
        };
    }

    static {
        // comparisons
        {
            CONVERTERS.put(I32_EQZ, makeOpInsn(1, I32));
            CONVERTERS.put(new byte[]{
                    I32_EQ,
                    I32_NE,
                    I32_LT_S,
                    I32_LT_U,
                    I32_GT_S,
                    I32_GT_U,
                    I32_LE_S,
                    I32_LE_U,
                    I32_GE_S,
                    I32_GE_U,
            }, makeOpInsn(2, I32));

            CONVERTERS.put(I64_EQZ, makeOpInsn(1, I32));
            CONVERTERS.put(new byte[]{
                    I64_EQ,
                    I64_NE,
                    I64_LT_S,
                    I64_LT_U,
                    I64_GT_S,
                    I64_GT_U,
                    I64_LE_S,
                    I64_LE_U,
                    I64_GE_S,
                    I64_GE_U,
            }, makeOpInsn(2, I32));

            CONVERTERS.put(new byte[]{
                    F32_EQ,
                    F32_NE,
                    F32_LT,
                    F32_GT,
                    F32_LE,
                    F32_GE,
            }, makeOpInsn(2, I32));

            CONVERTERS.put(new byte[]{
                    F64_EQ,
                    F64_NE,
                    F64_LT,
                    F64_GT,
                    F64_LE,
                    F64_GE,
            }, makeOpInsn(2, I32));
        }

        // maths
        {
            CONVERTERS.put(new byte[]{
                    I32_CLZ,
                    I32_CTZ,
                    I32_POPCNT,
            }, makeOpInsn(1, I32));
            CONVERTERS.put(new byte[]{
                    I32_ADD,
                    I32_SUB,
                    I32_MUL,
                    I32_DIV_S,
                    I32_DIV_U,
                    I32_REM_S,
                    I32_REM_U,
                    I32_AND,
                    I32_OR,
                    I32_XOR,
                    I32_SHL,
                    I32_SHR_S,
                    I32_SHR_U,
                    I32_ROTL,
                    I32_ROTR,
            }, makeOpInsn(2, I32));

            CONVERTERS.put(new byte[]{
                    I64_CLZ,
                    I64_CTZ,
                    I64_POPCNT,
            }, makeOpInsn(1, I64));
            CONVERTERS.put(new byte[]{
                    I64_ADD,
                    I64_SUB,
                    I64_MUL,
                    I64_DIV_S,
                    I64_DIV_U,
                    I64_REM_S,
                    I64_REM_U,
                    I64_AND,
                    I64_OR,
                    I64_XOR,
                    I64_SHL,
                    I64_SHR_S,
                    I64_SHR_U,
                    I64_ROTL,
                    I64_ROTR,
            }, makeOpInsn(2, I64));

            CONVERTERS.put(new byte[]{
                    F32_ABS,
                    F32_NEG,
                    F32_CEIL,
                    F32_FLOOR,
                    F32_TRUNC,
                    F32_NEAREST,
                    F32_SQRT,
            }, makeOpInsn(1, F32));
            CONVERTERS.put(new byte[]{
                    F32_ADD,
                    F32_SUB,
                    F32_MUL,
                    F32_DIV,
                    F32_MIN,
                    F32_MAX,
                    F32_COPYSIGN,
            }, makeOpInsn(2, F32));

            CONVERTERS.put(new byte[]{
                    F64_ABS,
                    F64_NEG,
                    F64_CEIL,
                    F64_FLOOR,
                    F64_TRUNC,
                    F64_NEAREST,
                    F64_SQRT,
            }, makeOpInsn(1, F64));
            CONVERTERS.put(new byte[]{
                    F64_ADD,
                    F64_SUB,
                    F64_MUL,
                    F64_DIV,
                    F64_MIN,
                    F64_MAX,
                    F64_COPYSIGN,
            }, makeOpInsn(2, F64));
        }

        // conversions
        {
            CONVERTERS.put(new byte[]{
                    I32_WRAP_I64,
                    I32_TRUNC_F32_S,
                    I32_TRUNC_F32_U,
                    I32_TRUNC_F64_S,
                    I32_TRUNC_F64_U,
            }, makeOpInsn(1, I32));
            CONVERTERS.put(new byte[]{
                    I64_EXTEND_I32_S,
                    I64_EXTEND_I32_U,
                    I64_TRUNC_F32_S,
                    I64_TRUNC_F32_U,
                    I64_TRUNC_F64_S,
                    I64_TRUNC_F64_U,
            }, makeOpInsn(1, I64));
            CONVERTERS.put(new byte[]{
                    F32_CONVERT_I32_S,
                    F32_CONVERT_I32_U,
                    F32_CONVERT_I64_S,
                    F32_CONVERT_I64_U,
                    F32_DEMOTE_F64,
            }, makeOpInsn(1, F32));
            CONVERTERS.put(new byte[]{
                    F64_CONVERT_I32_S,
                    F64_CONVERT_I32_U,
                    F64_CONVERT_I64_S,
                    F64_CONVERT_I64_U,
                    F64_PROMOTE_F32,
            }, makeOpInsn(1, F64));
            CONVERTERS.put(I32_REINTERPRET_F32, makeOpInsn(1, I32));
            CONVERTERS.put(I64_REINTERPRET_F64, makeOpInsn(1, I64));
            CONVERTERS.put(F32_REINTERPRET_I32, makeOpInsn(1, F32));
            CONVERTERS.put(F64_REINTERPRET_I64, makeOpInsn(1, F64));
        }

        // extension
        {
            CONVERTERS.put(new byte[]{
                    I32_EXTEND8_S,
                    I32_EXTEND16_S,
            }, makeOpInsn(1, I32));
            CONVERTERS.put(new byte[]{
                    I64_EXTEND8_S,
                    I64_EXTEND16_S,
                    I64_EXTEND32_S,
            }, makeOpInsn(1, I64));
        }

        // saturating truncation
        {
            CONVERTERS.putInt(new int[]{
                    I32_TRUNC_SAT_F32_S,
                    I32_TRUNC_SAT_F32_U,
                    I32_TRUNC_SAT_F64_S,
                    I32_TRUNC_SAT_F64_U,
            }, makeOpInsn(1, I32));

            CONVERTERS.putInt(new int[]{
                    I64_TRUNC_SAT_F32_S,
                    I64_TRUNC_SAT_F32_U,
                    I64_TRUNC_SAT_F64_S,
                    I64_TRUNC_SAT_F64_U,
            }, makeOpInsn(1, I64));
        }
    }

    private static class FullConvertState {
        private final TypeNode[] funcTypes;
        private final FuncNode[] referencableFuncs;

        public FullConvertState(ModuleNode node) {
            TypeNode[] funcTypes = new TypeNode[0];
            if (node.types != null && node.types.types != null) {
                funcTypes = node.types.types.toArray(funcTypes);
            }
            this.funcTypes = funcTypes;
            List<FuncNode> referencableFuncs = new ArrayList<>();
            if (node.imports != null) {
                for (AbstractImportNode importNode : node.imports) {
                    if (importNode instanceof FuncImportNode) {
                        referencableFuncs.add(new FuncNode(((FuncImportNode) importNode).type));
                    }
                }
            }
            if (node.funcs != null && node.funcs.funcs != null) {
                referencableFuncs.addAll(node.funcs.funcs);
            }
            this.referencableFuncs = referencableFuncs.toArray(new FuncNode[0]);
        }

        public void mapExprNode(
                Module module,
                TypeNode funcType,
                byte[] locals,
                ExprNode expr
        ) {
            ConvertState cs = new ConvertState(
                    funcTypes,
                    referencableFuncs,
                    funcType.params.length,
                    locals,
                    funcType.returns.length
            );
            Function func = convertFunc(cs, expr);
            module.funcions.add(func);
            module.getExtOrThrow(WasmExts.FUNC_MAP).put(expr, func);
        }

        public Function convertFunc(
                ConvertState cs,
                ExprNode expr
        ) {
            cs.pushC(END, new TypeNode(new byte[0], new byte[cs.returns]), cs.newBb());
            BasicBlock firstBb = cs.ctrlsRef(0).bb;
            for (int i = 0; i < cs.argC; i++) {
                Var argVar = cs.func.newVar("arg" + i);
                cs.varVals.add(argVar);
                firstBb.addEffect(CommonOps.ARG.create(i).insn().assignTo(argVar));
            }
            for (int i = 0; i < cs.localC; ++i) {
                Var localVar = cs.func.newVar("local" + i);
                cs.varVals.add(localVar);
                firstBb.addEffect(WasmOps.ZEROINIT.create(cs.localTypes[i]).insn().assignTo(localVar));
            }
            for (AbstractInsnNode insn : expr) {
                Converter converter = CONVERTERS.get(insn);
                if (converter == null) {
                    throw new UnsupportedOperationException("Instruction not supported");
                }
                converter.convert(cs, insn, cs.ctrlsRef(0).bb);
            }
            return cs.func;
        }
    }
}
