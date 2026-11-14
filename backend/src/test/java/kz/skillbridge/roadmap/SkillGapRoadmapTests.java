package kz.skillbridge.roadmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import kz.skillbridge.TestcontainersConfig;
import kz.skillbridge.job.ImportedJob;
import kz.skillbridge.job.JobImportService;
import kz.skillbridge.job.JobSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class SkillGapRoadmapTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JobImportService jobImport;

    private String demoWithGoal(String profession) throws Exception {
        String json = mvc.perform(post("/auth/demo")).andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + JsonPath.read(json, "$.token");
        mvc.perform(post("/me/career-goals").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"professionSlug\":\"" + profession + "\"}")).andExpect(status().isCreated());
        return auth;
    }

    private long userId(String auth) throws Exception {
        return ((Number) JsonPath.read(mvc.perform(get("/me").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void skill(long userId, String slug, int level) {
        jdbc.update("insert into user_skills (user_id, skill_id, level, source) select ?, id, ?, 'CV_EXTRACTED' "
                + "from skills where slug = ?", userId, level, slug);
    }

    @Test
    void baselineGapRoadmapAndProgress() throws Exception {
        String auth = demoWithGoal("financial-analyst");
        long uid = userId(auth);
        skill(uid, "excel", 4);
        skill(uid, "accounting", 3);
        skill(uid, "sql", 2);
        skill(uid, "financial-modeling", 1);

        // no vacancies → BASELINE; readiness = (5·1 + 5·0.25 + 4·1 + 3·2/3) / 40 = 30.63%
        mvc.perform(get("/me/skill-gap").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementsSource").value("BASELINE"))
                .andExpect(jsonPath("$.readiness.score").value(30.63))
                .andExpect(jsonPath("$.readiness.provenScore").value(0.0))
                .andExpect(jsonPath("$.skills[0].skill.slug").value("financial-analysis"))
                .andExpect(jsonPath("$.skills[0].status").value("BIG_GAP"))
                .andExpect(jsonPath("$.skills[?(@.skill.slug=='excel')].status").value("STRONG"))
                .andExpect(jsonPath("$.skills[?(@.skill.slug=='sql')].status").value("NEEDS_IMPROVEMENT"))
                .andExpect(jsonPath("$.skills[?(@.skill.slug=='financial-modeling')].currentSource").value("CV_EXTRACTED"));

        String roadmap = mvc.perform(post("/me/roadmap").header("Authorization", auth))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // prerequisites first: reporting → analysis → modeling → ... → valuation; final project last
        List<String> order = JsonPath.read(roadmap, "$.items[*].skill.slug");
        assertThat(order).containsExactly("financial-reporting", "financial-analysis", "financial-modeling",
                "budgeting", "english", "valuation");
        assertThat((String) JsonPath.read(roadmap, "$.items[6].title")).isEqualTo("Итоговый практический проект");
        assertThat((String) JsonPath.read(roadmap, "$.items[0].rationale")).contains("Базовое требование");
        assertThat((String) JsonPath.read(roadmap, "$.items[0].resources[0].url"))
                .isEqualTo("https://www.ifrs.org/issued-standards/list-of-standards/");
        assertThat((List<?>) JsonPath.read(roadmap, "$.items[2].topics")).isNotEmpty();
        assertThat((Integer) JsonPath.read(roadmap, "$.items[2].estimatedHours")).isEqualTo(30);

        int firstItem = JsonPath.read(roadmap, "$.items[0].id");
        mvc.perform(patch("/me/roadmap/items/" + firstItem).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.done").value(1))
                .andExpect(jsonPath("$.progress.percent").value(14.3))
                .andExpect(jsonPath("$.items[0].completedAt").isString());

        mvc.perform(get("/me/progress").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roadmap.done").value(1))
                .andExpect(jsonPath("$.readiness.score").value(30.63))
                .andExpect(jsonPath("$.skills.total").value(4))
                .andExpect(jsonPath("$.nextAction.type").value("UPLOAD_CV"))
                .andExpect(jsonPath("$.recentEvents[0].type").value("ROADMAP_ITEM_STATUS_CHANGED"))
                .andExpect(jsonPath("$.recentEvents[0].payload.to").value("DONE"))
                .andExpect(jsonPath("$.recentEvents[1].type").value("ROADMAP_GENERATED"));

        // regenerating archives the old roadmap; its items can no longer be changed
        mvc.perform(post("/me/roadmap").header("Authorization", auth)).andExpect(status().isCreated());
        mvc.perform(patch("/me/roadmap/items/" + firstItem).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"TODO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROADMAP_ARCHIVED"));

        // other users cannot touch the item
        String other = demoWithGoal("data-analyst");
        mvc.perform(patch("/me/roadmap/items/" + firstItem).header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/me/roadmap").header("Authorization", other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROADMAP_NOT_FOUND"));
    }

    @Test
    void marketDemandDrivesImportanceWhenEnoughVacancies() throws Exception {
        long se = jdbc.queryForObject("select id from professions where slug='software-engineer'", Long.class);
        for (int i = 0; i < 5; i++) {
            String reqs = "Git, SQL" + (i < 3 ? ", Docker" : "") + (i == 0 ? ", React" : "");
            jobImport.upsert(new ImportedJob(JobSource.HH, "se-" + i, se, "Software Engineer " + i, "Co", "Алматы",
                    null, null, null, null, null, null, null, "Требования:\n" + reqs, "https://hh.kz/vacancy/se" + i,
                    Instant.now(), false, List.of(), List.of(), null, null, false));
        }
        String auth = demoWithGoal("software-engineer");
        mvc.perform(get("/me/skill-gap").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementsSource").value("MARKET"))
                .andExpect(jsonPath("$.jobsAnalyzed").value(5))
                .andExpect(jsonPath("$.skills[?(@.skill.slug=='git')].importance").value(5))
                .andExpect(jsonPath("$.skills[?(@.skill.slug=='git')].demandShare").value(1.0))
                .andExpect(jsonPath("$.skills[?(@.skill.slug=='docker')].importance").value(5))
                // React is not a baseline SE skill but appears in 20% of vacancies
                .andExpect(jsonPath("$.skills[?(@.skill.slug=='react')].importanceSource").value("MARKET"))
                .andExpect(jsonPath("$.skills[?(@.skill.slug=='react')].importance").value(2))
                .andExpect(jsonPath("$.skills[?(@.skill.slug=='algorithms')].importance").value(1));

        String roadmap = mvc.perform(post("/me/roadmap").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(roadmap, "$.items[0].rationale")).contains("вакансий Software Engineer");
    }

    @Test
    void learningHubIsPublic() throws Exception {
        mvc.perform(get("/learning/resources?skill=sql"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].verifiedAt").isString());
    }
}
