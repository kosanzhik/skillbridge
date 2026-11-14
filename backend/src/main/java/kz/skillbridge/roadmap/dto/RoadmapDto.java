package kz.skillbridge.roadmap.dto;

import java.time.Instant;
import java.util.List;
import kz.skillbridge.career.dto.CareerGoalDto;
import kz.skillbridge.learning.LearningResourceDto;
import kz.skillbridge.roadmap.ItemStatus;
import kz.skillbridge.roadmap.RoadmapStatus;
import kz.skillbridge.skill.dto.SkillDto;

public record RoadmapDto(Long id, CareerGoalDto.ProfessionRef profession, RoadmapStatus status, Instant createdAt,
                         Progress progress, String note, List<Item> items) {

    public record Progress(int total, int done, int inProgress, double percent) {
    }

    /** {@code estimatedHours} is a rough estimate, not a measured duration. */
    public record Item(Long id, int position, SkillDto skill, String title, String goal, List<String> topics,
                       Integer estimatedHours, String completionCriteria, String rationale, ItemStatus status,
                       Instant completedAt, List<LearningResourceDto> resources) {
    }
}
