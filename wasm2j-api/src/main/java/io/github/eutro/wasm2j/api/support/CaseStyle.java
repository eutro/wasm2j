package io.github.eutro.wasm2j.api.support;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A style of capitalising words for a token.
 * <p>
 * This class can be used to convert between different case styles.
 */
public interface CaseStyle {
    /**
     * lowerCamelCase
     */
    CaseStyle LOWER_CAMEL = new CamelLike(Capitalisation.LOWER_CASE);
    /**
     * UpperCamelCase
     */
    CaseStyle UPPER_CAMEL = new CamelLike(Capitalisation.TITLE_CASE);
    /**
     * lower_snake_case
     */
    CaseStyle LOWER_SNAKE = new SnakeLike("_", Capitalisation.LOWER_CASE);
    /**
     * UPPER_SNAKE_CASE
     */
    CaseStyle UPPER_SNAKE = new SnakeLike("_", Capitalisation.UPPER_CASE);
    /**
     * lower-kebab-case
     */
    CaseStyle LOWER_KEBAB = new SnakeLike("-", Capitalisation.LOWER_CASE);

    /**
     * Create a case style that will, when converting from this style to another,
     * try to guess what case style the token uses.
     *
     * @param fallback The case style to use for output formatting.
     * @return The detecting case style.
     */
    static CaseStyle detect(CaseStyle fallback) {
        return new Detect(fallback);
    }

    /**
     * Split the token into words according to this case style.
     *
     * @param token The token to split.
     * @return The words in the token.
     */
    String[] splitToWords(String token);

    /**
     * Normalise the {@code index}th word of a token to how it should appear in this case style.
     *
     * @param word  The word.
     * @param index The index of the word.
     * @return The normalised word.
     */
    String normaliseWord(String word, int index);

    /**
     * Concatenate words into a token according to the rules of this case style.
     *
     * @param words The words to concatenate.
     * @return The token.
     */
    String concatenate(String[] words);

    /**
     * Convert a token from this case style to another.
     *
     * @param other The other case style.
     * @param token The token to convert.
     * @return The converted token.
     */
    default String convertTo(CaseStyle other, String token) {
        if (this == other) return token;
        return convertTo(other, null, token);
    }

    /**
     * Convert a token from this case style to another, optionally adding a prefix word.
     *
     * @param other  The other case style.
     * @param prefix The prefix to add.
     * @param token  The input token.
     * @return The output token.
     */
    default String convertTo(CaseStyle other, @Nullable String prefix, String token) {
        if (prefix == null && this == other) return token;
        String[] words = splitToWords(token);
        if (prefix != null) {
            String[] newWords = new String[words.length + 1];
            newWords[0] = prefix;
            System.arraycopy(words, 0, newWords, 1, words.length);
            words = newWords;
        }
        for (int i = 0; i < words.length; i++) {
            words[i] = other.normaliseWord(words[i], i);
        }
        return other.concatenate(words);
    }

    /**
     * A capitalisation style for a single word.
     */
    enum Capitalisation {
        /**
         * All letters upper case.
         */
        UPPER_CASE,
        /**
         * All letters lower case.
         */
        LOWER_CASE,
        /**
         * The first letter upper case, the rest lower.
         */
        TITLE_CASE,
        ;

        /**
         * Convert the word to this capitalisation.
         *
         * @param word The word.
         * @return The capitalised word.
         */
        public String normalise(String word) {
            switch (this) {
                case UPPER_CASE:
                    return word.toUpperCase(Locale.ROOT);
                case LOWER_CASE:
                    return word.toLowerCase(Locale.ROOT);
                case TITLE_CASE:
                    if (word.isEmpty()) return word;
                    return Character.toUpperCase(word.charAt(0))
                            + word.substring(1).toLowerCase(Locale.ROOT);
            }
            throw new IllegalStateException();
        }
    }

    /**
     * A case style like snake_case.
     */
    class SnakeLike implements CaseStyle {
        private final String separator;
        private final Pattern sepPat;
        private final Capitalisation caps;

        /**
         * Construct a {@link SnakeLike} case with the given separator and word capitalisation rule.
         *
         * @param separator The separator.
         * @param caps      The per-word capitalisation rule.
         */
        public SnakeLike(String separator, Capitalisation caps) {
            this.separator = separator;
            this.sepPat = Pattern.compile(Pattern.quote(separator));
            this.caps = caps;
        }

        @Override
        public String[] splitToWords(String token) {
            return sepPat.split(token);
        }

        @Override
        public String normaliseWord(String word, int index) {
            return caps.normalise(word);
        }

        @Override
        public String concatenate(String[] words) {
            return String.join(separator, words);
        }
    }

    /**
     * A case style like lowerCamelCase.
     */
    class CamelLike implements CaseStyle {
        private final Capitalisation firstWordCase;

        /**
         * Construct a {@link CamelLike} case style with the given case for the first word.
         *
         * @param firstWordCase The case of the first word.
         */
        public CamelLike(Capitalisation firstWordCase) {
            this.firstWordCase = firstWordCase;
        }

        @Override
        public String[] splitToWords(String token) {
            List<String> buf = new ArrayList<>();
            int start = 0;
            for (int i = 1; i < token.length(); i++) {
                if (Character.isUpperCase(token.charAt(i))) {
                    buf.add(token.substring(start, i));
                    start = i;
                }
            }
            if (start != token.length() - 1) {
                buf.add(token.substring(start));
            }
            return buf.toArray(new String[0]);
        }

        @Override
        public String normaliseWord(String word, int index) {
            Capitalisation caps = Capitalisation.TITLE_CASE;
            if (index == 0) caps = firstWordCase;
            return caps.normalise(word);
        }

        @Override
        public String concatenate(String[] words) {
            return String.join("", words);
        }
    }

    /**
     * A case-style that attempts to detect the style of an input token.
     *
     * @see #detect(CaseStyle)
     */
    class Detect implements CaseStyle {
        private final CaseStyle fallback;

        Detect(CaseStyle fallback) {
            this.fallback = fallback;
        }

        @Override
        public String[] splitToWords(String token) {
            if (token.indexOf('-') != -1 || token.indexOf('_') != -1) {
                return token.split("[-_]");
            }
            if (token.toUpperCase(Locale.ROOT).equals(token)) {
                return UPPER_CAMEL.splitToWords(token);
            }
            return LOWER_CAMEL.splitToWords(token);
        }

        @Override
        public String normaliseWord(String word, int index) {
            return fallback.normaliseWord(word, index);
        }

        @Override
        public String concatenate(String[] words) {
            return fallback.concatenate(words);
        }
    }
}
