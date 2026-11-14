package kz.skillbridge.resume;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kz.skillbridge.TestcontainersConfig;
import kz.skillbridge.ai.AiException;
import kz.skillbridge.ai.OpenAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AiResumeParsingTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OpenAiClient openAi;

    @Test
    void aiOutputIsSanitizedAndNormalized() throws Exception {
        when(openAi.isEnabled()).thenReturn(true);
        when(openAi.structuredJson(anyString(), anyString(), eq("parsed_resume"), any())).thenReturn("""
                {"fullName":"  Aliya  ","headline":null,"location":"Almaty","languages":["Kazakh","Kazakh"],
                 "experiences":[{"company":"Steppe Trade","title":"Junior Accountant","startDate":"2022.9","endDate":null,"description":"IFRS"},
                                {"company":"Ghost","title":"   ","startDate":"2020","endDate":"2021","description":null}],
                 "educations":[{"institution":"Narxoz","degree":"BSc","field":"Accounting","startYear":2018,"endYear":3022}],
                 "projects":[],"certifications":[],
                 "skills":[{"name":"MS Excel","level":9,"evidence":"pivot tables"},
                           {"name":"Power BI","level":0,"evidence":"dashboard"},
                           {"name":"Quantum Accounting","level":3,"evidence":"?"}]}
                """);
        String auth = ResumeFlowTests.demoToken(mvc);
        mvc.perform(multipart("/resumes").file(ResumeFlowTests.pdf(TestPdfs.sampleCv())).header("Authorization", auth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parser").value("OPENAI"))
                .andExpect(jsonPath("$.parsed.fullName").value("Aliya"))
                .andExpect(jsonPath("$.parsed.languages.length()").value(1))
                .andExpect(jsonPath("$.parsed.experiences.length()").value(1))         // empty title dropped
                .andExpect(jsonPath("$.parsed.experiences[0].startDate").value("2022-09"))
                .andExpect(jsonPath("$.parsed.educations[0].endYear").doesNotExist()) // implausible year dropped
                .andExpect(jsonPath("$.parsed.skills[0].skillName").value("Excel"))    // alias "ms excel"
                .andExpect(jsonPath("$.parsed.skills[0].level").value(5))             // clamped
                .andExpect(jsonPath("$.parsed.skills[1].level").value(1))
                .andExpect(jsonPath("$.unmatchedSkills[0]").value("Quantum Accounting"));
    }

    @Test
    void fallsBackToKeywordsWhenAiFails() throws Exception {
        when(openAi.isEnabled()).thenReturn(true);
        when(openAi.structuredJson(anyString(), anyString(), anyString(), any())).thenThrow(new AiException("timeout"));
        String auth = ResumeFlowTests.demoToken(mvc);
        mvc.perform(multipart("/resumes").file(ResumeFlowTests.pdf(TestPdfs.sampleCv())).header("Authorization", auth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARSED"))
                .andExpect(jsonPath("$.parser").value("KEYWORD"))
                .andExpect(jsonPath("$.parseError").isString());
    }
}
