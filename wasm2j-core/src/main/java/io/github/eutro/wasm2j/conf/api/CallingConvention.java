package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.jwasm.tree.TypeNode;
import io.github.eutro.wasm2j.conf.api.WirJavaConventionFactory.Builder;
import io.github.eutro.wasm2j.conf.impl.BasicCallingConvention;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.ssa.Var;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.github.eutro.wasm2j.util.IRUtils.METHOD_HANDLE_CLASS;

/**
 * A convention that defines how parameters are passed to, and arguments received from,
 * a function, and how values (possibly many) should be returned.
 * <p>
 * A calling convention converts from (resp. to) a WebAssembly function type
 * to (resp. from) a Java method type, handling passing of parameters and the
 * packing/unpacking of multiple returns.
 * <p>
 * There is therefore the distinction between the WebAssembly function, its type,
 * arguments and return values, and the actual "raw" Java method, its arguments, its return
 * value, and its type.
 * <p>
 * Currently, the only usable calling convention is {@link BasicCallingConvention}.
 *
 * @see Builder#setCallingConvention(CallingConvention)
 */
public interface CallingConvention {
    /**
     * Get the descriptor of the raw Java method that implements the given type.
     *
     * @param funcType The type of the function.
     * @return The Java descriptor for the function.
     */
    Type getDescriptor(TypeNode funcType);

    /**
     * For a given function type, emit code to convert the given WebAssembly {@code args}
     * (as given to a {@link WasmOps#CALL_INDIRECT} instruction)
     * to how they should be passed to the method handle implementing
     * the raw Java function.
     * <p>
     * This will be in the <i>caller</i> of the function.
     *
     * @param ib       The instruction builder.
     * @param args     The argument list.
     * @param funcType The type of the function.
     * @return The list of raw Java arguments.
     */
    List<Var> passArguments(IRBuilder ib, List<Var> args, TypeNode funcType);

    /**
     * For a given function type, emit code to convert the given raw Java {@code args}
     * (as loaded from the method arguments) to the WebAssembly arguments.
     * <p>
     * This will be in the <i>callee</i>.
     *
     * @param ib       The instruction builder.
     * @param rawArgs  The raw arguments to the method.
     * @param funcType The type of the function.
     * @return The list of WebAssembly arguments.
     */
    List<Var> receiveArguments(IRBuilder ib, List<Var> rawArgs, TypeNode funcType);

    /**
     * For a given function type, emit code to return, possibly many, WebAssembly return values
     * as a single Java value, or void.
     * <p>
     * This will be in the <i>callee</i>.
     *
     * @param ib       The instruction builder.
     * @param rawArgs  The raw arguments to the method.
     * @param returns  The list of values to return.
     * @param funcType The type of the function.
     * @return The single raw Java return value, or {@link Optional#empty()} to return void.
     */
    Optional<Var> emitReturn(IRBuilder ib, List<Var> rawArgs, List<Var> returns, TypeNode funcType);

    /**
     * For a given function type, emit code to convert a singular raw Java return value
     * to the possibly many WebAssembly return values.
     * <p>
     * This will be in the <i>caller</i> of the function.
     *
     * @param ib        The instruction builder.
     * @param rawArgs   The raw arguments given to the method.
     * @param rawReturn The value returned by the raw Java call
     * @param funcType  The type of the function.
     * @return The possibly many WebAssembly return values.
     */
    List<Var> receiveReturn(IRBuilder ib, List<Var> rawArgs, Var rawReturn, TypeNode funcType);

    /**
     * Emit code to call a function reference.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#CALL_INDIRECT},<br>
     * with as many arguments as the function has parameter types, prefixed by the
     * function reference already retrieved from the table;<br>
     * it will assign to as many variables as the function has return types.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     */
    default void emitCallIndirect(IRBuilder ib, Effect effect) {
        TypeNode callType = WasmOps.CALL_INDIRECT.cast(effect.insn().op).arg;
        List<Var> args = effect.insn().args();
        Var methodHandle = args.get(0);
        List<Var> invokeArgs = new ArrayList<>();
        invokeArgs.add(methodHandle);
        List<Var> rawArgs = passArguments(ib, args.subList(1, args.size()), callType);
        invokeArgs.addAll(rawArgs);
        Type ty = getDescriptor(callType);
        Var[] rets = ty.getReturnType().getSize() == 0
                ? new Var[0]
                : new Var[]{ib.func.newVar("ret")};
        ib.insert(JavaOps.INVOKE.create(new JClass.JavaMethod(
                        METHOD_HANDLE_CLASS,
                        "invokeExact",
                        ty.getDescriptor(),
                        Modifier.PUBLIC
                ))
                .insn(invokeArgs)
                .assignTo(rets));
        ib.insert(CommonOps.IDENTITY
                .insn(receiveReturn(ib, rawArgs, rets.length == 1 ? rets[0] : null, callType))
                .copyFrom(effect));
    }
}
