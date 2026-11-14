package kz.skillbridge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import kz.skillbridge.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ChallengeFlowTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private String demo() throws Exception {
        return "Bearer " + JsonPath.read(mvc.perform(post("/auth/demo")).andReturn().getResponse()
                .getContentAsString(), "$.token");
    }

    private long challenge(String title) {
        return jdbc.queryForObject("select id from challenges where title = ?", Long.class, title);
    }

    private ResultActions submit(String auth, long id, String body) throws Exception {
        return mvc.perform(post("/challenges/" + id + "/submissions").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void publicViewHidesAnswers() throws Exception {
        String auth = demo();
        String json = mvc.perform(get("/challenges/" + challenge("SQL: выручка по категориям"))
                        .header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("answerKey").doesNotContain("SUM(oi.quantity");
        mvc.perform(get("/challenges?skill=valuation").header("Authorization", auth))
                .andExpect(jsonPath("$[0].questions.length()").value(5))
                .andExpect(jsonPath("$[0].questions[0].options.length()").value(4));
    }

    @Test
    void sqlChallengesAreCheckedAgainstHandComputedResults() throws Exception {
        String auth = demo();
        // Electronics 1 525 000, Furniture 320 000, Stationery 40 000 (computed by hand from the dataset)
        submit(auth, challenge("SQL: выручка по категориям"), """
                {"sql":"select p.category, sum(oi.quantity*oi.unit_price) as revenue from order_items oi join orders o on o.id=oi.order_id join products p on p.id=oi.product_id where o.status='completed' group by p.category order by 2 desc;"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.details.preview[0][0]").value("Electronics"))
                .andExpect(jsonPath("$.details.preview[0][1]").value("1525000"))
                .andExpect(jsonPath("$.details.preview[1][1]").value("320000"))
                .andExpect(jsonPath("$.details.preview[2][1]").value("40000"))
                .andExpect(jsonPath("$.skillUpdate.newAssessedLevel").value(3));

        long allCustomers = challenge("SQL: все клиенты и их выручка");
        // typical mistake: filtering status in WHERE drops Timur (no completed orders) → 7 rows instead of 8
        submit(auth, allCustomers, """
                {"sql":"select c.name, count(distinct o.id), coalesce(sum(oi.quantity*oi.unit_price),0) as revenue from customers c left join orders o on o.customer_id=c.id left join order_items oi on oi.order_id=o.id where o.status='completed' group by c.id, c.name order by revenue desc, c.name"}
                """)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.details.rowCount").value(7))
                .andExpect(jsonPath("$.details.expectedRowCount").value(8));
        // correct: Aigerim 2/760000 first, Timur 0/0 last
        submit(auth, allCustomers, """
                {"sql":"select c.name, count(distinct o.id) as n, coalesce(sum(oi.quantity*oi.unit_price),0) as revenue from customers c left join orders o on o.customer_id=c.id and o.status='completed' left join order_items oi on oi.order_id=o.id group by c.id, c.name order by revenue desc, c.name"}
                """)
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.details.preview[0]").value(org.hamcrest.Matchers.contains("Aigerim", "2", "760000")))
                .andExpect(jsonPath("$.details.preview[7]").value(org.hamcrest.Matchers.contains("Timur", "0", "0")))
                .andExpect(jsonPath("$.skillUpdate.previousAssessedLevel").value(3))
                .andExpect(jsonPath("$.skillUpdate.newAssessedLevel").value(4));

        // right rows, wrong order
        submit(auth, challenge("SQL: клиенты из Алматы"), "{\"sql\":\"select name from customers where city='Almaty' order by name desc\"}")
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.feedback").value(org.hamcrest.Matchers.containsString("порядок")));
        // passing a lower-level challenge never lowers the assessed level
        submit(auth, challenge("SQL: клиенты из Алматы"), "{\"sql\":\"select name from customers where city='Almaty' order by signup_date\"}")
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.skillUpdate").doesNotExist());
        submit(auth, challenge("SQL: клиенты из Алматы"), "{\"sql\":\"drop table customers\"}")
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.feedback").value(org.hamcrest.Matchers.containsString("SELECT")));

        mvc.perform(get("/me/skills").header("Authorization", auth))
                .andExpect(jsonPath("$[?(@.source=='ASSESSED')].level").value(org.hamcrest.Matchers.contains(4)))
                .andExpect(jsonPath("$[?(@.source=='ASSESSED')].proven").value(org.hamcrest.Matchers.contains(true)));
    }

    @Test
    void passingQuizUpdatesSkillGapAndClosesRoadmapModule() throws Exception {
        String auth = demo();
        mvc.perform(post("/me/career-goals").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"professionSlug\":\"financial-analyst\"}")).andExpect(status().isCreated());
        long uid = ((Number) JsonPath.read(mvc.perform(get("/me").header("Authorization", auth)).andReturn()
                .getResponse().getContentAsString(), "$.id")).longValue();
        for (Object[] s : new Object[][]{{"excel", 4}, {"accounting", 3}, {"sql", 2}, {"financial-modeling", 1}}) {
            jdbc.update("insert into user_skills (user_id, skill_id, level, source) select ?, id, ?, 'CV_EXTRACTED' "
                    + "from skills where slug = ?", uid, s[1], s[0]);
        }
        String roadmap = mvc.perform(post("/me/roadmap").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString();
        List<Integer> valuationItem = JsonPath.read(roadmap, "$.items[?(@.skill.slug=='valuation')].id");
        List<Object> valuationChallenges = JsonPath.read(roadmap, "$.items[?(@.skill.slug=='valuation')].challenges[*]");
        assertThat(valuationChallenges).hasSize(1);

        // wrong answers: 2/5 = 40% → FAILED, module moves to IN_PROGRESS
        submit(auth, challenge("Оценка компаний: DCF и мультипликаторы"),
                "{\"answers\":{\"q1\":1,\"q2\":2,\"q3\":0,\"q4\":1,\"q5\":0}}")
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.score").value(40))
                .andExpect(jsonPath("$.details.questions[2].correct").value(false));
        mvc.perform(get("/me/roadmap").header("Authorization", auth))
                .andExpect(jsonPath("$.items[?(@.skill.slug=='valuation')].status")
                        .value(org.hamcrest.Matchers.contains("IN_PROGRESS")));

        submit(auth, challenge("Оценка компаний: DCF и мультипликаторы"),
                "{\"answers\":{\"q1\":1,\"q2\":2,\"q3\":3,\"q4\":0,\"q5\":1}}")
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.assessedBy").value("AUTO"))
                .andExpect(jsonPath("$.skillUpdate.newAssessedLevel").value(3))
                .andExpect(jsonPath("$.roadmapItemCompletedId").value(valuationItem.get(0)));

        // valuation (importance 3 of 40) is now proven: provenScore = 3/40 = 7.5%
        mvc.perform(get("/me/skill-gap").header("Authorization", auth))
                .andExpect(jsonPath("$.skills[?(@.skill.slug=='valuation')].status").value(org.hamcrest.Matchers.contains("STRONG")))
                .andExpect(jsonPath("$.skills[?(@.skill.slug=='valuation')].proven").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.readiness.provenScore").value(7.5));
        mvc.perform(get("/me/progress").header("Authorization", auth))
                .andExpect(jsonPath("$.roadmap.done").value(1))
                .andExpect(jsonPath("$.recentEvents[0].type").value("ROADMAP_ITEM_STATUS_CHANGED"))
                .andExpect(jsonPath("$.recentEvents[0].payload.auto").value(true));
        mvc.perform(get("/me/submissions").header("Authorization", auth))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("PASSED"));
    }

    @Test
    void openTextIsPendingWithoutAi() throws Exception {
        String auth = demo();
        submit(auth, challenge("Требования: user stories для загрузки CV"),
                "{\"text\":\"Как соискатель, я хочу загрузить CV в PDF, чтобы не заполнять профиль вручную.\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.score").doesNotExist());
    }
}
