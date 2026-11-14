package kz.skillbridge.job.hh;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import kz.skillbridge.job.Html;
import kz.skillbridge.job.ImportedJob;
import kz.skillbridge.job.JobSource;
import tools.jackson.databind.JsonNode;

/** Maps an hh.ru vacancy detail JSON to {@link ImportedJob}. Absent fields stay null. */
final class HhVacancyMapper {

    private static final DateTimeFormatter HH_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private HhVacancyMapper() {
    }

    static ImportedJob map(JsonNode v, Long professionId) {
        // newer API exposes salary_range; fall back to legacy salary
        JsonNode salary = v.hasNonNull("salary_range") ? v.get("salary_range") : v.path("salary");
        List<String> keySkills = new ArrayList<>();
        for (JsonNode ks : v.path("key_skills")) {
            String name = text(ks, "name");
            if (name != null) {
                keySkills.add(name);
            }
        }
        String id = text(v, "id");
        String url = text(v, "alternate_url");
        return new ImportedJob(
                JobSource.HH,
                id,
                professionId,
                text(v, "name"),
                text(v.path("employer"), "name"),
                text(v.path("area"), "name"),
                integer(salary, "from"),
                integer(salary, "to"),
                text(salary, "currency"),
                salary.hasNonNull("gross") ? salary.get("gross").asBoolean() : null,
                text(v.path("experience"), "name"),
                text(v.path("employment"), "name"),
                text(v.path("schedule"), "name"),
                Html.toText(text(v, "description")),
                url != null ? url : "https://hh.kz/vacancy/" + id,
                parseDate(text(v, "published_at")),
                v.path("archived").asBoolean(false),
                keySkills,
                List.of(),
                integer(salary, "from") == null && integer(salary, "to") == null ? null : period(salary),
                null,
                "remote".equals(text(v.path("schedule"), "id")));
    }

    /** hh salary_range.mode: MONTH, HOUR, SHIFT, FLY_IN_FLY_OUT; legacy salary has no mode and is monthly. */
    private static String period(JsonNode salary) {
        String mode = text(salary.path("mode"), "id");
        if (mode == null || "MONTH".equalsIgnoreCase(mode)) {
            return "MONTH";
        }
        return "HOUR".equalsIgnoreCase(mode) ? "HOUR" : null;
    }

    static Instant parseDate(String s) {
        if (s == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s, HH_DATE).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(s).toInstant();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode f = n == null ? null : n.get(field);
        if (f == null || f.isNull() || f.isMissingNode()) {
            return null;
        }
        String s = f.asString();
        return s.isBlank() ? null : s;
    }

    private static Integer integer(JsonNode n, String field) {
        JsonNode f = n == null ? null : n.get(field);
        return f == null || !f.isNumber() ? null : f.asInt();
    }
}
