package kz.skillbridge.job.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import kz.skillbridge.job.ImportedJob;
import org.junit.jupiter.api.Test;

class TelegramPostParserTests {

    final TelegramPostParser parser = new TelegramPostParser();

    private TelegramPost post(String channel, String text, List<String> links) {
        return new TelegramPost(channel, 42, text, links, Instant.parse("2026-11-10T10:00:00Z"));
    }

    @Test
    void parsesTemplatePost() {
        ImportedJob j = parser.parse(post("skillbridge_jobs", """
                #вакансия
                💼 Должность: Junior Data Analyst
                Компания: Example Bank
                Город: Алматы
                Зарплата: 400 000–600 000 KZT
                Опыт: 1–3 года
                Формат: гибрид
                Требования: SQL, Excel, Power BI
                Желательно: Python
                Ссылка: https://example.kz/jobs/1
                """, List.of())).orElseThrow();
        assertThat(j.title()).isEqualTo("Junior Data Analyst");
        assertThat(j.company()).isEqualTo("Example Bank");
        assertThat(j.location()).isEqualTo("Алматы");
        assertThat(j.salaryFrom()).isEqualTo(400_000);
        assertThat(j.salaryTo()).isEqualTo(600_000);
        assertThat(j.salaryCurrency()).isEqualTo("KZT");
        assertThat(j.schedule()).isEqualTo("гибрид");
        assertThat(j.explicitSkills()).containsExactly("SQL", "Excel", "Power BI");
        assertThat(j.explicitPreferredSkills()).containsExactly("Python");
        assertThat(j.url()).isEqualTo("https://example.kz/jobs/1");
        assertThat(j.externalId()).isEqualTo("skillbridge_jobs:42");
    }

    @Test
    void missingFieldsStayNullAndLinkFallsBackToPost() {
        ImportedJob j = parser.parse(post("skillbridge_jobs", "Position: Business Analyst\nCompany: ACME", List.of()))
                .orElseThrow();
        assertThat(j.salaryFrom()).isNull();
        assertThat(j.salaryCurrency()).isNull();
        assertThat(j.location()).isNull();
        assertThat(j.url()).isEqualTo("https://t.me/skillbridge_jobs/42");

        ImportedJob priv = parser.parse(post("-1001234567890", "Должность: QA", List.of())).orElseThrow();
        assertThat(priv.url()).isEqualTo("https://t.me/c/1234567890/42");

        ImportedJob linked = parser.parse(post("ch", "Должность: QA", List.of("https://hh.kz/vacancy/9"))).orElseThrow();
        assertThat(linked.url()).isEqualTo("https://hh.kz/vacancy/9");
    }

    @Test
    void nonTemplatePostIsNotParsed() {
        assertThat(parser.parse(post("ch", "Всем привет! Завтра вебинар по SQL.", List.of()))).isEmpty();
    }

    @Test
    void parsesSalaryVariants() {
        assertThat(SalaryParser.parse("от 300 000 ₸")).isEqualTo(new SalaryParser.Salary(300_000, null, "KZT"));
        assertThat(SalaryParser.parse("до $2000")).isEqualTo(new SalaryParser.Salary(null, 2000, "USD"));
        assertThat(SalaryParser.parse("500k–700k тг")).isEqualTo(new SalaryParser.Salary(500_000, 700_000, "KZT"));
        assertThat(SalaryParser.parse("по договорённости")).isEqualTo(SalaryParser.Salary.NONE);
    }
}
