package io.github.eutro.wasm2j.ops;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ssa.BasicBlock;

import java.util.List;
import java.util.stream.Collectors;

public class CommonOps {
    public static final Op BR = new SimpleOpKey("br").create();
    public static final Op RETURN = new SimpleOpKey("return").create();
    public static final Op UNREACHABLE = new SimpleOpKey("unreachable").create();
    public static final Op IDENTITY = new SimpleOpKey("id").create();

    public static final UnaryOpKey<List<BasicBlock>> PHI = new UnaryOpKey<>("phi", bbs ->
            bbs.stream().map(BasicBlock::toTargetString).collect(Collectors.joining(" ")));

    public static final UnaryOpKey<Integer> ARG = new UnaryOpKey<>("arg");
    public static final UnaryOpKey<Object> CONST = new UnaryOpKey<>("const");

    static {
        for (OpKey key : new OpKey[] {
                IDENTITY.key,
                PHI,
                ARG,
                CONST,
        }) {
            key.attachExt(CommonExts.IS_PURE, true);
        }
    }
}