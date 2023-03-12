package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.conf.api.WirJavaConventionFactory;
import io.github.eutro.wasm2j.events.*;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.passes.Passes;
import io.github.eutro.wasm2j.passes.convert.JirToJava;
import io.github.eutro.wasm2j.passes.convert.WasmToWir;
import io.github.eutro.wasm2j.passes.convert.WirToJir;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.util.Lazy;
import org.objectweb.asm.tree.ClassNode;

public class ModuleCompilation extends EventSupplier<ModuleCompileEvent> {
    private final WasmCompiler cc;
    public ModuleNode node;

    public ModuleCompilation(WasmCompiler cc, ModuleNode node) {
        this.cc = cc;
        this.node = node;
    }

    public void run() {
        cc.dispatch(RunModuleCompilationEvent.class, new RunModuleCompilationEvent(this));
        WirJavaConventionFactory conventions = dispatch(ModifyConventionsEvent.class,
                new ModifyConventionsEvent(WirJavaConventionFactory.builder()))
                .conventionBuilder
                .build();

        Module wir = WasmToWir.INSTANCE.run(node);
        wir = dispatch(WirPassesEvent.class, new WirPassesEvent(wir)).wir;

        JClass jir = new WirToJir(conventions).run(wir);
        jir = dispatch(JirPassesEvent.class, new JirPassesEvent(jir)).jir;

        for (JClass.JavaMethod method : jir.methods) {
            Lazy<Function> impl = method.getNullable(JavaExts.METHOD_IMPL);
            if (impl != null) {
                impl.mapInPlace(func -> Passes.SSA_OPTS.then(Passes.JAVA_PREEMIT).run(func));
            }
        }
        jir = dispatch(JavaPreemitEvent.class, new JavaPreemitEvent(jir)).jir;

        ClassNode classNode = JirToJava.INSTANCE.run(jir);
        dispatch(EmitClassEvent.class, new EmitClassEvent(classNode));
    }

    public ModuleCompilation setName(String name) {
        listen(ModifyConventionsEvent.class, mce -> mce.conventionBuilder.setNameSupplier(() -> name));
        return this;
    }
}
