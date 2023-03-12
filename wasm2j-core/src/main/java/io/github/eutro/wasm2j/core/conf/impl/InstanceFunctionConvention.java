package io.github.eutro.wasm2j.core.conf.impl;

import io.github.eutro.wasm2j.core.conf.itf.CallingConvention;
import io.github.eutro.wasm2j.core.conf.itf.ExportableConvention;
import io.github.eutro.wasm2j.core.conf.itf.FunctionConvention;
import io.github.eutro.wasm2j.core.ssa.IRBuilder;
import io.github.eutro.wasm2j.core.ext.Ext;
import io.github.eutro.wasm2j.core.ops.CommonOps;
import io.github.eutro.wasm2j.core.ops.JavaOps;
import io.github.eutro.wasm2j.core.ops.WasmOps;
import io.github.eutro.wasm2j.core.ssa.Effect;
import io.github.eutro.wasm2j.core.ssa.JClass;
import io.github.eutro.wasm2j.core.ssa.Var;
import io.github.eutro.wasm2j.core.util.IRUtils;
import io.github.eutro.wasm2j.core.util.ValueGetter;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link InstanceFunctionConvention} which implements function calls by invoking
 * a specific Java method on a Java object.
 */
public class InstanceFunctionConvention extends DelegatingExporter implements FunctionConvention {
    /**
     * The object on which the method will be invoked.
     */
    public static final Ext<ValueGetter> CONVENTION_TARGET = Ext.create(ValueGetter.class, "CONVENTION_TARGET");
    /**
     * The method which will be invoked.
     */
    public static final Ext<JClass.JavaMethod> CONVENTION_METHOD = Ext.create(JClass.JavaMethod.class, "CONVENTION_METHOD");
    /**
     * The calling convention with which the method will be invoked.
     */
    public static final Ext<CallingConvention> CONVENTION_CC = Ext.create(CallingConvention.class, "CONVENTION_CC");

    private final ValueGetter target;
    private final JClass.JavaMethod method;
    private final CallingConvention cc;

    /**
     * Construct a new {@link InstanceFunctionConvention}.
     *
     * @param exporter The exporter.
     * @param target The target to invoke the method on.
     * @param method The method to invoke.
     * @param cc The calling convention with which to call the method.
     */
    public InstanceFunctionConvention(
            ExportableConvention exporter,
            ValueGetter target,
            JClass.JavaMethod method,
            CallingConvention cc
    ) {
        super(exporter);
        this.target = target;
        this.method = method;
        this.cc = cc;
    }

    @Override
    public void emitCall(IRBuilder ib, Effect effect) {
        WasmOps.CallType callType = WasmOps.CALL.cast(effect.insn().op).arg;
        List<Var> args = new ArrayList<>();
        args.add(target.get(ib));
        List<Var> rawArgs = cc.passArguments(ib, effect.insn().args(), callType.type);
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
                .create(IRUtils.METHOD_HANDLE_CLASS.lookupMethod("bindTo", Object.class))
                .insn(handle, target.get(ib))
                .copyFrom(effect));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getNullable(Ext<T> ext) {
        if (ext == CONVENTION_TARGET) return (T) target;
        if (ext == CONVENTION_METHOD) return (T) method;
        if (ext == CONVENTION_CC) return (T) cc;
        return super.getNullable(ext);
    }

    @Override
    public <T> void removeExt(Ext<T> ext) {
        if (ext == CONVENTION_METHOD || ext == CONVENTION_TARGET || ext == CONVENTION_CC) {
            throw new IllegalArgumentException();
        }
        super.removeExt(ext);
    }

    @Override
    public <T> void attachExt(Ext<T> ext, T value) {
        if (ext == CONVENTION_METHOD || ext == CONVENTION_TARGET || ext == CONVENTION_CC) {
            throw new IllegalArgumentException();
        }
        super.attachExt(ext, value);
    }
}
