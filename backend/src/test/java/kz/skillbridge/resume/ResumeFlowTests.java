package kz.skillbridge.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kz.skillbridge.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** No OpenAI key in tests → keyword fallback path. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ResumeFlowTests {

    @Autowired
    MockMvc mvc;

    static String demoToken(MockMvc mvc) throws Exception {
        String json = mvc.perform(post("/auth/demo")).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.token");
    }

    static MockMultipartFile pdf(byte[] bytes) {
        return new MockMultipartFile("file", "my cv.pdf", MediaType.APPLICATION_PDF_VALUE, bytes);
    }

    @Test
    void uploadParsesSkillsByKeywordsAndConfirmSavesEditedData() throws Exception {
        String auth = demoToken(mvc);
        String uploaded = mvc.perform(multipart("/resumes").file(pdf(TestPdfs.sampleCv())).header("Authorization", auth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARSED"))
                .andExpect(jsonPath("$.parser").value("KEYWORD"))
                .andExpect(jsonPath("$.originalFilename").value("my cv.pdf"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(uploaded, "$.id");
        List<String> names = JsonPath.read(uploaded, "$.parsed.skills[*].skillName");
        assertThat(names).contains("SQL", "Excel", "Power BI", "Python", "1C", "Accounting", "Financial Reporting");
        assertThat(names).doesNotContain("R"); // single letters are not scanned
        // keyword fallback never invents experience
        assertThat((List<?>) JsonPath.read(uploaded, "$.parsed.experiences")).isEmpty();

        // user corrects data: adds experience/education, raises Excel, maps an unknown skill to nothing
        String body = """
                {"fullName":"Aliya N","headline":"Junior Accountant","location":"Almaty","languages":["Kazakh","English"],
                 "experiences":[{"company":"Steppe Trade LLP","title":"Junior Accountant","startDate":"2022-09","endDate":null,
                                 "description":"IFRS reporting"}],
                 "educations":[{"institution":"Narxoz University","degree":"BSc","field":"Accounting","startYear":2018,"endYear":2022}],
                 "projects":[],"certifications":[],
                 "skills":[{"name":"Excel","level":4,"evidence":"daily reconciliations"},
                           {"name":"sql","level":2,"evidence":"basic queries"},
                           {"name":"Underwater Basket Weaving","level":5,"evidence":"hobby"}]}
                """;
        mvc.perform(post("/resumes/" + id + "/confirm").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.unmatchedSkills[0]").value("Underwater Basket Weaving"));

        mvc.perform(get("/me/skills").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].skill.name").value("Excel"))
                .andExpect(jsonPath("$[0].level").value(4))
                .andExpect(jsonPath("$[0].source").value("CV_EXTRACTED"))
                .andExpect(jsonPath("$[0].proven").value(false));

        mvc.perform(get("/me/background").header("Authorization", auth))
                .andExpect(jsonPath("$.experiences[0].startDate").value("2022-09-01"))
                .andExpect(jsonPath("$.experiences[0].endDate").doesNotExist())
                .andExpect(jsonPath("$.educations[0].institution").value("Narxoz University"));

        // re-confirm replaces, not duplicates
        mvc.perform(post("/resumes/" + id + "/confirm").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(get("/me/skills").header("Authorization", auth)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/me/background").header("Authorization", auth))
                .andExpect(jsonPath("$.experiences.length()").value(1));
    }

    @Test
    void rejectsNonPdfAndScannedPdf() throws Exception {
        String auth = demoToken(mvc);
        mvc.perform(multipart("/resumes").header("Authorization", auth)
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", "hello".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CV_INVALID_FILE"));
        mvc.perform(multipart("/resumes").header("Authorization", auth).file(pdf(TestPdfs.withText())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CV_NO_TEXT"));
        mvc.perform(multipart("/resumes").header("Authorization", auth)
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-garbage".getBytes())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CV_UNREADABLE"));
    }

    @Test
    void resumesAreIsolatedBetweenUsersAndFilesAreDeleted() throws Exception {
        String owner = demoToken(mvc);
        String other = demoToken(mvc);
        String uploaded = mvc.perform(multipart("/resumes").file(pdf(TestPdfs.sampleCv())).header("Authorization", owner))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(uploaded, "$.id");

        mvc.perform(get("/resumes/" + id).header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(get("/resumes/" + id + "/file").header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(get("/resumes/" + id + "/file").header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray()).startsWith("%PDF-".getBytes()));

        String meJson = mvc.perform(get("/me").header("Authorization", owner)).andReturn().getResponse().getContentAsString();
        int userId = JsonPath.read(meJson, "$.id");
        Path userDir = Path.of("target/test-storage/users/" + userId);
        assertThat(userDir).exists();

        mvc.perform(delete("/me").header("Authorization", owner)).andExpect(status().isNoContent());
        assertThat(Files.exists(userDir)).isFalse();
    }
}
