package io.github.eutro.wasm2j.core.ssa.display;

import io.github.eutro.wasm2j.core.ssa.BasicBlock;
import io.github.eutro.wasm2j.core.util.Pair;
import org.w3c.dom.Element;

import java.util.Map;

interface Interaction {
    default String getCss() {
        return "";
    }

    default void bbHook(Map<BasicBlock, Element> bbs, Map<Pair<BasicBlock, BasicBlock>, Element> edges) {
    }
}
