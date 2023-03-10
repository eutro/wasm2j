package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.DelegatingExtHolder;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.ExtContainer;
import io.github.eutro.wasm2j.ops.Op;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class Insn extends DelegatingExtHolder implements Iterable<Var> {
    public static boolean TRACK_INSN_CREATIONS = System.getenv("WASM2J_TRACK_INSN_CREATIONS") != null;

    public Throwable created = TRACK_INSN_CREATIONS ? new Throwable("constructed") : null;
    public Op op;

    // null if empty,
    // Var if unary,
    // Var[] otherwise.
    // Micro-optimising the size of this is worth it because there are millions of these.
    private Object args;

    public Insn(Op op, List<Var> args) {
        this.op = op;
        switch (args.size()) {
            case 0:
                this.args = null;
                break;
            case 1:
                this.args = args.get(0);
                break;
            default:
                this.args = args.toArray(new Var[0]);
                break;
        }
    }

    public Insn(Op op, Var... args) {
        this(op, Arrays.asList(args));
    }

    @Override
    protected ExtContainer getDelegate() {
        return op;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(op);
        for (Var arg : args()) {
            sb.append(' ').append(arg);
        }
        return sb.toString();
    }

    public Effect assignTo(Var... vars) {
        return new Effect(new ArrayList<>(Arrays.asList(vars)), this);
    }

    public Effect assignTo(List<Var> vars) {
        return new Effect(vars, this);
    }

    public Control jumpsTo(BasicBlock... targets) {
        return jumpsTo(Arrays.asList(targets));
    }

    public Control jumpsTo(List<BasicBlock> targets) {
        return new Control(this, targets);
    }

    public Effect copyFrom(Effect fx) {
        return assignTo(new ArrayList<>(fx.getAssignsTo()));
    }

    // exts
    private Object owner = null;

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getNullable(Ext<T> ext) {
        if (ext == CommonExts.OWNING_EFFECT) {
            return owner instanceof Effect ? (T) owner : null;
        } else if (ext == CommonExts.OWNING_CONTROL) {
            return owner instanceof Control ? (T) owner : null;
        }
        return super.getNullable(ext);
    }

    @Override
    public <T> void attachExt(Ext<T> ext, T value) {
        if (ext == CommonExts.OWNING_EFFECT || ext == CommonExts.OWNING_CONTROL) {
            owner = value;
            return;
        }
        super.attachExt(ext, value);
    }

    @Override
    public <T> void removeExt(Ext<T> ext) {
        if (ext == CommonExts.OWNING_EFFECT || ext == CommonExts.OWNING_CONTROL) {
            owner = null;
            return;
        }
        super.removeExt(ext);
    }

    public List<Var> args() {
        return new Args();
    }

    @NotNull
    @Override
    public Iterator<Var> iterator() {
        if (args == null) return Collections.emptyIterator();
        if (args instanceof Var) return Collections.singletonList((Var) args).iterator();
        else return Arrays.asList((Var[]) args).iterator();
    }

    private class Args extends AbstractList<Var> implements List<Var> {
        int size;
        {
            if (args == null) size = 0;
            else if (args instanceof Var) size = 1;
            else size = ((Var[]) args).length;
        }

        @Override
        public Var get(int index) {
            if (args == null) throw new IndexOutOfBoundsException();
            if (args instanceof Var) {
                if (index != 0) throw new IndexOutOfBoundsException();
                return (Var) args;
            }
            Var[] vars = (Var[]) args;
            return vars[index];
        }

        @Override
        public Var set(int index, Var value) {
            if (args == null) throw new IndexOutOfBoundsException();
            if (args instanceof Var) {
                if (index != 0) throw new IndexOutOfBoundsException();
                Var old = (Var) args;
                args = value;
                return old;
            }
            Var[] vars = (Var[]) args;
            Var old = vars[index];
            vars[index] = value;
            return old;
        }

        @Override
        public boolean add(Var var) {
            if (args == null) {
                args = var;
            } else if (args instanceof Var) {
                Var old = (Var) args;
                args = new Var[] { old, var };
            } else {
                Var[] oldArgs = (Var[]) args;
                Var[] newArgs = Arrays.copyOf(oldArgs, oldArgs.length + 1);
                newArgs[oldArgs.length] = var;
                args = newArgs;
            }
            size++;
            return true;
        }

        @Override
        public Var remove(int index) {
            if (args == null) {
                throw new IndexOutOfBoundsException();
            }
            size--;
            if (args instanceof Var) {
                if (index != 0) throw new IndexOutOfBoundsException();
                Var old = (Var) args;
                args = null;
                return old;
            } else {
                Var[] oldArgs = (Var[]) args;
                int len = oldArgs.length;
                if (index < 0 || index >= len) throw new IndexOutOfBoundsException();
                Var old = oldArgs[index];
                if (len == 2) {
                    args = oldArgs[1 - index];
                } else {
                    // I copied this from CopyOnWriteArrayList it's definitely correct
                    Var[] newArgs = new Var[len - 1];
                    int numMoved = len - index - 1;
                    System.arraycopy(oldArgs, 0, newArgs, 0, index);
                    System.arraycopy(oldArgs, index + 1, newArgs, index, numMoved);
                    args = newArgs;
                }
                return old;
            }
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public @NotNull Iterator<Var> iterator() {
            return Insn.this.iterator();
        }
    }
}
