package io.github.eutro.wasm2j.ssa;

/**
 * An IR, or instruction, builder, which encapsulates a position in a function
 * where instructions are being inserted.
 */
public class IRBuilder {
    /**
     * The function being inserted into.
     */
    public Function func;
    private BasicBlock bb;

    /**
     * Construct an instruction builder, inserting into
     * a specific basic block.
     *
     * @param func The function.
     * @param bb One of the functions basic blocks.
     */
    public IRBuilder(Function func, BasicBlock bb) {
        this.func = func;
        this.bb = bb;
    }

    /**
     * Get the block this builder is inserting at the end of.
     *
     * @return The block.
     */
    public BasicBlock getBlock() {
        return bb;
    }

    /**
     * Set the block this builder should insert at the end of.
     *
     * @param bb The block.
     */
    public void setBlock(BasicBlock bb) {
        this.bb = bb;
    }

    /**
     * Insert an effect at the end of the block.
     *
     * @param effect The effect.
     */
    public void insert(Effect effect) {
        bb.addEffect(effect);
    }

    /**
     * Assign the result of the instruction to a variable,
     * and insert the effect.
     *
     * @param insn The instruction.
     * @param v The variable.
     * @return The same variable.
     */
    public Var insert(Insn insn, Var v) {
        insert(insn.assignTo(v));
        return v;
    }

    /**
     * Assign the result of the instruction to a new variable,
     * and insert the effect.
     *
     * @param insn The instruction.
     * @param name The name of variable.
     * @return The assigned variable.
     */
    public Var insert(Insn insn, String name) {
        return insert(insn, func.newVar(name));
    }

    /**
     * Insert a control instruction at the end of the current block.
     *
     * @param ctrl The instruction to insert.
     */
    public void insertCtrl(Control ctrl) {
        bb.setControl(ctrl);
    }
}
