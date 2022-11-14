package io.github.eutro.wasm2j.ext;

import io.github.eutro.wasm2j.ssa.Function;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class JavaExts {
    public static final Ext<Function> METHOD_IMPL = Ext.create(Function.class);
    public static final Ext<Type> TYPE = Ext.create(Type.class);
    public static final Ext<JavaMethod> FUNCTION_METHOD = Ext.create(JavaMethod.class);
    public static final Ext<JavaClass> FUNCTION_OWNER = Ext.create(JavaClass.class);

    public static Type BOTTOM_TYPE = Type.getType(Void.class);

    public static class JavaClass extends ExtHolder {
        public String name; // internal name
        public List<JavaMethod> methods = new ArrayList<>();
        public List<JavaField> fields = new ArrayList<>();

        public JavaClass(String name) {
            this.name = name;
        }

        public static JavaClass fromJava(Class<?> clazz) {
            JavaExts.JavaClass jClass = new JavaExts.JavaClass(Type.getInternalName(clazz));
            for (Method method : clazz.getMethods()) {
                if (method.isSynthetic()) continue;
                jClass.methods.add(JavaMethod.fromJava(jClass, method));
            }
            for (Field field : clazz.getFields()) {
                if (field.isSynthetic()) continue;
                jClass.fields.add(JavaField.fromJava(jClass, field));
            }
            return jClass;
        }

        @Override
        public String toString() {
            return name.replace('/', '.');
        }
    }

    public static class JavaMethod extends ExtHolder {
        public JavaClass owner;
        public String name;
        public Kind kind;

        private List<Type> paramTys;
        private Type returnTy;

        public JavaMethod(JavaClass owner, String name, String descriptor, Kind kind) {
            this.owner = owner;
            this.name = name;
            this.setDescriptor(descriptor);
            this.kind = kind;
        }

        public static JavaMethod fromJava(JavaClass owner, Method method) {
            return new JavaMethod(
                    owner,
                    method.getName(),
                    Type.getMethodDescriptor(method),
                    Modifier.isStatic(method.getModifiers())
                            ? Kind.STATIC
                            : Kind.VIRTUAL
            );
        }

        public static JavaMethod fromJava(Class<?> owner, String name, Class<?>... parameterTypes) {
            try {
                return fromJava(new JavaClass(Type.getInternalName(owner)),
                        owner.getMethod(name, parameterTypes));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        public List<Type> getParamTys() {
            return paramTys;
        }

        public void setParamTys(List<Type> tys) {
            paramTys = new ArrayList<>(tys);
        }

        public String getDescriptor() {
            return Type.getMethodDescriptor(
                    returnTy,
                    paramTys.toArray(new Type[0])
            );
        }

        public void setDescriptor(String descriptor) {
            returnTy = Type.getReturnType(descriptor);
            paramTys = new ArrayList<>(Arrays.asList(Type.getArgumentTypes(descriptor)));
        }

        public enum Kind {
            STATIC(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, Opcodes.INVOKESTATIC, Opcodes.H_INVOKESTATIC),
            STATIC_PRIVATE(Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE, Opcodes.INVOKESTATIC, Opcodes.H_INVOKESTATIC),
            VIRTUAL(Opcodes.ACC_PUBLIC, Opcodes.INVOKEVIRTUAL, Opcodes.H_INVOKEVIRTUAL),
            FINAL(Opcodes.ACC_PRIVATE, Opcodes.INVOKESPECIAL, Opcodes.H_INVOKESPECIAL),
            ABSTRACT(Opcodes.ACC_ABSTRACT | Opcodes.ACC_PROTECTED, Opcodes.INVOKEVIRTUAL, Opcodes.H_INVOKEVIRTUAL),
            ;

            public final int access;
            public final int opcode;
            public final int handleType;

            Kind(int access, int opcode, int handleType) {
                this.access = access;
                this.opcode = opcode;
                this.handleType = handleType;
            }
        }

        @Override
        public String toString() {
            return String.format("%s %s.%s%s",
                    kind.toString().toLowerCase(Locale.ROOT),
                    owner,
                    name,
                    getDescriptor());
        }
    }

    public static class JavaField extends ExtHolder {
        public JavaClass owner;
        public String name, descriptor;
        public boolean isStatic;
        public int otherAccess = Opcodes.ACC_PRIVATE;

        public JavaField(JavaClass owner, String name, String descriptor, boolean isStatic) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.isStatic = isStatic;
        }

        public static JavaField fromJava(JavaClass jClass, Field field) {
            return new JavaField(
                    jClass,
                    field.getName(),
                    Type.getDescriptor(field.getType()),
                    Modifier.isStatic(field.getModifiers())
            );
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
