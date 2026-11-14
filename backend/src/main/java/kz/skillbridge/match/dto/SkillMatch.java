package kz.skillbridge.match.dto;

import kz.skillbridge.job.RequirementType;
import kz.skillbridge.skill.SkillSource;

/**
 * One vacancy requirement vs. the user's profile.
 * credit = min(userLevel / requiredLevel, 1); contribution = weight × credit.
 */
public record SkillMatch(Long skillId, String skill, RequirementType requirement, int weight, int requiredLevel,
                         int userLevel, SkillSource userSource, boolean proven, double credit) {
}
