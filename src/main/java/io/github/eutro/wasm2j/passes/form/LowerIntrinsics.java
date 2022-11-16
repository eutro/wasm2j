package io.github.eutro.wasm2j.passes.form;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.Op;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LowerIntrinsics extends LowerCommon {

    public static final LowerIntrinsics INSTANCE = new LowerIntrinsics();

    @Override
    protected boolean lowerEffect(IRBuilder ib, Effect effect) {
        Insn insn = effect.insn();
        Op op = insn.op;
        if (op.key == JavaOps.INTRINSIC) {
            IntrinsicImpl intr = JavaOps.INTRINSIC.cast(op).arg;
            ib.insert(emitIntrinsic(ib, intr, effect.insn().args).copyFrom(effect));
            return true;
        }
        return false;
    }

    public static Insn emitIntrinsic(IRBuilder ib, IntrinsicImpl intr, List<Var> args) {
        if (intr.inline) {
            return new Inliner(ib)
                    .inline(Objects.requireNonNull(intr.impl), args);
        } else {
            Module module = ib.func.getExtOrThrow(CommonExts.OWNING_MODULE);
            Map<IntrinsicImpl, JavaExts.JavaMethod> intrinsics = module.getExtOrRun(JavaExts.ATTACHED_INTRINSICS, module, md -> {
                // TODO threading?
                module.attachExt(JavaExts.ATTACHED_INTRINSICS, new HashMap<>());
                return null;
            });
            JavaExts.JavaMethod intrMethod = intrinsics.computeIfAbsent(intr, it -> {
                JavaExts.JavaClass jClass = module.getExtOrThrow(JavaExts.JAVA_CLASS);
                JavaExts.JavaMethod method = new JavaExts.JavaMethod(
                        jClass,
                        intr.method.name,
                        intr.method.desc,
                        JavaExts.JavaMethod.Kind.STATIC
                );
                jClass.methods.add(method);
                method.attachExt(JavaExts.METHOD_NATIVE_IMPL, intr.method);
                return method;
            });
            return JavaOps.INVOKE
                    .create(intrMethod)
                    .insn(args);
        }
    }
}
