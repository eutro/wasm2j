package io.github.eutro.wasm2j.ssa;

public class IRBuilder {
    public Function func;
    private BasicBlock bb;

    public IRBuilder(Function func, BasicBlock bb) {
        this.func = func;
        this.bb = bb;
    }

    public BasicBlock getBlock() {
        return bb;
    }

    public void setBlock(BasicBlock bb) {
        this.bb = bb;
    }

    public void insert(Effect effect) {
        bb.addEffect(effect);
    }

    public Var insert(Insn insn, Var v) {
        insert(insn.assignTo(v));
        return v;
    }

    public Var insert(Insn insn, String name) {
        return insert(insn, func.newVar(name));
    }

    public void insertCtrl(Control ctrl) {
        bb.setControl(ctrl);
    }
}
