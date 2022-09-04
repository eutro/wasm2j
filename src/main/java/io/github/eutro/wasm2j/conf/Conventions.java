package io.github.eutro.wasm2j.conf;

import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ext.WasmExts;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.Var;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Conventions {
    public static final WirJavaConventionFactory DEFAULT_CONVENTIONS = new DefaultFactory();

    private static class DefaultFactory implements WirJavaConventionFactory {
        @Override
        public WirJavaConvention create(Module module) {
            JavaExts.JavaClass jClass = new JavaExts.JavaClass("com.example.FIXME");
            module.attachExt(JavaExts.JAVA_CLASS, jClass);

            ModuleNode node = module.getExtOrThrow(WasmExts.MODULE);

            List<JavaExts.JavaMethod> funcs = new ArrayList<>();
            List<JavaExts.JavaField> globals = new ArrayList<>();
            List<JavaExts.JavaField> memories = new ArrayList<>();
            List<JavaExts.JavaField> tables = new ArrayList<>();

            if (node.imports != null && node.imports.imports != null) {
                throw new UnsupportedOperationException(); // FIXME
            }

            if (node.funcs != null && node.funcs.funcs != null) {
                assert node.types != null && node.types.types != null;
                int i = 0;
                for (FuncNode ignored : node.funcs) {
                    JavaExts.JavaMethod method = new JavaExts.JavaMethod(
                            jClass,
                            "func" + i++,
                            "", // FIXME
                            JavaExts.JavaMethod.Type.FINAL
                    );
                    funcs.add(method);
                }
            }

            if (node.globals != null) {
                int i = 0;
                for (GlobalNode global : node.globals) {
                    globals.add(new JavaExts.JavaField(
                            jClass,
                            "global" + i++,
                            "[Ljava/lang/Object;", // FIXME
                            false
                    ));
                }
            }

            if (node.mems != null) {
                int i = 0;
                for (MemoryNode mem : node.mems) {
                    memories.add(new JavaExts.JavaField(
                            jClass,
                            "mem" + i++,
                            Type.getDescriptor(ByteBuffer.class),
                            false
                    ));
                }
            }

            if (node.tables != null) {
                int i = 0;
                for (TableNode table : node.tables) {
                    tables.add(new JavaExts.JavaField(
                            jClass,
                            "table" + i++,
                            Type.getDescriptor(Object[].class),
                            false
                    ));
                }
            }

            return new WirJavaConvention() {
                private Var getThis(IRBuilder ib) {
                    return ib.insert(JavaOps.THIS.insn(), "this");
                }

                @Override
                public void emitGlobalRef(IRBuilder ib, Effect effect) {
                    int global = WasmOps.GLOBAL_REF.cast(effect.insn().op).arg;
                    ib.insert(JavaOps.GET_FIELD.create(globals.get(global))
                            .insn(getThis(ib))
                            .copyFrom(effect));
                }

                @Override
                public void emitGlobalStore(IRBuilder ib, Effect effect) {
                    int global = WasmOps.GLOBAL_SET.cast(effect.insn().op).arg;
                    ib.insert(JavaOps.PUT_FIELD.create(globals.get(global))
                            .insn(getThis(ib), effect.insn().args.get(0))
                            .copyFrom(effect));
                }

                @Override
                public void emitCall(IRBuilder ib, Effect effect) {
                    WasmOps.CallType callType = WasmOps.CALL.cast(effect.insn().op).arg;
                    JavaExts.JavaMethod method = funcs.get(callType.func);
                    List<Var> args = new ArrayList<>();
                    args.add(getThis(ib));
                    args.addAll(effect.insn().args);
                    ib.insert(JavaOps.INVOKE.create(method)
                            .insn(args)
                            .copyFrom(effect));
                }

                @Override
                public void emitCallIndirect(IRBuilder ib, Effect effect) {
                    //TypeNode callType = WasmOps.CALL_INDIRECT.cast(effect.insn.op).arg;
                    ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                    new JavaExts.JavaClass(Type.getInternalName(MethodHandle.class)),
                                    "invokeExact",
                                    "()V", // FIXME
                                    JavaExts.JavaMethod.Type.VIRTUAL
                            ))
                            .copyFrom(effect));
                }

                @Override
                public void emitMemLoad(IRBuilder ib, Effect effect) {
                    //WasmOps.DerefType derefType = WasmOps.MEM_LOAD.cast(effect.insn.op).arg;
                    Var mem = ib.insert(JavaOps.GET_FIELD
                                    .create(memories.get(0))
                                    .insn(getThis(ib)),
                            "mem");
                    List<Var> args = new ArrayList<>();
                    args.add(mem);
                    args.addAll(effect.insn().args);
                    // FIXME
                    ib.insert(JavaOps.INTRINSIC.create("memload")
                            .insn(args)
                            .copyFrom(effect));
                }

                @Override
                public void emitMemStore(IRBuilder ib, Effect effect) {
                    //Integer derefType = WasmOps.MEM_STORE.cast(effect.insn.op).arg;
                    Var mem = ib.insert(JavaOps.GET_FIELD
                                    .create(memories.get(0))
                                    .insn(getThis(ib)),
                            "mem");
                    List<Var> args = new ArrayList<>();
                    args.add(mem);
                    args.addAll(effect.insn().args);
                    // FIXME
                    ib.insert(JavaOps.INTRINSIC.create("memstore")
                            .insn(args)
                            .copyFrom(effect));
                }

                @Override
                public void emitTableRef(IRBuilder ib, Effect effect) {
                    // TODO
                    ib.insert(effect);
                }

                @Override
                public void emitTableStore(IRBuilder ib, Effect effect) {
                    // TODO
                    ib.insert(effect);
                }

                @Override
                public void emitFuncRef(IRBuilder ib, Effect effect) {
                    ib.insert(effect);
                }

                @Override
                public void emitMemSize(IRBuilder ib, Effect effect) {
                    ib.insert(effect);
                }

                @Override
                public void emitMemGrow(IRBuilder ib, Effect effect) {
                    ib.insert(effect);
                }
            };
        }
    }
}
