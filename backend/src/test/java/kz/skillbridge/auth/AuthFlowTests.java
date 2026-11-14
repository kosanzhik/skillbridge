package kz.skillbridge.auth;

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
class AuthFlowTests {

    @Autowired
    MockMvc mvc;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"secret123","fullName":"Aruzhan","consent":true}
                """.formatted(email);
        String json = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.user.profile.fullName").value("Aruzhan"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    @Test
    void registerLoginAndReadProfile() throws Exception {
        String token = register("User1@Example.com");

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user1@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());

        mvc.perform(get("/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.onboarded").value(false));
    }

    @Test
    void rejectsDuplicateEmailWrongPasswordAndMissingConsent() throws Exception {
        register("dup@example.com");
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"DUP@example.com\",\"password\":\"secret123\",\"consent\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dup@example.com\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"noconsent@example.com\",\"password\":\"secret123\",\"consent\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.consent").exists());
    }

    @Test
    void protectedEndpointsRequireValidToken() throws Exception {
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/me").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfileCompletesOnboarding() throws Exception {
        String token = register("onboard@example.com");
        mvc.perform(put("/me/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Aruzhan K","location":"Astana","languages":["Kazakh","English"],
                                 "desiredLevel":"JUNIOR","workFormat":"REMOTE","completeOnboarding":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.languages[1]").value("English"))
                .andExpect(jsonPath("$.profile.workFormat").value("REMOTE"))
                .andExpect(jsonPath("$.profile.onboarded").value(true));
    }

    @Test
    void demoAccountIsOnboardedAndCannotLoginByPassword() throws Exception {
        String json = mvc.perform(post("/auth/demo"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.demo").value(true))
                .andExpect(jsonPath("$.user.profile.onboarded").value(true))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(json, "$.token");
        mvc.perform(get("/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void deleteAccountRemovesUser() throws Exception {
        String token = register("delete-me@example.com");
        mvc.perform(delete("/me").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/me").header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"delete-me@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
