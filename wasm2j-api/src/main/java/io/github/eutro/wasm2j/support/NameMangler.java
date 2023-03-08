package io.github.eutro.wasm2j.support;

import java.util.*;
import java.util.function.IntPredicate;

// TODO test
public interface NameMangler {
    String JVM_BANNED_CHARS = ".;[/<>";
    String EMPTY_TOKEN = "_EMPTY_";

    NameMangler JVM_UNQUALIFIED = new BanChars(JVM_BANNED_CHARS, IllegalTokenPolicy.MANGLE_BIJECTIVE);
    NameMangler JAVA_IDENT = new JavaIdent(IllegalTokenPolicy.MANGLE_BIJECTIVE);

    String mangle(String name);

    enum IllegalTokenPolicy {
        OMIT,
        MANGLE,
        MANGLE_SENSIBLE,
        MANGLE_BIJECTIVE,
        ;

        boolean shouldConvertAnyway(int c) {
            if (this == MANGLE_BIJECTIVE) {
                return c == '_';
            }
            return false;
        }

        void convert(Formatter into, int c) {
            if (this == OMIT) return;
            if (this == MANGLE_BIJECTIVE) {
                if (c == '_') {
                    into.format("_UNDERSCORE_");
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

    static boolean check(int c, IllegalTokenPolicy policy, IntPredicate pred) {
        return pred.test(c) && !policy.shouldConvertAnyway(c);
    }

    class BanChars implements NameMangler {
        private final BitSet FORBIDDEN = new BitSet();
        private final IllegalTokenPolicy policy;

        public BanChars(String forbidden, IllegalTokenPolicy policy) {
            this.policy = policy;
            for (char c : forbidden.toCharArray()) {
                FORBIDDEN.set(c);
            }
        }

        private boolean isForbidden(int c) {
            return FORBIDDEN.get(c);
        }

        public String mangle(String str) {
            if (str.isEmpty()) return EMPTY_TOKEN;
            IntPredicate pred = this::isForbidden;
            notOk:
            {
                for (int i = 0; i < str.length(); i = str.offsetByCodePoints(i, 1)) {
                    if (!check(str.codePointAt(i), policy, pred)) break notOk;
                }
                return str;
            }
            StringBuilder sb = new StringBuilder(str.length() + 8);
            Formatter fmt = new Formatter(sb, Locale.ROOT);
            for (int i = 0; i < str.length(); i = str.offsetByCodePoints(i, 1)) {
                int c = str.codePointAt(i);
                if (check(c, policy, pred)) {
                    sb.appendCodePoint(c);
                } else {
                    policy.convert(fmt, c);
                }
            }
            if (sb.length() == 0) return EMPTY_TOKEN;
            return sb.toString();
        }
    }

    class JavaIdent implements NameMangler {
        private final IllegalTokenPolicy policy;

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

                // not included: exports, opens, requires

                "true", "false", "null"
        ));

        public JavaIdent(IllegalTokenPolicy policy) {
            this.policy = policy;
        }

        public String mangle(String str) {
            if (str.isEmpty()) return EMPTY_TOKEN;
            if (JAVA_RESERVED.contains(str)) return "_" + str;
            notOk:
            {
                int i = 0;
                if (!check(str.codePointAt(i), policy, Character::isJavaIdentifierStart)) break notOk;
                for (i = str.offsetByCodePoints(i, 1);
                     i < str.length();
                     i = str.offsetByCodePoints(i, 1)) {
                    if (!check(str.codePointAt(i), policy, Character::isJavaIdentifierPart)) break notOk;
                }
                return str;
            }
            StringBuilder sb = new StringBuilder(str.length() + 8);
            Formatter fmt = new Formatter(sb, Locale.ROOT);
            int idx = 0;
            while (sb.length() == 0 && idx < str.length()) {
                int c = str.codePointAt(idx);
                if (check(c, policy, Character::isJavaIdentifierStart)) break;
                policy.convert(fmt, c);
                idx = str.offsetByCodePoints(idx, 1);
            }
            while (idx < str.length()) {
                int c = str.codePointAt(idx);
                idx = str.offsetByCodePoints(idx, 1);
                if (check(c, policy, Character::isJavaIdentifierPart)) {
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
