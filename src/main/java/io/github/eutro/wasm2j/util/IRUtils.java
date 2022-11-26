package io.github.eutro.wasm2j.util;

import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.Op;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnNode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static io.github.eutro.wasm2j.ext.CommonExts.markPure;
import static io.github.eutro.wasm2j.ops.JavaOps.JumpType.IFEQ;
import static org.objectweb.asm.Opcodes.IADD;

public class IRUtils {
    public static final JavaExts.JavaClass BYTE_BUFFER_CLASS = new JavaExts.JavaClass(Type.getInternalName(ByteBuffer.class));
    public static final JavaExts.JavaClass METHOD_HANDLE_CLASS = new JavaExts.JavaClass(Type.getInternalName(MethodHandle.class));
    public static final JavaExts.JavaClass MTY_CLASS = new JavaExts.JavaClass(Type.getInternalName(MethodType.class));
    public static final JavaExts.JavaMethod MTY_METHOD_TYPE = new JavaExts.JavaMethod(
            MTY_CLASS,
            "methodType",
            Type.getMethodType(
                    Type.getType(MethodType.class),
                    Type.getType(Class.class),
                    Type.getType(Class[].class)
            ).getDescriptor(),
            JavaExts.JavaMethod.Kind.STATIC
    );

    public static Var getThis(IRBuilder ib) {
        return ib.insert(JavaOps.THIS.insn(), "this");
    }

    public static Insn loadClass(Type ty) {
        switch (ty.getSort()) {
            case Type.OBJECT:
            case Type.ARRAY:
                return CommonOps.constant(ty);
            default: {
                Class<?> boxedTy;
                switch (ty.getSort()) {
                    // @formatter:off
                    case Type.VOID: boxedTy = Void.class; break;
                    case Type.BOOLEAN: boxedTy = Boolean.class; break;
                    case Type.CHAR: boxedTy = Character.class; break;
                    case Type.BYTE: boxedTy = Byte.class; break;
                    case Type.SHORT: boxedTy = Short.class; break;
                    case Type.INT: boxedTy = Integer.class; break;
                    case Type.FLOAT: boxedTy = Float.class; break;
                    case Type.LONG: boxedTy = Long.class; break;
                    case Type.DOUBLE: boxedTy = Double.class; break;
                    // @formatter:on
                    default:
                        throw new IllegalArgumentException();
                }
                return JavaOps.GET_FIELD.create(new JavaExts.JavaField(
                        new JavaExts.JavaClass(Type.getInternalName(boxedTy)),
                        "TYPE",
                        Type.getDescriptor(Class.class),
                        true
                )).insn();
            }
        }
    }

    public static Var getAddr(IRBuilder ib, WasmOps.WithMemArg<?> wmArg, Var ptr) {
        return wmArg.offset == 0
                ? ptr
                : ib.insert(markPure(JavaOps.insns(new InsnNode(Opcodes.IADD)))
                        .insn(ptr,
                                ib.insert(CommonOps.constant(wmArg.offset),
                                        "offset")),
                "addr");
    }

    public static void lenLoop(IRBuilder ib, Var[] toInc, Var len, Consumer<Var[]> f) {
        BasicBlock srcBlock = ib.getBlock();
        BasicBlock condBlock = ib.func.newBb();
        BasicBlock mvBlock = ib.func.newBb();
        BasicBlock endBlock = ib.func.newBb();
        ib.insertCtrl(Control.br(condBlock));
        ib.setBlock(condBlock);

        List<BasicBlock> preds = Arrays.asList(srcBlock, mvBlock);
        Var[] allLoopVars = Arrays.copyOf(toInc, toInc.length + 1);
        allLoopVars[toInc.length] = len;
        Var[] loopSuccs = new Var[allLoopVars.length];
        Var[] loopVs = new Var[allLoopVars.length];
        for (int i = 0; i < allLoopVars.length; i++) {
            Var succ = ib.func.newVar("i");
            loopSuccs[i] = succ;
            loopVs[i] = ib.insert(CommonOps.PHI.create(preds)
                    .insn(allLoopVars[i], succ), "i");
        }
        Var lenV = loopVs[toInc.length];
        ib.insertCtrl(JavaOps.BR_COND.create(IFEQ).insn(lenV).jumpsTo(endBlock, mvBlock));
        ib.setBlock(mvBlock);

        f.accept(loopVs);

        Op add = JavaOps.insns(new InsnNode(IADD));
        for (int i = 0; i < allLoopVars.length; i++) {
            ib.insert(add.insn(loopVs[i],
                    ib.insert(CommonOps.constant(i == toInc.length ? -1 : 1),
                            "i")),
                    loopSuccs[i]);
        }

        ib.insertCtrl(Control.br(condBlock));
        preds.set(1, ib.getBlock());

        ib.setBlock(endBlock);
    }

    public static void trapWhen(IRBuilder ib, Insn insn, String msg) {
        BasicBlock errBb = ib.func.newBb();
        BasicBlock contBb = ib.func.newBb();
        ib.insertCtrl(insn.jumpsTo(errBb, contBb));
        ib.setBlock(errBb);
        ib.insertCtrl(CommonOps.TRAP.create(msg).insn().jumpsTo());
        ib.setBlock(contBb);
    }
}
