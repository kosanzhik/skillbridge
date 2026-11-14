package kz.skillbridge.job.telegram;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kz.skillbridge.job.ImportedJob;
import kz.skillbridge.job.JobSource;
import org.springframework.stereotype.Component;

/**
 * Parses the team's vacancy post template:
 * <pre>
 * #вакансия
 * Должность: Junior Data Analyst
 * Компания: Example
 * Город: Алматы
 * Зарплата: 400 000–600 000 KZT
 * Опыт: 1–3 года
 * Формат: гибрид
 * Требования: SQL, Excel, Power BI
 * Желательно: Python
 * Ссылка: https://...
 * </pre>
 * Returns empty when the post does not follow the template (no title field).
 */
@Component
public class TelegramPostParser {

    private static final Map<String, String> FIELD_ALIASES = new HashMap<>();
    private static final Pattern FIELD_LINE = Pattern.compile("^\\s*[\\p{So}\\p{Sk}*•\\-]*\\s*([\\p{L} ]{3,25})\\s*[:：]\\s*(.*)$");
    private static final Pattern URL = Pattern.compile("https?://\\S+");

    static {
        alias("title", "должность", "позиция", "вакансия", "position", "title", "role");
        alias("company", "компания", "работодатель", "company", "employer");
        alias("location", "город", "локация", "location", "city");
        alias("salary", "зарплата", "зп", "оклад", "salary");
        alias("experience", "опыт", "опыт работы", "experience");
        alias("schedule", "формат", "график", "формат работы", "schedule", "format");
        alias("employment", "занятость", "тип занятости", "employment");
        alias("required", "требования", "навыки", "обязательно", "requirements", "skills", "must have");
        alias("preferred", "желательно", "будет плюсом", "плюсом будет", "nice to have", "preferred");
        alias("link", "ссылка", "откликнуться", "отклик", "link", "apply");
    }

    private static void alias(String field, String... names) {
        Arrays.stream(names).forEach(n -> FIELD_ALIASES.put(n, field));
    }

    public Optional<ImportedJob> parse(TelegramPost post) {
        if (post.text() == null || post.text().isBlank()) {
            return Optional.empty();
        }
        Map<String, String> fields = new HashMap<>();
        for (String line : post.text().split("\\R")) {
            Matcher m = FIELD_LINE.matcher(line);
            if (m.matches()) {
                String field = FIELD_ALIASES.get(m.group(1).strip().toLowerCase(Locale.ROOT));
                if (field != null && !m.group(2).isBlank()) {
                    fields.putIfAbsent(field, m.group(2).strip());
                }
            }
        }
        String title = fields.get("title");
        if (title == null) {
            return Optional.empty();
        }
        SalaryParser.Salary salary = SalaryParser.parse(fields.get("salary"));
        return Optional.of(new ImportedJob(
                JobSource.TELEGRAM,
                externalId(post),
                null,
                title,
                fields.get("company"),
                fields.get("location"),
                salary.from(),
                salary.to(),
                salary.currency(),
                null,
                fields.get("experience"),
                fields.get("employment"),
                fields.get("schedule"),
                post.text().strip(),
                link(post, fields.get("link")),
                post.publishedAt(),
                false,
                splitList(fields.get("required")),
                splitList(fields.get("preferred")),
                null,
                null,
                false));
    }

    static String externalId(TelegramPost post) {
        return post.channel() + ":" + post.messageId();
    }

    /** External link from the post if present, otherwise a link to the post itself. */
    static String link(TelegramPost post, String linkField) {
        if (linkField != null) {
            Matcher m = URL.matcher(linkField);
            if (m.find()) {
                return m.group();
            }
        }
        if (post.links() != null && !post.links().isEmpty()) {
            return post.links().get(0);
        }
        return postUrl(post);
    }

    static String postUrl(TelegramPost post) {
        String ch = post.channel();
        if (ch.startsWith("-100")) {
            return "https://t.me/c/" + ch.substring(4) + "/" + post.messageId();
        }
        return "https://t.me/" + ch + "/" + post.messageId();
    }

    static List<String> splitList(String s) {
        if (s == null) {
            return List.of();
        }
        return Arrays.stream(s.split("[,;/•\\n]")).map(String::strip).filter(x -> !x.isEmpty()).toList();
    }
}
