package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.tree.TypeNode;
import org.jetbrains.annotations.Contract;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

import java.util.LinkedList;
import java.util.List;

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

    @Contract(pure = true)
    public Type jfuncType(int index) {
        return Types.methodDesc(funcType(index));
    }

    @Contract(pure = true)
    public TypeNode funcType(int index) {
        return funcTypes.get(index);
    }

    @Contract(pure = true)
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

    public List<Object> getCurrentStack() {
        return aa.stack;
    }

    public InsnList popDownToStack(
            List<Object> currentStack,
            List<Object> targetStack
    ) {
        if (targetStack.size() > currentStack.size()) {
            throw new IllegalStateException();
        }
        if (!targetStack.equals(currentStack.subList(0, targetStack.size()))) {
            throw new IllegalStateException();
        }
        int csi = currentStack.size();
        InsnList insns = new InsnList();
        while (csi > targetStack.size()) {
            if (currentStack.get(csi - 1) == TOP) {
                insns.add(new InsnNode(POP2));
                csi -= 2;
            } else {
                insns.add(new InsnNode(POP));
                csi -= 1;
            }
        }
        return insns;
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

    public Block getBlock(int label) {
        return blocks.get(label);
    }

    public void goTo(Block block) {
        visitJumpInsn(GOTO, block);
    }

    public void visitJumpInsn(int opcode, Block block) {
        if (needsPopping(opcode, block)) {
            int temp = 0;
            if (opcode != GOTO) {
                temp = newLocal(Type.INT_TYPE);
                storeLocal(temp);
            }
            popDownToStack(
                    getCurrentStack(),
                    block.stackAtLabel()
            ).accept(this);
            if (opcode != GOTO) {
                loadLocal(temp);
            }
        }
        visitJumpInsn(opcode, block.label());
    }

    public boolean needsPopping(int opcode, Block block) {
        List<Object> stackAtLabel = block.stackAtLabel();
        if (opcode == GOTO) {
            return !stackAtLabel.equals(getCurrentStack());
        } else {
            List<Object> cs = getCurrentStack();
            return !stackAtLabel.equals(cs.subList(0, cs.size() - 1));
        }
    }

    public boolean isTopDouble() {
        return aa.isTopDouble();
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
