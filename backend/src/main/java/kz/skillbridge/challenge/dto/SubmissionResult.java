package kz.skillbridge.challenge.dto;

import java.time.Instant;
import java.util.Map;

/**
 * status: PASSED, FAILED or PENDING (saved, not graded yet). AI-graded results are recommendations.
 * skillUpdate is set when the assessed skill level increased.
 */
public record SubmissionResult(Long submissionId, Long challengeId, String challengeTitle, String status,
                               Integer score, int passScore, String assessedBy, String feedback,
                               Map<String, Object> details, SkillUpdate skillUpdate, Long roadmapItemCompletedId,
                               Instant submittedAt) {

    public record SkillUpdate(String skill, Integer previousAssessedLevel, int newAssessedLevel) {
    }
}
