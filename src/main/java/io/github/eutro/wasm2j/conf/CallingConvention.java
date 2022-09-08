package io.github.eutro.wasm2j.conf;

import io.github.eutro.jwasm.tree.TypeNode;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Var;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.Optional;

public interface CallingConvention {
    Type getDescriptor(TypeNode funcType);

    List<Var> passArguments(IRBuilder ib, List<Var> args, TypeNode funcType);

    List<Var> receiveArguments(IRBuilder ib, List<Var> rawArgs, TypeNode funcType);

    Optional<Var> emitReturn(IRBuilder ib, List<Var> rawArgs, List<Var> returns, TypeNode funcType);

    List<Var> receiveReturn(IRBuilder ib, List<Var> rawArgs, Var rawReturn, TypeNode funcType);
}
