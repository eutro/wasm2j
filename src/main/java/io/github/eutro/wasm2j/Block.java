package io.github.eutro.wasm2j;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

abstract class Block {
    public final int type;

    public Block(int type) {
        this.type = type;
    }

    abstract void end(MethodVisitor mv);

    public abstract Label label();

    public static class If extends Block {
        public Label elseLabel = new Label();
        public Label endLabel = null;

        public If(int type) {
            super(type);
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
    }

    public static class BBlock extends Block {
        public Label label = new Label();

        public BBlock(int type) {
            super(type);
        }

        @Override
        void end(MethodVisitor mv) {
            mv.visitLabel(label());
        }

        @Override
        public Label label() {
            return label;
        }
    }

    public static class Loop extends Block {
        private final Label label;

        public Loop(int type, Label label) {
            super(type);
            this.label = label;
        }

        @Override
        void end(MethodVisitor mv) {
        }

        @Override
        public Label label() {
            return label;
        }
    }
}
