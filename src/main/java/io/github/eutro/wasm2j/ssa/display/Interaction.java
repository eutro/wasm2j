package io.github.eutro.wasm2j.ssa.display;

import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.util.Pair;
import org.w3c.dom.Element;

import java.util.Map;

public interface Interaction {
    default String getCss() {
        return "";
    }

    default void bbHook(Map<BasicBlock, Element> bbs, Map<Pair<BasicBlock, BasicBlock>, Element> edges) {
    }
}
