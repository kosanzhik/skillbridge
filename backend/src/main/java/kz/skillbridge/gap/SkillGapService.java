package kz.skillbridge.gap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kz.skillbridge.career.CareerGoal;
import kz.skillbridge.career.CareerService;
import kz.skillbridge.career.Profession;
import kz.skillbridge.career.ProfessionRepository;
import kz.skillbridge.career.ProfessionSkill;
import kz.skillbridge.career.dto.CareerGoalDto;
import kz.skillbridge.gap.dto.ReadinessScore;
import kz.skillbridge.gap.dto.SkillGapItem;
import kz.skillbridge.gap.dto.SkillGapResponse;
import kz.skillbridge.job.JobRepository;
import kz.skillbridge.skill.Skill;
import kz.skillbridge.skill.SkillProfileService;
import kz.skillbridge.skill.SkillProfileService.Level;
import kz.skillbridge.skill.SkillRepository;
import kz.skillbridge.skill.dto.SkillDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skill Gap Analysis (spec §4 step 6): profile levels vs. what real vacancies of the target profession ask for.
 * With fewer than {@link #MIN_JOBS_FOR_MARKET} analysed vacancies the catalog baseline is used instead.
 */
@Service
@RequiredArgsConstructor
public class SkillGapService {

    public static final int MIN_JOBS_FOR_MARKET = 5;
    static final double MIN_SHARE_FOR_MARKET_ONLY_SKILL = 0.15;
    static final int DEFAULT_REQUIRED_LEVEL = 3;

    static final String METHODOLOGY = "Требуемый уровень — базовое ожидание для профессии (по умолчанию 3/5). "
            + "Важность (1–5) считается по доле вакансий, где встречается навык: ≥60% → 5, ≥40% → 4, ≥25% → 3, "
            + "≥10% → 2, иначе 1; если вакансий меньше " + MIN_JOBS_FOR_MARKET + ", используется базовая важность. "
            + "Статус: уровень ≥ требуемого — сильная сторона, отставание на 1–2 — нужно улучшить, на 3+ — большой пробел. "
            + "Приоритет = важность × отставание.";

    private final CareerService careerService;
    private final ProfessionRepository professions;
    private final JobRepository jobs;
    private final SkillRepository skills;
    private final SkillProfileService skillProfile;

    @Transactional(readOnly = true)
    public SkillGapResponse forPrimaryGoal(Long userId) {
        CareerGoal goal = careerService.requirePrimaryGoal(userId);
        return analyse(userId, goal.getProfession());
    }

    @Transactional(readOnly = true)
    public SkillGapResponse analyse(Long userId, Profession profession) {
        int jobsAnalyzed = (int) jobs.countJobsWithRequirements(profession.getId());
        boolean market = jobsAnalyzed >= MIN_JOBS_FOR_MARKET;

        Map<Long, long[]> demand = new HashMap<>();
        for (Object[] row : jobs.skillDemand(profession.getId())) {
            demand.put((Long) row[0], new long[]{(Long) row[1], ((Number) row[2]).longValue()});
        }

        // candidate skills: baseline requirements + skills frequently demanded by the market
        Map<Long, ProfessionSkill> baseline = new LinkedHashMap<>();
        for (ProfessionSkill ps : professions.findRequirements(profession.getId())) {
            baseline.put(ps.getSkill().getId(), ps);
        }
        Map<Long, Skill> candidates = new LinkedHashMap<>();
        baseline.values().forEach(ps -> candidates.put(ps.getSkill().getId(), ps.getSkill()));
        if (market) {
            List<Long> extra = demand.entrySet().stream()
                    .filter(e -> !candidates.containsKey(e.getKey()))
                    .filter(e -> (double) e.getValue()[0] / jobsAnalyzed >= MIN_SHARE_FOR_MARKET_ONLY_SKILL)
                    .map(Map.Entry::getKey).toList();
            skills.findAllById(extra).forEach(s -> candidates.put(s.getId(), s));
        }

        Map<Long, Level> user = skillProfile.effectiveLevels(userId);
        List<SkillGapItem> items = new ArrayList<>();
        for (Skill s : candidates.values()) {
            ProfessionSkill base = baseline.get(s.getId());
            long[] d = demand.get(s.getId());
            int mentioning = d == null ? 0 : (int) d[0];
            Double share = jobsAnalyzed == 0 ? null : round((double) mentioning / jobsAnalyzed);
            int importance = market ? importanceFromShare((double) mentioning / jobsAnalyzed)
                    : base != null ? base.getImportance() : 1;
            int required = base != null ? base.getRequiredLevel() : DEFAULT_REQUIRED_LEVEL;
            Level have = user.get(s.getId());
            int current = have == null ? 0 : have.level();
            int gap = Math.max(0, required - current);
            items.add(new SkillGapItem(SkillDto.from(s), current, have == null ? null : have.source(),
                    have != null && have.proven(), required, gap, status(gap), importance,
                    market ? "MARKET" : "BASELINE", share, mentioning, importance * gap));
        }
        items.sort(Comparator.comparingDouble(SkillGapItem::priority).reversed()
                .thenComparing(SkillGapItem::importance, Comparator.reverseOrder())
                .thenComparing(i -> i.skill().name()));

        return new SkillGapResponse(
                new CareerGoalDto.ProfessionRef(profession.getId(), profession.getSlug(), profession.getTitle()),
                jobsAnalyzed, market ? "MARKET" : "BASELINE", readiness(items), METHODOLOGY, items);
    }

    static GapStatus status(int gap) {
        return gap <= 0 ? GapStatus.STRONG : gap <= 2 ? GapStatus.NEEDS_IMPROVEMENT : GapStatus.BIG_GAP;
    }

    static int importanceFromShare(double share) {
        return share >= 0.6 ? 5 : share >= 0.4 ? 4 : share >= 0.25 ? 3 : share >= 0.10 ? 2 : 1;
    }

    static ReadinessScore readiness(List<SkillGapItem> items) {
        double total = 0;
        double earned = 0;
        double provenEarned = 0;
        for (SkillGapItem i : items) {
            double credit = Math.min((double) i.currentLevel() / i.requiredLevel(), 1.0);
            total += i.importance();
            earned += i.importance() * credit;
            if (i.proven()) {
                provenEarned += i.importance() * credit;
            }
        }
        double score = total == 0 ? 0 : round(earned / total * 100);
        double proven = total == 0 ? 0 : round(provenEarned / total * 100);
        String explanation = "Взвешенная по важности доля закрытых требований: Σ(важность × min(ваш уровень / требуемый, 1)) "
                + "/ Σ(важность). " + score + "% с учётом навыков из CV и самооценки, " + proven
                + "% — только по навыкам, подтверждённым заданиями. Это ориентир, а не вероятность получить работу.";
        return new ReadinessScore(score, proven, explanation);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
