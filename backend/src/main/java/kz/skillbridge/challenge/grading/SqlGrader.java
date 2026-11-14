package kz.skillbridge.challenge.grading;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kz.skillbridge.challenge.Challenge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Compares the user's result set with the reference query's result (column names are ignored). */
@Component
@RequiredArgsConstructor
public class SqlGrader {

    static final int PREVIEW_ROWS = 20;

    private final SqlSandbox sandbox;
    private final JsonMapper json;

    public GradeResult grade(Challenge c, String userSql) {
        boolean ordered = json.readTree(c.getRubricJson() == null ? "{}" : c.getRubricJson()).path("ordered").asBoolean(false);
        SqlSandbox.Result expected;
        try {
            expected = sandbox.run(c.getAnswerKey());
        } catch (SqlSandbox.SandboxException e) {
            throw new IllegalStateException("Reference query failed for challenge " + c.getId() + ": " + e.getMessage());
        }
        SqlSandbox.Result actual;
        try {
            actual = sandbox.run(userSql);
        } catch (SqlSandbox.SandboxException e) {
            return new GradeResult(0, "Запрос не выполнен: " + e.getMessage(), Map.of("error", e.getMessage()), "AUTO",
                    false);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("columns", actual.columns());
        details.put("preview", actual.rows().stream().limit(PREVIEW_ROWS).toList());
        details.put("rowCount", actual.rows().size());
        details.put("expectedRowCount", expected.rows().size());

        String feedback;
        boolean ok = false;
        if (actual.columns().size() != expected.columns().size()) {
            feedback = "Ожидается " + expected.columns().size() + " столбц(а/ов), получено " + actual.columns().size() + ".";
        } else if (actual.rows().size() != expected.rows().size()) {
            feedback = "Ожидается " + expected.rows().size() + " строк(и), получено " + actual.rows().size()
                    + ". Проверьте фильтры и соединения таблиц.";
        } else if (ordered ? actual.rows().equals(expected.rows()) : sorted(actual.rows()).equals(sorted(expected.rows()))) {
            ok = true;
            feedback = "Результат совпадает с эталоном.";
        } else if (ordered && sorted(actual.rows()).equals(sorted(expected.rows()))) {
            feedback = "Данные верные, но порядок строк не соответствует условию (ORDER BY).";
        } else {
            feedback = "Количество строк совпадает, но значения отличаются. Проверьте условия и агрегаты.";
        }
        return new GradeResult(ok ? 100 : 0, feedback, details, "AUTO", false);
    }

    private static List<List<String>> sorted(List<List<String>> rows) {
        List<List<String>> copy = new ArrayList<>(rows);
        copy.sort(Comparator.comparing(Object::toString));
        return copy;
    }
}
