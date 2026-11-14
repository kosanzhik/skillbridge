package kz.skillbridge.challenge.grading;

import java.util.Locale;

/**
 * Static pre-check for user SQL. Defence in depth only — real isolation comes from the unprivileged,
 * read-only sandbox role. Allows exactly one SELECT/WITH statement.
 */
final class SqlGuard {

    static final int MAX_LENGTH = 5000;

    private SqlGuard() {
    }

    /** Returns the cleaned query or throws with a user-facing message. */
    static String check(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("Пустой запрос");
        }
        if (sql.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Запрос длиннее " + MAX_LENGTH + " символов");
        }
        String q = sql.strip();
        while (q.endsWith(";")) {
            q = q.substring(0, q.length() - 1).stripTrailing();
        }
        String code = stripLiteralsAndComments(q);
        if (code.contains(";")) {
            throw new IllegalArgumentException("Разрешён только один запрос");
        }
        String head = code.stripLeading().replaceFirst("^\\(+", "").toLowerCase(Locale.ROOT);
        if (!(head.startsWith("select") || head.startsWith("with"))) {
            throw new IllegalArgumentException("Разрешены только запросы SELECT (или WITH … SELECT)");
        }
        return q;
    }

    /** Replaces string/identifier literals and comments with spaces so keywords/semicolons inside them are ignored. */
    static String stripLiteralsAndComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\'' || c == '"') {
                int j = i + 1;
                while (j < s.length()) {
                    if (s.charAt(j) == c) {
                        if (j + 1 < s.length() && s.charAt(j + 1) == c) {
                            j += 2;
                            continue;
                        }
                        break;
                    }
                    j++;
                }
                out.append(' ');
                i = j + 1;
            } else if (c == '-' && i + 1 < s.length() && s.charAt(i + 1) == '-') {
                int j = s.indexOf('\n', i);
                i = j < 0 ? s.length() : j;
                out.append(' ');
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int j = s.indexOf("*/", i + 2);
                i = j < 0 ? s.length() : j + 2;
                out.append(' ');
            } else if (c == '$') {
                // dollar-quoted strings are not needed for SELECT exercises; reject to keep parsing simple
                throw new IllegalArgumentException("Символ $ в запросе не поддерживается");
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
