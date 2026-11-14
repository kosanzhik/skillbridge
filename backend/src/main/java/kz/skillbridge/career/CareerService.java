package kz.skillbridge.career;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kz.skillbridge.career.dto.AddCareerGoalRequest;
import kz.skillbridge.career.dto.CareerGoalDto;
import kz.skillbridge.career.dto.ProfessionDetail;
import kz.skillbridge.career.dto.ProfessionSummary;
import kz.skillbridge.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CareerService {

    /** Spec §4 step 3: several goals allowed, one primary is used for analysis. */
    public static final int MAX_GOALS = 3;

    private final ProfessionRepository professions;
    private final CareerGoalRepository goals;

    @Transactional(readOnly = true)
    public List<ProfessionSummary> listProfessions() {
        Map<Long, Long> counts = professions.countRequirements().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        return professions.findAllByOrderByTitle().stream()
                .map(p -> ProfessionSummary.from(p, counts.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfessionDetail profession(String slug) {
        Profession p = professions.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Profession"));
        List<ProfessionDetail.Requirement> reqs = professions.findRequirements(p.getId()).stream()
                .map(ProfessionDetail.Requirement::from).toList();
        return new ProfessionDetail(p.getId(), p.getSlug(), p.getTitle(), p.getDescription(), "BASELINE", reqs);
    }

    @Transactional(readOnly = true)
    public List<CareerGoalDto> goals(Long userId) {
        return goals.findByUserIdOrderByPrimaryDescCreatedAtAsc(userId).stream().map(CareerGoalDto::from).toList();
    }

    @Transactional
    public CareerGoalDto addGoal(Long userId, AddCareerGoalRequest req) {
        Profession p = professions.findBySlug(req.professionSlug().trim())
                .orElseThrow(() -> ApiException.notFound("Profession"));
        if (goals.existsByUserIdAndProfessionId(userId, p.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "GOAL_EXISTS", "This profession is already a career goal");
        }
        long count = goals.countByUserId(userId);
        if (count >= MAX_GOALS) {
            throw new ApiException(HttpStatus.CONFLICT, "GOAL_LIMIT",
                    "You can have at most " + MAX_GOALS + " career goals");
        }
        boolean makePrimary = count == 0 || Boolean.TRUE.equals(req.primary());
        if (makePrimary) {
            goals.clearPrimary(userId);
        }
        CareerGoal g = new CareerGoal();
        g.setUserId(userId);
        g.setProfession(p);
        g.setPrimary(makePrimary);
        return CareerGoalDto.from(goals.save(g));
    }

    @Transactional
    public CareerGoalDto makePrimary(Long userId, Long goalId) {
        owned(userId, goalId);
        goals.clearPrimary(userId);
        CareerGoal g = owned(userId, goalId); // reload after bulk update cleared the context
        g.setPrimary(true);
        return CareerGoalDto.from(g);
    }

    /** Removing the primary goal promotes the oldest remaining one. Roadmaps of the goal cascade away. */
    @Transactional
    public void deleteGoal(Long userId, Long goalId) {
        CareerGoal g = owned(userId, goalId);
        boolean wasPrimary = g.isPrimary();
        goals.delete(g);
        goals.flush();
        if (wasPrimary) {
            goals.findByUserIdOrderByPrimaryDescCreatedAtAsc(userId).stream()
                    .min(Comparator.comparing(CareerGoal::getCreatedAt).thenComparing(CareerGoal::getId))
                    .ifPresent(next -> next.setPrimary(true));
        }
    }

    /** Used by matching / skill gap / roadmap modules. */
    @Transactional(readOnly = true)
    public CareerGoal requirePrimaryGoal(Long userId) {
        return goals.findByUserIdAndPrimaryTrue(userId).orElseThrow(() -> new ApiException(
                HttpStatus.CONFLICT, "NO_CAREER_GOAL", "Choose a target profession first"));
    }

    private CareerGoal owned(Long userId, Long goalId) {
        return goals.findByIdAndUserId(goalId, userId).orElseThrow(() -> ApiException.notFound("Career goal"));
    }
}
