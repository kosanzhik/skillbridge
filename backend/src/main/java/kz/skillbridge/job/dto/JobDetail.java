package kz.skillbridge.job.dto;

import java.util.List;
import kz.skillbridge.job.RequirementType;
import kz.skillbridge.skill.dto.SkillDto;

public record JobDetail(JobSummary job, String employment, String description, List<Requirement> requirements) {
    public record Requirement(SkillDto skill, RequirementType type) {
    }
}
