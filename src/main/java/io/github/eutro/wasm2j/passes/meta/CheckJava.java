package io.github.eutro.wasm2j.passes.meta;

import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;

public class CheckJava implements InPlaceIRPass<ClassNode> {
    public static final CheckJava INSTANCE = new CheckJava();

    @Override
    public void runInPlace(ClassNode classNode) {
        classNode.accept(new CheckClassAdapter(null));
    }
}
