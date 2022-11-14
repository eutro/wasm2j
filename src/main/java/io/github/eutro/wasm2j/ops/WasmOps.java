package io.github.eutro.wasm2j.ops;

import io.github.eutro.jwasm.tree.TypeNode;
import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Control;
import io.github.eutro.wasm2j.ssa.Var;
import io.github.eutro.wasm2j.util.Disassembler;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.ByteBuffer;

public class WasmOps {
    // break convention is that the last target is the fallback, while the first targets are taken conditionally
    public static final Op BR_IF = new SimpleOpKey("br_if").create();
    public static final Op BR_TABLE = new SimpleOpKey("br_table").create();

    public static final UnaryOpKey</* var */ Integer> GLOBAL_REF = new UnaryOpKey<>("global.ref");
    public static final UnaryOpKey</* var */ Integer> GLOBAL_SET = new UnaryOpKey<>("global.set");

    public static final UnaryOpKey<WithMemArg<StoreType>> MEM_STORE = new UnaryOpKey<>("mem.set");
    public static final UnaryOpKey<WithMemArg<DerefType>> MEM_LOAD = new UnaryOpKey<>("mem.load");

    public static final SimpleOpKey MEM_SIZE = new SimpleOpKey("mem.length");
    public static final SimpleOpKey MEM_GROW = new SimpleOpKey("mem.grow");

    public static final UnaryOpKey</* table */ Integer> TABLE_STORE = new UnaryOpKey<>("table.set");
    public static final UnaryOpKey</* table */ Integer> TABLE_REF = new UnaryOpKey<>("table.ref");
    public static final UnaryOpKey</* table */ Integer> TABLE_SIZE = new UnaryOpKey<>("table.size");
    public static final UnaryOpKey</* table */ Integer> TABLE_GROW = new UnaryOpKey<>("table.grow");

    public static final UnaryOpKey</* func */ Integer> FUNC_REF = new UnaryOpKey<>("func.ref");
    public static final UnaryOpKey<CallType> CALL = new UnaryOpKey<>("call");
    public static final UnaryOpKey<TypeNode> CALL_INDIRECT = new UnaryOpKey<>("call_indirect");

    public static final UnaryOpKey</* type */ Byte> ZEROINIT = new UnaryOpKey<>("zeroinit");

    public static final SimpleOpKey IS_NULL = new SimpleOpKey("is_null");

    public static final SimpleOpKey SELECT = new SimpleOpKey("select"); /* cond ift iff */

    public static final UnaryOpKey<OperatorType> OPERATOR = new UnaryOpKey<>("op");

    public static Control brIf(Var cond, BasicBlock thenB, BasicBlock elseB) {
        return BR_IF.insn(cond).jumpsTo(thenB, elseB);
    }

    static {
        for (OpKey key : new OpKey[]{
                GLOBAL_REF,
                ZEROINIT,
                FUNC_REF,
                MEM_SIZE,
                IS_NULL,
                SELECT
        }) {
            key.attachExt(CommonExts.IS_PURE, true);
        }
    }

    public static class CallType {
        public int func;
        public TypeNode type;

        public CallType(int func, TypeNode type) {
            this.func = func;
            this.type = type;
        }

        @Override
        public String toString() {
            return "func=" + func;
        }
    }

    public static class WithMemArg<T> {
        public T value;
        public int offset;

        public WithMemArg(T value, int offset) {
            this.value = value;
            this.offset = offset;
        }

        @Override
        public String toString() {
            return "offset=" + offset + " " + value;
        }
    }

    public static class DerefType {
        public byte outType;
        public LoadType load;
        public ExtType ext;

        public DerefType(byte outType, LoadType load, ExtType ext) {
            this.outType = outType;
            this.load = load;
            this.ext = ext;
        }

        @Override
        public String toString() {
            return String.format("(%s) load %s", ext, load);
        }

        public enum LoadType {
            I8("get", "(I)B"),
            I16("getShort", "(I)S"),
            I32("getInt", "(I)I"),
            I64("getLong", "(I)J"),
            F32("getFloat", "(I)F"),
            F64("getDouble", "(I)D"),
            ;

            public final String funcName;
            public final String desc;

            LoadType(String funcName, String desc) {
                this.funcName = funcName;
                this.desc = desc;
            }
        }

        public enum ExtType {
            NOEXT,

            S8_32(/* noop */),
            S16_32(/* noop */),
            S8_64(Opcodes.I2L),
            S16_64(Opcodes.I2L),
            S32_64(Opcodes.I2L),

            U8_32(Byte.class, "toUnsignedInt", "(B)I"),
            U16_32(Short.class, "toUnsignedInt", "(S)I"),
            U8_64(Byte.class, "toUnsignedLong", "(B)J"),
            U16_64(Short.class, "toUnsignedLong", "(S)J"),
            U32_64(Integer.class, "toUnsignedLong", "(I)J"),
            ;

            public final InsnList insns;

            ExtType(int opcode) {
                this(new InsnNode(opcode));
            }

            ExtType(Class<?> cls, String name, String desc) {
                this(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(cls),
                        name,
                        desc,
                        false
                ));
            }

            ExtType(AbstractInsnNode... insns) {
                this.insns = new InsnList();
                for (AbstractInsnNode insn : insns) {
                    this.insns.add(insn);
                }
            }
        }
    }

    public enum StoreType {
        F32("putFloat", "(IF)Ljava/nio/ByteBuffer;"),
        F64("putDouble", "(ID)Ljava/nio/ByteBuffer;"),

        I32_8("put", "(IB)Ljava/nio/ByteBuffer;"),
        I32_16("putShort", "(IS)Ljava/nio/ByteBuffer;"),
        I32("putInt", "(II)Ljava/nio/ByteBuffer;"),

        I64_8(Opcodes.L2I, "put", "(IB)Ljava/nio/ByteBuffer;"),
        I64_16(Opcodes.L2I, "putShort", "(IS)Ljava/nio/ByteBuffer;"),
        I64_32(Opcodes.L2I, "putInt", "(II)Ljava/nio/ByteBuffer;"),
        I64("putLong", "(IJ)Ljava/nio/ByteBuffer;"),
        ;

        public final InsnList insns = new InsnList();

        StoreType(int ext, String name, String desc) {
            this(name, desc);
            insns.insert(new InsnNode(ext));
        }

        StoreType(String name, String desc) {
            insns.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    Type.getInternalName(ByteBuffer.class),
                    name,
                    desc,
                    false
            ));
        }
    }

    public static class OperatorType {
        public byte op;
        public int intOp;
        public byte returnType;

        public OperatorType(byte op, int intOp, byte returnType) {
            this.op = op;
            this.intOp = intOp;
            this.returnType = returnType;
        }

        @Override
        public String toString() {
            return Disassembler.getWasmMnemonic(op, intOp);
        }
    }
}
