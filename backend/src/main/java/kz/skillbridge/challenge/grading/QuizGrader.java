package kz.skillbridge.challenge.grading;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kz.skillbridge.challenge.Challenge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class QuizGrader {

    private final JsonMapper json;

    public GradeResult grade(Challenge c, Map<String, Integer> answers) {
        Map<String, Integer> key = json.readValue(c.getAnswerKey(), new TypeReference<Map<String, Integer>>() { });
        JsonNode questions = json.readTree(c.getRubricJson()).path("questions");
        int correct = 0;
        List<Map<String, Object>> perQuestion = new ArrayList<>();
        List<String> wrongTexts = new ArrayList<>();
        for (JsonNode q : questions) {
            String id = q.path("id").asString();
            Integer given = answers == null ? null : answers.get(id);
            boolean ok = given != null && given.equals(key.get(id));
            if (ok) {
                correct++;
            } else {
                wrongTexts.add(q.path("text").asString());
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", id);
            r.put("correct", ok);
            r.put("given", given);
            r.put("expected", key.get(id)); // revealed after submission for learning
            perQuestion.add(r);
        }
        int total = questions.size();
        int score = total == 0 ? 0 : Math.round(correct * 100f / total);
        String feedback = "Верно " + correct + " из " + total + "."
                + (wrongTexts.isEmpty() ? "" : " Повторите темы вопросов: " + String.join(" | ", wrongTexts));
        return new GradeResult(score, feedback, Map.of("questions", perQuestion), "AUTO", false);
    }
}
