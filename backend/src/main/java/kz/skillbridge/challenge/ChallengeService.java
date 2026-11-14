package kz.skillbridge.challenge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kz.skillbridge.challenge.dto.ChallengeDto;
import kz.skillbridge.challenge.dto.SubmissionResult;
import kz.skillbridge.challenge.dto.SubmitRequest;
import kz.skillbridge.challenge.grading.GradeResult;
import kz.skillbridge.challenge.grading.OpenTextGrader;
import kz.skillbridge.challenge.grading.QuizGrader;
import kz.skillbridge.challenge.grading.SqlGrader;
import kz.skillbridge.common.ApiException;
import kz.skillbridge.progress.ProgressRecorder;
import kz.skillbridge.roadmap.ItemStatus;
import kz.skillbridge.roadmap.RoadmapItem;
import kz.skillbridge.roadmap.RoadmapRepository;
import kz.skillbridge.skill.Skill;
import kz.skillbridge.skill.SkillSource;
import kz.skillbridge.skill.UserSkill;
import kz.skillbridge.skill.UserSkillRepository;
import kz.skillbridge.skill.dto.SkillDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Practical assessment (spec §4 step 8–9, §10 "Assessment Engine"): grade → store → raise the ASSESSED skill
 * level → close matching roadmap modules → record progress. Grading runs outside DB transactions.
 */
@Service
public class ChallengeService {

    private final ChallengeRepository challenges;
    private final SubmissionRepository submissions;
    private final AssessmentRepository assessments;
    private final UserSkillRepository userSkills;
    private final RoadmapRepository roadmaps;
    private final ProgressRecorder progress;
    private final QuizGrader quizGrader;
    private final SqlGrader sqlGrader;
    private final OpenTextGrader openTextGrader;
    private final JsonMapper json;
    private final TransactionTemplate tx;

    public ChallengeService(ChallengeRepository challenges, SubmissionRepository submissions,
                            AssessmentRepository assessments, UserSkillRepository userSkills,
                            RoadmapRepository roadmaps, ProgressRecorder progress, QuizGrader quizGrader,
                            SqlGrader sqlGrader, OpenTextGrader openTextGrader, JsonMapper json,
                            PlatformTransactionManager txManager) {
        this.challenges = challenges;
        this.submissions = submissions;
        this.assessments = assessments;
        this.userSkills = userSkills;
        this.roadmaps = roadmaps;
        this.progress = progress;
        this.quizGrader = quizGrader;
        this.sqlGrader = sqlGrader;
        this.openTextGrader = openTextGrader;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
    }

    public List<ChallengeDto> bySkill(String slug) {
        return tx.execute(s -> challenges.findBySkillSlugOrderByLevelAscIdAsc(slug).stream().map(this::toDto).toList());
    }

    public ChallengeDto get(Long id) {
        return tx.execute(s -> toDto(load(id)));
    }

    public SubmissionResult submit(Long userId, Long challengeId, SubmitRequest req) {
        Challenge c = tx.execute(s -> {
            Challenge ch = load(challengeId);
            ch.getSkill().getName(); // initialize for use outside the transaction
            return ch;
        });
        GradeResult grade = switch (c.getType()) {
            case QUIZ -> quizGrader.grade(c, req.answers());
            case SQL -> sqlGrader.grade(c, req.sql());
            case OPEN_TEXT -> openTextGrader.grade(c, req.text());
        };
        String answer = switch (c.getType()) {
            case QUIZ -> json.writeValueAsString(req.answers() == null ? Map.of() : req.answers());
            case SQL -> req.sql() == null ? "" : req.sql();
            case OPEN_TEXT -> req.text() == null ? "" : req.text();
        };
        return tx.execute(s -> persist(userId, c, answer, grade));
    }

    public List<SubmissionResult> history(Long userId) {
        return tx.execute(s -> assessments.findForUser(userId).stream().limit(50).map(a -> {
            Challenge c = a.getSubmission().getChallenge();
            return new SubmissionResult(a.getSubmission().getId(), c.getId(), c.getTitle(),
                    a.isPassed() ? "PASSED" : "FAILED", a.getScore(), c.getPassScore(), a.getAssessedBy(),
                    a.getFeedback(), null, null, null, a.getSubmission().getSubmittedAt());
        }).toList());
    }

    private SubmissionResult persist(Long userId, Challenge c, String answer, GradeResult grade) {
        ChallengeSubmission sub = new ChallengeSubmission();
        sub.setChallenge(challenges.getReferenceById(c.getId()));
        sub.setUserId(userId);
        sub.setAnswer(answer);
        sub = submissions.save(sub);

        if (grade.pending()) {
            progress.record(userId, ProgressRecorder.CHALLENGE_SUBMITTED,
                    Map.of("challengeId", c.getId(), "title", c.getTitle(), "status", "PENDING"));
            return new SubmissionResult(sub.getId(), c.getId(), c.getTitle(), "PENDING", null, c.getPassScore(),
                    null, grade.feedback(), grade.details(), null, null, sub.getSubmittedAt());
        }

        boolean passed = grade.score() >= c.getPassScore();
        Assessment a = new Assessment();
        a.setSubmission(sub);
        a.setScore(grade.score());
        a.setPassed(passed);
        a.setFeedback(grade.feedback());
        a.setAssessedBy(grade.assessedBy());
        a.setDetailsJson(json.writeValueAsString(grade.details()));
        assessments.save(a);
        progress.record(userId, ProgressRecorder.CHALLENGE_SUBMITTED, Map.of("challengeId", c.getId(),
                "title", c.getTitle(), "score", grade.score(), "status", passed ? "PASSED" : "FAILED"));

        SubmissionResult.SkillUpdate update = null;
        Long completedItem = null;
        Long skillId = c.getSkill().getId();
        if (passed) {
            update = raiseAssessedLevel(userId, c);
            int assessedLevel = update != null ? update.newAssessedLevel() : currentAssessed(userId, skillId);
            for (RoadmapItem item : roadmaps.findOpenItemsForSkill(userId, skillId)) {
                if (item.getTargetLevel() != null && assessedLevel >= item.getTargetLevel()) {
                    ItemStatus from = item.getStatus();
                    item.setStatus(ItemStatus.DONE);
                    item.setCompletedAt(Instant.now());
                    completedItem = item.getId();
                    progress.record(userId, ProgressRecorder.ROADMAP_ITEM_STATUS_CHANGED, Map.of("itemId", item.getId(),
                            "title", item.getTitle(), "from", from, "to", ItemStatus.DONE, "auto", true));
                } else if (item.getStatus() == ItemStatus.TODO) {
                    item.setStatus(ItemStatus.IN_PROGRESS);
                }
            }
        } else {
            roadmaps.findOpenItemsForSkill(userId, skillId).stream()
                    .filter(i -> i.getStatus() == ItemStatus.TODO)
                    .forEach(i -> i.setStatus(ItemStatus.IN_PROGRESS));
        }
        return new SubmissionResult(sub.getId(), c.getId(), c.getTitle(), passed ? "PASSED" : "FAILED", grade.score(),
                c.getPassScore(), grade.assessedBy(), grade.feedback(), grade.details(), update, completedItem,
                sub.getSubmittedAt());
    }

    /** Raises (never lowers) the ASSESSED level to the challenge level. Returns null when unchanged. */
    private SubmissionResult.SkillUpdate raiseAssessedLevel(Long userId, Challenge c) {
        Skill skill = c.getSkill();
        UserSkill assessed = userSkills.findByUserIdOrderBySkillName(userId).stream()
                .filter(us -> us.getSkill().getId().equals(skill.getId()) && us.getSource() == SkillSource.ASSESSED)
                .findFirst().orElse(null);
        Integer previous = assessed == null ? null : (int) assessed.getLevel();
        if (previous != null && previous >= c.getLevel()) {
            return null;
        }
        if (assessed == null) {
            assessed = new UserSkill();
            assessed.setUserId(userId);
            assessed.setSkill(skill);
            assessed.setSource(SkillSource.ASSESSED);
        }
        assessed.setLevel(c.getLevel());
        assessed.setEvidence("Пройдено задание «" + c.getTitle() + "» (уровень " + c.getLevel() + ")");
        userSkills.save(assessed);
        progress.record(userId, ProgressRecorder.SKILL_LEVEL_CHANGED, Map.of("skill", skill.getName(),
                "source", SkillSource.ASSESSED, "from", previous == null ? 0 : previous, "to", (int) c.getLevel()));
        return new SubmissionResult.SkillUpdate(skill.getName(), previous, c.getLevel());
    }

    private int currentAssessed(Long userId, Long skillId) {
        return userSkills.findByUserIdOrderBySkillName(userId).stream()
                .filter(us -> us.getSkill().getId().equals(skillId) && us.getSource() == SkillSource.ASSESSED)
                .mapToInt(UserSkill::getLevel).max().orElse(0);
    }

    private Challenge load(Long id) {
        return challenges.findById(id).orElseThrow(() -> ApiException.notFound("Challenge"));
    }

    private ChallengeDto toDto(Challenge c) {
        List<ChallengeDto.Question> questions = List.of();
        List<ChallengeDto.Criterion> criteria = List.of();
        if (c.getRubricJson() != null) {
            JsonNode rubric = json.readTree(c.getRubricJson());
            if (c.getType() == ChallengeType.QUIZ) {
                questions = new ArrayList<>();
                for (JsonNode q : rubric.path("questions")) {
                    questions.add(new ChallengeDto.Question(q.path("id").asString(), q.path("text").asString(),
                            json.convertValue(q.path("options"), new TypeReference<List<String>>() { })));
                }
            } else if (c.getType() == ChallengeType.OPEN_TEXT) {
                criteria = new ArrayList<>();
                for (JsonNode cr : rubric.path("criteria")) {
                    criteria.add(new ChallengeDto.Criterion(cr.path("name").asString(),
                            cr.path("description").asString(), cr.path("maxPoints").asInt()));
                }
            }
        }
        return new ChallengeDto(c.getId(), SkillDto.from(c.getSkill()), c.getTitle(), c.getDescription(), c.getType(),
                c.getLevel(), c.getPassScore(), questions, criteria);
    }
}
