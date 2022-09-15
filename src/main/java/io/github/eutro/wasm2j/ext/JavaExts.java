package io.github.eutro.wasm2j.ext;

import io.github.eutro.wasm2j.ssa.Function;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JavaExts {
    public static final Ext<Function> METHOD_IMPL = Ext.create(Function.class);
    public static final Ext<Type> TYPE = Ext.create(Type.class);
    public static final Ext<String> FUNCTION_DESCRIPTOR = Ext.create(String.class);
    public static final Ext<JavaClass> FUNCTION_OWNER = Ext.create(JavaClass.class);

    public static Type BOTTOM_TYPE = Type.getType(Void.class);

    public static class JavaClass extends ExtHolder {
        public String name; // internal name
        public List<JavaMethod> methods = new ArrayList<>();
        public List<JavaField> fields = new ArrayList<>();

        public JavaClass(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name.replace('/', '.');
        }
    }

    public static class JavaMethod extends ExtHolder {
        public JavaClass owner;
        public String name, descriptor;
        public Type type;

        public JavaMethod(JavaClass owner, String name, String descriptor, Type type) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.type = type;
        }

        public enum Type {
            STATIC(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, Opcodes.INVOKESTATIC),
            STATIC_PRIVATE(Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE, Opcodes.INVOKESTATIC),
            VIRTUAL(Opcodes.ACC_PUBLIC, Opcodes.INVOKEVIRTUAL),
            FINAL(Opcodes.ACC_PRIVATE, Opcodes.INVOKESPECIAL),
            ;

            public final int access;
            public final int opcode;

            Type(int access, int opcode) {
                this.access = access;
                this.opcode = opcode;
            }
        }

        @Override
        public String toString() {
            return String.format("%s %s.%s%s",
                    type.toString().toLowerCase(Locale.ROOT),
                    owner,
                    name,
                    descriptor);
        }
    }

    public static class JavaField extends ExtHolder {
        public JavaClass owner;
        public String name, descriptor;
        public boolean isStatic;

        public JavaField(JavaClass owner, String name, String descriptor, boolean isStatic) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.isStatic = isStatic;
        }

        @Override
        public String toString() {
            return String.format("%s%s.%s %s",
                    isStatic ? "static " : "",
                    owner,
                    name,
                    descriptor);
        }
    }

    public static final Ext<JavaClass> JAVA_CLASS = Ext.create(JavaClass.class);
}
