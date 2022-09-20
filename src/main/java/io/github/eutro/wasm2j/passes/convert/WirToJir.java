package io.github.eutro.wasm2j.passes.convert;

import io.github.eutro.wasm2j.conf.api.WirJavaConvention;
import io.github.eutro.wasm2j.conf.api.WirJavaConventionFactory;
import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.intrinsics.JavaIntrinsics;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.OpKey;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        WirJavaConvention conventions = conventionsFactory.create(module);

        conventions.preEmit();
        ForPass.liftFunctions(new WirToJirPerFunc(conventions)).runInPlace(module);
        conventions.buildConstructor();

        module.attachExt(CommonExts.CODE_TYPE, CommonExts.CodeType.JAVA);
    }

    public static class WirToJirPerFunc implements InPlaceIRPass<Function> {
        private final WirJavaConvention conventions;

        public WirToJirPerFunc(WirJavaConvention conventions) {
            this.conventions = conventions;
        }

        @Override
        public void runInPlace(Function function) {
            new Runner(function).run();
        }

        private interface Converter<T> {
            void convert(T t, IRBuilder jb, WirToJirPerFunc slf);
        }

        private static final Map<OpKey, Converter<Effect>> FX_CONVERTERS = new HashMap<>();

        static {
            for (OpKey key : new OpKey[]{
                    CommonOps.IDENTITY.key,
                    CommonOps.PHI,
                    CommonOps.ARG,
                    CommonOps.CONST,
            }) {
                FX_CONVERTERS.put(key, (fx, jb, slf) -> jb.insert(fx));
            }

            FX_CONVERTERS.put(WasmOps.GLOBAL_REF, (fx, jb, slf) ->
                    slf.conventions.getGlobal(WasmOps.GLOBAL_REF.cast(fx.insn().op).arg).emitGlobalRef(jb, fx));
            FX_CONVERTERS.put(WasmOps.GLOBAL_SET, (fx, jb, slf) ->
                    slf.conventions.getGlobal(WasmOps.GLOBAL_SET.cast(fx.insn().op).arg).emitGlobalStore(jb, fx));
            FX_CONVERTERS.put(WasmOps.MEM_STORE, (fx, jb, slf) ->
                    slf.conventions.getMemory(0).emitMemStore(jb, fx));
            FX_CONVERTERS.put(WasmOps.MEM_LOAD, (fx, jb, slf) ->
                    slf.conventions.getMemory(0).emitMemLoad(jb, fx));
            FX_CONVERTERS.put(WasmOps.MEM_SIZE, (fx, jb, slf) ->
                    slf.conventions.getMemory(0).emitMemSize(jb, fx));
            FX_CONVERTERS.put(WasmOps.MEM_GROW, (fx, jb, slf) ->
                    slf.conventions.getMemory(0).emitMemGrow(jb, fx));
            FX_CONVERTERS.put(WasmOps.TABLE_STORE, (fx, jb, slf) ->
                    slf.conventions.getTable(WasmOps.TABLE_STORE.cast(fx.insn().op).arg).emitTableStore(jb, fx));
            FX_CONVERTERS.put(WasmOps.TABLE_REF, (fx, jb, slf) ->
                    slf.conventions.getTable(WasmOps.TABLE_REF.cast(fx.insn().op).arg).emitTableRef(jb, fx));
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
                jb.insert(CommonOps.CONST.create(value).copyFrom(fx));
            });
            FX_CONVERTERS.put(WasmOps.IS_NULL, (fx, jb, slf) ->
                    jb.insert(JavaOps.BOOL_SELECT.create(JavaOps.JumpType.IFNULL).copyFrom(fx)));

            FX_CONVERTERS.put(WasmOps.SELECT, (fx, jb, slf) ->
                    jb.insert(JavaOps.SELECT.create(JavaOps.JumpType.IFNE)
                            .insn(fx.insn().args.get(0 /* cond */),
                                    fx.insn().args.get(1 /* ift -> taken */),
                                    fx.insn().args.get(2 /* iff -> fallthrough */))
                            .copyFrom(fx)));

            FX_CONVERTERS.put(WasmOps.OPERATOR, (fx, jb, slf) -> {
                WasmOps.OperatorType opTy = WasmOps.OPERATOR.cast(fx.insn().op).arg;
                IntrinsicImpl intr = opTy.op == INSN_PREFIX
                        ? JavaIntrinsics.INTRINSICS.getInt(opTy.intOp)
                        : JavaIntrinsics.INTRINSICS.getByte(opTy.op);
                if (intr == null) throw new RuntimeException("operator " + opTy + " is not implemented");
                fx.insn().op = JavaOps.INTRINSIC.create(intr);
                jb.insert(fx);
            });
        }

        private static final Map<OpKey, Converter<Control>> CTRL_CONVERTERS = new HashMap<>();

        static {
            for (OpKey key : new OpKey[]{
                    CommonOps.BR.key,
                    CommonOps.RETURN.key,
                    CommonOps.UNREACHABLE.key,
            }) {
                CTRL_CONVERTERS.put(key, (ctrl, jb, slf) -> {
                    // noop
                });
            }

            CTRL_CONVERTERS.put(WasmOps.BR_IF.key, (ctrl, jb, slf) ->
                    ctrl.insn.op = JavaOps.BR_COND.create(JavaOps.JumpType.IFNE));
            CTRL_CONVERTERS.put(WasmOps.BR_TABLE.key, (ctrl, jb, slf) ->
                    ctrl.insn.op = JavaOps.TABLESWITCH.create());
        }

        private class Runner {
            public final Function func;

            private Runner(Function func) {
                this.func = func;
            }

            void run() {
                for (BasicBlock block : func.blocks) {
                    processBlock(block);
                }
            }

            private void processBlock(BasicBlock bb) {
                IRBuilder ib = new IRBuilder(func, bb);

                List<Effect> oldEffects = new ArrayList<>(bb.getEffects());
                bb.getEffects().clear();
                for (Effect effect : oldEffects) {
                    translateEffect(effect, ib);
                }
                translateControl(bb.getControl(), ib);
            }

            private void translateEffect(Effect fct, IRBuilder jb) {
                Converter<Effect> cc = FX_CONVERTERS.get(fct.insn().op.key);
                if (cc == null) {
                    throw new IllegalArgumentException(fct.insn().op.key + " is not supported");
                }
                cc.convert(fct, jb, WirToJirPerFunc.this);
            }

            private void translateControl(Control ctrl, IRBuilder jb) {
                Converter<Control> cc = CTRL_CONVERTERS.get(ctrl.insn.op.key);
                if (cc == null) {
                    throw new IllegalArgumentException(ctrl.insn.op.key + " is not supported");
                }
                cc.convert(ctrl, jb, WirToJirPerFunc.this);
            }
        }
    }
}
