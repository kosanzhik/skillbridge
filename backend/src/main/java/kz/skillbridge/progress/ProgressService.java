package kz.skillbridge.progress;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kz.skillbridge.career.CareerGoal;
import kz.skillbridge.career.CareerGoalRepository;
import kz.skillbridge.gap.SkillGapService;
import kz.skillbridge.gap.dto.ReadinessScore;
import kz.skillbridge.progress.dto.ProgressResponse;
import kz.skillbridge.resume.ParseStatus;
import kz.skillbridge.resume.ResumeRepository;
import kz.skillbridge.roadmap.ItemStatus;
import kz.skillbridge.roadmap.RoadmapRepository;
import kz.skillbridge.roadmap.RoadmapService;
import kz.skillbridge.roadmap.RoadmapStatus;
import kz.skillbridge.roadmap.dto.RoadmapDto;
import kz.skillbridge.skill.SkillProfileService;
import kz.skillbridge.skill.SkillSource;
import kz.skillbridge.skill.UserSkill;
import kz.skillbridge.skill.UserSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final CareerGoalRepository goals;
    private final RoadmapRepository roadmaps;
    private final RoadmapService roadmapService;
    private final SkillGapService gapService;
    private final UserSkillRepository userSkills;
    private final ResumeRepository resumes;
    private final ProgressEventRepository events;
    private final JsonMapper json;

    @Transactional(readOnly = true)
    public ProgressResponse progress(Long userId) {
        Optional<CareerGoal> goal = goals.findByUserIdAndPrimaryTrue(userId);
        Optional<RoadmapDto> roadmap = goal.flatMap(g -> roadmaps
                .findFirstByUserIdAndCareerGoalIdAndStatusOrderByCreatedAtDesc(userId, g.getId(), RoadmapStatus.ACTIVE)
                .map(roadmapService::toDto));
        ReadinessScore readiness = goal.map(g -> gapService.analyse(userId, g.getProfession()).readiness()).orElse(null);

        List<UserSkill> skills = userSkills.findByUserIdOrderBySkillName(userId);
        long distinct = skills.stream().map(s -> s.getSkill().getId()).distinct().count();
        long proven = skills.stream().filter(s -> SkillProfileService.PROVEN.contains(s.getSource()))
                .map(s -> s.getSkill().getId()).distinct().count();
        var summary = new ProgressResponse.SkillsSummary((int) distinct, (int) proven,
                count(skills, SkillSource.CV_EXTRACTED), count(skills, SkillSource.SELF_REPORTED));

        List<ProgressResponse.Event> recent = events.findTop20ByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(e -> new ProgressResponse.Event(e.getType(), e.getPayloadJson() == null ? Map.of()
                        : json.readValue(e.getPayloadJson(), new TypeReference<Map<String, Object>>() { }),
                        e.getCreatedAt()))
                .toList();

        return new ProgressResponse(roadmap.map(RoadmapDto::progress).orElse(null), readiness, summary,
                nextAction(userId, goal, roadmap), recent);
    }

    private ProgressResponse.NextAction nextAction(Long userId, Optional<CareerGoal> goal, Optional<RoadmapDto> roadmap) {
        if (resumes.findByUserIdAndParseStatus(userId, ParseStatus.CONFIRMED).isEmpty()) {
            return new ProgressResponse.NextAction("UPLOAD_CV", "Загрузите и подтвердите CV", null);
        }
        if (goal.isEmpty()) {
            return new ProgressResponse.NextAction("CHOOSE_GOAL", "Выберите целевую профессию", null);
        }
        if (roadmap.isEmpty()) {
            return new ProgressResponse.NextAction("GENERATE_ROADMAP", "Сформируйте персональный план развития", null);
        }
        return roadmap.get().items().stream()
                .filter(i -> i.status() != ItemStatus.DONE)
                .min(Comparator.comparing((RoadmapDto.Item i) -> i.status() == ItemStatus.IN_PROGRESS ? 0 : 1)
                        .thenComparing(RoadmapDto.Item::position))
                .map(i -> new ProgressResponse.NextAction("CONTINUE_ITEM", i.title(), i.id()))
                .orElse(new ProgressResponse.NextAction("REVIEW_JOBS",
                        "План выполнен — посмотрите подходящие вакансии", null));
    }

    private static int count(List<UserSkill> skills, SkillSource source) {
        return (int) skills.stream().filter(s -> s.getSource() == source).count();
    }
}
