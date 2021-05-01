package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.tree.TypeNode;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

import java.util.LinkedList;
import java.util.List;

import static io.github.eutro.wasm2j.Util.makeList;
import static org.objectweb.asm.Opcodes.*;

class Context extends GeneratorAdapter {
    public Externs externs;
    public final TypeNode funcType;
    public final FieldNode[] passiveElems;
    public final FieldNode[] passiveDatas;

    private final Type[] localTypes;
    private final LinkedList<Block> blocks = new LinkedList<>();
    private final int[] localIndeces;
    private final JumpTrackingVisitor aa;
    private final List<TypeNode> funcTypes;

    public Context(JumpTrackingVisitor mv,
                   int access,
                   String name,
                   String desc,
                   List<TypeNode> funcTypes,
                   TypeNode funcType,
                   Externs externs,
                   int[] localIndeces,
                   Type[] localTypes,
                   FieldNode[] passiveElems,
                   FieldNode[] passiveDatas) {
        super(ASM9, mv, access, name, desc);
        aa = mv;
        this.funcTypes = funcTypes;
        this.funcType = funcType;
        this.localIndeces = localIndeces;
        this.externs = externs;
        this.localTypes = localTypes;
        this.passiveElems = passiveElems;
        this.passiveDatas = passiveDatas;
    }

    public Type jfuncType(int index) {
        return Types.methodDesc(funcType(index));
    }

    public TypeNode funcType(int index) {
        return funcTypes.get(index);
    }

    public Type localType(int local) {
        return localTypes[local];
    }

    public Context compress(byte[] types) {
        if (types.length > 1) {
            Type arType = Types.returnType(types);
            Type elType = arType.getElementType();
            push(types.length);
            newArray(elType);
            int local = newLocal(arType);
            storeLocal(local);
            for (int i = types.length - 1; i >= 0; i--) {
                Type thisElType = Types.toJava(types[i]);
                if (!thisElType.equals(elType)) {
                    if (thisElType.getSort() != Type.OBJECT) {
                        valueOf(thisElType);
                    } else {
                        checkCast(elType);
                    }
                }
                loadLocal(local);
                swap(elType, arType);
                push(i);
                swap(elType, Type.INT_TYPE);
                arrayStore(elType);
            }
        }
        return this;
    }

    public Context decompress(byte[] types) {
        if (types.length > 1) {
            Type arType = Types.returnType(types);
            Type elType = arType.getElementType();
            for (int i = 0; i < types.length; i++) {
                if (i + 1 != types.length) {
                    dup();
                }
                push(i);
                arrayLoad(elType);
                byte type = types[i];
                Type thisElType = Types.toJava(type);
                if (!elType.equals(thisElType)) {
                    unbox(thisElType);
                }
                if (i + 1 != types.length) {
                    swap(arType, thisElType);
                }
            }
        }
        return this;
    }

    public int localIndex(int local) {
        return localIndeces[local];
    }

    public void pushBlock(Block b) {
        blocks.push(b);
    }

    public Block peekBlock() {
        return blocks.peek();
    }

    public Block popBlock() {
        return blocks.pop();
    }

    public Label getLabel(int label) {
        return blocks.get(label).label();
    }

    public FrameNode getFrame() {
        return aa.getFrame();
    }

    public Context addInsns(org.objectweb.asm.tree.AbstractInsnNode... insns) {
        for (org.objectweb.asm.tree.AbstractInsnNode insn : insns) {
            insn.accept(this);
        }
        return this;
    }

    public void ifThenElse(int opcode, InsnList ifn, InsnList ifj) {
        Label elsl = new Label();
        Label endl = new Label();
        visitJumpInsn(opcode, elsl);
        ifn.accept(this);
        goTo(endl);
        mark(elsl);
        ifj.accept(this);
        mark(endl);
    }
}
