package io.github.eutro.wasm2j.passes.convert;

import io.github.eutro.jwasm.tree.TypeNode;
import io.github.eutro.wasm2j.conf.api.CallingConvention;
import io.github.eutro.wasm2j.conf.api.WirJavaConvention;
import io.github.eutro.wasm2j.conf.api.WirJavaConventionFactory;
import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.MetadataState;
import io.github.eutro.wasm2j.ext.WasmExts;
import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.intrinsics.JavaIntrinsics;
import io.github.eutro.wasm2j.ops.*;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.util.Pair;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.eutro.jwasm.Opcodes.*;

public class WirToJir implements InPlaceIRPass<Module> {
    private final WirJavaConventionFactory conventionsFactory;

    public WirToJir(WirJavaConventionFactory conventionsFactory) {
        this.conventionsFactory = conventionsFactory;
    }

    @Override
    public void runInPlace(Module module) {
        if (module.getExt(CommonExts.CODE_TYPE).orElse(null) != CommonExts.CodeType.WASM) {
            throw new IllegalArgumentException("module must be in WASM code type");
        }

        Module extrasModule = new Module(module);
        AtomicReference<WirToJirPerFunc> pass = new AtomicReference<>();
        WirJavaConvention conventions = conventionsFactory.create(module, extrasModule,
                (InPlaceIRPass<Function>) function -> pass.get().runInPlace(function));
        WirToJirPerFunc convertPass = new WirToJirPerFunc(
                conventions,
                conventions.getIndirectCallingConvention()
        );
        pass.set(convertPass);

        conventions.preConvert();
        ForPass.liftFunctions(
                        convertPass)
                .runInPlace(module);
        conventions.postConvert();

        module.functions.addAll(extrasModule.functions);
        module.attachExt(CommonExts.CODE_TYPE, CommonExts.CodeType.JAVA);
    }

    public static class WirToJirPerFunc implements InPlaceIRPass<Function> {
        private final WirJavaConvention conventions;
        private final CallingConvention callConv;
        private final Map<BasicBlock, BasicBlock> startBlockMap = new HashMap<>();
        private final Map<BasicBlock, BasicBlock> endBlockMap = new HashMap<>();
        private List<Var> args;

        public WirToJirPerFunc(WirJavaConvention conventions, CallingConvention callConv) {
            this.conventions = conventions;
            this.callConv = callConv;
        }

        private void translateBbs(List<BasicBlock> oldBlocks, Map<BasicBlock, BasicBlock> map) {
            ListIterator<BasicBlock> li = oldBlocks.listIterator();
            while (li.hasNext()) {
                BasicBlock nextBb = li.next();
                li.set(map.getOrDefault(nextBb, nextBb));
            }
        }

        @Override
        public void runInPlace(Function func) {
            MetadataState ms = func.getExtOrThrow(CommonExts.METADATA_STATE);
            ms.ensureValid(func, MetadataState.SSA_FORM);

            ArrayList<BasicBlock> oldBlocks = new ArrayList<>(func.blocks);
            func.blocks.clear();

            IRBuilder ib = new IRBuilder(func, func.newBb());
            TypeNode type = func.getExtOrThrow(WasmExts.TYPE);
            List<Var> argVars = new ArrayList<>(type.params.length);
            for (int i = 0; i < type.params.length; i++) {
                argVars.add(ib.insert(CommonOps.ARG.create(i).insn(), "arg" + i));
            }
            args = callConv.receiveArguments(ib, argVars, type);
            ib.insertCtrl(Control.br(oldBlocks.get(0)));
            for (BasicBlock block : oldBlocks) {
                ib.setBlock(func.newBb());
                startBlockMap.put(block, ib.getBlock());

                List<Effect> oldEffects = new ArrayList<>(block.getEffects());
                block.getEffects().clear();
                for (Effect effect : oldEffects) {
                    translateEffect(effect, ib);
                }
                translateControl(block.getControl(), ib);

                endBlockMap.put(block, ib.getBlock());
            }

            for (BasicBlock block : func.blocks) {
                for (Effect effect : block.getEffects()) {
                    Insn insn = effect.insn();
                    if (insn.op.key != CommonOps.PHI) break;
                    translateBbs(CommonOps.PHI.cast(insn.op).arg, endBlockMap);
                }
                translateBbs(block.getControl().targets, startBlockMap);
            }

            ms.graphChanged();
        }

        private void translateEffect(Effect fct, IRBuilder jb) {
            Converter<Effect> cc = FX_CONVERTERS.get(fct.insn().op.key);
            if (cc == null) {
                throw new IllegalArgumentException("op: " + fct.insn().op.key + " is not supported");
            }
            cc.convert(fct, jb, WirToJirPerFunc.this);
        }

        private void translateControl(Control ctrl, IRBuilder jb) {
            Converter<Control> cc = CTRL_CONVERTERS.get(ctrl.insn().op.key);
            if (cc == null) {
                throw new IllegalArgumentException(ctrl.insn().op.key + " is not supported");
            }
            cc.convert(ctrl, jb, WirToJirPerFunc.this);
            jb.insertCtrl(ctrl);
        }

        private interface Converter<T> {
            void convert(T t, IRBuilder jb, WirToJirPerFunc slf);
        }

        private static final Map<OpKey, Converter<Effect>> FX_CONVERTERS = new HashMap<>();

        static {
            for (OpKey key : new OpKey[]{
                    CommonOps.IDENTITY.key,
                    CommonOps.PHI,
                    CommonOps.CONST,
            }) {
                FX_CONVERTERS.put(key, (fx, jb, slf) -> jb.insert(fx));
            }
            FX_CONVERTERS.put(CommonOps.ARG, (fx, jb, slf) ->
                    jb.insert(CommonOps.IDENTITY
                            .insn(slf.args.get(CommonOps.ARG.cast(fx.insn().op).arg))
                            .copyFrom(fx)));

            FX_CONVERTERS.put(WasmOps.GLOBAL_REF, (fx, jb, slf) ->
                    slf.conventions.getGlobal(WasmOps.GLOBAL_REF.cast(fx.insn().op).arg).emitGlobalRef(jb, fx));
            FX_CONVERTERS.put(WasmOps.GLOBAL_SET, (fx, jb, slf) ->
                    slf.conventions.getGlobal(WasmOps.GLOBAL_SET.cast(fx.insn().op).arg).emitGlobalStore(jb, fx));

            FX_CONVERTERS.put(WasmOps.MEM_STORE, (fx, jb, slf) ->
                    slf.conventions.getMemory(WasmOps.MEM_STORE.cast(fx.insn().op).arg.memory).emitMemStore(jb, fx));
            FX_CONVERTERS.put(WasmOps.MEM_LOAD, (fx, jb, slf) ->
                    slf.conventions.getMemory(WasmOps.MEM_LOAD.cast(fx.insn().op).arg.memory).emitMemLoad(jb, fx));
            FX_CONVERTERS.put(WasmOps.MEM_SIZE, (fx, jb, slf) ->
                    slf.conventions.getMemory(WasmOps.MEM_SIZE.cast(fx.insn().op).arg).emitMemSize(jb, fx));
            FX_CONVERTERS.put(WasmOps.MEM_GROW, (fx, jb, slf) ->
                    slf.conventions.getMemory(WasmOps.MEM_GROW.cast(fx.insn().op).arg).emitMemGrow(jb, fx));
            FX_CONVERTERS.put(WasmOps.MEM_INIT, (fx, jb, slf) -> {
                Pair<Integer, Integer> pair = WasmOps.MEM_INIT.cast(fx.insn().op).arg;
                slf.conventions.getMemory(pair.left)
                        .emitMemInit(jb, fx, slf.conventions
                                .getData(pair.right)
                                .byteBuffer()
                                .get(jb));
            });
            FX_CONVERTERS.put(WasmOps.DATA_DROP, (fx, jb, slf) ->
                    slf.conventions.getData(WasmOps.DATA_DROP.cast(fx.insn().op).arg).emitDrop(jb, fx));
            FX_CONVERTERS.put(WasmOps.MEM_COPY, (fx, jb, slf) -> {
                Pair<Integer, Integer> arg = WasmOps.MEM_COPY.cast(fx.insn().op).arg;
                slf.conventions.getMemory(arg.left).emitMemCopy(jb, fx, slf.conventions.getMemory(arg.right));
            });
            FX_CONVERTERS.put(WasmOps.MEM_FILL, (fx, jb, slf) ->
                    slf.conventions.getMemory(WasmOps.MEM_FILL.cast(fx.insn().op).arg).emitMemFill(jb, fx));

            FX_CONVERTERS.put(WasmOps.TABLE_STORE, (fx, jb, slf) ->
                    slf.conventions.getTable(WasmOps.TABLE_STORE.cast(fx.insn().op).arg).emitTableStore(jb, fx));
            FX_CONVERTERS.put(WasmOps.TABLE_REF, (fx, jb, slf) ->
                    slf.conventions.getTable(WasmOps.TABLE_REF.cast(fx.insn().op).arg).emitTableRef(jb, fx));
            FX_CONVERTERS.put(WasmOps.TABLE_SIZE, (fx, jb, slf) ->
                    slf.conventions.getTable(WasmOps.TABLE_SIZE.cast(fx.insn().op).arg).emitTableSize(jb, fx));
            FX_CONVERTERS.put(WasmOps.TABLE_GROW, (fx, jb, slf) ->
                    slf.conventions.getTable(WasmOps.TABLE_GROW.cast(fx.insn().op).arg).emitTableGrow(jb, fx));
            FX_CONVERTERS.put(WasmOps.TABLE_INIT, (fx, jb, slf) -> {
                Pair<Integer, Integer> arg = WasmOps.TABLE_INIT.cast(fx.insn().op).arg;
                slf.conventions.getTable(arg.left).emitTableInit(jb, fx, slf.conventions.getElem(arg.right).array().get(jb));
            });
            FX_CONVERTERS.put(WasmOps.ELEM_DROP, (fx, jb, slf) ->
                    slf.conventions.getElem(WasmOps.ELEM_DROP.cast(fx.insn().op).arg).emitDrop(jb, fx));
            FX_CONVERTERS.put(WasmOps.TABLE_COPY, (fx, jb, slf) -> {
                Pair<Integer, Integer> arg = WasmOps.TABLE_COPY.cast(fx.insn().op).arg;
                slf.conventions.getTable(arg.left).emitTableCopy(jb, fx, slf.conventions.getTable(arg.right));
            });
            FX_CONVERTERS.put(WasmOps.TABLE_FILL, (fx, jb, slf) ->
                    slf.conventions.getTable(WasmOps.TABLE_FILL.cast(fx.insn().op).arg).emitTableFill(jb, fx));

            FX_CONVERTERS.put(WasmOps.FUNC_REF, (fx, jb, slf) ->
                    slf.conventions.getFunction(WasmOps.FUNC_REF.cast(fx.insn().op).arg).emitFuncRef(jb, fx));

            FX_CONVERTERS.put(WasmOps.CALL, (fx, jb, slf) ->
                    slf.conventions.getFunction(WasmOps.CALL.cast(fx.insn().op).arg.func).emitCall(jb, fx));
            FX_CONVERTERS.put(WasmOps.CALL_INDIRECT, (fx, jb, slf) ->
                    slf.conventions.getIndirectCallingConvention().emitCallIndirect(jb, fx));

            FX_CONVERTERS.put(WasmOps.ZEROINIT, (fx, jb, slf) -> {
                Object value;
                switch (WasmOps.ZEROINIT.cast(fx.insn().op).arg) {
                    case I32:
                        value = 0;
                        break;
                    case I64:
                        value = 0L;
                        break;
                    case F32:
                        value = 0F;
                        break;
                    case F64:
                        value = 0D;
                        break;
                    case FUNCREF:
                    case EXTERNREF:
                        value = null;
                        break;
                    default:
                        throw new IllegalArgumentException();
                }
                jb.insert(CommonOps.constant(value).copyFrom(fx));
            });
            FX_CONVERTERS.put(WasmOps.IS_NULL, (fx, jb, slf) ->
                    jb.insert(JavaOps.BOOL_SELECT.create(JavaOps.JumpType.IFNONNULL).copyFrom(fx)));

            FX_CONVERTERS.put(WasmOps.SELECT, (fx, jb, slf) ->
                    jb.insert(JavaOps.SELECT.create(JavaOps.JumpType.IFNE)
                            .insn(fx.insn().args.get(1 /* ift -> taken */),
                                    fx.insn().args.get(2 /* iff -> fallthrough */),
                                    fx.insn().args.get(0 /* cond */))
                            .copyFrom(fx)));

            FX_CONVERTERS.put(WasmOps.OPERATOR, (fx, jb, slf) -> {
                WasmOps.OperatorType opTy = WasmOps.OPERATOR.cast(fx.insn().op).arg;
                IntrinsicImpl intr = JavaIntrinsics.INTRINSICS.get(opTy.op, opTy.intOp);
                if (intr == null) throw new UnsupportedOperationException("operator " + opTy + " is not implemented");
                fx.insn().op = JavaOps.INTRINSIC.create(intr);
                jb.insert(fx);
            });
        }

        private static final Map<OpKey, Converter<Control>> CTRL_CONVERTERS = new HashMap<>();

        static {
            CTRL_CONVERTERS.put(CommonOps.RETURN.key, (ctrl, jb, slf) -> {
                jb.func.getExt(WasmExts.TYPE).ifPresent(typeNode -> {
                    Optional<Var> returns = slf.callConv.emitReturn(jb, null /*FIXME*/, ctrl.insn().args, typeNode);
                    ctrl.insn().args.clear();
                    returns.ifPresent(ctrl.insn().args::add);
                });
                // else noop
            });

            for (OpKey key : new OpKey[]{
                    CommonOps.BR.key,
                    CommonOps.TRAP,
            }) {
                CTRL_CONVERTERS.put(key, (ctrl, jb, slf) -> {
                    // noop
                });
            }

            CTRL_CONVERTERS.put(WasmOps.BR_IF.key, (ctrl, jb, slf) ->
                    ctrl.insn().op = JavaOps.BR_COND.create(JavaOps.JumpType.IFNE));
            CTRL_CONVERTERS.put(WasmOps.BR_TABLE.key, (ctrl, jb, slf) ->
                    ctrl.insn().op = ctrl.targets.size() == 1 ? CommonOps.BR : JavaOps.TABLESWITCH.create());
        }
    }
}
