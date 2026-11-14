package kz.skillbridge.job.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import kz.skillbridge.TestcontainersConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class TelegramIngestTests {

    static final List<String> QUERIES = new CopyOnWriteArrayList<>();
    static final HttpServer TG = fakeTelegram();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("skillbridge.telegram.api-base-url", () -> "http://localhost:" + TG.getAddress().getPort());
        r.add("skillbridge.telegram.bot-token", () -> "TEST");
        r.add("skillbridge.telegram.channel", () -> "@skillbridge_jobs");
        r.add("skillbridge.admin-emails", () -> "tg-admin@example.com");
    }

    @AfterAll
    static void stop() {
        TG.stop(0);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private String admin() throws Exception {
        String json = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"tg-admin@example.com\",\"password\":\"secret123\",\"consent\":true}"))
                .andReturn().getResponse().getContentAsString();
        if (json.contains("EMAIL_TAKEN")) {
            json = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"tg-admin@example.com\",\"password\":\"secret123\"}"))
                    .andReturn().getResponse().getContentAsString();
        }
        return "Bearer " + JsonPath.read(json, "$.token");
    }

    @Test
    void importsChannelHistoryFromDesktopExport() throws Exception {
        String export = """
                {"name":"SkillBridge Jobs","type":"public_channel","id":1234567890,"messages":[
                  {"id":10,"type":"message","date_unixtime":"1762770000",
                   "text":["#вакансия\\nДолжность: Junior Data Analyst\\nКомпания: Example Bank\\nЗарплата: от 350 000 ₸\\n",
                           "Требования: SQL, Excel, Power BI\\nЖелательно: Python\\nСсылка: ",
                           {"type":"text_link","text":"откликнуться","href":"https://example.kz/apply/10"}]},
                  {"id":11,"type":"message","date_unixtime":"1762770100","text":"Друзья, завтра вебинар по SQL!"},
                  {"id":12,"type":"service","action":"pin_message"}
                ]}
                """;
        mvc.perform(multipart("/admin/jobs/telegram/import").header("Authorization", admin())
                        .file(new MockMultipartFile("file", "result.json", "application/json", export.getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.IMPORTED_TEMPLATE").value(1))
                .andExpect(jsonPath("$.SKIPPED_NO_TEMPLATE").value(1));

        Map<String, Object> job = jdbc.queryForMap("select j.*, p.slug from jobs j left join professions p "
                + "on p.id = j.profession_id where source='TELEGRAM' and external_id='skillbridge_jobs:10'");
        assertThat(job.get("slug")).isEqualTo("data-analyst");
        assertThat(job.get("url")).isEqualTo("https://example.kz/apply/10");
        assertThat(job.get("salary_from")).isEqualTo(350000);
        assertThat(job.get("salary_to")).isNull();
        assertThat(jdbc.queryForList("select s.slug || ':' || js.requirement_type from job_skills js "
                        + "join skills s on s.id = js.skill_id where js.job_id = ?", String.class, job.get("id")))
                .containsExactlyInAnyOrder("sql:REQUIRED", "excel:REQUIRED", "power-bi:REQUIRED", "python:PREFERRED");
    }

    @Test
    void botPollingIngestsOnlyOurChannelAndAdvancesOffset() throws Exception {
        String auth = admin();
        mvc.perform(post("/admin/jobs/telegram/poll").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.IMPORTED_TEMPLATE").value(1))
                .andExpect(jsonPath("$.length()").value(1)); // the foreign chat's post is ignored entirely
        assertThat(jdbc.queryForObject("select title from jobs where external_id = 'skillbridge_jobs:77'",
                String.class)).isEqualTo("Business Analyst");

        mvc.perform(post("/admin/jobs/telegram/poll").header("Authorization", auth)).andExpect(status().isOk());
        assertThat(QUERIES.get(QUERIES.size() - 1)).contains("offset=502");
    }

    private static HttpServer fakeTelegram() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            s.createContext("/botTEST/getUpdates", ex -> {
                String q = ex.getRequestURI().getRawQuery();
                QUERIES.add(q);
                String body = q.contains("offset=502") ? "{\"ok\":true,\"result\":[]}" : """
                        {"ok":true,"result":[
                          {"update_id":500,"channel_post":{"message_id":77,"date":1762770000,
                            "chat":{"id":-1001234567890,"username":"skillbridge_jobs","type":"channel"},
                            "text":"Position: Business Analyst\\nRequirements: BPMN, SQL, Jira\\nApply: https://acme.kz/ba",
                            "entities":[{"type":"url","offset":63,"length":18}]}},
                          {"update_id":501,"channel_post":{"message_id":5,"date":1762770000,
                            "chat":{"id":-1009999,"username":"other_channel","type":"channel"},
                            "text":"Position: Spam"}}
                        ]}
                        """;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.close();
            });
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
