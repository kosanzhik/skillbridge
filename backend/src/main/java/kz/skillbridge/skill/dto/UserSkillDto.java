package kz.skillbridge.skill.dto;

import java.time.Instant;
import kz.skillbridge.skill.SkillSource;
import kz.skillbridge.skill.UserSkill;

public record UserSkillDto(SkillDto skill, int level, SkillSource source, boolean proven, String evidence,
                           Instant updatedAt) {
    public static UserSkillDto from(UserSkill us) {
        boolean proven = us.getSource() == SkillSource.ASSESSED || us.getSource() == SkillSource.VERIFIED;
        return new UserSkillDto(SkillDto.from(us.getSkill()), us.getLevel(), us.getSource(), proven,
                us.getEvidence(), us.getUpdatedAt());
    }
}
