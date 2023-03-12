package io.github.eutro.wasm2j.api.bits;

import io.github.eutro.jwasm.ByteInputStream;
import io.github.eutro.jwasm.tree.CustomNode;
import io.github.eutro.wasm2j.core.conf.impl.InstanceFunctionConvention;
import io.github.eutro.wasm2j.api.events.EventSupplier;
import io.github.eutro.wasm2j.api.events.ModifyConventionsEvent;
import io.github.eutro.wasm2j.api.events.RunModuleCompilationEvent;
import io.github.eutro.wasm2j.core.ssa.JClass;
import io.github.eutro.wasm2j.api.support.NameMangler;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A bit which parses the name section of a module, and
 * renames the Java methods to match the names given in the section.
 *
 * @param <T> The type of compiler this can be attached to.
 * @see NameSection
 */
public class NameSectionParser<T extends EventSupplier<? super RunModuleCompilationEvent>>
        implements Bit<T, Void> {
    @Override
    public Void addTo(T cc) {
        cc.listen(RunModuleCompilationEvent.class, mce -> {
            CustomNode nameSection = null;
            found:
            for (List<CustomNode> customs : mce.compilation.node.customs) {
                if (customs != null) {
                    for (CustomNode custom : customs) {
                        if (custom.name.equals("name")) {
                            nameSection = custom;
                            break found;
                        }
                    }
                }
            }
            if (nameSection == null) {
                return;
            }
            ByteInputStream.ByteBufferByteInputStream bis = new ByteInputStream
                    .ByteBufferByteInputStream(ByteBuffer.wrap(nameSection.data));

            NameSection section = NameSection.parse(bis); // may throw
            if (section.functionNames != null) {
                mce.compilation.listen(ModifyConventionsEvent.class, mcve -> mcve.conventionBuilder
                        .setModifyFuncConvention((functionConvention, funcNodeCodeNodePair, index) -> {
                            String funcName = section.functionNames.get(index);
                            if (funcName != null) {
                                JClass.JavaMethod method = functionConvention
                                        .getExtOrThrow(InstanceFunctionConvention.CONVENTION_METHOD);
                                method.name = NameMangler
                                        .jvmUnqualified(NameMangler.IllegalSymbolPolicy.MANGLE_BIJECTIVE)
                                        .mangle(funcName);
                            }
                            return functionConvention;
                        }));
            }
        });
        return null;
    }

    /**
     * A representation of the
     * <a href="https://webassembly.github.io/spec/core/appendix/custom.html#name-section">name section</a>
     * of a WebAssembly module.
     */
    public static class NameSection {
        /**
         * The module name.
         */
        public final @Nullable String moduleName;
        /**
         * The function name map.
         */
        public final @Nullable SortedMap<Integer, String> functionNames;
        /**
         * The local indirect name map.
         */
        public final @Nullable SortedMap<Integer, SortedMap<Integer, String>> localNames;

        /**
         * Read the name section from a given input stream.
         *
         * @param bis The stream.
         * @param <E> The type of exception the stream throws.
         * @throws E If reading the stream fails.
         */
        public <E extends Exception> NameSection(ByteInputStream<E> bis) throws E {
            int id = bis.get();
            if (id == -1) {
                moduleName = null;
                functionNames = null;
                localNames = null;
                return;
            }
            if (id == 0) {
                ByteInputStream<E> ss = bis.sectionStream();
                moduleName = ss.getName();
                ss.expectEmpty();
                id = bis.get();
            } else {
                moduleName = null;
            }

            if (id == -1) {
                functionNames = null;
                localNames = null;
                return;
            }
            if (id == 1) {
                ByteInputStream<E> ss = bis.sectionStream();
                functionNames = parseNameMap(ss);
                ss.expectEmpty();
                id = bis.get();
            } else {
                functionNames = null;
            }

            if (id == -1) {
                localNames = null;
                return;
            }
            if (id == 2) {
                ByteInputStream<E> ss = bis.sectionStream();
                localNames = parseIndirectNameMap(ss);
                ss.expectEmpty();
                id = bis.get();
            } else {
                localNames = null;
            }

            //if (id != -1) {
            //    throw new ValidationException(String.format("too many or out of order sections in name section (id: %d)", id));
            //}
        }

        private <E extends Exception> SortedMap<Integer, SortedMap<Integer, String>>
        parseIndirectNameMap(ByteInputStream<E> bis) throws E {
            TreeMap<Integer, SortedMap<Integer, String>> map = new TreeMap<>();
            int len = bis.getVarUInt32();
            for (int i = 0; i < len; i++) {
                map.put(bis.getVarUInt32(), parseNameMap(bis));
            }
            return map;
        }

        private <E extends Exception> SortedMap<Integer, String> parseNameMap(ByteInputStream<E> bis) throws E {
            TreeMap<Integer, String> map = new TreeMap<>();
            int len = bis.getVarUInt32();
            for (int i = 0; i < len; i++) {
                map.put(bis.getVarUInt32(), bis.getName());
            }
            return map;
        }

        /**
         * Read the name section from a given input stream.
         *
         * @param bis The stream.
         * @param <E> The type of exception the stream throws.
         * @return The parsed name section.
         * @throws E If reading the stream fails.
         */
        public static <E extends Exception> NameSection parse(ByteInputStream<E> bis) throws E {
            return new NameSection(bis);
        }
    }
}
