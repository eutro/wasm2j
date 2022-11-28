package io.github.eutro.wasm2j.passes.meta;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.MetadataState;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Control;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.util.GraphWalker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 Thomas Lengauer and Robert Endre Tarjan. A fast algorithm for finding dominators in a flow-graph.
 ACM Transactions on Programming Languages and Systems, 1(1):121-141, July 1979.
*/
public class ComputeDoms implements InPlaceIRPass<Function> {
    public static final ComputeDoms INSTANCE = new ComputeDoms();

    @Override
    public void runInPlace(Function function) {
        computeDoms(function);
    }

    private static void computeDoms(Function func) {
        GraphWalker<BasicBlock> walker = GraphWalker.blockWalker(func);
        func.blocks.clear();
        for (BasicBlock basicBlock : walker.preOrder()) {
            func.blocks.add(basicBlock);
        }

        class Runner {
            int n = func.blocks.size();
            final int[][] succ = new int[n + 1][];
            final int[] dom = new int[n + 1];
            final int[] parent = new int[n + 1];
            final int[] ancestor = new int[n + 1];
            final int[] child = new int[n + 1];
            final int[] vertex = new int[n + 1];
            final int[] label = new int[n + 1];
            final int[] semi = new int[n + 1];
            final int[] size = new int[n + 1];
            @SuppressWarnings("unchecked")
            final
            Set<Integer>[] pred = new Set[n + 1];
            @SuppressWarnings("unchecked")
            final
            Set<Integer>[] bucket = new Set[n + 1];

            void dfs(int v) {
                semi[v] = ++n;
                vertex[n] = label[v] = v;
                ancestor[v] = child[v] = 0;
                size[v] = 1;
                for (int w : succ[v]) {
                    if (semi[w] == 0) {
                        parent[w] = v;
                        dfs(w);
                    }
                    pred[w].add(v);
                }
            }

            void compress(int v) {
                if (ancestor[ancestor[v]] != 0) {
                    compress(ancestor[v]);
                    if (semi[label[ancestor[v]]] < semi[label[v]]) {
                        label[v] = label[ancestor[v]];
                    }
                    ancestor[v] = ancestor[ancestor[v]];
                }
            }

            int eval(int v) {
                if (ancestor[v] == 0) {
                    return label[v];
                } else {
                    compress(v);
                    return semi[label[ancestor[v]]] >= semi[label[v]]
                            ? label[v]
                            : label[ancestor[v]];
                }
            }

            void link(int v, int w) {
                int s = w;
                while (semi[label[w]] < semi[label[child[s]]]) {
                    if (size[s] + size[child[child[s]]] >= 2 * size[child[s]]) {
                        ancestor[child[s]] = s;
                        child[s] = child[child[s]];
                    } else {
                        size[child[s]] = size[s];
                        s = ancestor[s] = child[s];
                    }
                }
                label[s] = label[w];
                size[v] += size[w];
                if (size[v] < 2 * size[w]) {
                    int t = s;
                    s = child[v];
                    child[v] = t;
                }
                while (s != 0) {
                    ancestor[s] = v;
                    s = child[s];
                }
            }

            void run() {
                Ext<Integer> indexExt = new Ext<>(Integer.class);
                int u, w;
                for (int i = 0; i < n; i++) {
                    BasicBlock block = func.blocks.get(i);
                    block.attachExt(indexExt, i + 1);
                }
                for (BasicBlock block : func.blocks) {
                    int i = block.getExtOrThrow(indexExt);
                    Control br = block.getControl();
                    succ[i] = new int[br.targets.size()];
                    for (int j = 0; j < succ[i].length; j++) {
                        succ[i][j] = br.targets.get(j).getExtOrThrow(indexExt);
                    }
                }

                for (int v = 1; v <= n; ++v) {
                    pred[v] = new HashSet<>();
                    bucket[v] = new HashSet<>();
                    semi[v] = 0;
                }
                n = 0;
                dfs(1);
                size[0] = label[0] = semi[0] = 0;
                for (int i = n; i >= 2; i--) {
                    w = vertex[i];
                    for (int v : pred[w]) {
                        u = eval(v);
                        if (semi[u] < semi[w]) {
                            semi[w] = semi[u];
                        }
                    }
                    bucket[vertex[semi[w]]].add(w);
                    link(parent[w], w);
                    for (int v : bucket[parent[w]]) {
                        u = eval(v);
                        dom[v] = semi[u] < semi[v] ? u : parent[w];
                    }
                    bucket[parent[w]].clear();
                }
                for (int i = 2; i <= n; ++i) {
                    w = vertex[i];
                    if (dom[w] != vertex[semi[w]]) {
                        dom[w] = dom[dom[w]];
                    }
                }
                dom[1] = 0;

                for (int i = 1; i < func.blocks.size(); i++) {
                    BasicBlock blockI = func.blocks.get(i);
                    blockI.attachExt(CommonExts.IDOM, func.blocks.get(dom[i + 1] - 1));
                    List<BasicBlock> preds = new ArrayList<>();
                    blockI.attachExt(CommonExts.PREDS, preds);
                    for (int p : pred[i + 1]) {
                        preds.add(func.blocks.get(p - 1));
                    }
                }
                func.blocks.get(0).attachExt(CommonExts.PREDS, new ArrayList<>());

                for (BasicBlock block : func.blocks) {
                    block.removeExt(indexExt);
                }
            }
        }
        new Runner().run();

        MetadataState ms = func.getExtOrThrow(CommonExts.METADATA_STATE);
        ms.validate(MetadataState.DOMS, MetadataState.PREDS);
    }
}
