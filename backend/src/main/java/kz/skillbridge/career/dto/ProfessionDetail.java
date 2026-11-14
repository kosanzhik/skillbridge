package kz.skillbridge.career.dto;

import java.util.List;
import kz.skillbridge.career.ProfessionSkill;
import kz.skillbridge.skill.dto.SkillDto;

/**
 * Profession with baseline skill requirements. {@code requirementsSource = BASELINE} until the
 * Job Integration module recomputes them from real vacancies.
 */
public record ProfessionDetail(Long id, String slug, String title, String description, String requirementsSource,
                               List<Requirement> requirements) {

    public record Requirement(SkillDto skill, int requiredLevel, int importance) {
        public static Requirement from(ProfessionSkill ps) {
            return new Requirement(SkillDto.from(ps.getSkill()), ps.getRequiredLevel(), ps.getImportance());
        }
    }
}
