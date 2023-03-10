package io.github.eutro.wasm2j.support;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public interface CaseStyle {
    CaseStyle LOWER_CAMEL = new CamelLike(Capitalisation.LOWER_CASE);
    CaseStyle UPPER_CAMEL = new CamelLike(Capitalisation.TITLE_CASE);
    CaseStyle LOWER_SNAKE = new SnakeLike("_", Capitalisation.LOWER_CASE);
    CaseStyle UPPER_SNAKE = new SnakeLike("_", Capitalisation.UPPER_CASE);
    CaseStyle LOWER_KEBAB = new SnakeLike("-", Capitalisation.LOWER_CASE);

    static CaseStyle detect(CaseStyle fallback) {
        return new Detect(fallback);
    }

    String[] splitToWords(String token);

    String normaliseWord(String word, int index);

    String concatenate(String[] words);

    default String convertTo(CaseStyle other, String token) {
        return convertTo(other, null, token);
    }

    default String convertTo(CaseStyle other, @Nullable String prefix, String token) {
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

    enum Capitalisation {
        UPPER_CASE,
        LOWER_CASE,
        TITLE_CASE,
        ;

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

    class SnakeLike implements CaseStyle {
        private final String separator;
        private final Pattern sepPat;
        private final Capitalisation caps;

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

    class CamelLike implements CaseStyle {
        private final Capitalisation firstWordCase;

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

    class Detect implements CaseStyle {
        private final CaseStyle fallback;

        public Detect(CaseStyle fallback) {
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
