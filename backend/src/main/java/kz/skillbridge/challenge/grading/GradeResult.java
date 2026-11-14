package kz.skillbridge.challenge.grading;

import java.util.Map;

/** {@code pending} = could not be graded automatically now (e.g. AI not configured). */
public record GradeResult(int score, String feedback, Map<String, Object> details, String assessedBy,
                          boolean pending) {

    public static GradeResult pending(String reason) {
        return new GradeResult(0, reason, Map.of(), null, true);
    }
}
