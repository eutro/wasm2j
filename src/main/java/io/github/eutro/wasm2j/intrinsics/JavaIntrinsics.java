package io.github.eutro.wasm2j.intrinsics;

import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.wasm2j.util.InsnMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class JavaIntrinsics {
    public static final InsnMap<IntrinsicImpl> INTRINSICS = new InsnMap<>();

    private static final ClassNode IMPL_NODE = new ClassNode();

    static {
        ImplClassBytes.getClassReader().accept(IMPL_NODE, ClassReader.SKIP_DEBUG);

        for (MethodNode method : IMPL_NODE.methods) {
            if (method.visibleAnnotations == null) continue;
            AnnotationNode intrinsicAnnot = null;
            for (AnnotationNode annot : method.visibleAnnotations) {
                if (Type.getDescriptor(Intrinsic.class).equals(annot.desc)) {
                    intrinsicAnnot = annot;
                    break;
                }
            }
            if (intrinsicAnnot == null) {
                continue;
            }

            if (intrinsicAnnot.values == null) {
                throw new IllegalStateException(String.format("Method %s's intrinsic annotation has no parameters!", method.name));
            }

            Map<String, Object> annotAsMap = new HashMap<>();
            Iterator<Object> it = intrinsicAnnot.values.iterator();
            while (it.hasNext()) {
                annotAsMap.put((String) it.next(), it.next());
            }

            boolean inline = (boolean) annotAsMap.getOrDefault("inline", true);
            IntrinsicImpl impl = new IntrinsicImpl(method, inline);

            if (annotAsMap.containsKey("iOp")) {
                byte bOp = (byte) annotAsMap.getOrDefault("value", Opcodes.INSN_PREFIX);
                int iOp = (int) annotAsMap.get("iOp");
                INTRINSICS.put(bOp, iOp, impl);
            } else if (annotAsMap.containsKey("value")) {
                byte op = (byte) annotAsMap.get("value");
                INTRINSICS.putByte(op, impl);
            } else {
                throw new IllegalStateException("Method %s's intrinsic annotation specifies neither value nor iOp.");
            }
        }
    }
}
