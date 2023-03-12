package io.github.eutro.wasm2j.passes.form;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.Op;
import io.github.eutro.wasm2j.ssa.*;
import org.objectweb.asm.Opcodes;

import java.util.*;

import static io.github.eutro.wasm2j.ext.CommonExts.takeNull;

/**
 * A pass which lowers {@link JavaOps#INTRINSIC intrinsics},
 * either by inlining their code, or attaching them as a method
 * to the class.
 */
public class LowerIntrinsics extends LowerCommon {
    /**
     * An instance of this pass.
     */
    public static final LowerIntrinsics INSTANCE = new LowerIntrinsics();

    @Override
    boolean lowerEffect(IRBuilder ib, Effect effect) {
        Insn insn = effect.insn();
        Op op = insn.op;
        if (op.key == JavaOps.INTRINSIC) {
            IntrinsicImpl intr = JavaOps.INTRINSIC.cast(op).arg;
            ib.insert(emitIntrinsic(ib, intr, effect.insn().args()).copyFrom(effect));
            return true;
        }
        return false;
    }

    private static Insn emitIntrinsic(IRBuilder ib, IntrinsicImpl intr, List<Var> args) {
        foldConstant:
        if (intr.eval != null) {
            for (Var arg : args) {
                if (arg.getNullable(CommonExts.CONSTANT_VALUE) == null) {
                    break foldConstant;
                }
            }
            List<Object> intrArgs = new ArrayList<>();
            for (Var arg : args) {
                intrArgs.add(takeNull(arg.getNullable(CommonExts.CONSTANT_VALUE)));
            }
            try {
                Object result = intr.eval.invokeWithArguments(intrArgs);
                return CommonOps.constant(result);
            } catch (Throwable ignored) {
            }
        }
        if (intr.inline) {
            return new Inliner(ib)
                    .inline(Objects.requireNonNull(intr.impl), args);
        } else {
            JClass jClass = ib.func.getExtOrThrow(JavaExts.FUNCTION_METHOD).owner;
            Map<IntrinsicImpl, JClass.JavaMethod> intrinsics = jClass.getExtOrRun(JavaExts.ATTACHED_INTRINSICS, jClass, md -> {
                // TODO threading?
                jClass.attachExt(JavaExts.ATTACHED_INTRINSICS, new HashMap<>());
                return null;
            });
            JClass.JavaMethod intrMethod = intrinsics.computeIfAbsent(intr, it -> {
                JClass.JavaMethod method = new JClass.JavaMethod(
                        jClass,
                        intr.method.name,
                        intr.method.desc,
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC
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
