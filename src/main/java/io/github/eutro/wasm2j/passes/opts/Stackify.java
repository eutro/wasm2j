package io.github.eutro.wasm2j.passes.opts;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

// inspired by https://github.com/llvm/llvm-project/blob/main/llvm/lib/Target/WebAssembly/WebAssemblyRegStackify.cpp,
// see that for notes
public class Stackify implements InPlaceIRPass<Function> {
    public static Stackify INSTANCE = new Stackify();

    public static boolean CHECK_STACK_INTEGRITY = true;

    private static class LinkedList<T> implements Iterable<Node<T>> {
        Node<T> first, last;

        public static <T> LinkedList<T> fromIterator(Iterator<T> iter) {
            LinkedList<T> list = new LinkedList<>();
            if (!iter.hasNext()) return list;
            list.first = list.last = new Node<>(iter.next(), null, null, list);
            while (iter.hasNext()) {
                list.last = list.last.insertAfter(iter.next());
            }
            return list;
        }

        @NotNull
        @Override
        public Iterator<Node<T>> iterator() {
            return new NodeIterator<>(false, first);
        }
    }

    public static class NodeIterator<T> implements Iterator<Node<T>> {
        private final boolean reverse;
        private Node<T> node;

        public NodeIterator(boolean reverse, Node<T> node) {
            this.reverse = reverse;
            this.node = node;
        }

        @Override
        public boolean hasNext() {
            return node != null;
        }

        @Override
        public Node<T> next() {
            if (!hasNext()) throw new NoSuchElementException();
            Node<T> ret = node;
            node = reverse ? ret.prev : ret.next;
            return ret;
        }
    }

    private static final class Node<T> {
        final T value;
        Node<T> prev, next;
        final LinkedList<T> list;

        private Node(T value, Node<T> prev, Node<T> next, LinkedList<T> list) {
            this.value = value;
            this.prev = prev;
            this.next = next;
            this.list = list;
        }

        void remove() {
            if (value instanceof Effect) ((Effect) value).removeExt(NODE_EXT);
            if (prev == null) list.first = next;
            else prev.next = next;
            if (next == null) list.last = prev;
            else next.prev = prev;
            next = prev = null;
        }

        void linkAfter(Node<T> node) {
            if (node == null) return;
            node.prev = this;
            next = node;
        }

        @SuppressWarnings("unchecked")
        Node<T> insertAfter(T t) {
            Node<T> node = new Node<>(t, this, next, list);
            if (t instanceof Effect) ((Effect) t).attachExt(NODE_EXT, (Node<Effect>) node);
            if (node.next == null) {
                list.last = node;
            } else {
                next.prev = node;
            }
            next = node;
            return node;
        }

        @SuppressWarnings("unchecked")
        Node<T> insertBefore(T t) {
            Node<T> node = new Node<>(t, prev, this, list);
            if (t instanceof Effect) ((Effect) t).attachExt(NODE_EXT, (Node<Effect>) node);
            if (node.prev == null) {
                list.first = node;
            } else {
                prev.next = node;
            }
            prev = node;
            return node;
        }
    }

    private static final Ext<Node<Effect>> NODE_EXT = Ext.create(Node.class);
    private static final Ext<LinkedList<Effect>> LIST_EXT = Ext.create(LinkedList.class);
    private static final Ext<Set<Insn>> USES_EXT = Ext.create(Set.class);

    private static class Use {
        final Insn insn;
        final int index;

        Use(Insn insn, int index) {
            this.insn = insn;
            this.index = index;
        }

        Var getReg() {
            return insn.args.get(index);
        }

        void setReg(Var v) {
            insn.args.set(index, v);
        }
    }

    @Override
    public void runInPlace(Function func) {
        for (BasicBlock block : func.blocks) {
            LinkedList<Effect> list = LinkedList.fromIterator(block.getEffects().iterator());
            block.attachExt(LIST_EXT, list);
            for (Node<Effect> node : list) {
                Effect effect = node.value;
                effect.attachExt(NODE_EXT, node);
                for (Var arg : effect.insn().args) {
                    getOrCreateUses(arg).add(effect.insn());
                }
            }
            if (list.last == null) {
                list.first = list.last = new Node<>(null, null, null, list);
            } else {
                list.last.insertAfter(null);
            }
            Control ctrl = block.getControl();
            for (Var arg : ctrl.insn.args) {
                getOrCreateUses(arg).add(ctrl.insn);
            }
        }

        for (BasicBlock block : func.blocks) {
            for (
                    Node<Effect> insert = block.getExtOrThrow(LIST_EXT).last;
                    insert != null;
                    insert = insert.prev
            ) {
                if (insert.value != null && insert.value.insn().op.key == CommonOps.PHI) {
                    break;
                }

                Node<Use> stackTop = pushOperands(insert.value == null
                        ? block.getControl().insn
                        : insert.value.insn());
                while (stackTop != null) {
                    Use use = stackTop.value;
                    Var reg = use.getReg();
                    stackTop = stackTop.prev;

                    Effect def = reg.getExtOrThrow(CommonExts.ASSIGNED_AT);
                    BasicBlock defBlock = def.getExtOrThrow(CommonExts.OWNING_BLOCK);
                    boolean sameBlock = defBlock == block;
                    boolean canMove = sameBlock && isSafeToMove(def, insert) &&
                            !regOnStack(stackTop, reg);
                    if (canMove && reg.getExtOrThrow(USES_EXT).size() == 1) {
                        insert = moveForSingleUse(reg, def, insert);
                    } else if (shouldRematerialize(def)) {
                        insert = rematerializeCheapDef(use, def, insert, block, func);
                        // } else if (canMove && oneUseDominatesOtherUses(reg, def, insert)) {
                        //     insert = moveAndTeeForMultiUse(reg, def, insert);
                    } else {
                        // didn't stackify, so just materialize the load and move on
                        insert = emitLoad(use, insert, block, func);
                        continue;
                    }

                    Node<Use> operands = pushOperands(insert.value.insn());
                    if (stackTop != null) {
                        stackTop.linkAfter(operands);
                    }
                    if (operands != null) {
                        stackTop = operands;
                    }
                }
            }
        }

        for (BasicBlock block : func.blocks) {
            LinkedList<Effect> list = block.getExtOrThrow(LIST_EXT);
            block.removeExt(LIST_EXT);

            List<Effect> effects = block.getEffects();
            effects.clear();
            for (Node<Effect> node : list) {
                if (node.value == null) continue;
                node.value.removeExt(NODE_EXT);
                for (Var var : node.value.getAssignsTo()) {
                    var.removeExt(USES_EXT);
                }
                block.addEffect(node.value);
            }
        }

        if (CHECK_STACK_INTEGRITY) checkStackIntegrity(func);
    }

    private void checkStackIntegrity(Function func) {
        Deque<Var> stack = new ArrayDeque<>();
        for (BasicBlock block : func.blocks) {
            for (Effect effect : block.getEffects()) {
                Insn insn = effect.insn();
                checkInsn(stack, insn);
                for (Var assigned : effect.getAssignsTo()) {
                    if (assigned.getExt(CommonExts.STACKIFIED).orElse(false)) {
                        stack.addLast(assigned);
                    }
                }
            }
            checkInsn(stack, block.getControl().insn);
            if (!stack.isEmpty()) {
                throw new IllegalStateException("unmatched stack pushes");
            }
        }
    }

    private void checkInsn(Deque<Var> stack, Insn insn) {
        List<Var> args = insn.args;
        ListIterator<Var> iter = args.listIterator(args.size());
        while (iter.hasPrevious()) {
            Var arg = iter.previous();
            if (arg.getExt(CommonExts.STACKIFIED).orElse(false)) {
                if (stack.pollLast() != arg) {
                    throw new IllegalStateException("unmatched stack pop");
                }
            }
        }
    }

    @NotNull
    private Set<Insn> getOrCreateUses(Var arg) {
        return arg.getExt(USES_EXT).orElseGet(() -> {
            HashSet<Insn> set = new HashSet<>();
            arg.attachExt(USES_EXT, set);
            return set;
        });
    }

    private boolean isSafeToMove(Effect def, Node<Effect> insert) {
        // if there are multiple assignments it gets too annoying...
        if (def.getAssignsTo().size() > 1) return false;

        // must be at the start of the block
        if (def.insn().op.key == CommonOps.PHI) return false;

        // if the instruction is pure we can always move it
        if (def.getExt(CommonExts.IS_PURE).orElse(false)) return true;

        // same if all the intervening instructions are pure
        Node<Effect>
                defN = def.getExtOrThrow(NODE_EXT),
                revIt = insert;
        for (revIt = revIt.prev; revIt != defN; revIt = revIt.prev) {
            if (!def.getExt(CommonExts.IS_PURE).orElse(false)) return false;
        }

        return true;
    }

    private Node<Effect> rematerializeCheapDef(Use use, Effect def, Node<Effect> insert, BasicBlock block, Function func) {
        Var remat = func.newVar("remat");
        remat.attachExt(CommonExts.STACKIFIED, true);

        Effect reDef = def.insn().op.insn(def.insn().args).assignTo(remat);
        for (Var arg : reDef.insn().args) {
            arg.getExtOrThrow(USES_EXT).add(reDef.insn());
        }
        Set<Insn> regUses = use.getReg().getExtOrThrow(USES_EXT);
        regUses.remove(use.insn);
        use.setReg(remat);

        return insertBefore(insert, block, reDef);
    }

    private boolean shouldRematerialize(Effect def) {
        return def.getExt(CommonExts.IS_TRIVIAL).orElse(false);
    }

    private Node<Effect> moveForSingleUse(Var reg, Effect def, Node<Effect> insert) {
        Node<Effect> defNode = def.getExtOrThrow(NODE_EXT);
        defNode.remove();

        def.attachExt(CommonExts.IS_PURE, false);
        reg.attachExt(CommonExts.STACKIFIED, true);
        return insert.insertBefore(def);
    }

    private Node<Effect> emitLoad(Use use, Node<Effect> insert, BasicBlock block, Function func) {
        Var stacked = func.newVar("stacked");
        stacked.attachExt(CommonExts.STACKIFIED, true);

        Var reg = use.getReg();
        Effect load = CommonOps.IDENTITY.insn(reg).assignTo(stacked);
        Set<Insn> regUses = reg.getExtOrThrow(USES_EXT);
        regUses.remove(use.insn);
        regUses.add(load.insn());
        use.setReg(stacked);

        return insertBefore(insert, block, load);
    }

    @NotNull
    private Node<Effect> insertBefore(Node<Effect> insert, BasicBlock block, Effect effect) {
        effect.attachExt(CommonExts.OWNING_BLOCK, block);
        effect.attachExt(CommonExts.IS_PURE, false);
        insert = insert.insertBefore(effect);
        return insert;
    }

    private Node<Use> pushOperands(Insn insn) {
        ListIterator<Var> iter = insn.args.listIterator();
        return LinkedList.fromIterator(new Iterator<Use>() {
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public Use next() {
                iter.next();
                return new Use(insn, iter.previousIndex());
            }
        }).last;
    }

    private boolean regOnStack(Node<Use> haystack, Var needle) {
        NodeIterator<Use> iter = new NodeIterator<>(true, haystack);
        while (iter.hasNext()) {
            if (iter.next().value.getReg() == needle) return true;
        }
        return false;
    }
}
