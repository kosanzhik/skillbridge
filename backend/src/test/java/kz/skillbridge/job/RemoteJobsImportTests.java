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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import kz.skillbridge.TestcontainersConfig;
import kz.skillbridge.job.remote.RemoteJobsImporter;
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
class RemoteJobsImportTests {

    static final List<String> HIMALAYAS_QUERIES = new CopyOnWriteArrayList<>();
    static final long PAST = Instant.now().minusSeconds(86_400).getEpochSecond();
    static final long FUTURE = Instant.now().plusSeconds(30 * 86_400).getEpochSecond();
    static final HttpServer API = fakeApis();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        String base = "http://localhost:" + API.getAddress().getPort();
        r.add("skillbridge.remote-jobs.remotive-base-url", () -> base);
        r.add("skillbridge.remote-jobs.himalayas-base-url", () -> base);
    }

    @AfterAll
    static void stop() {
        API.stop(0);
    }

    @Autowired
    RemoteJobsImporter importer;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Test
    void importsOnlyRelevantJobsOpenToKazakhstan() throws Exception {
        // a stale remote job that the APIs no longer return must be archived
        jdbc.update("insert into jobs (source, external_id, title, url, fetched_at, remote) "
                + "values ('REMOTIVE', 'gone-1', 'Old Data Analyst', 'https://remotive.com/x', now() - interval '30 days', true)");

        List<RemoteJobsImporter.Result> results = importer.importAll();
        importer.importAll(); // idempotent

        Map<String, Integer> imported = results.stream().filter(r -> r.imported() > 0)
                .collect(java.util.stream.Collectors.toMap(r -> r.source() + ":" + r.profession(),
                        RemoteJobsImporter.Result::imported));
        assertThat(imported).containsOnly(Map.entry("REMOTIVE:data-analyst", 1),
                Map.entry("HIMALAYAS:business-analyst", 1), Map.entry("HIMALAYAS:financial-analyst", 1));
        assertThat(results).filteredOn(r -> r.source().equals("REMOTIVE") && r.profession().equals("data-analyst"))
                .singleElement().satisfies(r -> {
                    assertThat(r.notOpenToCountry()).isEqualTo(1); // "USA" only
                    assertThat(r.otherProfession()).isEqualTo(1);  // "Office Assistant"
                });
        assertThat(jdbc.queryForObject("select count(*) from jobs where source in ('REMOTIVE','HIMALAYAS')",
                Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select archived from jobs where external_id = 'gone-1'", Boolean.class)).isTrue();
        assertThat(HIMALAYAS_QUERIES).allMatch(q -> q.contains("country=Kazakhstan"));

        Map<String, Object> da = jdbc.queryForMap("select * from jobs where source = 'REMOTIVE' and external_id = '101'");
        assertThat(da.get("salary_text")).isEqualTo("$40k - $50k");
        assertThat(da.get("salary_from")).isNull();
        assertThat(da.get("remote")).isEqualTo(true);
        assertThat(da.get("location")).isEqualTo("Удалённо · Worldwide");
        assertThat(da.get("url")).isEqualTo("https://remotive.com/remote-jobs/data/junior-data-analyst-101");
        assertThat(jdbc.queryForList("select s.slug from job_skills js join skills s on s.id = js.skill_id "
                + "where js.job_id = ?", String.class, da.get("id"))).contains("sql", "excel", "power-bi");

        Map<String, Object> ba = jdbc.queryForMap("select * from jobs where source = 'HIMALAYAS' and title = 'Business Analyst'");
        assertThat(ba.get("salary_from")).isEqualTo(2000);
        assertThat(ba.get("salary_to")).isEqualTo(3000);
        assertThat(ba.get("salary_currency")).isEqualTo("USD");
        assertThat(ba.get("salary_period")).isEqualTo("MONTH");
        assertThat(((String) ba.get("external_id"))).hasSize(40);
        assertThat(jdbc.queryForObject("select archived from jobs where title = 'Financial Analyst'", Boolean.class))
                .isTrue(); // expired on Himalayas

        // user-facing: remote job shows up in matches with its free-text salary
        String json = mvc.perform(post("/auth/demo")).andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + JsonPath.read(json, "$.token");
        mvc.perform(post("/me/career-goals").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"professionSlug\":\"data-analyst\"}")).andExpect(status().isCreated());
        mvc.perform(get("/me/job-matches").header("Authorization", auth))
                .andExpect(jsonPath("$.matches.length()").value(1))
                .andExpect(jsonPath("$.matches[0].job.source").value("REMOTIVE"))
                .andExpect(jsonPath("$.matches[0].job.remote").value(true))
                .andExpect(jsonPath("$.matches[0].job.salaryText").value("$40k - $50k"));
    }

    // ---- fake Remotive + Himalayas --------------------------------------------------------------

    private static HttpServer fakeApis() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            s.createContext("/api/remote-jobs", ex -> respond(ex, """
                    {"job-count":3,"jobs":[
                      {"id":101,"url":"https://remotive.com/remote-jobs/data/junior-data-analyst-101","title":"Junior Data Analyst",
                       "company_name":"Acme","category":"Data","tags":["SQL","excel"],"job_type":"full_time",
                       "publication_date":"2026-11-10T12:00:00","candidate_required_location":"Worldwide","salary":"$40k - $50k",
                       "description":"<p>Requirements:</p><ul><li>Power BI dashboards</li></ul>"},
                      {"id":102,"url":"https://remotive.com/x/102","title":"Data Analyst","company_name":"US Co","tags":[],
                       "job_type":"full_time","publication_date":"2026-11-10T12:00:00","candidate_required_location":"USA",
                       "salary":"","description":"<p>SQL</p>"},
                      {"id":103,"url":"https://remotive.com/x/103","title":"Remote Office Assistant","company_name":"Co",
                       "tags":["excel"],"job_type":"full_time","publication_date":"2026-11-10T12:00:00",
                       "candidate_required_location":"Worldwide","salary":"","description":"<p>Excel</p>"}]}
                    """));
            s.createContext("/jobs/api/search", ex -> {
                HIMALAYAS_QUERIES.add(ex.getRequestURI().getRawQuery());
                respond(ex, """
                        {"totalCount":3,"jobs":[
                          {"title":"Business Analyst","companyName":"Beta","locationRestrictions":[],"minSalary":2000,"maxSalary":3000,
                           "currency":"USD","salaryPeriod":"monthly","seniority":["Junior"],"employmentType":"Full Time",
                           "description":"<p>Requirements: BPMN, SQL</p>","pubDate":%d,"expiryDate":%d,
                           "guid":"https://himalayas.app/companies/beta/jobs/business-analyst","categories":["Business-Analyst"]},
                          {"title":"Financial Analyst","companyName":"Gamma","locationRestrictions":["Kazakhstan","Georgia"],
                           "minSalary":null,"maxSalary":null,"currency":null,"salaryPeriod":"annual","seniority":["Mid-level"],
                           "description":"<p>Excel, financial modeling</p>","pubDate":%d,"expiryDate":%d,
                           "guid":"https://himalayas.app/companies/gamma/jobs/financial-analyst","categories":[]},
                          {"title":"Software Engineer","companyName":"Delta","locationRestrictions":["Brazil"],
                           "description":"<p>Java</p>","pubDate":%d,"expiryDate":%d,
                           "guid":"https://himalayas.app/companies/delta/jobs/software-engineer","categories":[]}]}
                        """.formatted(PAST, FUTURE, PAST, PAST, PAST, FUTURE));
            });
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
