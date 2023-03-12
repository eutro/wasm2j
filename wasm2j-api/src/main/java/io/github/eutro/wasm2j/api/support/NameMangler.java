package io.github.eutro.wasm2j.api.support;

import java.util.*;
import java.util.function.IntPredicate;

// TODO test

/**
 * A class for mangling names, converting (possibly invalid) tokens to new tokens
 * that <i>are</i> valid, according to a set of rules.
 */
public interface NameMangler {
    /**
     * The characters banned in a
     * <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.2.2">JVM unqualified name</a>.
     */
    String JVM_BANNED_CHARS = ".;[/<>";
    /**
     * The token used when the input token is empty.
     */
    String EMPTY_TOKEN = "_EMPTY_";

    /**
     * A name mangler which outputs valid JVM unqualified names.
     *
     * @param policy The policy for handling illegal characters.
     * @return The name mangler.
     */
    static NameMangler jvmUnqualified(IllegalSymbolPolicy policy) {
        return new BanChars(JVM_BANNED_CHARS, policy);
    }

    /**
     * A name mangler which outputs valid Java identifiers.
     *
     * @param policy The policy for handling illegal characters.
     * @return The name mangler.
     */
    static NameMangler javaIdent(IllegalSymbolPolicy policy) {
        return new JavaIdent(policy);
    }

    /**
     * Mangle a string, so it becomes a valid name according to the rules of this mangler.
     *
     * @param name The name to mangle
     * @return The mangled string.
     */
    String mangle(String name);

    /**
     * A policy for how illegal characters should be handled.
     */
    enum IllegalSymbolPolicy {
        /**
         * Illegal characters should be omitted.
         */
        OMIT,
        /**
         * Illegal characters should be mangled to their "_O%o_" formatting.
         */
        MANGLE,
        /**
         * Illegal characters should be mangled, but in a human-readable way where
         * possible.
         */
        MANGLE_SENSIBLE,
        /**
         * Illegal characters should be mangled, with a human-readable mangling where
         * possible, and no two distinct strings should mangle to the same thing.
         */
        MANGLE_BIJECTIVE,
        ;

        boolean shouldConvertAnyway(int c) {
            if (this == MANGLE_BIJECTIVE) {
                return c == '_';
            }
            return false;
        }

        boolean check(IntPredicate pred, int c) {
            return pred.test(c) && !shouldConvertAnyway(c);
        }

        void convert(Formatter into, int c) {
            if (this == OMIT) return;
            if (this == MANGLE_BIJECTIVE) {
                if (c == '_') {
                    into.format("__");
                    return;
                }
                if (c == '-') {
                    into.format("_DASH_");
                    return;
                }
            }
            if (this == MANGLE_SENSIBLE || this == MANGLE_BIJECTIVE) {
                String s = null;
                // taken from Clojure
                switch (c) {
                    // @formatter:off
                    case '-': s = "_"; break;
                    case '.': s = "_DOT_"; break;
                    case ':': s = "_COLON_"; break;
                    case '+': s = "_PLUS_"; break;
                    case '>': s = "_GT_"; break;
                    case '<': s = "_LT_"; break;
                    case '=': s = "_EQ_"; break;
                    case '~': s = "_TILDE_"; break;
                    case '!': s = "_BANG_"; break;
                    case '@': s = "_CIRCA_"; break;
                    case '#': s = "_SHARP_"; break;
                    case '\'': s = "_SINGLEQUOTE_"; break;
                    case '"': s = "_DOUBLEQUOTE_"; break;
                    case '%': s = "_PERCENT_"; break;
                    case '^': s = "_CARET_"; break;
                    case '&': s = "_AMPERSAND_"; break;
                    case '*': s = "_STAR_"; break;
                    case '|': s = "_BAR_"; break;
                    case '{': s = "_LBRACE_"; break;
                    case '}': s = "_RBRACE_"; break;
                    case '[': s = "_LBRACK_"; break;
                    case ']': s = "_RBRACK_"; break;
                    case '/': s = "_SLASH_"; break;
                    case '\\': s = "_BSLASH_"; break;
                    case '?': s = "_QMARK_"; break;
                    // extensions
                    case ';': s = "_SEMICOLON_"; break;
                    // @formatter:on
                }
                if (s != null) {
                    into.format("%s", s);
                    return;
                }
            }
            into.format("_O%o_", c);
        }
    }

    /**
     * A name mangler that forbids a certain set of characters.
     */
    class BanChars implements NameMangler {
        private final BitSet FORBIDDEN = new BitSet();
        private final IllegalSymbolPolicy policy;

        /**
         * Construct a character-banning name mangler that forbids the given characters.
         *
         * @param forbidden The forbidden characters.
         * @param policy    The policy for handling illegal characters.
         */
        public BanChars(String forbidden, IllegalSymbolPolicy policy) {
            this.policy = policy;
            for (char c : forbidden.toCharArray()) {
                FORBIDDEN.set(c);
            }
        }

        private boolean isAllowed(int c) {
            return !FORBIDDEN.get(c);
        }

        @Override
        public String mangle(String str) {
            if (str.isEmpty()) return EMPTY_TOKEN;
            IntPredicate pred = this::isAllowed;
            notOk:
            {
                for (int i = 0; i < str.length(); i = str.offsetByCodePoints(i, 1)) {
                    if (!policy.check(pred, str.codePointAt(i))) break notOk;
                }
                return str;
            }
            StringBuilder sb = new StringBuilder(str.length() + 8);
            Formatter fmt = new Formatter(sb, Locale.ROOT);
            for (int i = 0; i < str.length(); i = str.offsetByCodePoints(i, 1)) {
                int c = str.codePointAt(i);
                if (policy.check(pred, c)) {
                    sb.appendCodePoint(c);
                } else {
                    policy.convert(fmt, c);
                }
            }
            if (sb.length() == 0) return EMPTY_TOKEN;
            return sb.toString();
        }
    }

    /**
     * A mangler which outputs valid Java identifiers.
     */
    class JavaIdent implements NameMangler {
        private final IllegalSymbolPolicy policy;

        // https://docs.oracle.com/javase/specs/jls/se19/html/jls-3.html#jls-3.9
        private static final Set<String> JAVA_RESERVED = new HashSet<>(Arrays.asList(
                "abstract", "continue", "for", "new", "switch",
                "assert", "default", "if", "package", "synchronized",
                "boolean", "do", "goto", "private", "this",
                "break", "double", "implements", "protected", "throw",
                "byte", "else", "import", "public", "throws",
                "case", "enum", "instanceof", "return", "transient",
                "catch", "extends", "int", "short", "try",
                "char", "final", "interface", "static", "void",
                "class", "finally", "long", "strictfp", "volatile",
                "const", "float", "native", "super", "while", "_",

                // not included: contextual keywords

                "true", "false", "null"
        ));

        /**
         * Construct a Java identifier mangler.
         *
         * @param policy The policy for handling illegal characters.
         */
        public JavaIdent(IllegalSymbolPolicy policy) {
            this.policy = policy;
        }

        @Override
        public String mangle(String str) {
            if (str.isEmpty()) return EMPTY_TOKEN;
            if (JAVA_RESERVED.contains(str)) return "_" + str;
            notOk:
            {
                int i = 0;
                if (!policy.check(Character::isJavaIdentifierStart, str.codePointAt(i))) break notOk;
                for (i = str.offsetByCodePoints(i, 1);
                     i < str.length();
                     i = str.offsetByCodePoints(i, 1)) {
                    if (!policy.check(Character::isJavaIdentifierPart, str.codePointAt(i))) break notOk;
                }
                return str;
            }
            StringBuilder sb = new StringBuilder(str.length() + 8);
            Formatter fmt = new Formatter(sb, Locale.ROOT);
            int idx = 0;
            while (sb.length() == 0 && idx < str.length()) {
                int c = str.codePointAt(idx);
                if (policy.check(Character::isJavaIdentifierStart, c)) break;
                policy.convert(fmt, c);
                idx = str.offsetByCodePoints(idx, 1);
            }
            while (idx < str.length()) {
                int c = str.codePointAt(idx);
                idx = str.offsetByCodePoints(idx, 1);
                if (policy.check(Character::isJavaIdentifierPart, c)) {
                    sb.appendCodePoint(c);
                } else {
                    policy.convert(fmt, c);
                }
            }
            if (sb.length() == 0) return EMPTY_TOKEN;
            return sb.toString();
        }
    }
}
