package kz.skillbridge.gap.dto;

/**
 * Job Readiness Score (spec §5.1): an orientation metric, not a probability of getting a job.
 * {@code provenScore} counts only assessed/verified levels.
 */
public record ReadinessScore(double score, double provenScore, String explanation) {
}
