package kz.skillbridge.challenge.dto;

import java.util.List;
import kz.skillbridge.challenge.ChallengeType;
import kz.skillbridge.skill.dto.SkillDto;

/** Public view of a challenge: never contains the answer key or reference query. */
public record ChallengeDto(Long id, SkillDto skill, String title, String description, ChallengeType type, int level,
                           int passScore, List<Question> questions, List<Criterion> criteria) {

    public record Question(String id, String text, List<String> options) {
    }

    public record Criterion(String name, String description, int maxPoints) {
    }
}
