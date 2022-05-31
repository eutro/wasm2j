package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.jwasm.tree.TypeNode;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.objectweb.asm.Opcodes.*;

abstract class Block {
    public final int type;

    public final List<Object> stackAtEntry;
    public final List<Object> stackAtExit;

    public Block(Context ctx, int type) {
        this.type = type;
        stackAtEntry = new ArrayList<>(ctx.getCurrentStack());
        if (this instanceof If) {
            stackAtEntry.remove(stackAtEntry.size() - 1);
        }
        if (type == Opcodes.EMPTY_TYPE) {
            stackAtExit = stackAtEntry;
        } else {
            stackAtExit = new ArrayList<>(stackAtEntry);
            switch (type) {
                // FIXME: block type encoding is slightly harder than this
                case Opcodes.I32:
                case Opcodes.I64:
                case Opcodes.F32:
                case Opcodes.F64:
                case Opcodes.FUNCREF:
                case Opcodes.EXTERNREF:
                    addToStack(stackAtExit, (byte) type);
                    break;
                default:
                    TypeNode funcType = ctx.funcType(type);
                    for (byte argTy : funcType.params) {
                        removeFromStack(stackAtExit, argTy);
                    }
                    for (byte retTy : funcType.returns) {
                        addToStack(stackAtExit, retTy);
                    }
            }
        }
    }

    private void removeFromStack(List<Object> stack, byte type) {
        List<Object> stackRepr = new ArrayList<>();
        addToStack(stackRepr, type);
        while (!stackRepr.isEmpty()) {
            Object expected = stackRepr.remove(stackRepr.size() - 1);
            Object actual = stack.remove(stack.size() - 1);
            if (!Objects.equals(expected, actual)) {
                throw new IllegalStateException("Malformed stack");
            }
        }
    }

    private void addToStack(List<Object> stack, byte type) {
        switch (type) {
            case Opcodes.I32:
                stack.add(INTEGER);
                break;
            case Opcodes.I64:
                stack.add(LONG);
                stack.add(TOP);
                break;
            case Opcodes.F32:
                stack.add(FLOAT);
                break;
            case Opcodes.F64:
                stack.add(DOUBLE);
                stack.add(TOP);
                break;
            case Opcodes.FUNCREF:
            case Opcodes.EXTERNREF:
                stack.add(Types.toJava(type).getDescriptor());
                break;
        }
    }

    abstract List<Object> stackAtLabel();

    abstract void end(MethodVisitor mv);

    public abstract Label label();

    public static class If extends Block {
        public Label elseLabel = new Label();
        public Label endLabel = null;

        public If(Context ctx, int type) {
            super(ctx, type);
        }

        public Label endLabel() {
            if (endLabel != null) throw new IllegalStateException();
            return endLabel = new Label();
        }

        @Override
        public void end(MethodVisitor mv) {
            mv.visitLabel(label());
        }

        @Override
        public Label label() {
            return endLabel == null ? elseLabel : endLabel;
        }

        @Override
        List<Object> stackAtLabel() {
            return stackAtExit;
        }
    }

    public static class BBlock extends Block {
        public Label label = new Label();

        public BBlock(Context ctx, int type) {
            super(ctx, type);
        }

        @Override
        void end(MethodVisitor mv) {
            mv.visitLabel(label());
        }

        @Override
        public Label label() {
            return label;
        }

        @Override
        List<Object> stackAtLabel() {
            return stackAtExit;
        }
    }

    public static class Loop extends Block {
        private final Label label;

        public Loop(Context ctx, int type, Label label) {
            super(ctx, type);
            this.label = label;
        }

        @Override
        void end(MethodVisitor mv) {
        }

        @Override
        public Label label() {
            return label;
        }

        @Override
        List<Object> stackAtLabel() {
            return stackAtEntry;
        }
    }
}
