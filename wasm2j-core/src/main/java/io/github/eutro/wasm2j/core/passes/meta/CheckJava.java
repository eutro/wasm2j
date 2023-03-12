package io.github.eutro.wasm2j.core.passes.meta;

import io.github.eutro.wasm2j.core.passes.InPlaceIRPass;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * A pass which checks the validity of compiled Java code
 * using {@link CheckClassAdapter}.
 *
 * @see CheckClassAdapter
 */
public class CheckJava implements InPlaceIRPass<ClassNode> {
    /**
     * A singleton instance of this class.
     */
    public static final CheckJava INSTANCE = new CheckJava();

    @Override
    public void runInPlace(ClassNode classNode) {
        classNode.accept(new CheckClassAdapter(null));
    }
}
