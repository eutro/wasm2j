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
                .match(Opcodes.NOP).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> { /* NOP */ })
                .match(BLOCK).terminal((Rule<BlockInsnNode>) (ctx, insn) -> ctx.pushBlock(new Block.BBlock(insn.blockType)))
                .match(LOOP).terminal((Rule<BlockInsnNode>) (ctx, insn) ->
                ctx.pushBlock(new Block.Loop(insn.blockType, ctx.mark())))
                .match(IF).terminal((Rule<BlockInsnNode>) (ctx, insn) -> {
            Block.If block = new Block.If(insn.blockType, ctx.getFrame());
            ctx.visitJumpInsn(IFEQ, block.elseLabel);
            ctx.pushBlock(block);
        })
                .match(ELSE).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> {
            Block.If ifBlock = (Block.If) ctx.peekBlock();
            ctx.goTo(ifBlock.endLabel());
            ifBlock.frame.accept(ctx);
            ctx.mark(ifBlock.elseLabel);
        })
                .match(END).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> {
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
                .match(Opcodes.RETURN).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> {
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
                .match(REF_NULL).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> {
            Type.getType(Object.class);
            ctx.push((String) null);
        })
                .match(REF_IS_NULL).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.jumpStack(IFNULL))
                .match(REF_FUNC).terminal((Rule<FuncRefInsnNode>) (ctx, insn) -> {
            Type.getType(MethodHandle.class);
            ctx.externs.funcs.get(insn.function).emitGet(ctx);
        });
        // endregion
        // region Parametric
        Rule<Object> select = (ctx, insn) -> {
            // WASM: if the top stack value is not 0, keep the bottom value, otherwise the top value.
            // JVM: if the top stack value is not zero, pop the top value, otherwise swap before popping
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
                .match(DROP).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> {
            List<Object> stack1 = ctx.getFrame().stack;
            if (stack1.get(stack1.size() - 1).equals(TOP)) {
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
            TypedExtern global1 = ctx.externs.globals.get(insn.variable);
            Types.toJava(global1.type());
            global1.emitGet(ctx);
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
            TypedExtern table1 = ctx.externs.tables.get(insn.table);
            table1.emitGet(ctx);
            ctx.dupX2();
            ctx.pop();
            ctx.arrayStore(Types.toJava(table1.type()));
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
                .match(MEMORY_SIZE).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> {
            ctx.externs.mems.get(0).emitGet(ctx);
            ctx.addInsns(virtualNode("java/nio/ByteBuffer", "capacity", "()I"));
            ctx.push(PAGE_SIZE);
            ctx.visitInsn(IDIV);
        })
                .match(MEMORY_GROW).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> {
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
        MethodInsnNode compareUnsignedI = staticNode("java/lang/Integer", "compareUnsigned", "(II)I");
        b
                .match(I32_EQZ).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.jumpStack(IFEQ))
                .match(I32_EQ).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.jumpStack(IF_ICMPEQ))
                .match(I32_NE).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.jumpStack(IF_ICMPNE))
                .match(I32_LT_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.jumpStack(IF_ICMPLT))
                .match(I32_LT_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(compareUnsignedI.clone(null)).jumpStack(IFLT))
                .match(I32_GT_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.jumpStack(IF_ICMPGT))
                .match(I32_GT_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(compareUnsignedI.clone(null)).jumpStack(IFGT))
                .match(I32_LE_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .jumpStack(IF_ICMPLE))
                .match(I32_LE_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(compareUnsignedI.clone(null)).jumpStack(IFLE))
                .match(I32_GE_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.jumpStack(IF_ICMPGE))
                .match(I32_GE_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(compareUnsignedI.clone(null)).jumpStack(IFGE));
        // endregion
        // region i64
        MethodInsnNode compareUnsignedL = staticNode("java/lang/Long", "compareUnsigned", "(JJ)I");
        b
                .match(I64_EQZ).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(L2I)).jumpStack(IFEQ))
                .match(I64_EQ).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(LCMP)).jumpStack(IFEQ))
                .match(I64_NE).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(LCMP)).jumpStack(IFNE))
                .match(I64_LT_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(LCMP)).jumpStack(IFLT))
                .match(I64_LT_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(compareUnsignedL.clone(null)).jumpStack(IFLT))
                .match(I64_GT_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(new InsnNode(LCMP)).jumpStack(IFGT))
                .match(I64_GT_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(compareUnsignedL.clone(null)).jumpStack(IFGT))
                .match(I64_LE_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(new InsnNode(LCMP)).jumpStack(IFLE))
                .match(I64_LE_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(compareUnsignedL.clone(null)).jumpStack(IFLE))
                .match(I64_GE_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(new InsnNode(LCMP)).jumpStack(IFGE))
                .match(I64_GE_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(compareUnsignedL.clone(null)).jumpStack(IFGE));
        // endregion
        // region f32
        b
                .match(F32_EQ).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(FCMPG)).jumpStack(IFEQ))
                .match(F32_NE).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(FCMPG)).jumpStack(IFNE))
                .match(F32_LT).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(FCMPG)).jumpStack(IFLT))
                .match(F32_GT).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(FCMPL)).jumpStack(IFGT))
                .match(F32_LE).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(FCMPG)).jumpStack(IFLE))
                .match(F32_GE).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(FCMPL)).jumpStack(IFGE));
        // endregion
        // region f64
        b
                .match(F64_EQ).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(DCMPG)).jumpStack(IFEQ))
                .match(F64_NE).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(DCMPG)).jumpStack(IFNE))
                .match(F64_LT).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(DCMPG)).jumpStack(IFLT))
                .match(F64_GT).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(DCMPL)).jumpStack(IFGT))
                .match(F64_LE).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(DCMPG)).jumpStack(IFLE))
                .match(F64_GE).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(DCMPL)).jumpStack(IFGE));
        // endregion
        // endregion
        // endregion
        // region Mathematical
        // region i32
        b
                .match(I32_CLZ).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Integer", "numberOfLeadingZeros", "(I)I")))
                .match(I32_CTZ).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Integer", "numberOfTrailingZeros", "(I)I")))
                .match(I32_POPCNT).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Integer", "numberOfTrailingZeros", "(I)I")))
                .match(I32_ADD).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(IADD)))
                .match(I32_SUB).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(ISUB)))
                .match(I32_MUL).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(IMUL)))
                .match(I32_DIV_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(IDIV)))
                .match(I32_DIV_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Integer", "divideUnsigned", "(II)I")))
                .match(I32_REM_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(IREM)))
                .match(I32_REM_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Integer", "remainderUnsigned", "(II)I")))
                .match(I32_AND).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(IAND)))
                .match(I32_OR).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(IOR)))
                .match(I32_XOR).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(IXOR)))
                .match(I32_SHL).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(ISHL)))
                .match(I32_SHR_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(ISHR)))
                .match(I32_SHR_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(IUSHR)))
                .match(I32_ROTL).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Integer", "rotateLeft", "(II)I")))
                .match(I32_ROTR).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Integer", "rotateRight", "(II)I")));
        // endregion
        // region i64
        b
                .match(I64_CLZ).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Long", "numberOfLeadingZeros", "(J)I"), new InsnNode(I2L)))
                .match(I64_CTZ).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Long", "numberOfTrailingZeros", "(J)I"), new InsnNode(I2L)))
                .match(I64_POPCNT).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Long", "bitCount", "(J)I"), new InsnNode(I2L)))
                .match(I64_ADD).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(LADD)))
                .match(I64_SUB).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(LSUB)))
                .match(I64_MUL).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(LMUL)))
                .match(I64_DIV_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(LDIV)))
                .match(I64_DIV_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Long", "divideUnsigned", "(JJ)J")))
                .match(I64_REM_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(LREM)))
                .match(I64_REM_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Long", "remainderUnsigned", "(JJ)J")))
                .match(I64_AND).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(LAND)))
                .match(I64_OR).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(LOR)))
                .match(I64_XOR).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(LXOR)))
                .match(I64_SHL).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(LSHL)))
                .match(I64_SHR_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(LSHR)))
                .match(I64_SHR_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(LUSHR)))
                .match(I64_ROTL).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(new InsnNode(L2I), staticNode("java/lang/Long", "rotateLeft", "(JI)J")))
                .match(I64_ROTR).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(new InsnNode(L2I), staticNode("java/lang/Long", "rotateRight", "(JI)J")));
        // endregion
        // region f32
        b
                .match(F32_ABS).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Math", "abs", "(F)F")))
                .match(F32_NEG).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(FNEG)))
                .match(F32_CEIL).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(
                new InsnNode(F2D),
                staticNode("java/lang/Math", "ceil", "(D)D"),
                new InsnNode(D2F)))
                .match(F32_FLOOR).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(
                new InsnNode(F2D),
                staticNode("java/lang/Math", "floor", "(D)D"),
                new InsnNode(D2F)))
                .match(F32_TRUNC).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> {
            LabelNode els1 = new LabelNode();
            LabelNode end1 = new LabelNode();
            ctx.addInsns(
                    new InsnNode(F2D),
                    new InsnNode(DUP2),
                    new InsnNode(DCMPG),
                    new JumpInsnNode(IFLT, els1),
                    staticNode("java/lang/Math", "floor", "(D)D"),
                    new JumpInsnNode(GOTO, end1),
                    els1,
                    staticNode("java/lang/Math", "ceil", "(D)D"),
                    end1,
                    new InsnNode(D2F));
        })
                .match(F32_NEAREST).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> {
            // TODO I can't think of a clean way to do this
            throw new UnsupportedOperationException();
        })
                .match(F32_SQRT).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(
                new InsnNode(F2D),
                staticNode("java/lang/Math", "sqrt", "(D)D"),
                new InsnNode(D2F)))
                .match(F32_ADD).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(FADD)))
                .match(F32_SUB).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(FSUB)))
                .match(F32_MUL).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(FMUL)))
                .match(F32_DIV).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(FDIV)))
                .match(F32_MIN).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Math", "min", "(FF)F")))
                .match(F32_MAX).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Math", "max", "(FF)F")))
                .match(F32_COPYSIGN).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Math", "copySign", "(FF)F")));
        // endregion
        // region f64
        b
                .match(F64_ABS).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Math", "abs", "(D)D")))
                .match(F64_NEG).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(DNEG)))
                .match(F64_CEIL).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Math", "ceil", "(D)D")))
                .match(F64_FLOOR).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Math", "floor", "(D)D")))
                .match(F64_TRUNC).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> {
            LabelNode els = new LabelNode();
            LabelNode end = new LabelNode();
            ctx
                    .addInsns(
                            new InsnNode(DUP2),
                            new InsnNode(DCMPG),
                            new JumpInsnNode(IFLT, els),
                            staticNode("java/lang/Math", "floor", "(D)D"),
                            new JumpInsnNode(GOTO, end),
                            els,
                            staticNode("java/lang/Math", "ceil", "(D)D"),
                            end);
        })
                // TODO I can't think of a clean way to do this
                .match(F64_NEAREST).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> {
            // TODO I can't think of a clean way to do this
            throw new UnsupportedOperationException();
        })
                .match(F64_SQRT).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Math", "sqrt", "(D)D")))
                .match(F64_ADD).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(DADD)))
                .match(F64_SUB).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(DSUB)))
                .match(F64_MUL).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(DMUL)))
                .match(F64_DIV).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.addInsns(new InsnNode(DDIV)))
                .match(F64_MIN).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Math", "min", "(DD)D")))
                .match(F64_MAX).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Math", "max", "(DD)D")))
                .match(F64_COPYSIGN).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Math", "copySign", "(DD)D")));
        // endregion
        // endregion
        // region Conversions
        b
                .match(I32_WRAP_I64).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(L2I))
                // FIXME these truncations are actually saturating, while they should cause a trap instead
                .match(I32_TRUNC_F32_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(F2I))
                .match(I32_TRUNC_F32_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(F2I))
                .match(I32_TRUNC_F64_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(D2I))
                .match(I32_TRUNC_F64_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(D2I))
                .match(I64_EXTEND_I32_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(I2L))
                .match(I64_EXTEND_I32_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Integer", "toUnsignedLong", "(I)J")))
                .match(I64_TRUNC_F32_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(F2L))
                .match(I64_TRUNC_F32_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(F2L))
                .match(I64_TRUNC_F64_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(D2L))
                .match(I64_TRUNC_F64_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(D2L))
                .match(F32_CONVERT_I32_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(I2F))
                .match(F32_CONVERT_I32_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(I2F))
                .match(F32_CONVERT_I64_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(L2F))
                .match(F32_CONVERT_I64_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(L2F))
                .match(F32_DEMOTE_F64).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(D2F))
                .match(F64_CONVERT_I32_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(I2D))
                .match(F64_CONVERT_I32_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(I2D))
                .match(F64_CONVERT_I64_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(L2D))
                .match(F64_CONVERT_I64_U).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(L2D))
                .match(F64_PROMOTE_F32).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(F2D))
                .match(I32_REINTERPRET_F32).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Float", "floatToRawIntBits", "(F)I")))
                .match(I64_REINTERPRET_F64).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Double", "doubleToRawLongBits", "(D)J")))
                .match(F32_REINTERPRET_I32).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Float", "intBitsToFloat", "(I)F")))
                .match(F64_REINTERPRET_I64).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(staticNode("java/lang/Double", "longBitsToDouble", "(J)D")));
        // endregion
        // region Extension
        b
                .match(I32_EXTEND8_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(I2B))
                .match(I32_EXTEND16_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx.visitInsn(I2S))
                .match(I64_EXTEND8_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(new InsnNode(L2I), new InsnNode(I2B), new InsnNode(I2L)))
                .match(I64_EXTEND16_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(new InsnNode(L2I), new InsnNode(I2S), new InsnNode(I2L)))
                .match(I64_EXTEND32_S).terminal((Rule<AbstractInsnNode>) (ctx, insn) -> ctx
                .addInsns(new InsnNode(L2I), new InsnNode(I2L)));
        // endregion
        // region Saturating Truncation
        b
                .match(I32_TRUNC_SAT_F32_S).terminal((ctx, insn) -> ctx.visitInsn(F2I))
                .match(I32_TRUNC_SAT_F32_U).terminal((ctx, insn) -> ctx.visitInsn(F2I))
                .match(I32_TRUNC_SAT_F64_S).terminal((ctx, insn) -> ctx.visitInsn(D2I))
                .match(I32_TRUNC_SAT_F64_U).terminal((ctx, insn) -> ctx.visitInsn(D2I))
                .match(I64_TRUNC_SAT_F32_S).terminal((ctx, insn) -> ctx.visitInsn(F2L))
                .match(I64_TRUNC_SAT_F32_U).terminal((ctx, insn) -> ctx.visitInsn(F2L))
                .match(I64_TRUNC_SAT_F64_S).terminal((ctx, insn) -> ctx.visitInsn(D2L))
                .match(I64_TRUNC_SAT_F64_U).terminal((ctx, insn) -> ctx.visitInsn(D2L));
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

    private interface Rule<T> {
        void apply(Context ctx, T insn);

        default void applyTo(Context ctx, List<T> matched) {
            apply(ctx, matched.get(matched.size() - 1));
        }
    }
}
