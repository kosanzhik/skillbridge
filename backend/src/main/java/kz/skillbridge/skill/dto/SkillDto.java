package kz.skillbridge.skill.dto;

import kz.skillbridge.skill.Skill;

public record SkillDto(Long id, String slug, String name, String category) {
    public static SkillDto from(Skill s) {
        return new SkillDto(s.getId(), s.getSlug(), s.getName(), s.getCategory());
    }
}
