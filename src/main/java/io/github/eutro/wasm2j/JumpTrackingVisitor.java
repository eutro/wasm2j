package io.github.eutro.wasm2j;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.FrameNode;

import java.util.HashMap;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

class JumpTrackingVisitor extends AnalyzerAdapter {
    private final Map<Label, FrameNode> jumps = new HashMap<>();

    protected JumpTrackingVisitor(String owner,
                                  int access,
                                  String name,
                                  String descriptor,
                                  MethodVisitor methodVisitor) {
        super(ASM9, owner, access, name, descriptor, methodVisitor);
    }

    private void associate(Label label, FrameNode with) {
        jumps.put(label, with);
    }

    public FrameNode getFrame() {
        if (stack == null) throw new IllegalStateException("Unknown frame");
        return new FrameNode(F_NEW, locals.size(), locals.toArray(), stack.size(), stack.toArray());
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        if (opcode != GOTO) super.visitJumpInsn(opcode, label);
        associate(label, getFrame());
        if (opcode == GOTO) super.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        FrameNode frame = getFrame();
        frame.stack.remove(frame.stack.size() - 1);
        associate(dflt, frame);
        for (Label label : labels) {
            associate(label, frame);
        }
        super.visitTableSwitchInsn(min, max, dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        FrameNode frame = getFrame();
        frame.stack.remove(frame.stack.size() - 1);
        associate(dflt, frame);
        for (Label label : labels) {
            associate(label, frame);
        }
        super.visitLookupSwitchInsn(dflt, keys, labels);
    }

    @Override
    public void visitLabel(Label label) {
        super.visitLabel(label);
        if (jumps.containsKey(label)) {
            FrameNode frame = jumps.remove(label);
            if (stack == null) {
                frame.accept(this);
            }
        }
    }
}
