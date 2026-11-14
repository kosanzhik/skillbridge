package kz.skillbridge.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import kz.skillbridge.TestcontainersConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class HhImportAndMatchingTests {

    static final HttpServer HH = fakeHh();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("skillbridge.hh.base-url", () -> "http://localhost:" + HH.getAddress().getPort());
        r.add("skillbridge.hh.app-token", () -> "test-token");
        r.add("skillbridge.admin-emails", () -> "admin@example.com");
    }

    @AfterAll
    static void stop() {
        HH.stop(0);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void adminImportsHhVacanciesAndUserGetsExplainableMatches() throws Exception {
        String admin = token(mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@example.com\",\"password\":\"secret123\",\"consent\":true}")));
        String auth = token(mvc.perform(post("/auth/demo")));

        mvc.perform(post("/admin/jobs/hh/import?profession=data-analyst").header("Authorization", auth))
                .andExpect(status().isForbidden());
        for (int i = 0; i < 2; i++) { // second run must update, not duplicate
            mvc.perform(post("/admin/jobs/hh/import?profession=data-analyst&limit=10").header("Authorization", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].found").value(2))
                    .andExpect(jsonPath("$[0].imported").value(2));
        }
        assertThat(jdbc.queryForObject("select count(*) from jobs where source='HH'", Integer.class)).isEqualTo(2);

        mvc.perform(get("/me/job-matches").header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_CAREER_GOAL"));
        mvc.perform(post("/me/career-goals").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"professionSlug\":\"data-analyst\"}")).andExpect(status().isCreated());

        Long userId = Long.valueOf(((Integer) JsonPath.read(
                mvc.perform(get("/me").header("Authorization", auth)).andReturn().getResponse().getContentAsString(),
                "$.id")).longValue());
        addSkill(userId, "sql", 4, "CV_EXTRACTED");
        addSkill(userId, "excel", 2, "CV_EXTRACTED");
        addSkill(userId, "python", 5, "CV_EXTRACTED");
        addSkill(userId, "python", 1, "ASSESSED"); // assessment overrides the CV claim

        // job 1: SQL, Excel (key skills, required), Python (required in text), Tableau (preferred)
        // baseline levels: sql 4, excel 4, python 3, tableau 3
        // (2·1 + 2·0.5 + 2·0.333 + 1·0) / 7 × 100 = 52.38
        mvc.perform(get("/me/job-matches").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profession.slug").value("data-analyst"))
                .andExpect(jsonPath("$.jobsAnalyzed").value(2))
                .andExpect(jsonPath("$.methodology").isString())
                .andExpect(jsonPath("$.matches[0].job.title").value("Junior Data Analyst"))
                .andExpect(jsonPath("$.matches[0].job.salaryFrom").value(400000))
                .andExpect(jsonPath("$.matches[0].job.salaryTo").doesNotExist())
                .andExpect(jsonPath("$.matches[0].job.url").value("https://hh.kz/vacancy/1001"))
                .andExpect(jsonPath("$.matches[0].score").value(52.38))
                .andExpect(jsonPath("$.matches[0].matched[0].skill").value("SQL"))
                .andExpect(jsonPath("$.matches[0].partial.length()").value(2))
                .andExpect(jsonPath("$.matches[0].partial[?(@.skill=='Python')].proven").value(true))
                .andExpect(jsonPath("$.matches[0].missing[0].skill").value("Tableau"))
                .andExpect(jsonPath("$.matches[0].missing[0].requirement").value("PREFERRED"))
                // job 2 has no detectable requirements: no score, no invented salary
                .andExpect(jsonPath("$.matches[1].score").doesNotExist())
                .andExpect(jsonPath("$.matches[1].job.salaryFrom").doesNotExist());

        assertThat(jdbc.queryForObject("select count(*) from job_matches where user_id = ?", Integer.class, userId))
                .isEqualTo(1);

        String jobId = jdbc.queryForObject("select id::text from jobs where external_id = '1001'", String.class);
        mvc.perform(get("/jobs/" + jobId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirements.length()").value(4))
                .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.containsString("Будет плюсом")));
    }

    private void addSkill(Long userId, String slug, int level, String source) {
        jdbc.update("insert into user_skills (user_id, skill_id, level, source) "
                + "select ?, id, ?, ? from skills where slug = ?", userId, level, source, slug);
    }

    private static String token(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return "Bearer " + JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.token");
    }

    // ---- fake api.hh.ru -------------------------------------------------------------------------

    private static HttpServer fakeHh() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            s.createContext("/vacancies", HhImportAndMatchingTests::handle);
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handle(HttpExchange ex) throws IOException {
        if (!"Bearer test-token".equals(ex.getRequestHeaders().getFirst("Authorization"))) {
            respond(ex, 403, "{\"errors\":[{\"type\":\"forbidden\"}]}");
            return;
        }
        String path = ex.getRequestURI().getPath();
        switch (path) {
            case "/vacancies" -> respond(ex, 200,
                    "{\"items\":[{\"id\":\"1001\"},{\"id\":\"1002\"}],\"found\":2,\"pages\":1,\"page\":0}");
            case "/vacancies/1001" -> respond(ex, 200, """
                    {"id":"1001","name":"Junior Data Analyst","alternate_url":"https://hh.kz/vacancy/1001",
                     "employer":{"name":"Example Bank"},"area":{"name":"Алматы"},
                     "salary_range":{"from":400000,"to":null,"currency":"KZT","gross":false},
                     "experience":{"name":"От 1 года до 3 лет"},"employment":{"name":"Полная занятость"},
                     "schedule":{"name":"Гибридный"},"published_at":"2026-11-10T12:00:00+0500","archived":false,
                     "key_skills":[{"name":"SQL"},{"name":"MS Excel"}],
                     "description":"<p>Ищем аналитика.</p><strong>Требования:</strong><ul><li>Python</li></ul><strong>Будет плюсом:</strong><ul><li>Tableau</li></ul>"}
                    """);
            case "/vacancies/1002" -> respond(ex, 200, """
                    {"id":"1002","name":"Аналитик данных (стажёр)","alternate_url":"https://hh.kz/vacancy/1002",
                     "employer":{"name":"Startup"},"area":{"name":"Астана"},"salary":null,
                     "published_at":"2026-11-09T09:00:00+0500","key_skills":[],
                     "description":"<p>Обучим всему.</p>"}
                    """);
            default -> respond(ex, 404, "{}");
        }
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
