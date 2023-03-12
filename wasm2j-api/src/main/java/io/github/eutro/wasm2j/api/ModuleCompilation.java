package io.github.eutro.wasm2j.api;

import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.api.events.*;
import io.github.eutro.wasm2j.core.conf.itf.WirJavaConventionFactory;
import io.github.eutro.wasm2j.core.ext.JavaExts;
import io.github.eutro.wasm2j.core.passes.Passes;
import io.github.eutro.wasm2j.core.passes.convert.JirToJava;
import io.github.eutro.wasm2j.core.passes.convert.WasmToWir;
import io.github.eutro.wasm2j.core.passes.convert.WirToJir;
import io.github.eutro.wasm2j.core.ssa.Function;
import io.github.eutro.wasm2j.core.ssa.JClass;
import io.github.eutro.wasm2j.core.ssa.Module;
import io.github.eutro.wasm2j.core.util.Lazy;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;

import java.util.function.Supplier;

/**
 * Represents the compilation of a single (already parsed and validated) WebAssembly module.
 * <p>
 * Compilation, performed when {@link #run()} is called, currently takes place roughly as follows:
 * <ol>
 *     <li>{@link RunModuleCompilationEvent} is fired on the {@link WasmCompiler compiler}.</li>
 *     <li>{@link ModifyConventionsEvent} is fired.</li>
 *     <li>The WebAssembly module is {@link WasmToWir compiled to WebAssembly IR}.</li>
 *     <li>{@link WirPassesEvent} is fired.</li>
 *     <li>The WebAssembly IR is {@link WirToJir converted to Java IR} according to the conventions.</li>
 *     <li>{@link JirPassesEvent} is fired.</li>
 *     <li>Minor optimisations are run.</li>
 *     <li>{@link Passes#JAVA_PREEMIT Stackification and register allocation} are performed.</li>
 *     <li>{@link JavaPreemitEvent} is fired.</li>
 *     <li>Java IR is {@link JirToJava compiled to Java bytecode}.</li>
 *     <li>{@link EmitClassEvent} is fired.</li>
 * </ol>
 * With the exception that passes are applied <i>lazily</i>. That is, most of the passes
 * and conversions are not actually run until the final {@link JirToJava IR to Java bytecode} compilation.
 * This is done to reduce the maximum memory footprint of the compilation, and in the future to facilitate threading.
 */
public class ModuleCompilation extends EventSupplier<ModuleCompileEvent> {
    private final WasmCompiler cc;

    /**
     * The module being compiled.
     */
    @NotNull
    public ModuleNode node;

    /**
     * Construct a new module compilation in the given compiler for the given node.
     *
     * @param cc   The compiler.
     * @param node The module being compiled.
     */
    ModuleCompilation(WasmCompiler cc, @NotNull ModuleNode node) {
        this.cc = cc;
        this.node = node;
    }

    /**
     * Run the compilation.
     * <p>
     * See the documentation of this class for details.
     */
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

    /**
     * Set the name of the class produced by this compilation, by
     * {@link ModifyConventionsEvent modifying the conventions}.
     *
     * @param name The internal name of the class.
     * @return This, for convenience.
     * @see WirJavaConventionFactory.Builder#setNameSupplier(Supplier)
     */
    public ModuleCompilation setName(String name) {
        listen(ModifyConventionsEvent.class, mce -> mce.conventionBuilder.setNameSupplier(() -> name));
        return this;
    }
}
