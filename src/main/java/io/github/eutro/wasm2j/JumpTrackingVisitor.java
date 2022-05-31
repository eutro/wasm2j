package io.github.eutro.wasm2j;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.util.CheckMethodAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

class JumpTrackingVisitor extends AnalyzerAdapter {
    private final Map<Label, FrameNode> jumps = new HashMap<>();
    private @Nullable FrameNode nextFrame;

    protected JumpTrackingVisitor(String owner,
                                  int access,
                                  String name,
                                  String descriptor,
                                  MethodVisitor methodVisitor) {
        super(ASM9, owner, access, name, descriptor, methodVisitor);
        visitCode();
    }

    private void associate(Label label, FrameNode with) {
        jumps.put(label, with);
    }

    public FrameNode getFrame() {
        if (stack == null) throw new IllegalStateException("Unknown frame");
        // NB: visitFrame has a single element per long/double; AnalyzerAdapter keeps 2
        Object[] localsS = removeTops(locals);
        Object[] stackS = removeTops(stack);
        return new FrameNode(F_NEW, localsS.length, localsS, stackS.length, stackS);
    }

    private static Object[] removeTops(List<Object> input) {
        List<Object> list = new ArrayList<>();
        for (Object it : input) {
            if (it != TOP) {
                list.add(it);
            }
        }
        return list.toArray();
    }

    public boolean isTopDouble() {
        return stack.get(stack.size() - 1) == TOP;
    }

    private void maybeSetFrame() {
        if (nextFrame != null) {
            nextFrame.accept(this);
            nextFrame = null;
        }
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        maybeSetFrame();

        if (opcode != GOTO) super.visitJumpInsn(opcode, label);
        associate(label, getFrame());
        if (opcode == GOTO) super.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        maybeSetFrame();

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
        maybeSetFrame();

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
            nextFrame = jumps.remove(label);
        }
    }

    @Override
    public void visitInsn(int opcode) {
        maybeSetFrame();
        super.visitInsn(opcode);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        maybeSetFrame();
        super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        maybeSetFrame();
        super.visitVarInsn(opcode, var);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        maybeSetFrame();
        super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        maybeSetFrame();
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitMethodInsn(int opcodeAndSource, String owner, String name, String descriptor, boolean isInterface) {
        maybeSetFrame();
        super.visitMethodInsn(opcodeAndSource, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        maybeSetFrame();
        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
    }

    @Override
    public void visitLdcInsn(Object value) {
        maybeSetFrame();
        super.visitLdcInsn(value);
    }

    @Override
    public void visitIincInsn(int var, int increment) {
        maybeSetFrame();
        super.visitIincInsn(var, increment);
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        maybeSetFrame();
        super.visitMultiANewArrayInsn(descriptor, numDimensions);
    }
}
