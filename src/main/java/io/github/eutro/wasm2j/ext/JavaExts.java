package io.github.eutro.wasm2j.ext;

import java.util.Locale;

public class JavaExts {
    public static class JavaClass {
        public String name; // internal name

        public JavaClass(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name.replace('/', '.');
        }
    }

    public static class JavaMethod {
        public final JavaClass owner;
        public final String name, descriptor;
        public final Type type;

        public JavaMethod(JavaClass owner, String name, String descriptor, Type type) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.type = type;
        }

        public enum Type {
            STATIC,
            VIRTUAL,
            FINAL,
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

    public static class JavaField {
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
