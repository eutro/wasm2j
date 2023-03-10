package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.wasm2j.conf.api.CallingConvention;
import io.github.eutro.wasm2j.conf.api.ExportableConvention;
import io.github.eutro.wasm2j.conf.api.FunctionConvention;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Var;
import io.github.eutro.wasm2j.util.IRUtils;
import io.github.eutro.wasm2j.util.ValueGetter;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

public class InstanceFunctionConvention extends DelegatingExporter implements FunctionConvention {
    public static final Ext<InstanceFunctionConvention> FUNCTION_CONVENTION = Ext.create(InstanceFunctionConvention.class, "FUNCTION_CONVENTION");
    public final ValueGetter target;
    public final JavaExts.JavaMethod method;
    public final CallingConvention cc;

    public InstanceFunctionConvention(
            ExportableConvention exporter,
            ValueGetter target,
            JavaExts.JavaMethod method,
            CallingConvention cc
    ) {
        super(exporter);
        this.target = target;
        this.method = method;
        attachExt(FUNCTION_CONVENTION, this);
        this.cc = cc;
    }

    @Override
    public void emitCall(IRBuilder ib, Effect effect) {
        WasmOps.CallType callType = WasmOps.CALL.cast(effect.insn().op).arg;
        List<Var> args = new ArrayList<>();
        args.add(target.get(ib));
        List<Var> rawArgs = cc.passArguments(ib, effect.insn().args, callType.type);
        args.addAll(rawArgs);
        Var[] rets = cc.getDescriptor(callType.type).getReturnType() == Type.VOID_TYPE
                ? new Var[0]
                : new Var[]{ib.func.newVar("ret")};
        ib.insert(JavaOps.INVOKE.create(method)
                .insn(args)
                .assignTo(rets));
        ib.insert(CommonOps.IDENTITY
                .insn(cc.receiveReturn(ib, rawArgs, rets.length == 1 ? rets[0] : null, callType.type))
                .copyFrom(effect));
    }

    @Override
    public void emitFuncRef(IRBuilder ib, Effect effect) {
        Var handle = ib.insert(JavaOps.HANDLE_OF.create(method).insn(),
                "handle");
        ib.insert(JavaOps.INVOKE
                .create(new JavaExts.JavaMethod(
                        IRUtils.METHOD_HANDLE_CLASS,
                        "bindTo",
                        "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;",
                        JavaExts.JavaMethod.Kind.VIRTUAL
                ))
                .insn(handle, target.get(ib))
                .copyFrom(effect));
    }
}
