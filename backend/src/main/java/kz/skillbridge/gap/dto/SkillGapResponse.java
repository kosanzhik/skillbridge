package kz.skillbridge.gap.dto;

import java.util.List;
import kz.skillbridge.career.dto.CareerGoalDto;

public record SkillGapResponse(CareerGoalDto.ProfessionRef profession, int jobsAnalyzed, String requirementsSource,
                               ReadinessScore readiness, String methodology, List<SkillGapItem> skills) {
}
