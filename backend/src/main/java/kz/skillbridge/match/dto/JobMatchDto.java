package kz.skillbridge.match.dto;

import java.util.List;
import kz.skillbridge.job.dto.JobSummary;

/** {@code score} is null when no requirements could be detected in the vacancy. */
public record JobMatchDto(JobSummary job, Double score, List<SkillMatch> matched, List<SkillMatch> partial,
                          List<SkillMatch> missing) {
}
