package kz.skillbridge.challenge.dto;

import jakarta.validation.constraints.Size;
import java.util.Map;

/** QUIZ → answers {questionId: optionIndex}; SQL → sql; OPEN_TEXT → text. */
public record SubmitRequest(@Size(max = 50) Map<String, Integer> answers, @Size(max = 5000) String sql,
                            @Size(max = 8000) String text) {
}
