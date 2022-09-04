package io.github.eutro.wasm2j.passes.convert;

import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.Op;
import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.ssa.*;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaToJir implements IRPass<MethodNode, Function> {
    public static final JavaToJir INSTANCE = new JavaToJir();

    @Override
    public Function run(MethodNode method) {
        Function func = new Function();
        func.attachExt(JavaExts.FUNCTION_DESCRIPTOR, method.desc);
        new Converter(func).convert(method);
        return func;
    }

    private static class Converter {
        public final Function func;
        public BasicBlock bb;

        private final Map<Label, BasicBlock> labelMap = new HashMap<>();
        public final List<Var> stackVars = new ArrayList<>();

        public final List<Var> vars = new ArrayList<>();
        public int stackHeight = 0;

        private Converter(Function func) {
            this.func = func;
            bb = func.newBb();
        }

        public void convert(MethodNode method) {
            Type[] paramTypes = Type.getArgumentTypes(method.desc);
            for (int i = 0; i < paramTypes.length; i++) {
                Var paramVar = func.newVar("param" + i);
                bb.addEffect(CommonOps.ARG.create(i).insn().assignTo(paramVar));
                vars.add(paramVar);
                if (paramTypes[i].getSize() > 1) {
                    vars.add(null);
                }
            }
            for (AbstractInsnNode insn : method.instructions) {
                execute(insn);
            }
        }

        Var pushVar() {
            if (stackHeight == -1) throw new IllegalStateException("unknown stack");
            if (stackHeight < stackVars.size()) {
                return stackVars.get(stackHeight++);
            } else {
                Var stackVar = func.newVar("stack" + stackHeight++);
                stackVars.add(stackVar);
                return stackVar;
            }
        }

        Var peekVar(int depth) {
            if (stackHeight == -1) throw new IllegalStateException("unknown stack");
            return stackVars.get(stackHeight - depth - 1);
        }

        Var peekVar() {
            return peekVar(0);
        }

        Var popVar() {
            Var v = peekVar();
            --stackHeight;
            return v;
        }

        private Var[] popVars(int n) {
            Var[] vars = new Var[n];
            for (int i = n - 1; i >= 0; i--) {
                vars[i] = popVar();
            }
            return vars;
        }

        private Var getVar(int var) {
            while (vars.size() <= var) {
                vars.add(func.newVar("var" + vars.size()));
            }
            return vars.get(var);
        }

        private Op insnOp(AbstractInsnNode insn) {
            InsnList il = new InsnList();
            il.add(insn.clone(new HashMap<>()));
            return JavaOps.INSNS.create(il);
        }

        private BasicBlock getBlock(Label label) {
            return labelMap.computeIfAbsent(label, $ -> func.newBb());
        }

        private void setLabel(Label label) {
            if (labelMap.containsKey(label)) {
                BasicBlock target = labelMap.get(label);
                if (bb.getControl() == null) {
                    bb.setControl(Control.br(target));
                }
                bb = target;
            } else if (bb.getEffects().isEmpty()) {
                labelMap.put(label, bb);
            } else {
                BasicBlock newBb = func.newBb();
                if (bb.getControl() == null) {
                    bb.setControl(Control.br(newBb));
                }
                bb = newBb;
                labelMap.put(label, newBb);
            }
        }

        private Effect identity(Var from, Var to) {
            return CommonOps.IDENTITY.insn(from).assignTo(to);
        }

        private void execute(AbstractInsnNode insn) {
            int opcode = insn.getOpcode();
            if (opcode == Opcodes.JSR || opcode == Opcodes.RET) {
                throw new IllegalArgumentException("JSR/RET are not supported");
            }
            switch (opcode) {
                case -1:
                    if (insn instanceof LabelNode) {
                        Label label = ((LabelNode) insn).getLabel();
                        setLabel(label);
                    } else if (insn instanceof FrameNode) {
                        List<Object> stack = ((FrameNode) insn).stack;
                        stackHeight = stack == null ? 0 : stack.size();
                    }
                    break;
                case Opcodes.NOP:
                    break;
                case Opcodes.INEG:
                case Opcodes.LNEG:
                case Opcodes.FNEG:
                case Opcodes.DNEG:
                case Opcodes.I2B:
                case Opcodes.I2C:
                case Opcodes.I2S:
                case Opcodes.LALOAD:
                case Opcodes.D2L:
                case Opcodes.DALOAD:
                case Opcodes.L2D:
                case Opcodes.AALOAD:
                case Opcodes.I2L:
                case Opcodes.F2L:
                case Opcodes.I2F:
                case Opcodes.I2D:
                case Opcodes.F2D:
                case Opcodes.F2I:
                case Opcodes.ARRAYLENGTH:
                case Opcodes.INSTANCEOF:
                case Opcodes.GETFIELD:
                case Opcodes.NEWARRAY:
                case Opcodes.ANEWARRAY:
                case Opcodes.CHECKCAST:
                case Opcodes.L2F:
                case Opcodes.D2F:
                    bb.addEffect(insnOp(insn).insn(popVar()).assignTo(pushVar()));
                    break;
                case Opcodes.LDC:
                    bb.addEffect(CommonOps.CONST.create(((LdcInsnNode) insn).cst).insn().assignTo(pushVar()));
                    break;
                case Opcodes.ACONST_NULL:
                case Opcodes.ICONST_M1:
                case Opcodes.ICONST_0:
                case Opcodes.ICONST_1:
                case Opcodes.ICONST_2:
                case Opcodes.ICONST_3:
                case Opcodes.ICONST_4:
                case Opcodes.ICONST_5:
                case Opcodes.BIPUSH:
                case Opcodes.SIPUSH:
                case Opcodes.LCONST_0:
                case Opcodes.LCONST_1:
                case Opcodes.FCONST_0:
                case Opcodes.FCONST_1:
                case Opcodes.FCONST_2:
                case Opcodes.DCONST_0:
                case Opcodes.DCONST_1:
                case Opcodes.GETSTATIC:
                case Opcodes.NEW:
                    bb.addEffect(insnOp(insn).insn().assignTo(pushVar()));
                    break;
                case Opcodes.LLOAD:
                case Opcodes.DLOAD:
                case Opcodes.ILOAD:
                case Opcodes.FLOAD:
                case Opcodes.ALOAD:
                    bb.addEffect(identity(getVar(((VarInsnNode) insn).var), pushVar()));
                    break;
                case Opcodes.ISTORE:
                case Opcodes.FSTORE:
                case Opcodes.ASTORE:
                case Opcodes.LSTORE:
                case Opcodes.DSTORE:
                    bb.addEffect(identity(popVar(), getVar(((VarInsnNode) insn).var)));
                    break;
                case Opcodes.POP2:
                    popVars(2);
                    break;
                case Opcodes.IASTORE:
                case Opcodes.BASTORE:
                case Opcodes.CASTORE:
                case Opcodes.SASTORE:
                case Opcodes.FASTORE:
                case Opcodes.AASTORE:
                case Opcodes.LASTORE:
                case Opcodes.DASTORE:
                case Opcodes.PUTFIELD:
                    bb.addEffect(insnOp(insn).insn(popVars(2)).assignTo());
                    break;
                case Opcodes.POP:
                    popVars(1);
                    break;
                case Opcodes.MONITORENTER:
                case Opcodes.MONITOREXIT:
                case Opcodes.PUTSTATIC:
                    bb.addEffect(insnOp(insn).insn(popVar()).assignTo());
                    break;
                case Opcodes.IALOAD:
                case Opcodes.BALOAD:
                case Opcodes.CALOAD:
                case Opcodes.SALOAD:
                case Opcodes.IADD:
                case Opcodes.ISUB:
                case Opcodes.IMUL:
                case Opcodes.IDIV:
                case Opcodes.IREM:
                case Opcodes.IAND:
                case Opcodes.IOR:
                case Opcodes.IXOR:
                case Opcodes.ISHL:
                case Opcodes.ISHR:
                case Opcodes.IUSHR:
                case Opcodes.L2I:
                case Opcodes.D2I:
                case Opcodes.FCMPL:
                case Opcodes.FCMPG:
                case Opcodes.FALOAD:
                case Opcodes.FADD:
                case Opcodes.FSUB:
                case Opcodes.FMUL:
                case Opcodes.FDIV:
                case Opcodes.FREM:
                case Opcodes.DADD:
                case Opcodes.DSUB:
                case Opcodes.DMUL:
                case Opcodes.DDIV:
                case Opcodes.DREM:
                case Opcodes.LADD:
                case Opcodes.LSUB:
                case Opcodes.LMUL:
                case Opcodes.LDIV:
                case Opcodes.LREM:
                case Opcodes.LAND:
                case Opcodes.LOR:
                case Opcodes.LXOR:
                case Opcodes.LSHL:
                case Opcodes.LSHR:
                case Opcodes.LUSHR:
                case Opcodes.LCMP:
                case Opcodes.DCMPL:
                case Opcodes.DCMPG:
                    bb.addEffect(insnOp(insn).insn(popVars(2)).assignTo(pushVar()));
                    break;
                case Opcodes.RETURN:
                    bb.setControl(CommonOps.RETURN.insn().jumpsTo());
                    stackHeight = -1;
                    break;
                case Opcodes.IRETURN:
                case Opcodes.FRETURN:
                case Opcodes.ARETURN:
                case Opcodes.LRETURN:
                case Opcodes.DRETURN:
                    bb.setControl(CommonOps.RETURN.insn(popVar()).jumpsTo());
                    stackHeight = -1;
                    break;
                case Opcodes.ATHROW:
                    bb.setControl(insnOp(insn).insn(popVar()).jumpsTo());
                    stackHeight = -1;
                    break;
                case Opcodes.GOTO:
                    bb.setControl(Control.br(getBlock(((JumpInsnNode) insn).label.getLabel())));
                    stackHeight = -1;
                    break;
                case Opcodes.IFEQ:
                case Opcodes.IFNE:
                case Opcodes.IFLT:
                case Opcodes.IFGE:
                case Opcodes.IFGT:
                case Opcodes.IFLE:
                case Opcodes.IFNULL:
                case Opcodes.IFNONNULL:
                case Opcodes.IF_ICMPEQ:
                case Opcodes.IF_ICMPNE:
                case Opcodes.IF_ICMPLT:
                case Opcodes.IF_ICMPGE:
                case Opcodes.IF_ICMPGT:
                case Opcodes.IF_ICMPLE:
                case Opcodes.IF_ACMPEQ:
                case Opcodes.IF_ACMPNE:
                    int count = Opcodes.IF_ICMPEQ <= opcode && opcode <= Opcodes.IF_ACMPNE ? 2 : 1;
                    BasicBlock elseB = func.newBb();
                    bb.setControl(JavaOps.BR_COND.create(JavaOps.JumpType.fromOpcode(opcode))
                            .insn(popVars(count))
                            .jumpsTo(getBlock(((JumpInsnNode) insn).label.getLabel()), elseB));
                    bb = elseB;
                    break;
                case Opcodes.TABLESWITCH:
                case Opcodes.LOOKUPSWITCH:
                    throw new UnsupportedOperationException("switch");
                case Opcodes.DUP: {
                    bb.addEffect(identity(peekVar(), pushVar()));
                    break;
                }
                case Opcodes.DUP_X1: {
                    Var hi = func.newVar("dupx1.hi");
                    Var lo = func.newVar("dupx1.lo");
                    bb.addEffect(identity(popVar(), hi));
                    bb.addEffect(identity(popVar(), lo));
                    bb.addEffect(identity(hi, pushVar()));
                    bb.addEffect(identity(lo, pushVar()));
                    bb.addEffect(identity(hi, pushVar()));
                    break;
                }
                case Opcodes.DUP_X2: {
                    Var hi = func.newVar("dupx2.hi");
                    Var md = func.newVar("dupx2.md");
                    Var lo = func.newVar("dupx2.lo");
                    bb.addEffect(identity(popVar(), hi));
                    bb.addEffect(identity(popVar(), md));
                    bb.addEffect(identity(popVar(), lo));
                    bb.addEffect(identity(hi, pushVar()));
                    bb.addEffect(identity(lo, pushVar()));
                    bb.addEffect(identity(md, pushVar()));
                    bb.addEffect(identity(hi, pushVar()));
                    break;
                }
                case Opcodes.DUP2:
                case Opcodes.DUP2_X1:
                    throw new UnsupportedOperationException("long/double dups");
                case Opcodes.SWAP: {
                    Var hi = func.newVar("swap.hi");
                    Var lo = func.newVar("swap.lo");
                    bb.addEffect(identity(popVar(), hi));
                    bb.addEffect(identity(popVar(), lo));
                    bb.addEffect(identity(hi, pushVar()));
                    bb.addEffect(identity(lo, pushVar()));
                    break;
                }
                case Opcodes.IINC: {
                    IincInsnNode iinc = (IincInsnNode) insn;
                    int value = iinc.incr;
                    Var v = getVar(iinc.var);
                    InsnList il = new InsnList();
                    if (value >= -1 && value <= 5) {
                        il.add(new InsnNode(Opcodes.ICONST_0 + value));
                    } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
                        il.add(new IntInsnNode(Opcodes.BIPUSH, value));
                    } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
                        il.add(new IntInsnNode(Opcodes.SIPUSH, value));
                    } else {
                        il.add(new LdcInsnNode(value));
                    }
                    il.add(new InsnNode(Opcodes.IADD));
                    bb.addEffect(JavaOps.INSNS.create(il).insn(v).assignTo(v));
                    break;
                }
                case Opcodes.MULTIANEWARRAY:
                    bb.addEffect(insnOp(insn).insn(popVars(((MultiANewArrayInsnNode) insn).dims)).assignTo(pushVar()));
                    break;
                case Opcodes.INVOKESTATIC:
                case Opcodes.INVOKEDYNAMIC:
                case Opcodes.INVOKEVIRTUAL:
                case Opcodes.INVOKEINTERFACE:
                case Opcodes.INVOKESPECIAL: {
                    String desc = insn instanceof MethodInsnNode ?
                            ((MethodInsnNode) insn).desc :
                            ((InvokeDynamicInsnNode) insn).desc;
                    bb.addEffect(insnOp(insn)
                            .insn(popVars(Type.getArgumentTypes(desc).length +
                                    (opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.INVOKEDYNAMIC
                                            ? 0
                                            : 1)))
                            .assignTo(Type.getReturnType(desc) == Type.VOID_TYPE ? new Var[0] : new Var[]{pushVar()}));
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unsupported opcode: " + opcode);
            }
        }
    }
}
