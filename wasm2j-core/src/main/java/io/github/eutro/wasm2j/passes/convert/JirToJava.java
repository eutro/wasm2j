package io.github.eutro.wasm2j.passes.convert;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.OpKey;
import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.util.GraphWalker;
import io.github.eutro.wasm2j.util.Pair;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.*;

import java.util.*;

public class JirToJava implements IRPass<Module, ClassNode> {
    public static final Ext<Integer> LOCAL_EXT = Ext.create(Integer.class, "LOCAL_EXT");
    public static final Ext<Label> LABEL_EXT = Ext.create(Label.class, "LABEL_EXT");
    public static final Ext<BasicBlock> NEXT_BLOCK_EXT = Ext.create(BasicBlock.class, "NEXT_BLOCK_EXT");

    public static JirToJava INSTANCE = new JirToJava();

    @Override
    public ClassNode run(Module module) {
        JavaExts.JavaClass jClass = module.getExt(JavaExts.JAVA_CLASS)
                .orElseThrow(() -> new RuntimeException("Missing Java class extension data"));

        ClassNode cn = new ClassNode();
        cn.visit(
                Opcodes.V1_8,
                Opcodes.ACC_SUPER | Opcodes.ACC_PUBLIC,
                jClass.name,
                null,
                Type.getInternalName(Object.class),
                null
        );

        {
            Set<Pair<String, String>> existingFields = new HashSet<>();
            for (JavaExts.JavaField field : jClass.fields) {
                while (!existingFields.add(Pair.of(field.name, field.descriptor))) {
                    field.name += "_";
                }
                cn.fields.add(new FieldNode(
                        (field.isStatic ? Opcodes.ACC_STATIC : 0)
                                | field.otherAccess,
                        field.name,
                        field.descriptor,
                        null,
                        null
                ));
            }
        }

        {
            Set<Pair<String, String>> existingMethods = new HashSet<>();
            for (JavaExts.JavaMethod method : jClass.methods) {
                String desc = method.getDescriptor();
                while (!existingMethods.add(Pair.of(method.name, desc))) {
                    method.name += "_";
                }
            }
        }
        for (JavaExts.JavaMethod method : jClass.methods) {
            Optional<MethodNode> maybeNative = method.getExt(JavaExts.METHOD_NATIVE_IMPL);
            MethodNode mn = new MethodNode(
                    method.kind.access,
                    method.name,
                    method.getDescriptor(),
                    null,
                    null
            );
            if (maybeNative.isPresent()) {
                maybeNative.get().accept(mn);
                mn.visibleAnnotations = null;
                mn.access &= ~Opcodes.ACC_PUBLIC;
                mn.access |= Opcodes.ACC_PRIVATE;
            } else {
                Optional<Function> maybeImpl = method.getExt(JavaExts.METHOD_IMPL);
                if (maybeImpl.isPresent()) {
                    try {
                        compileFuncInto(jClass, mn, maybeImpl.get());
                    } catch (RuntimeException e) {
                        throw new RuntimeException("error generating code for method " + method.name, e);
                    }
                } else if (method.kind != JavaExts.JavaMethod.Kind.ABSTRACT) {
                    throw new RuntimeException("method impl missing for non-abstract function");
                }
            }
            cn.methods.add(mn);
        }

        return cn;
    }

    private void compileFuncInto(JavaExts.JavaClass jClass, MethodNode mn, Function impl) {
        // steps:
        // 1. sort blocks into a nice order (no need to overthink it)
        // 2. tag each block with a label for a jump target
        // 3. compute local variable types
        // 4. run through blocks and emit their bytecode
        //    a. emit the label and a frame for each block
        //       i. compute stack from the stackified phi nodes,
        //          variables are allocated globally for the function
        //    b. run through instructions and emit their bytecode
        //       i. emit stores/loads only for unstackified variables
        //    c. emit the jump and then another if the fallthrough block isn't immediately after

        // 1.
        // order is important here, the last one to be pushed is visited first
        // in the next cycle; in this case we want the fallthrough branch
        // to be visited next, which we have put as the last target
        List<BasicBlock> blockOrder = GraphWalker.blockWalker(impl, true).postOrder().toList();
        Collections.reverse(blockOrder);

        // 2.
        {
            Iterator<BasicBlock> it = blockOrder.iterator();
            BasicBlock next = it.next();
            while (true) {
                BasicBlock curr = next;
                curr.attachExt(LABEL_EXT, new Label());
                if (it.hasNext()) {
                    next = it.next();
                    curr.attachExt(NEXT_BLOCK_EXT, next);
                } else {
                    break;
                }
            }
        }

        // 3.
        LabelNode startLabel = new LabelNode();
        LabelNode endLabel = new LabelNode();
        mn.localVariables = new ArrayList<>();
        Object[] locals;
        Set<Var> allVars = new HashSet<>();
        {
            List<Object> localsList = new ArrayList<>();
            localsList.add(jClass.name);
            for (Type argTy : Type.getArgumentTypes(mn.desc)) {
                localsList.add(getLocalForType(argTy));
            }
            int localC = Type.getArgumentsAndReturnSizes(mn.desc) >> 2;
            for (BasicBlock block : blockOrder) {
                for (Effect effect : block.getEffects()) {
                    for (Var var : effect.getAssignsTo()) {
                        if (var.getExt(CommonExts.STACKIFIED).orElse(false)) continue;
                        if (!allVars.add(var)) continue;
                        Type ty = var.getExt(JavaExts.TYPE).orElseThrow(() ->
                                new IllegalStateException(String.format(
                                        "type of var %s has not been inferred",
                                        var)));
                        Object v = getLocalForType(ty);
                        localsList.add(v);
                        var.attachExt(LOCAL_EXT, localC);
                        localC += ty.getSize();
                    }
                }
            }
            locals = localsList.toArray();
        }

        // 4.
        JavaBuilder jb = new JavaBuilder(mn);
        // FIXME calculate frames and stack map properly...
        mn.visitFrame(Opcodes.F_FULL, locals.length, locals, 0, new Object[0]);
        boolean isFirst = true;
        for (BasicBlock block : blockOrder) {
            mn.visitLabel(block.getExtOrThrow(LABEL_EXT));
            AbstractInsnNode labelNode = mn.instructions.getLast();
            AbstractInsnNode frameNode;
            if (isFirst) {
                isFirst = false;
                frameNode = null;
            } else if (block.getEffects().size() >= 1 &&
                    block.getEffects().get(0).insn().op.key == JavaOps.CATCH) {
                Type ty = JavaOps.CATCH.cast(block.getEffects().get(0).insn().op).arg;
                mn.visitFrame(Opcodes.F_SAME1, locals.length, null, 1, new Object[]{ty.getInternalName()});
                frameNode = mn.instructions.getLast();
            } else {
                mn.visitFrame(Opcodes.F_SAME, locals.length, null, 0, null);
                frameNode = mn.instructions.getLast();
            }
            for (Effect effect : block.getEffects()) {
                if (effect.insn().op.key == CommonOps.PHI) continue;

                Converter<Effect> converter = FX_CONVERTERS.get(effect.insn().op.key);
                if (converter == null) {
                    throw missingConverter(effect.insn());
                }
                emitLoads(jb, effect.insn());
                converter.convert(jb, effect);
                emitStores(jb, effect);
            }
            Control ctrl = block.getControl();
            Converter<Control> converter = CTRL_CONVERTERS.get(ctrl.insn().op.key);
            if (converter == null) {
                throw missingConverter(ctrl.insn());
            }
            emitLoads(jb, ctrl.insn());
            converter.convert(jb, ctrl);

            if (mn.instructions.getLast() == frameNode) {
                mn.instructions.remove(frameNode);
            }
            if (mn.instructions.getLast() == labelNode) {
                // should be fairly uncommon, but we can put more than one label here this way
                mn.visitInsn(Opcodes.NOP);
            }
        }

        mn.instructions.insert(startLabel);
        mn.instructions.add(endLabel);

        for (BasicBlock block : blockOrder) {
            block.removeExt(LABEL_EXT);
            block.removeExt(NEXT_BLOCK_EXT);
        }

        for (Var var : allVars) {
            var.removeExt(LOCAL_EXT);
        }
    }

    private Object getLocalForType(Type ty) {
        Object v;
        if (ty == Type.INT_TYPE) v = Opcodes.INTEGER;
        else if (ty == Type.FLOAT_TYPE) v = Opcodes.FLOAT;
        else if (ty == Type.DOUBLE_TYPE) v = Opcodes.DOUBLE;
        else if (ty == Type.LONG_TYPE) v = Opcodes.LONG;
        else if (ty == JavaExts.BOTTOM_TYPE) v = Opcodes.NULL;
        else v = ty.getInternalName();
        return v;
    }

    private static RuntimeException missingConverter(Insn insn) {
        return new IllegalStateException("converter missing for key: " + insn.op.key);
    }

    private static class JavaBuilder extends GeneratorAdapter {
        protected JavaBuilder(MethodNode node) {
            super(Opcodes.ASM9, node, node.access, node.name, node.desc);
        }
    }

    private interface Converter<T> {
        void convert(JavaBuilder jb, T t);
    }

    private static final Map<OpKey, Converter<Effect>> FX_CONVERTERS = new HashMap<>();
    private static final Map<OpKey, Converter<Control>> CTRL_CONVERTERS = new HashMap<>();

    static {
        FX_CONVERTERS.put(CommonOps.ARG, (jb, fx) ->
                jb.loadArg(CommonOps.ARG.cast(fx.insn().op).arg));
        FX_CONVERTERS.put(JavaOps.INTRINSIC, (jb, fx) -> {
            throw new IllegalStateException(
                    String.format(
                            "intrinsic not lowered: %s",
                            fx.insn().op
                    ));
        });
        FX_CONVERTERS.put(JavaOps.SELECT, (jb, fx) -> {
            JavaOps.JumpType jumpType = JavaOps.SELECT.cast(fx.insn().op).arg;
            Label elseLabel = jb.newLabel();

            Var arg = fx.insn().args.get(0);
            Type ty = arg.getExtOrThrow(JavaExts.TYPE);

            jb.visitJumpInsn(jumpType.inverse.opcode, elseLabel);
            jb.swap(ty, ty);
            jb.mark(elseLabel);
            if (ty.getSize() == 2) jb.pop2();
            else jb.pop();
        });
        FX_CONVERTERS.put(JavaOps.DROP.key, (jb, fx) -> {
            List<Var> args = fx.insn().args;
            ListIterator<Var> li = args.listIterator(args.size());
            while (li.hasPrevious()) {
                Var arg = li.previous();
                Type ty = arg.getExtOrThrow(JavaExts.TYPE);
                if (ty.getSize() == 2) jb.pop2();
                else jb.pop();
            }
        });
        FX_CONVERTERS.put(JavaOps.BOOL_SELECT, (jb, fx) -> {
            JavaOps.JumpType jumpType = JavaOps.BOOL_SELECT.cast(fx.insn().op).arg;
            Label elseLabel = jb.newLabel();
            Label endLabel = jb.newLabel();
            jb.visitJumpInsn(jumpType.opcode, elseLabel);
            jb.push(true);
            jb.goTo(endLabel);
            jb.mark(elseLabel);
            jb.push(false);
            jb.mark(endLabel);
        });
        FX_CONVERTERS.put(CommonOps.PHI, (jb, fx) -> {
            // not our responsibility :P
            throw new IllegalStateException("phi node not lowered");
        });
        FX_CONVERTERS.put(CommonOps.IDENTITY.key, (jb, fx) -> {
        });
        FX_CONVERTERS.put(CommonOps.CONST, (jb, fx) -> {
            Object cst = CommonOps.CONST.cast(fx.insn().op).arg;
            if (cst == null) jb.push((String) null);
            else if (cst instanceof Integer) jb.push((int) cst);
            else if (cst instanceof Long) jb.push((long) cst);
            else if (cst instanceof Float) jb.push((float) cst);
            else if (cst instanceof Double) jb.push((double) cst);
            else jb.visitLdcInsn(cst);
        });
        FX_CONVERTERS.put(JavaOps.INSNS, (jb, fx) ->
                JavaOps.INSNS.cast(fx.insn().op).arg.accept(jb));
        FX_CONVERTERS.put(JavaOps.CATCH, (jb, fx) -> {/* noop, pop from stack */});
        FX_CONVERTERS.put(JavaOps.THIS.key, (jb, fx) ->
                jb.loadThis());
        FX_CONVERTERS.put(JavaOps.GET_FIELD, (jb, fx) -> {
            JavaExts.JavaField field = JavaOps.GET_FIELD.cast(fx.insn().op).arg;
            Type owner = Type.getObjectType(field.owner.name);
            Type type = Type.getType(field.descriptor);
            if (field.isStatic) {
                jb.getStatic(owner, field.name, type);
            } else {
                jb.getField(owner, field.name, type);
            }
        });
        FX_CONVERTERS.put(JavaOps.PUT_FIELD, (jb, fx) -> {
            JavaExts.JavaField field = JavaOps.PUT_FIELD.cast(fx.insn().op).arg;
            Type owner = Type.getObjectType(field.owner.name);
            Type type = Type.getType(field.descriptor);
            if (field.isStatic) {
                jb.putStatic(owner, field.name, type);
            } else {
                jb.putField(owner, field.name, type);
            }
        });
        FX_CONVERTERS.put(JavaOps.INVOKE, (jb, fx) -> {
            JavaExts.JavaMethod method = JavaOps.INVOKE.cast(fx.insn().op).arg;
            jb.visitMethodInsn(
                    method.kind.opcode,
                    method.owner.name,
                    method.name,
                    method.getDescriptor(),
                    method.kind.isInterface()
            );
        });
        FX_CONVERTERS.put(JavaOps.ARRAY_GET, (jb, fx) ->
                jb.arrayLoad(fx.insn().args.get(0).getExtOrThrow(JavaExts.TYPE).getElementType()));
        FX_CONVERTERS.put(JavaOps.ARRAY_SET, (jb, fx) ->
                jb.arrayStore(fx.insn().args.get(0).getExtOrThrow(JavaExts.TYPE).getElementType()));
        FX_CONVERTERS.put(JavaOps.HANDLE_OF, (jb, fx) -> {
            JavaExts.Handlable handlable = JavaOps.HANDLE_OF.cast(fx.insn().op).arg;
            jb.push(handlable.getHandle());
        });
    }

    static {
        CTRL_CONVERTERS.put(CommonOps.BR.key, (jb, ct) -> {
            BasicBlock target = ct.targets.get(0);
            if (ct.getExtOrThrow(CommonExts.OWNING_BLOCK)
                    .getExt(NEXT_BLOCK_EXT).orElse(null) != target) {
                jb.goTo(target.getExtOrThrow(LABEL_EXT));
            }
        });
        CTRL_CONVERTERS.put(JavaOps.BR_COND, (jb, ct) -> {
            JavaOps.JumpType ty = JavaOps.BR_COND.cast(ct.insn().op).arg;
            BasicBlock targetBlock = ct.targets.get(0);
            BasicBlock fallthroughBlock = ct.targets.get(1);
            jb.visitJumpInsn(ty.opcode, targetBlock.getExtOrThrow(LABEL_EXT));
            BasicBlock nextBlock = ct.getExtOrThrow(CommonExts.OWNING_BLOCK)
                    .getExt(NEXT_BLOCK_EXT).orElse(null);
            if (fallthroughBlock != nextBlock) {
                jb.goTo(fallthroughBlock.getExtOrThrow(LABEL_EXT));
            }
        });
        CTRL_CONVERTERS.put(CommonOps.RETURN.key, (jb, ct) ->
                jb.returnValue());
        CTRL_CONVERTERS.put(JavaOps.INSNS, (jb, ct) ->
                JavaOps.INSNS.cast(ct.insn().op).arg.accept(jb));
        CTRL_CONVERTERS.put(JavaOps.TABLESWITCH, (jb, ct) -> {
            Label[] labels = new Label[ct.targets.size() - 1];
            for (int i = 0; i < labels.length; i++) {
                BasicBlock bb = ct.targets.get(i);
                labels[i] = bb.getExtOrThrow(LABEL_EXT);
            }
            jb.visitTableSwitchInsn(
                    0,
                    labels.length - 1,
                    ct.targets.get(ct.targets.size() - 1).getExtOrThrow(LABEL_EXT),
                    labels
            );
        });
        CTRL_CONVERTERS.put(JavaOps.LOOKUPSWITCH, (jb, ct) -> {
            Label[] labels = new Label[ct.targets.size() - 1];
            for (int i = 0; i < labels.length; i++) {
                BasicBlock bb = ct.targets.get(i);
                labels[i] = bb.getExtOrThrow(LABEL_EXT);
            }
            jb.visitLookupSwitchInsn(
                    ct.targets.get(ct.targets.size() - 1).getExtOrThrow(LABEL_EXT),
                    JavaOps.LOOKUPSWITCH.cast(ct.insn().op).arg,
                    labels
            );
        });
        CTRL_CONVERTERS.put(JavaOps.TRY, (jb, ct) -> {
            BasicBlock catchingBlock = ct.targets.get(0);
            BasicBlock tryingBlock = ct.targets.get(1);
            jb.visitTryCatchBlock(
                    tryingBlock.getExtOrThrow(LABEL_EXT),
                    tryingBlock.getExtOrThrow(NEXT_BLOCK_EXT) // must be nonnull
                            .getExtOrThrow(LABEL_EXT),
                    catchingBlock.getExtOrThrow(LABEL_EXT),
                    JavaOps.TRY.cast(ct.insn().op).arg.getInternalName()
            );
        });
        CTRL_CONVERTERS.put(CommonOps.TRAP, (jb, ct) ->
                jb.throwException(Type.getType(RuntimeException.class),
                        CommonOps.TRAP.cast(ct.insn().op).arg));
    }

    private static void emitLoads(JavaBuilder jb, Insn insn) {
        for (Var arg : insn.args) {
            if (arg.getExt(CommonExts.STACKIFIED).orElse(false)) {
                continue;
            }
            jb.loadLocal(
                    arg.getExtOrThrow(LOCAL_EXT),
                    arg.getExtOrThrow(JavaExts.TYPE)
            );
        }
    }

    private static void emitStores(JavaBuilder jb, Effect fx) {
        ListIterator<Var> it = fx.getAssignsTo().listIterator(fx.getAssignsTo().size());
        while (it.hasPrevious()) {
            Var var = it.previous();
            if (var.getExt(CommonExts.STACKIFIED).orElse(false)) {
                continue;
            }
            jb.storeLocal(
                    var.getExtOrThrow(LOCAL_EXT),
                    var.getExtOrThrow(JavaExts.TYPE)
            );
        }
    }
}
