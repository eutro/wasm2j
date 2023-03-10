package io.github.eutro.wasm2j.ops;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Insn;
import io.github.eutro.wasm2j.ssa.Var;

import java.util.List;
import java.util.stream.Collectors;

import static io.github.eutro.wasm2j.ext.CommonExts.takeNull;

public class CommonOps {
    public static final Op BR = new SimpleOpKey("br").create();
    public static final Op RETURN = new SimpleOpKey("return").create();
    public static final Op IDENTITY = new SimpleOpKey("id").create();
    public static final UnaryOpKey<String> TRAP = new UnaryOpKey<>("trap");

    public static final UnaryOpKey<List<BasicBlock>> PHI = new UnaryOpKey<>("phi", bbs ->
            bbs.stream().map(BasicBlock::toTargetString).collect(Collectors.joining(" ")));

    public static final UnaryOpKey<Integer> ARG = new UnaryOpKey<>("arg");
    public static final UnaryOpKey<Object> CONST = new UnaryOpKey<>("const").allowNull();

    static {
        for (OpKey key : new OpKey[] {
                IDENTITY.key,
                PHI,
                ARG,
                CONST,
        }) {
            key.attachExt(CommonExts.IS_PURE, true);
        }
        IDENTITY.attachExt(CommonExts.CONSTANT_PROPAGATOR, insn -> {
            if (insn.args().size() == 1) {
                Var arg = insn.args().get(0);
                Object maybeValue = arg.getNullable(CommonExts.CONSTANT_VALUE);
                if (maybeValue == null) return insn;
                return constant(takeNull(maybeValue));
            }
            return insn;
        });
    }

    public static Insn constant(Object k) {
        return CONST.create(k).insn();
    }
}
