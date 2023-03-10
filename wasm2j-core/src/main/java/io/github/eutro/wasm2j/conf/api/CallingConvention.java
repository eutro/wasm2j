package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.jwasm.tree.TypeNode;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Var;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.Optional;

import static io.github.eutro.wasm2j.util.IRUtils.METHOD_HANDLE_CLASS;

public interface CallingConvention {
    Type getDescriptor(TypeNode funcType);

    List<Var> passArguments(IRBuilder ib, List<Var> args, TypeNode funcType);

    List<Var> receiveArguments(IRBuilder ib, List<Var> rawArgs, TypeNode funcType);

    Optional<Var> emitReturn(IRBuilder ib, List<Var> rawArgs, List<Var> returns, TypeNode funcType);

    List<Var> receiveReturn(IRBuilder ib, List<Var> rawArgs, Var rawReturn, TypeNode funcType);

    default void emitCallIndirect(IRBuilder ib, Effect effect) {
        TypeNode callType = WasmOps.CALL_INDIRECT.cast(effect.insn().op).arg;
        List<Var> rawArgs = passArguments(ib, effect.insn().args(), callType);
        Type ty = getDescriptor(callType);
        Var[] rets = ty.getReturnType() == Type.VOID_TYPE
                ? new Var[0]
                : new Var[]{ib.func.newVar("ret")};
        ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                        METHOD_HANDLE_CLASS,
                        "invokeExact",
                        ty.getDescriptor(),
                        JavaExts.JavaMethod.Kind.VIRTUAL
                ))
                .insn(rawArgs)
                .assignTo(rets));
        ib.insert(CommonOps.IDENTITY
                .insn(receiveReturn(ib, rawArgs, rets.length == 1 ? rets[0] : null, callType))
                .copyFrom(effect));
    }
}
