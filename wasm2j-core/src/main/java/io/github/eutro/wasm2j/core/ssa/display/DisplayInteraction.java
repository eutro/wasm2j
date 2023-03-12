package io.github.eutro.wasm2j.core.ssa.display;

import io.github.eutro.wasm2j.core.ssa.BasicBlock;
import io.github.eutro.wasm2j.core.ext.CommonExts;
import io.github.eutro.wasm2j.core.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import java.util.*;
import java.util.stream.Collectors;

class DisplayInteraction implements Interaction {
    public static final DisplayInteraction HIGHLIGHT_PREDS = new DisplayInteraction()
            .styleEdgesOnClick(CommonExts.PREDS::getIn, strokeLine("red"));
    public static final DisplayInteraction HIGHLIGHT_SUCCS = new DisplayInteraction()
            .styleEdgesOnClick(bb -> Optional.ofNullable(bb.getControl()).map(it -> it.targets), strokeLine("green"));
    public static final DisplayInteraction HIGHLIGHT_INTERESTING = new DisplayInteraction()
            .add(HIGHLIGHT_PREDS)
            .add(HIGHLIGHT_SUCCS)
            .styleOnClick(bb -> bb.getExt(CommonExts.IDOM).map(Collections::singleton), fillRect("orange"))
            .styleOnClick(CommonExts.DOM_FRONTIER::getIn, fillRect("#8888ff"));

    @Override
    public String getCss() {
        return interactions.stream().map(Interaction::getCss).collect(Collectors.joining());
    }

    @Override
    public void bbHook(
            Map<BasicBlock, Element> bbs,
            Map<Pair<BasicBlock, BasicBlock>, Element> edges
    ) {
        interactions.forEach(it -> it.bbHook(bbs, edges));
    }

    private final List<Interaction> interactions = new ArrayList<>();

    public static java.util.function.Function<String, String> fillRect(String colour) {
        return cls -> String.format(".%s>rect{fill:%s;}", cls, colour);
    }

    public static java.util.function.Function<String, String> strokeLine(String colour) {
        return cls -> String.format("line.%s{stroke-width:5px;stroke:%s;}", cls, colour);
    }

    public DisplayInteraction add(Interaction it) {
        interactions.add(it);
        return this;
    }

    @NotNull
    private String getId() {
        return "soh" + Integer.toString(System.identityHashCode(this), 16) + interactions.size();
    }

    public DisplayInteraction styleOnClick(
            java.util.function.Function<BasicBlock, Optional<? extends Collection<BasicBlock>>> bbExt,
            java.util.function.Function<String, String> styles
    ) {
        String id = getId();
        String activeClass = id + "-active";
        interactions.add(new Interaction() {
            @Override
            public String getCss() {
                return styles.apply(activeClass);
            }

            @Override
            public void bbHook(Map<BasicBlock, Element> bbs, Map<Pair<BasicBlock, BasicBlock>, Element> edges) {
                bbs.forEach((bb, bbElt) -> bbExt.apply(bb).ifPresent(targets -> {
                    String triggerClass = String.format("bb%s-%s", System.identityHashCode(bb), id);
                    for (BasicBlock target : targets) {
                        Element targetElt = bbs.get(target);
                        if (targetElt == null) continue;
                        String tClass = targetElt.getAttribute("class");
                        targetElt.setAttribute(
                                "class",
                                (tClass.isEmpty() ? "" : tClass + " ") + triggerClass
                        );
                    }
                    String bOmo = bbElt.getAttribute("onclick");
                    bbElt.setAttribute(
                            "onclick",
                            (bOmo.isEmpty() ? "" : bOmo + ";") +
                                    String.format("setClassesTo(\"%s\", \"%s\")", triggerClass, activeClass)
                    );
                }));
            }
        });
        return this;
    }

    public DisplayInteraction styleEdgesOnClick(
            java.util.function.Function<BasicBlock, Optional<? extends Collection<BasicBlock>>> bbExt,
            java.util.function.Function<String, String> styles
    ) {
        String id = getId();
        String activeClass = id + "-active";
        interactions.add(new Interaction() {
            @Override
            public String getCss() {
                return styles.apply(activeClass);
            }

            @Override
            public void bbHook(Map<BasicBlock, Element> bbs, Map<Pair<BasicBlock, BasicBlock>, Element> edges) {
                bbs.forEach((bb, bbElt) -> bbExt.apply(bb).ifPresent(targets -> {
                    String triggerClass = String.format("bb%s-%s", System.identityHashCode(bb), id);
                    for (BasicBlock target : targets) {
                        Pair<BasicBlock, BasicBlock> edge = Pair.of(bb, target);
                        Element targetElt = edges.get(edge);
                        if (targetElt == null) continue;
                        String tClass = targetElt.getAttribute("class");
                        targetElt.setAttribute(
                                "class",
                                (tClass.isEmpty() ? "" : tClass + " ") + triggerClass
                        );
                    }
                    String bOmo = bbElt.getAttribute("onclick");
                    bbElt.setAttribute(
                            "onclick",
                            (bOmo.isEmpty() ? "" : bOmo + ";") +
                                    String.format("setClassesTo(\"%s\", \"%s\")", triggerClass, activeClass)
                    );
                }));
            }
        });
        return this;
    }
}

