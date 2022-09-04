package io.github.eutro.wasm2j.ops;

import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.jwasm.tree.TypeNode;
import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Control;
import io.github.eutro.wasm2j.ssa.Var;

public class WasmOps {
    public static final Op BR_IF = new SimpleOpKey("br_if").create();
    public static final Op BR_TABLE = new SimpleOpKey("br_table").create();

    public static final UnaryOpKey</* var */ Integer> GLOBAL_REF = new UnaryOpKey<>("global.ref");
    public static final UnaryOpKey</* var */ Integer> GLOBAL_SET = new UnaryOpKey<>("global.set");

    public static final UnaryOpKey</* bytes */ Integer> MEM_STORE = new UnaryOpKey<>("mem.set");
    public static final UnaryOpKey<DerefType> MEM_LOAD = new UnaryOpKey<>("mem.load");

    public static final SimpleOpKey MEM_SIZE = new SimpleOpKey("mem.length");
    public static final SimpleOpKey MEM_GROW = new SimpleOpKey("mem.grow");

    public static final UnaryOpKey</* table */ Integer> TABLE_STORE = new UnaryOpKey<>("table.set");
    public static final UnaryOpKey</* table */ Integer> TABLE_REF = new UnaryOpKey<>("table.ref");

    public static final UnaryOpKey</* func */ Integer> FUNC_REF = new UnaryOpKey<>("func.ref");
    public static final UnaryOpKey<CallType> CALL = new UnaryOpKey<>("call");
    public static final UnaryOpKey<TypeNode> CALL_INDIRECT = new UnaryOpKey<>("call_indirect");

    public static final UnaryOpKey</* type */ Byte> ZEROINIT = new UnaryOpKey<>("zeroinit");

    public static final SimpleOpKey IS_NULL = new SimpleOpKey("is_null");

    public static final SimpleOpKey SELECT = new SimpleOpKey("select");

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

    public static class DerefType {
        public byte outType;
        public int loadBytes;
        public boolean extendUnsigned;

        public DerefType(byte outType, int loadBytes, boolean extendUnsigned) {
            this.outType = outType;
            this.loadBytes = loadBytes;
            this.extendUnsigned = extendUnsigned;
        }

        @Override
        public String toString() {
            return String.format("ty: 0x%02x, len: %d, %sext", outType, loadBytes, extendUnsigned ? "z" : "s");
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
            return op == Opcodes.INSN_PREFIX ? Integer.toString(intOp) : String.format("0x%02x", op);
        }
    }
}
