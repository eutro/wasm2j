package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.jwasm.tree.AbstractInsnNode;
import io.github.eutro.jwasm.tree.*;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.*;

import java.lang.invoke.MethodHandle;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;

import static io.github.eutro.jwasm.Opcodes.*;
import static io.github.eutro.wasm2j.Util.*;
import static org.objectweb.asm.Opcodes.*;

class ExprAdapter {
    public static void translateInto(ExprNode expr, Context ctx) {
        if (expr.instructions == null) return;
        ListIterator<AbstractInsnNode> li = expr.instructions.listIterator();
        while (li.hasNext()) {
            List<AbstractInsnNode> matched = new LinkedList<>();
            Rule<AbstractInsnNode> rule = SIMPLE.fullMatch(li, matched);
            if (rule == null) {
                throw new UnsupportedOperationException(String.format("Opcode 0x%02x not supported", li.next().opcode));
            }
            rule.applyTo(ctx, matched);
        }
    }

    @SuppressWarnings("unchecked")
    private static final DFA<State, Rule<AbstractInsnNode>, AbstractInsnNode> SIMPLE =
            (DFA<State, Rule<AbstractInsnNode>, AbstractInsnNode>) (Object) simple().build();

    private static DFA.Builder<State, Rule<?>, Object, AbstractInsnNode> simple() {
        DFA.Builder<State, Rule<?>, Object, AbstractInsnNode> b = DFA.builder(
                State::new,
                State::setNext,
                (State s, Rule<?> t) -> s.terminal = t,
                State::advance,
                s -> s.terminal);
        // region Control
        b
                .match(UNREACHABLE).terminal((ctx, insn) -> {
            Type aeType = Type.getType(AssertionError.class);
            ctx.newInstance(aeType);
            ctx.dup();
            ctx.push("Unreachable");
            ctx.invokeConstructor(aeType, new Method("<init>", "(Ljava/lang/Object;)V"));
            ctx.throwException();
        })
                .match(Opcodes.NOP).terminal((ctx, insn) -> { /* NOP */ })
                .match(BLOCK).terminal((Rule<BlockInsnNode>) (ctx, insn) -> ctx.pushBlock(new Block.BBlock(insn.blockType)))
                .match(LOOP).terminal((Rule<BlockInsnNode>) (ctx, insn) ->
                ctx.pushBlock(new Block.Loop(insn.blockType, ctx.mark())))
                .match(IF).terminal((Rule<BlockInsnNode>) (ctx, insn) -> {
            Block.If block = new Block.If(insn.blockType);
            ctx.visitJumpInsn(IFEQ, block.elseLabel);
            ctx.pushBlock(block);
        })
                .match(ELSE).terminal((ctx, insn) -> {
            Block.If ifBlock = (Block.If) ctx.peekBlock();
            ctx.goTo(ifBlock.endLabel());
            ctx.mark(ifBlock.elseLabel);
        })
                .match(END).terminal((ctx, insn) -> {
            if (ctx.peekBlock() != null) {
                ctx.popBlock().end(ctx);
            }
        })
                .match(BR).terminal((Rule<BreakInsnNode>) (ctx, insn) -> ctx.goTo(ctx.getLabel(insn.label)))
                .match(BR_IF).terminal((Rule<BreakInsnNode>) (ctx, insn) -> ctx.visitJumpInsn(IFNE, ctx.getLabel(insn.label)))
                .match(BR_TABLE).terminal((Rule<TableBreakInsnNode>) (ctx, insn) -> {
            Label[] labels = new Label[insn.labels.length];
            for (int i2 = 0; i2 < insn.labels.length; i2++) {
                labels[i2] = ctx.getLabel(insn.labels[i2]);
            }
            ctx.visitTableSwitchInsn(0,
                    insn.labels.length - 1,
                    ctx.getLabel(insn.defaultLabel),
                    labels);
        })
                .match(Opcodes.RETURN).terminal((ctx, insn) -> {
            for (byte retType : ctx.funcType.returns) {
                Types.toJava(retType);
            }
            ctx.compress(ctx.funcType.returns).returnValue();
        })
                .match(CALL).terminal((Rule<CallInsnNode>) (ctx, insn) -> {
            FuncExtern func = ctx.externs.funcs.get(insn.function);
            func.emitInvoke(ctx);
            ctx.decompress(func.type().returns);
        })
                .match(CALL_INDIRECT).terminal((Rule<CallIndirectInsnNode>) (ctx, insn) -> {
            int index = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(index);
            Type fType = ctx.jfuncType(insn.type);
            Type[] argumentTypes = fType.getArgumentTypes();
            int[] locals = new int[argumentTypes.length];
            for (int i = argumentTypes.length - 1; i >= 0; i--) {
                ctx.storeLocal(locals[i] = ctx.newLocal(argumentTypes[i]));
            }
            ctx.externs.tables.get(insn.table).emitGet(ctx);
            ctx.loadLocal(index);
            Type mhType = Type.getType(MethodHandle.class);
            ctx.arrayLoad(mhType);
            for (int local : locals) {
                ctx.loadLocal(local);
            }
            ctx.invokeVirtual(mhType, new Method("invokeExact", fType.toString()));
            ctx.decompress(ctx.funcType(insn.type).returns);
        });
        // endregion
        // region Reference
        b
                .match(REF_NULL).terminal((ctx, insn) -> {
            Type.getType(Object.class);
            ctx.push((String) null);
        });
        boolInsn(b.match(REF_IS_NULL), IFNULL, Rule.noop())
                .match(REF_FUNC).terminal((Rule<FuncRefInsnNode>) (ctx, insn) -> {
            Type.getType(MethodHandle.class);
            ctx.externs.funcs.get(insn.function).emitGet(ctx);
        });
        // endregion
        // region Parametric
        Rule<Object> select = (ctx, insn) -> {
            // WASM: if the top stack value is not 0, keep the bottom value, otherwise the top value.
            // JVM: if the top stack value is not 0, pop the top value, otherwise swap before popping
            Label end = new Label();
            ctx.ifZCmp(GeneratorAdapter.NE, end);
            List<Object> stack = ctx.getFrame().stack;
            boolean two = stack.get(stack.size() - 1).equals(TOP);
            if (two) {
                ctx.dup2X2();
                ctx.pop2();
            } else {
                ctx.swap();
            }
            ctx.visitLabel(end);
            if (two) {
                ctx.pop2();
            } else {
                ctx.pop();
            }
        };
        b
                .match(DROP).terminal((ctx, insn) -> {
            List<Object> stack = ctx.getFrame().stack;
            if (stack.get(stack.size() - 1).equals(TOP)) {
                ctx.pop2();
            } else {
                ctx.pop();
            }
        })
                .match(SELECT).terminal(select)
                .match(SELECTT).terminal(select);
        // endregion
        // region Variable
        b
                .match(LOCAL_GET).terminal((Rule<VariableInsnNode>) (ctx, insn) -> {
            ctx.localType(insn.variable);
            ctx.visitVarInsn(ctx.localType(insn.variable).getOpcode(ILOAD), ctx.localIndex(insn.variable));
        })
                .match(LOCAL_SET).terminal((Rule<VariableInsnNode>) (ctx, insn) -> {
            ctx.localType(insn.variable);
            ctx.visitVarInsn(ctx.localType(insn.variable).getOpcode(ISTORE), ctx.localIndex(insn.variable));
        })
                .match(LOCAL_TEE).terminal((Rule<VariableInsnNode>) (ctx, insn) -> {
            Type type = ctx.localType(insn.variable);
            if (type.getSize() == 2) {
                ctx.dup2();
            } else {
                ctx.dup();
            }
            ctx.visitVarInsn(type.getOpcode(ISTORE), ctx.localIndex(insn.variable));
        })
                .match(GLOBAL_GET).terminal((Rule<VariableInsnNode>) (ctx, insn) -> {
            TypedExtern global = ctx.externs.globals.get(insn.variable);
            Types.toJava(global.type());
            global.emitGet(ctx);
        })
                .match(GLOBAL_SET).terminal((Rule<VariableInsnNode>) (ctx, insn) -> {
            TypedExtern global = ctx.externs.globals.get(insn.variable);
            Types.toJava(global.type());
            global.emitSet(ctx);
        });
        // endregion
        // region Table
        b
                .match(TABLE_GET).terminal((Rule<TableInsnNode>) (ctx, insn) -> {
            TypedExtern table2 = ctx.externs.tables.get(insn.table);
            table2.emitGet(ctx);
            ctx.swap();
            ctx.arrayLoad(Types.toJava(table2.type()));
        })
                .match(TABLE_SET).terminal((Rule<TableInsnNode>) (ctx, insn) -> {
            TypedExtern table = ctx.externs.tables.get(insn.table);
            table.emitGet(ctx);
            ctx.dupX2();
            ctx.pop();
            ctx.arrayStore(Types.toJava(table.type()));
        })
                .match(TABLE_INIT).<Rule<PrefixBinaryTableInsnNode>>terminal((ctx, insn) -> {
            TypedExtern table = ctx.externs.tables.get(insn.firstIndex);
            FieldNode elem = ctx.passiveElems[insn.secondIndex];
            if (elem == null) throw new IllegalStateException("No such passive elem");
            // System.arraycopy(src, srcPos, dest, destPos, length);
            int n = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(n);
            int s = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(s);
            int d = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(d);

            ctx.loadThis();
            ctx.visitFieldInsn(GETFIELD, ctx.getName(), elem.name, elem.desc);
            ctx.loadLocal(s);
            table.emitGet(ctx);
            ctx.loadLocal(d);
            ctx.loadLocal(n);
            ctx.visitMethodInsn(INVOKESTATIC, "java/lang/System", "arraycopy",
                    "(Ljava/lang/Object;ILjava/lang/Object;II)V",
                    false);
        })
                .match(ELEM_DROP).terminal((Rule<PrefixTableInsnNode>) (ctx, insn) -> {
            FieldNode elem = ctx.passiveElems[insn.table];
            if (elem == null) throw new IllegalStateException("No such passive elem");
            ctx.loadThis();
            ctx.push((String) null);
            ctx.visitFieldInsn(PUTFIELD, ctx.getName(), elem.name, elem.desc);
        })
                .match(TABLE_COPY).terminal((Rule<PrefixBinaryTableInsnNode>) (ctx, insn) -> {
            TypedExtern srcTable = ctx.externs.tables.get(insn.secondIndex);
            TypedExtern dstTable = ctx.externs.tables.get(insn.firstIndex);
            // System.arraycopy(src, srcPos, dest, destPos, length);
            int n = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(n);
            int s = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(s);
            int d = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(d);

            ctx.loadThis();
            srcTable.emitGet(ctx);
            ctx.loadLocal(s);
            dstTable.emitGet(ctx);
            ctx.loadLocal(d);
            ctx.loadLocal(n);
            ctx.visitMethodInsn(INVOKESTATIC, "java/lang/System", "arrayCopy",
                    "(Ljava/lang/Object;ILjava/lang/Object;II)V",
                    false);
        })
                .match(TABLE_GROW).terminal((Rule<PrefixTableInsnNode>) (ctx, insn) -> {
            // TODO maybe implement table growth?
            ctx.pop2();
            ctx.push(-1);
        })
                .match(TABLE_SIZE).terminal((Rule<PrefixTableInsnNode>) (ctx, insn) -> {
            TypedExtern table = ctx.externs.tables.get(insn.table);
            table.emitGet(ctx);
            ctx.arrayLength();
        })
                .match(TABLE_FILL).terminal((Rule<PrefixTableInsnNode>) (ctx, insn) -> {
            TypedExtern table = ctx.externs.tables.get(insn.table);
            Type tableType = Types.toJava(table.type());
            int i = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(i);
            int v = ctx.newLocal(tableType);
            ctx.storeLocal(v);
            int n = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(n);

            table.emitGet(ctx);
            Label loop = ctx.mark();
            Label end = new Label();
            ctx.loadLocal(n);
            ctx.visitJumpInsn(IFEQ, end);
            ctx.dup();
            ctx.loadLocal(i);
            ctx.loadLocal(v);
            ctx.arrayStore(tableType);
            ctx.iinc(i, 1);
            ctx.iinc(n, -1);
            ctx.goTo(loop);
            ctx.mark(end);
            ctx.pop();
        });
        // endregion
        // region Memory
        // region Load
        b
                .match(I32_LOAD).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), virtualNode("java/nio/ByteBuffer", "getInt", "(I)I"));
        })
                .match(I64_LOAD).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), virtualNode("java/nio/ByteBuffer", "getLong", "(I)J"));
        })
                .match(F32_LOAD).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), virtualNode("java/nio/ByteBuffer", "getFloat", "(I)F"));
        })
                .match(F64_LOAD).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), virtualNode("java/nio/ByteBuffer", "getDouble", "(I)D"));
        })
                .match(I32_LOAD8_S).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), virtualNode("java/nio/ByteBuffer", "get", "(I)B"));
        })
                .match(I32_LOAD8_U).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), virtualNode("java/nio/ByteBuffer", "get", "(I)B"),
                    staticNode("java/lang/Byte", "toUnsignedInt", "(B)I"));
        })
                .match(I32_LOAD16_S).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), virtualNode("java/nio/ByteBuffer", "getShort", "(I)S"));
        })
                .match(I32_LOAD16_U).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), virtualNode("java/nio/ByteBuffer", "getShort", "(I)S"),
                    staticNode("java/lang/Short", "toUnsignedInt", "(S)I"));
        })
                .match(I64_LOAD8_S).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), virtualNode("java/nio/ByteBuffer", "get", "(I)B"),
                    new InsnNode(I2L));
        })
                .match(I64_LOAD8_U).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), virtualNode("java/nio/ByteBuffer", "get", "(I)B"),
                    staticNode("java/lang/Byte", "toUnsignedLong", "(B)J"));
        })
                .match(I64_LOAD16_S).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), virtualNode("java/nio/ByteBuffer", "getShort", "(I)S"),
                    new InsnNode(I2L));
        })
                .match(I64_LOAD16_U).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), virtualNode("java/nio/ByteBuffer", "getShort", "(I)S"),
                    staticNode("java/lang/Short", "toUnsignedLong", "(S)J"));
        })
                .match(I64_LOAD32_S).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), virtualNode("java/nio/ByteBuffer", "getInt", "(I)I"),
                    new InsnNode(I2L));
        })
                .match(I64_LOAD32_U).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), virtualNode("java/nio/ByteBuffer", "getInt", "(I)I"),
                    staticNode("java/lang/Short", "toUnsignedLong", "(S)J"));
        });
        // endregion
        // region Store
        b
                .match(I32_STORE).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            ctx.addInsns(new InsnNode(SWAP));
            offsetFor(ctx, insn);
            ctx.addInsns(new InsnNode(SWAP));
            getMem(ctx);
            ctx.addInsns(new InsnNode(DUP_X2), new InsnNode(POP),
                    virtualNode("java/nio/ByteBuffer", "putInt", "(II)Ljava/nio/ByteBuffer;"),
                    new InsnNode(POP));
        })
                .match(I64_STORE).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            ctx.addInsns(new InsnNode(DUP2_X1), new InsnNode(POP2));
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), new InsnNode(DUP2_X2), new InsnNode(POP2),
                    virtualNode("java/nio/ByteBuffer", "putLong", "(IJ)Ljava/nio/ByteBuffer;"),
                    new InsnNode(POP));
        })
                .match(F32_STORE).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            ctx.addInsns(new InsnNode(SWAP));
            offsetFor(ctx, insn);
            ctx.addInsns(new InsnNode(SWAP));
            getMem(ctx);
            ctx.addInsns(new InsnNode(DUP_X2), new InsnNode(POP),
                    virtualNode("java/nio/ByteBuffer", "putInt", "(IF)Ljava/nio/ByteBuffer;"),
                    new InsnNode(POP));
        })
                .match(F64_STORE).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            ctx.addInsns(new InsnNode(DUP2_X1), new InsnNode(POP2));
            offsetFor(ctx, insn);
            getMem(ctx);
            ctx.addInsns(new InsnNode(SWAP), new InsnNode(DUP2_X2), new InsnNode(POP2),
                    virtualNode("java/nio/ByteBuffer", "putDouble", "(ID)Ljava/nio/ByteBuffer;"),
                    new InsnNode(POP));
        })
                .match(I32_STORE8).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            ctx.addInsns(new InsnNode(I2B), new InsnNode(SWAP));
            offsetFor(ctx, insn);
            ctx.addInsns(new InsnNode(SWAP));
            getMem(ctx);
            ctx.addInsns(new InsnNode(DUP_X2), new InsnNode(POP),
                    virtualNode("java/nio/ByteBuffer", "put", "(IB)Ljava/nio/ByteBuffer;"),
                    new InsnNode(POP));
        })
                .match(I32_STORE16).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            ctx.addInsns(new InsnNode(I2S), new InsnNode(SWAP));
            offsetFor(ctx, insn);
            ctx.addInsns(new InsnNode(SWAP));
            getMem(ctx);
            ctx.addInsns(new InsnNode(DUP_X2), new InsnNode(POP),
                    virtualNode("java/nio/ByteBuffer", "putShort", "(IS)Ljava/nio/ByteBuffer;"),
                    new InsnNode(POP));
        })
                .match(I64_STORE8).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            ctx.addInsns(new InsnNode(L2I), new InsnNode(I2B), new InsnNode(SWAP));
            offsetFor(ctx, insn);
            ctx.addInsns(new InsnNode(SWAP));
            getMem(ctx);
            ctx.addInsns(new InsnNode(DUP_X2), new InsnNode(POP),
                    virtualNode("java/nio/ByteBuffer", "put", "(IB)Ljava/nio/ByteBuffer;"),
                    new InsnNode(POP));
        })
                .match(I64_STORE16).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            ctx.addInsns(new InsnNode(L2I), new InsnNode(I2S), new InsnNode(SWAP));
            offsetFor(ctx, insn);
            ctx.addInsns(new InsnNode(SWAP));
            getMem(ctx);
            ctx.addInsns(new InsnNode(DUP_X2), new InsnNode(POP),
                    virtualNode("java/nio/ByteBuffer", "putShort", "(IS)Ljava/nio/ByteBuffer;"),
                    new InsnNode(POP));
        })
                .match(I64_STORE32).terminal((Rule<MemInsnNode>) (ctx, insn) -> {
            ctx.addInsns(new InsnNode(L2I), new InsnNode(SWAP));
            offsetFor(ctx, insn);
            ctx.addInsns(new InsnNode(SWAP));
            getMem(ctx);
            ctx.addInsns(new InsnNode(DUP_X2), new InsnNode(POP),
                    virtualNode("java/nio/ByteBuffer", "putInt", "(II)Ljava/nio/ByteBuffer;"),
                    new InsnNode(POP));
        });
        // endregion
        b
                .match(MEMORY_SIZE).terminal((ctx, insn) -> {
            ctx.externs.mems.get(0).emitGet(ctx);
            ctx.addInsns(virtualNode("java/nio/ByteBuffer", "capacity", "()I"));
            ctx.push(PAGE_SIZE);
            ctx.visitInsn(IDIV);
        })
                .match(MEMORY_GROW).terminal((ctx, insn) -> {
            // TODO maybe implement memory growth?
            ctx.pop();
            ctx.push(-1);
        })
                .match(MEMORY_INIT).terminal((Rule<IndexedMemInsnNode>) (ctx, insn) -> {
            Extern mem = ctx.externs.mems.get(0);
            FieldNode data = ctx.passiveDatas[insn.index];
            if (data == null) throw new IllegalStateException("No such passive data");
            // System.arraycopy(src, srcPos, dest, destPos, length);
            int n = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(n);
            int s = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(s);
            int d = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(d);

            ctx.loadThis();
            ctx.visitFieldInsn(GETFIELD, ctx.getName(), data.name, data.desc);
            ctx.loadLocal(s);
            mem.emitGet(ctx);
            ctx.loadLocal(d);
            ctx.loadLocal(n);
            ctx.visitMethodInsn(INVOKESTATIC, "java/lang/System", "arraycopy",
                    "(Ljava/lang/Object;ILjava/lang/Object;II)V",
                    false);
        })
                .match(DATA_DROP).terminal((Rule<IndexedMemInsnNode>) (ctx, insn) -> {
            FieldNode data = ctx.passiveDatas[insn.index];
            if (data == null) throw new IllegalStateException("No such passive data");
            ctx.loadThis();
            ctx.push((String) null);
            ctx.visitFieldInsn(PUTFIELD, ctx.getName(), data.name, data.desc);
        })
                .match(MEMORY_COPY).terminal((ctx, insn) -> {
            int n = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(n);
            int s = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(s);
            Extern mem = ctx.externs.mems.get(0);
            mem.emitGet(ctx);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "duplicate",
                    "()Ljava/nio/ByteBuffer;",
                    false);
            ctx.swap();
            ctx.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "position",
                    "(I)Ljava/nio/ByteBuffer;",
                    false);
            ctx.dup();
            ctx.loadLocal(s);
            ctx.loadLocal(n);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "slice",
                    "(II)Ljava/nio/ByteBuffer;",
                    false);
        })
                .match(MEMORY_FILL).terminal((ctx, insn) -> {
            int n = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(n);
            int v = ctx.newLocal(Type.INT_TYPE);
            ctx.storeLocal(v);

            Extern mem = ctx.externs.mems.get(0);
            mem.emitGet(ctx);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "duplicate",
                    "()Ljava/nio/ByteBuffer;",
                    false);
            ctx.swap();
            ctx.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "position",
                    "(I)Ljava/nio/ByteBuffer;",
                    false);

            Label end = new Label();
            Label loop = ctx.mark();
            ctx.loadLocal(v);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "put",
                    "(I)Ljava/nio/ByteBuffer;",
                    false);
            ctx.iinc(n, -1);
            ctx.visitJumpInsn(IFEQ, end);
            ctx.goTo(loop);
            ctx.mark(end);
            ctx.pop();
        });
        // endregion
        // region Numeric
        // region Const
        b
                .match(I32_CONST).<Rule<ConstInsnNode>>terminal((ctx, insn) -> ctx.push((int) insn.value))
                .match(I64_CONST).<Rule<ConstInsnNode>>terminal((ctx, insn) -> ctx.push((long) insn.value))
                .match(F32_CONST).<Rule<ConstInsnNode>>terminal((ctx, insn) -> ctx.push((float) insn.value))
                .match(F64_CONST).<Rule<ConstInsnNode>>terminal((ctx, insn) -> ctx.push((double) insn.value));
        // endregion
        // region Comparisons
        // region i32
        Rule<Object> icmpU = (ctx, insn) -> ctx.visitMethodInsn(INVOKESTATIC,
                "java/lang/Integer",
                "compareUnsigned",
                "(II)I",
                false);
        boolInsn(b.match(I32_EQZ), IFEQ, Rule.noop());
        boolInsn(b.match(I32_EQ), IF_ICMPEQ, Rule.noop());
        boolInsn(b.match(I32_NE), IF_ICMPNE, Rule.noop());
        boolInsn(b.match(I32_LT_S), IF_ICMPLT, Rule.noop());
        boolInsn(b.match(I32_LT_U), IFLT, icmpU);
        boolInsn(b.match(I32_GT_S), IF_ICMPGT, Rule.noop());
        boolInsn(b.match(I32_GT_U), IFGT, icmpU);
        boolInsn(b.match(I32_LE_S), IF_ICMPLE, Rule.noop());
        boolInsn(b.match(I32_LE_U), IFLE, icmpU);
        boolInsn(b.match(I32_GE_S), IF_ICMPGE, Rule.noop());
        boolInsn(b.match(I32_GE_U), IFGE, icmpU);
        // endregion
        // region i64
        Rule<Object> lcmpU = (ctx, insn) -> ctx.visitMethodInsn(INVOKESTATIC,
                "java/lang/Long",
                "compareUnsigned",
                "(JJ)I",
                false);
        Rule<Object> lcmpS = Rule.insn(LCMP);
        boolInsn(b.match(I64_EQZ), IFEQ, (ctx, insn) -> ctx.visitInsn(L2I));
        boolInsn(b.match(I64_EQ), IFEQ, lcmpS);
        boolInsn(b.match(I64_NE), IFNE, lcmpS);
        boolInsn(b.match(I64_LT_S), IFLT, lcmpS);
        boolInsn(b.match(I64_LT_U), IFLT, lcmpU);
        boolInsn(b.match(I64_GT_S), IFGT, lcmpS);
        boolInsn(b.match(I64_GT_U), IFGT, lcmpU);
        boolInsn(b.match(I64_LE_S), IFLE, lcmpS);
        boolInsn(b.match(I64_LE_U), IFLE, lcmpU);
        boolInsn(b.match(I64_GE_S), IFGE, lcmpS);
        boolInsn(b.match(I64_GE_U), IFGE, lcmpU);
        // endregion
        // region f32
        boolInsn(b.match(F32_EQ), IFEQ, Rule.insn(FCMPG));
        boolInsn(b.match(F32_NE), IFNE, Rule.insn(FCMPG));
        boolInsn(b.match(F32_LT), IFLT, Rule.insn(FCMPG));
        boolInsn(b.match(F32_GT), IFGT, Rule.insn(FCMPL));
        boolInsn(b.match(F32_LE), IFLE, Rule.insn(FCMPG));
        boolInsn(b.match(F32_GE), IFGE, Rule.insn(FCMPL));
        // endregion
        // region f64
        boolInsn(b.match(F64_EQ), IFEQ, Rule.insn(DCMPG));
        boolInsn(b.match(F64_NE), IFNE, Rule.insn(DCMPG));
        boolInsn(b.match(F64_LT), IFLT, Rule.insn(DCMPG));
        boolInsn(b.match(F64_GT), IFGT, Rule.insn(DCMPL));
        boolInsn(b.match(F64_LE), IFLE, Rule.insn(DCMPG));
        boolInsn(b.match(F64_GE), IFGE, Rule.insn(DCMPL));
        // endregion
        // endregion
        // endregion
        // region Mathematical
        // region i32
        b
                .match(I32_CLZ).terminal(Rule.staticRule("java/lang/Integer", "numberOfLeadingZeros", "(I)I"))
                .match(I32_CTZ).terminal(Rule.staticRule("java/lang/Integer", "numberOfTrailingZeros", "(I)I"))
                .match(I32_POPCNT).terminal(Rule.staticRule("java/lang/Integer", "numberOfTrailingZeros", "(I)I"))
                .match(I32_ADD).terminal(Rule.insn(IADD))
                .match(I32_SUB).terminal(Rule.insn(ISUB))
                .match(I32_MUL).terminal(Rule.insn(IMUL))
                .match(I32_DIV_S).terminal(Rule.insn(IDIV))
                .match(I32_DIV_U).terminal(Rule.staticRule("java/lang/Integer", "divideUnsigned", "(II)I"))
                .match(I32_REM_S).terminal(Rule.insn(IREM))
                .match(I32_REM_U).terminal(Rule.staticRule("java/lang/Integer", "remainderUnsigned", "(II)I"))
                .match(I32_AND).terminal(Rule.insn(IAND))
                .match(I32_OR).terminal(Rule.insn(IOR))
                .match(I32_XOR).terminal(Rule.insn(IXOR))
                .match(I32_SHL).terminal(Rule.insn(ISHL))
                .match(I32_SHR_S).terminal(Rule.insn(ISHR))
                .match(I32_SHR_U).terminal(Rule.insn(IUSHR))
                .match(I32_ROTL).terminal(Rule.staticRule("java/lang/Integer", "rotateLeft", "(II)I"))
                .match(I32_ROTR).terminal(Rule.staticRule("java/lang/Integer", "rotateRight", "(II)I"));
        // endregion
        // region i64
        b
                .match(I64_CLZ).terminal(Rule.staticRule("java/lang/Long", "numberOfLeadingZeros", "(J)I").comp(Rule.insn(I2L)))
                .match(I64_CTZ).terminal(Rule.staticRule("java/lang/Long", "numberOfTrailingZeros", "(J)I").comp(Rule.insn(I2L)))
                .match(I64_POPCNT).terminal(Rule.staticRule("java/lang/Long", "bitCount", "(J)I").comp(Rule.insn(I2L)))
                .match(I64_ADD).terminal(Rule.insn(LADD))
                .match(I64_SUB).terminal(Rule.insn(LSUB))
                .match(I64_MUL).terminal(Rule.insn(LMUL))
                .match(I64_DIV_S).terminal(Rule.insn(LDIV))
                .match(I64_DIV_U).terminal(Rule.staticRule("java/lang/Long", "divideUnsigned", "(JJ)J"))
                .match(I64_REM_S).terminal(Rule.insn(LREM))
                .match(I64_REM_U).terminal(Rule.staticRule("java/lang/Long", "remainderUnsigned", "(JJ)J"))
                .match(I64_AND).terminal(Rule.insn(LAND))
                .match(I64_OR).terminal(Rule.insn(LOR))
                .match(I64_XOR).terminal(Rule.insn(LXOR))
                .match(I64_SHL).terminal(Rule.insn(LSHL))
                .match(I64_SHR_S).terminal(Rule.insn(LSHR))
                .match(I64_SHR_U).terminal(Rule.insn(LUSHR))
                .match(I64_ROTL).terminal(Rule.insn(L2I).comp(Rule.staticRule("java/lang/Long", "rotateLeft", "(JI)J")))
                .match(I64_ROTR).terminal(Rule.insn(L2I).comp(Rule.staticRule("java/lang/Long", "rotateRight", "(JI)J")));
        // endregion
        // region f32
        Rule<Object> dtrunc = (ctx, insn) -> {
            ctx.dup2();
            ctx.push(0D);
            ctx.visitInsn(DCMPG);
            ctx.ifThenElse(IFLT,
                    makeList(staticNode("java/lang/Math", "floor", "(D)D")),
                    makeList(staticNode("java/lang/Math", "ceil", "(D)D")));
        };
        b
                .match(F32_ABS).terminal(Rule.staticRule("java/lang/Math", "abs", "(F)F"))
                .match(F32_NEG).terminal(Rule.insn(FNEG))
                .match(F32_CEIL).terminal(Rule.comp(Rule.insn(F2D), Rule.staticRule("java/lang/Math", "ceil", "(D)D"), Rule.insn(D2F)))
                .match(F32_FLOOR).terminal(Rule.comp(Rule.insn(F2D), Rule.staticRule("java/lang/Math", "floor", "(D)D"), Rule.insn(D2F)))
                .match(F32_TRUNC).terminal(Rule.comp(Rule.insn(F2D), dtrunc, Rule.insn(D2F)))
                .match(F32_NEAREST).terminal((ctx, insn) -> {
            // TODO I can't think of a clean way to do this
            throw new UnsupportedOperationException();
        })
                .match(F32_SQRT).terminal(Rule.comp(Rule.insn(F2D), Rule.staticRule("java/lang/Math", "sqrt", "(D)D"), Rule.insn(D2F)))
                .match(F32_ADD).terminal(Rule.insn(FADD))
                .match(F32_SUB).terminal(Rule.insn(FSUB))
                .match(F32_MUL).terminal(Rule.insn(FMUL))
                .match(F32_DIV).terminal(Rule.insn(FDIV))
                .match(F32_MIN).terminal(Rule.staticRule("java/lang/Math", "min", "(FF)F"))
                .match(F32_MAX).terminal(Rule.staticRule("java/lang/Math", "max", "(FF)F"))
                .match(F32_COPYSIGN).terminal(Rule.staticRule("java/lang/Math", "copySign", "(FF)F"));
        // endregion
        // region f64
        b
                .match(F64_ABS).terminal(Rule.staticRule("java/lang/Math", "abs", "(D)D"))
                .match(F64_NEG).terminal(Rule.insn(DNEG))
                .match(F64_CEIL).terminal(Rule.staticRule("java/lang/Math", "ceil", "(D)D"))
                .match(F64_FLOOR).terminal(Rule.staticRule("java/lang/Math", "floor", "(D)D"))
                .match(F64_TRUNC).terminal(dtrunc)
                .match(F64_NEAREST).terminal((ctx, insn) -> {
            // TODO I can't think of a clean way to do this
            throw new UnsupportedOperationException();
        })
                .match(F64_SQRT).terminal(Rule.staticRule("java/lang/Math", "sqrt", "(D)D"))
                .match(F64_ADD).terminal(Rule.insn(DADD))
                .match(F64_SUB).terminal(Rule.insn(DSUB))
                .match(F64_MUL).terminal(Rule.insn(DMUL))
                .match(F64_DIV).terminal(Rule.insn(DDIV))
                .match(F64_MIN).terminal(Rule.staticRule("java/lang/Math", "min", "(DD)D"))
                .match(F64_MAX).terminal(Rule.staticRule("java/lang/Math", "max", "(DD)D"))
                .match(F64_COPYSIGN).terminal(Rule.staticRule("java/lang/Math", "copySign", "(DD)D"));
        // endregion
        // endregion
        // region Conversions
        b
                .match(I32_WRAP_I64).terminal(Rule.insn(L2I))
                // FIXME these truncations are actually saturating, while they should cause a trap instead
                .match(I32_TRUNC_F32_S).terminal(Rule.insn(F2I))
                .match(I32_TRUNC_F32_U).terminal(Rule.insn(F2I))
                .match(I32_TRUNC_F64_S).terminal(Rule.insn(D2I))
                .match(I32_TRUNC_F64_U).terminal(Rule.insn(D2I))
                .match(I64_EXTEND_I32_S).terminal(Rule.insn(I2L))
                .match(I64_EXTEND_I32_U).terminal(Rule.staticRule("java/lang/Integer", "toUnsignedLong", "(I)J"))
                .match(I64_TRUNC_F32_S).terminal(Rule.insn(F2L))
                .match(I64_TRUNC_F32_U).terminal(Rule.insn(F2L))
                .match(I64_TRUNC_F64_S).terminal(Rule.insn(D2L))
                .match(I64_TRUNC_F64_U).terminal(Rule.insn(D2L))
                .match(F32_CONVERT_I32_S).terminal(Rule.insn(I2F))
                .match(F32_CONVERT_I32_U).terminal(Rule.insn(I2F))
                .match(F32_CONVERT_I64_S).terminal(Rule.insn(L2F))
                .match(F32_CONVERT_I64_U).terminal(Rule.insn(L2F))
                .match(F32_DEMOTE_F64).terminal(Rule.insn(D2F))
                .match(F64_CONVERT_I32_S).terminal(Rule.insn(I2D))
                .match(F64_CONVERT_I32_U).terminal(Rule.insn(I2D))
                .match(F64_CONVERT_I64_S).terminal(Rule.insn(L2D))
                .match(F64_CONVERT_I64_U).terminal(Rule.insn(L2D))
                .match(F64_PROMOTE_F32).terminal(Rule.insn(F2D))
                .match(I32_REINTERPRET_F32).terminal(Rule.staticRule("java/lang/Float", "floatToRawIntBits", "(F)I"))
                .match(I64_REINTERPRET_F64).terminal(Rule.staticRule("java/lang/Double", "doubleToRawLongBits", "(D)J"))
                .match(F32_REINTERPRET_I32).terminal(Rule.staticRule("java/lang/Float", "intBitsToFloat", "(I)F"))
                .match(F64_REINTERPRET_I64).terminal(Rule.staticRule("java/lang/Double", "longBitsToDouble", "(J)D"));
        // endregion
        // region Extension
        b
                .match(I32_EXTEND8_S).terminal(Rule.insn(I2B))
                .match(I32_EXTEND16_S).terminal(Rule.insn(I2S))
                .match(I64_EXTEND8_S).terminal(Rule.insn(L2I, I2B, I2L))
                .match(I64_EXTEND16_S).terminal(Rule.insn(L2I, I2S, I2L))
                .match(I64_EXTEND32_S).terminal(Rule.insn(L2I, I2L));
        // endregion
        // region Saturating Truncation
        b
                .match(I32_TRUNC_SAT_F32_S).terminal(Rule.insn(F2I))
                .match(I32_TRUNC_SAT_F32_U).terminal(Rule.insn(F2I))
                .match(I32_TRUNC_SAT_F64_S).terminal(Rule.insn(D2I))
                .match(I32_TRUNC_SAT_F64_U).terminal(Rule.insn(D2I))
                .match(I64_TRUNC_SAT_F32_S).terminal(Rule.insn(F2L))
                .match(I64_TRUNC_SAT_F32_U).terminal(Rule.insn(F2L))
                .match(I64_TRUNC_SAT_F64_S).terminal(Rule.insn(D2L))
                .match(I64_TRUNC_SAT_F64_U).terminal(Rule.insn(D2L));
        // endregion
        return b;
    }

    private static class State {
        State[] basic = null;
        State[] prefix = null;
        Rule<?> terminal;

        public State advance(AbstractInsnNode insn) {
            if (insn.opcode == INSN_PREFIX) {
                return prefix != null ? prefix[((PrefixInsnNode) insn).intOpcode] : null;
            } else {
                return basic != null ? basic[Byte.toUnsignedInt(insn.opcode)] : null;
            }
        }

        public void setNext(Object inSym, @Nullable State next) {
            if (inSym instanceof Byte) {
                if (basic == null) basic = new State[Byte.MAX_VALUE - Byte.MIN_VALUE];
                basic[Byte.toUnsignedInt((Byte) inSym)] = next;
            } else {
                if (prefix == null) prefix = new State[TABLE_FILL + 1];
                prefix[(Integer) inSym] = next;
            }
        }
    }

    private static void offsetFor(Context ctx, MemInsnNode insn) {
        if (insn.offset != 0) {
            ctx.push(insn.offset);
            ctx.visitInsn(IADD);
        }
    }

    private static void getMem(Context ctx) {
        ctx.externs.mems.get(0).emitGet(ctx);
    }

    @SuppressWarnings("unchecked")
    private static <R, I, T extends DFA.Builder<State, Rule<?>, Object, AbstractInsnNode>.StateBuilder<R>> R boolInsn(
            T builder,
            int jumpInsn,
            Rule<I> emit
    ) {
        return builder
                .match(IF).terminal((Rule<BlockInsnNode>) (ctx, insn) -> {
                    Block.If block = new Block.If(insn.blockType);
                    emit.apply(ctx, (I) insn);
                    ctx.visitJumpInsn(invertJump(jumpInsn), block.elseLabel);
                    ctx.pushBlock(block);
                })
                .match(BR_IF).terminal((Rule<BreakInsnNode>) (ctx, insn) -> {
                    emit.apply(ctx, (I) insn);
                    ctx.visitJumpInsn(jumpInsn, ctx.getLabel(insn.label));
                })
                .terminal((ctx, insn) -> {
                    emit.apply(ctx, (I) insn);
                    ctx.ifThenElse(jumpInsn, makeList(new InsnNode(ICONST_0)), makeList(new InsnNode(ICONST_1)));
                });
    }

    private static int invertJump(int opcode) {
        return opcode % 2 == 1 ? opcode + 1 : opcode - 1;
    }

    private interface Rule<T> {
        static <T> Rule<T> noop() {
            return (ctx, insn) -> {
            };
        }

        static <T> Rule<T> insn(int opcode) {
            return (ctx, insn) -> ctx.visitInsn(opcode);
        }

        static <T> Rule<T> insn(int... opcodes) {
            return (ctx, insn) -> {
                for (int opcode : opcodes) {
                    ctx.visitInsn(opcode);
                }
            };
        }

        static <T> Rule<T> consumer(Consumer<Context> consumer) {
            return (ctx, insn) -> consumer.accept(ctx);
        }

        static <T> Rule<T> staticRule(String owner, String name, String signature) {
            return consumer(staticNode(owner, name, signature)::accept);
        }

        @SafeVarargs
        static <T> Rule<T> comp(Rule<T>... rules) {
            return (ctx, insn) -> {
                for (Rule<T> rule : rules) {
                    rule.apply(ctx, insn);
                }
            };
        }

        default Rule<T> comp(Rule<T> o) {
            return comp(this, o);
        }

        void apply(Context ctx, T insn);

        default void applyTo(Context ctx, List<T> matched) {
            apply(ctx, matched.get(matched.size() - 1));
        }
    }
}
