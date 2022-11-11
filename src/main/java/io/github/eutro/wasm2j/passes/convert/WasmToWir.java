package io.github.eutro.wasm2j.passes.convert;

import io.github.eutro.jwasm.BlockType;
import io.github.eutro.jwasm.attrs.InsnAttributes;
import io.github.eutro.jwasm.attrs.Opcode;
import io.github.eutro.jwasm.attrs.StackType;
import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.WasmExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ops.WasmOps.DerefType.ExtType;
import io.github.eutro.wasm2j.ops.WasmOps.DerefType.LoadType;
import io.github.eutro.wasm2j.ops.WasmOps.StoreType;
import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.*;
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

        int errIndex = 0;
        String errIn = "???";

        try {
            if (node.globals != null) {
                errIn = "global";
                for (GlobalNode global : node.globals) {
                    errIndex++;
                    state.mapExprNode(
                            module,
                            new TypeNode(new byte[0], new byte[]{global.type.type}),
                            new byte[0],
                            global.init
                    );
                }
            }

            if (node.datas != null) {
                errIndex = 0;
                errIn = "data";
                for (DataNode data : node.datas) {
                    errIndex++;
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
                errIndex = 0;
                errIn = "code";
                int i = 0;
                for (CodeNode code : node.codes) {
                    errIndex++;
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
            }

            if (node.elems != null) {
                errIndex = 0;
                errIn = "elem";
                for (ElementNode elem : node.elems) {
                    errIndex++;
                    if (elem.offset != null) {
                        // allowed, it's either passive or declarative
                        state.mapExprNode(
                                module,
                                new TypeNode(new byte[0], new byte[]{I32}),
                                new byte[0],
                                elem.offset
                        );
                    }
                    if (elem.init != null) {
                        for (ExprNode expr : elem.init) {
                            state.mapExprNode(
                                    module,
                                    new TypeNode(new byte[0], new byte[]{elem.type}),
                                    new byte[0],
                                    expr
                            );
                        }
                    }
                }
            }
        } catch (Throwable t) {
            t.addSuppressed(new RuntimeException(String.format("In %s #%d", errIn, errIndex)));
            throw t;
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

        public int height;
        public final List<ConvertState.CtrlFrame> ctrls = new ArrayList<>();

        public ConvertState.CtrlFrame ctrlsRef(int idx) {
            return ctrls.get(ctrls.size() - idx - 1);
        }

        public int pushV() {
            return ++height + locals;
        }

        public Var pushVar() {
            return refVar(pushV());
        }

        public int popV() {
            ConvertState.CtrlFrame frame = ctrlsRef(0);
            if (frame.unreachable &&
                    /* should be true by validation anyway */
                    height == frame.height) {
                return -1;
            }
            return height-- + locals;
        }

        public Var popVar() {
            return refVar(popV());
        }

        public int peekV() {
            return height + locals;
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
            frame.height = height - type.params.length;
            frame.unreachable = false;
            frame.firstBb = frame.bb = bb;
            ctrls.add(frame);
            return frame;
        }

        public ConvertState.CtrlFrame popC() {
            ConvertState.CtrlFrame frame = ctrls.remove(ctrls.size() - 1);
            int expectedHeight = frame.height + frame.type.returns.length;
            if (frame.unreachable) {
                height = expectedHeight;
            } else {
                if (expectedHeight != height) {
                    throw new RuntimeException(String.format("Frame height mismatch, likely a bug or an unverified module (expected: %d, actual: %d)",
                            expectedHeight, height));
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
            height = frame.height;
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
                bb.addEffect(CommonOps.IDENTITY.insn(refVar(locals + from + i))
                        .assignTo(refVar(locals + to + i)));
            }
        }

        public ConvertState(TypeNode[] funcTypes, FuncNode[] referencableFuncs, int argC, byte[] locals, int returns) {
            this.funcTypes = funcTypes;
            this.referencableFuncs = referencableFuncs;
            this.argC = argC;
            this.localTypes = locals;
            this.localC = locals.length;
            this.locals = argC + localC;
            this.height = 0;
            this.returns = returns;
            this.func = new Function();
        }
    }

    public interface Converter {
        void convert(ConvertState cs, AbstractInsnNode node, BasicBlock topB);
    }

    private static final InsnMap<Converter> CONVERTERS = new InsnMap<>();

    static {
        CONVERTERS.putByte(UNREACHABLE, (cs, node, topB) -> {
            topB.setControl(CommonOps.UNREACHABLE.insn().jumpsTo());
            cs.unreachable();
        });
        CONVERTERS.putByte(NOP, (cs, node, topB) -> {
        });
        CONVERTERS.putByte(new byte[]{
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
        CONVERTERS.putByte(IF, (cs, node, topB) -> {
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
        CONVERTERS.putByte(ELSE, (cs, node, topB) -> {
            ConvertState.CtrlFrame frame = cs.ctrlsRef(0);
            cs.popVs(frame.type.returns.length);
            cs.height += frame.type.params.length;
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
            exprs[i] = cs.refVar(cs.peekV() + i + 1);
        }
        bb.setControl(CommonOps.RETURN.insn(exprs).jumpsTo());
    }

    static {
        CONVERTERS.putByte(END, (cs, node, topB) -> {
            ConvertState.CtrlFrame frame = cs.popC();
            if (cs.ctrls.isEmpty()) {
                throw new RuntimeException("Too many ends");
            } else {
                frame.bb.setControl(Control.br(cs.ctrlsRef(0).bb));
                if (frame.elseBb != null) {
                    frame.elseBb.setControl(Control.br(cs.ctrlsRef(0).bb));
                }
            }
            assert frame.bb.getControl() != null;
        });

        CONVERTERS.putByte(BR, (cs, node, topB) -> {
            ConvertState.CtrlFrame topFrame = cs.ctrlsRef(0);
            int depth = ((BreakInsnNode) node).label;
            ConvertState.CtrlFrame targetFrame = cs.ctrlsRef(depth);
            int arity = cs.labelArity(targetFrame);
            cs.popVs(arity);
            cs.copyStack(topFrame.bb, arity, cs.height, targetFrame.height);
            topFrame.bb.setControl(Control.br(cs.labelTarget(depth)));
            cs.unreachable();
        });
        CONVERTERS.putByte(BR_IF, (cs, node, topB) -> {
            BasicBlock thenBb = cs.newBb();
            BasicBlock elseBb = cs.newBb();
            ConvertState.CtrlFrame topFrame = cs.ctrlsRef(0);
            topFrame.bb.setControl(WasmOps.brIf(cs.popVar(), thenBb, elseBb));
            topFrame.bb = elseBb;

            int depth = ((BreakInsnNode) node).label;
            ConvertState.CtrlFrame targetFrame = cs.ctrlsRef(depth);
            int arity = cs.labelArity(targetFrame);
            cs.copyStack(thenBb, arity, cs.height - arity, targetFrame.height);
            thenBb.setControl(Control.br(cs.labelTarget(depth)));
        });
        CONVERTERS.putByte(BR_TABLE, (cs, node, topB) -> {
            TableBreakInsnNode tblBr = (TableBreakInsnNode) node;
            Map<Integer, BasicBlock> commonBBs = new HashMap<>();
            BasicBlock[] bbs = new BasicBlock[tblBr.labels.length + 1];
            BasicBlock elseBb = cs.newBb();
            commonBBs.put(tblBr.defaultLabel, elseBb);

            ConvertState.CtrlFrame defaultFrame = cs.ctrlsRef(tblBr.defaultLabel);
            int arity = cs.labelArity(defaultFrame);
            Var condVal = cs.popVar();
            cs.popVs(arity);

            cs.copyStack(elseBb, arity, cs.height, defaultFrame.height);
            elseBb.setControl(Control.br(cs.labelTarget(tblBr.defaultLabel)));
            for (int i = 0; i < bbs.length - 1; i++) {
                bbs[i] = commonBBs.computeIfAbsent(tblBr.labels[i], lbl -> {
                    BasicBlock bb = cs.newBb();
                    ConvertState.CtrlFrame frame = cs.ctrlsRef(lbl);
                    cs.copyStack(bb, arity, cs.height, frame.height);
                    bb.setControl(Control.br(cs.labelTarget(lbl)));
                    return bb;
                });
            }
            bbs[bbs.length - 1] = elseBb;

            ConvertState.CtrlFrame topFrame = cs.ctrlsRef(0);
            topFrame.bb.setControl(WasmOps.BR_TABLE
                    .insn(condVal)
                    .jumpsTo(bbs));
            cs.unreachable();
        });
        CONVERTERS.putByte(RETURN, (cs, node, topB) -> {
            cs.popVs(cs.returns);
            doReturn(cs, topB);
            cs.unreachable();
        });
        CONVERTERS.putByte(new byte[]{
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
                args[i] = cs.refVar(cs.peekV() + i + 1);
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
        CONVERTERS.putByte(REF_NULL, (cs, node, topB) -> topB.addEffect(WasmOps.ZEROINIT
                .create(((NullInsnNode) node).type).insn().assignTo(cs.pushVar())));
        CONVERTERS.putByte(REF_IS_NULL, (cs, node, topB) -> topB.addEffect(WasmOps.IS_NULL
                .create().insn(cs.popVar()).assignTo(cs.pushVar())));
        CONVERTERS.putByte(REF_FUNC, (cs, node, topB) -> topB.addEffect(WasmOps.FUNC_REF
                .create(((FuncRefInsnNode) node).function).insn().assignTo(cs.pushVar())));
    }

    static {
        CONVERTERS.putByte(DROP, (cs, node, topB) -> cs.popV());
        CONVERTERS.putByte(new byte[]{
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
        CONVERTERS.putByte(LOCAL_GET, (cs, node, topB) -> topB.addEffect(CommonOps.IDENTITY
                .insn(cs.refVar(((VariableInsnNode) node).variable)).assignTo(cs.pushVar())));
        CONVERTERS.putByte(new byte[]{
                LOCAL_SET,
                LOCAL_TEE
        }, (cs, node, topB) -> topB.addEffect(CommonOps.IDENTITY
                .insn(cs.refVar(node.opcode == LOCAL_SET ? cs.popV() : cs.peekV()))
                .assignTo(cs.refVar(((VariableInsnNode) node).variable))));
        CONVERTERS.putByte(GLOBAL_GET, (cs, node, topB) -> topB.addEffect(WasmOps.GLOBAL_REF
                .create(((VariableInsnNode) node).variable).insn().assignTo(cs.pushVar())));
        CONVERTERS.putByte(GLOBAL_SET, (cs, node, topB) -> topB.addEffect(WasmOps.GLOBAL_SET
                .create(((VariableInsnNode) node).variable)
                .insn(cs.popVar())
                .assignTo()));
    }

    static {
        CONVERTERS.putByte(TABLE_GET, (cs, node, topB) -> topB.addEffect(WasmOps.TABLE_REF
                .create(((TableInsnNode) node).table)
                .insn(cs.popVar())
                .assignTo(cs.pushVar())));
        CONVERTERS.putByte(TABLE_SET, (cs, node, topB) -> topB.addEffect(WasmOps.TABLE_STORE
                .create(((TableInsnNode) node).table)
                .insn(
                        cs.popVar(), // value
                        cs.popVar() // index
                )
                .assignTo()));
        /*
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
        */
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
        CONVERTERS.putByte(I32_LOAD, makeLoadInsn(I32, LoadType.I32, ExtType.NOEXT));
        CONVERTERS.putByte(I64_LOAD, makeLoadInsn(I64, LoadType.I64, ExtType.NOEXT));
        CONVERTERS.putByte(F32_LOAD, makeLoadInsn(F32, LoadType.F32, ExtType.NOEXT));
        CONVERTERS.putByte(F64_LOAD, makeLoadInsn(F64, LoadType.F64, ExtType.NOEXT));

        CONVERTERS.putByte(I32_LOAD8_S, makeLoadInsn(I32, LoadType.I8, ExtType.S8_32));
        CONVERTERS.putByte(I32_LOAD8_U, makeLoadInsn(I32, LoadType.I8, ExtType.U8_32));
        CONVERTERS.putByte(I32_LOAD16_S, makeLoadInsn(I32, LoadType.I16, ExtType.S16_32));
        CONVERTERS.putByte(I32_LOAD16_U, makeLoadInsn(I32, LoadType.I16, ExtType.U16_32));

        CONVERTERS.putByte(I64_LOAD8_S, makeLoadInsn(I64, LoadType.I8, ExtType.S8_64));
        CONVERTERS.putByte(I64_LOAD8_U, makeLoadInsn(I64, LoadType.I8, ExtType.U8_64));
        CONVERTERS.putByte(I64_LOAD16_S, makeLoadInsn(I64, LoadType.I16, ExtType.S16_64));
        CONVERTERS.putByte(I64_LOAD16_U, makeLoadInsn(I64, LoadType.I16, ExtType.U16_64));
        CONVERTERS.putByte(I64_LOAD32_S, makeLoadInsn(I64, LoadType.I32, ExtType.S32_64));
        CONVERTERS.putByte(I64_LOAD32_U, makeLoadInsn(I64, LoadType.I32, ExtType.U32_64));
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
        CONVERTERS.putByte(I32_STORE, makeStoreInsn(StoreType.I32));
        CONVERTERS.putByte(I64_STORE, makeStoreInsn(StoreType.I64));
        CONVERTERS.putByte(F32_STORE, makeStoreInsn(StoreType.F32));
        CONVERTERS.putByte(F64_STORE, makeStoreInsn(StoreType.F64));

        CONVERTERS.putByte(I32_STORE8, makeStoreInsn(StoreType.I32_8));
        CONVERTERS.putByte(I32_STORE16, makeStoreInsn(StoreType.I32_16));

        CONVERTERS.putByte(I64_STORE8, makeStoreInsn(StoreType.I64_8));
        CONVERTERS.putByte(I64_STORE16, makeStoreInsn(StoreType.I64_16));
        CONVERTERS.putByte(I64_STORE32, makeStoreInsn(StoreType.I64_32));
    }

    static {
        CONVERTERS.putByte(MEMORY_SIZE, (cs, node, topB) -> topB.addEffect(WasmOps.MEM_SIZE
                .create().insn().assignTo(cs.pushVar())));
        CONVERTERS.putByte(MEMORY_GROW, (cs, node, topB) -> topB.addEffect(WasmOps.MEM_GROW
                .create().insn(cs.popVar()).assignTo(cs.pushVar())));
        /*
        CONVERTERS.putInt(new int[]{
                MEMORY_INIT,
                DATA_DROP,
                MEMORY_COPY,
                MEMORY_FILL,
        }, (cs, node, topB) -> {
            throw new UnsupportedOperationException();
        });
         */
    }

    static {
        CONVERTERS.putByte(new byte[]{
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
                args[i] = cs.refVar(cs.peekV() + i + 1);
            }
            topB.addEffect(WasmOps.OPERATOR
                    .create(new WasmOps.OperatorType(
                            node.opcode,
                            node instanceof PrefixInsnNode
                                    ? ((PrefixInsnNode) node).intOpcode
                                    : node instanceof VectorInsnNode
                                    ? ((VectorInsnNode) node).intOpcode
                                    : 0,
                            returnType
                    )).insn(args)
                    .assignTo(cs.pushVar()));
        };
    }

    static {
        for (Opcode opc : InsnAttributes.allOpcodes()) {
            if (CONVERTERS.get(opc.opcode, opc.intOpcode) != null) continue;
            InsnAttributes attrs = InsnAttributes.lookup(opc);
            assert attrs != null;
            switch (attrs.getVisitTarget()) {
                case Insn:
                case VectorInsn: {
                    StackType type = attrs.getType();
                    if (type == null || type.pushes.length != 1) {
                        throw new IllegalStateException("Insn: " + attrs.getMnemonic());
                    }
                    CONVERTERS.put(
                            opc.opcode,
                            opc.intOpcode,
                            makeOpInsn(type.pops.length, type.pushes[0])
                    );
                    break;
                }
                default: {
                    String mnem = attrs.getMnemonic();
                    CONVERTERS.put(
                            opc.opcode,
                            opc.intOpcode,
                            (cs, node, bb) -> {
                                throw new UnsupportedOperationException(
                                        "instruction " + mnem + " is not supported"
                                );
                            }
                    );
                }
            }
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
            module.functions.add(func);
            func.attachExt(WasmExts.TYPE, funcType);
            module.getExtOrThrow(WasmExts.FUNC_MAP).put(expr, func);
        }

        public Function convertFunc(
                ConvertState cs,
                ExprNode expr
        ) {
            ConvertState.CtrlFrame rootFrame = cs.pushC(END, new TypeNode(new byte[0], new byte[0]), cs.newBb());
            BasicBlock firstBb = rootFrame.bb;
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
            ConvertState.CtrlFrame startFrame = cs.pushC(END, new TypeNode(new byte[0], new byte[cs.returns]), cs.newBb());
            firstBb.setControl(Control.br(startFrame.bb));

            int unreachableDepth = -1;
            for (AbstractInsnNode insn : expr) {
                Converter converter = CONVERTERS.get(insn);
                if (converter == null) {
                    throw new UnsupportedOperationException("Instruction not supported");
                }

                boolean unreachableBefore = unreachableDepth >= 0;
                if (unreachableBefore) {
                    switch (insn.opcode) {
                        case IF:
                        case BLOCK:
                        case LOOP:
                            unreachableDepth++;
                            continue;
                        case END:
                            unreachableDepth--;
                            if (unreachableDepth < 0) break;
                            continue;
                        case ELSE:
                            if (unreachableDepth == 0) {
                                unreachableDepth = -1;
                                break;
                            }
                            continue;
                        default:
                            continue; // do not even bother compiling beyond this
                    }
                }
                ConvertState.CtrlFrame topFrame = cs.ctrlsRef(0);
                converter.convert(cs, insn, topFrame.bb);
                if (!unreachableBefore && topFrame.unreachable) {
                    unreachableDepth = 0;
                }
            }

            if (cs.ctrls.size() != 1) {
                throw new RuntimeException("Not enough ends");
            }
            startFrame.bb.setControl(Control.br(rootFrame.bb = cs.newBb()));
            // last end should verify that we have the correct number of returns
            cs.height = 0;
            doReturn(cs, cs.popC().bb);
            return cs.func;
        }
    }
}
