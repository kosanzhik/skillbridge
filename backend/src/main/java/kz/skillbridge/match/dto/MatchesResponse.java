package kz.skillbridge.match.dto;

import java.util.List;
import kz.skillbridge.career.dto.CareerGoalDto;

public record MatchesResponse(CareerGoalDto.ProfessionRef profession, int jobsAnalyzed, String methodology,
                              String disclaimer, List<JobMatchDto> matches) {
}
