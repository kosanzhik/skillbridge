package kz.skillbridge.progress.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import kz.skillbridge.gap.dto.ReadinessScore;
import kz.skillbridge.roadmap.dto.RoadmapDto;

/** Dashboard summary. Learning time is not tracked, so it is intentionally absent (spec §4 step 9). */
public record ProgressResponse(RoadmapDto.Progress roadmap, ReadinessScore readiness, SkillsSummary skills,
                               NextAction nextAction, List<Event> recentEvents) {

    public record SkillsSummary(int total, int proven, int cvExtracted, int selfReported) {
    }

    /** type: UPLOAD_CV, CHOOSE_GOAL, GENERATE_ROADMAP, CONTINUE_ITEM, REVIEW_JOBS. */
    public record NextAction(String type, String title, Long roadmapItemId) {
    }

    public record Event(String type, Map<String, Object> payload, Instant createdAt) {
    }
}
