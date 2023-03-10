package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.conf.Conventions;
import io.github.eutro.wasm2j.conf.api.WirJavaConventionFactory;
import io.github.eutro.wasm2j.events.*;
import io.github.eutro.wasm2j.passes.Passes;
import io.github.eutro.wasm2j.passes.convert.JirToJava;
import io.github.eutro.wasm2j.passes.convert.WasmToWir;
import io.github.eutro.wasm2j.passes.convert.WirToJir;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.ssa.Module;
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
                new ModifyConventionsEvent(Conventions.createBuilder()))
                .conventionBuilder
                .build();

        Module wir = WasmToWir.INSTANCE.run(node);
        wir = dispatch(WirPassesEvent.class, new WirPassesEvent(wir)).wir;

        Module jir = new WirToJir(conventions).run(wir);
        jir = dispatch(JirPassesEvent.class, new JirPassesEvent(jir)).jir;

        jir = ForPass.liftFunctions(Passes.SSA_OPTS)
                .then(ForPass.liftFunctions(Passes.JAVA_PREEMIT))
                .run(jir);
        jir = dispatch(JavaPreemitEvent.class, new JavaPreemitEvent(jir)).jir;

        ClassNode classNode = JirToJava.INSTANCE.run(jir);
        dispatch(EmitClassEvent.class, new EmitClassEvent(classNode));
    }

    public ModuleCompilation setName(String name) {
        listen(ModifyConventionsEvent.class, mce -> mce.conventionBuilder.setNameSupplier(() -> name));
        return this;
    }
}
