package kz.skillbridge.career;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import kz.skillbridge.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CareerGoalTests {

    @Autowired
    MockMvc mvc;

    private String demo() throws Exception {
        String json = mvc.perform(post("/auth/demo")).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.token");
    }

    private int addGoal(String auth, String slug, Boolean primary) throws Exception {
        String body = primary == null ? "{\"professionSlug\":\"%s\"}".formatted(slug)
                : "{\"professionSlug\":\"%s\",\"primary\":%s}".formatted(slug, primary);
        String json = mvc.perform(post("/me/career-goals").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void professionsArePublicWithRequirements() throws Exception {
        mvc.perform(get("/professions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.slug=='data-analyst')].requirementCount").value(11));
        mvc.perform(get("/professions/financial-analyst"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementsSource").value("BASELINE"))
                .andExpect(jsonPath("$.requirements[0].importance").value(5));
        mvc.perform(get("/professions/astronaut")).andExpect(status().isNotFound());
    }

    @Test
    void firstGoalIsPrimaryAndPrimaryCanBeSwitched() throws Exception {
        String auth = demo();
        int da = addGoal(auth, "data-analyst", null);
        int fa = addGoal(auth, "financial-analyst", null);

        mvc.perform(get("/me/career-goals").header("Authorization", auth))
                .andExpect(jsonPath("$[0].id").value(da))
                .andExpect(jsonPath("$[0].primary").value(true))
                .andExpect(jsonPath("$[1].primary").value(false));

        mvc.perform(put("/me/career-goals/" + fa + "/primary").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.primary").value(true));
        mvc.perform(get("/me/career-goals").header("Authorization", auth))
                .andExpect(jsonPath("$[0].id").value(fa))
                .andExpect(jsonPath("$[1].primary").value(false));

        // adding with primary=true takes over
        int ba = addGoal(auth, "business-analyst", true);
        mvc.perform(get("/me/career-goals").header("Authorization", auth)).andExpect(jsonPath("$[0].id").value(ba));
    }

    @Test
    void enforcesDuplicatesLimitAndOwnership() throws Exception {
        String auth = demo();
        addGoal(auth, "data-analyst", null);
        mvc.perform(post("/me/career-goals").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"professionSlug\":\"data-analyst\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GOAL_EXISTS"));
        addGoal(auth, "financial-analyst", null);
        int third = addGoal(auth, "business-analyst", null);
        mvc.perform(post("/me/career-goals").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"professionSlug\":\"software-engineer\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GOAL_LIMIT"));

        String other = demo();
        mvc.perform(put("/me/career-goals/" + third + "/primary").header("Authorization", other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/me/career-goals/" + third).header("Authorization", other))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingPrimaryPromotesOldestRemaining() throws Exception {
        String auth = demo();
        int da = addGoal(auth, "data-analyst", null);
        int fa = addGoal(auth, "financial-analyst", null);
        addGoal(auth, "business-analyst", null);
        mvc.perform(delete("/me/career-goals/" + da).header("Authorization", auth)).andExpect(status().isNoContent());
        mvc.perform(get("/me/career-goals").header("Authorization", auth))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(fa))
                .andExpect(jsonPath("$[0].primary").value(true));
    }
}
