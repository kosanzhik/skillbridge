package kz.skillbridge.learning;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LearningController {

    private final LearningResourceRepository resources;
    private final LinkChecker linkChecker;

    /** Learning Hub: materials for a skill (by slug). */
    @GetMapping("/learning/resources")
    @Transactional(readOnly = true)
    public List<LearningResourceDto> bySkill(@RequestParam String skill) {
        return resources.findBySkillSlugAndCheckFailedFalseOrderByLevelAscTitleAsc(skill).stream()
                .map(LearningResourceDto::from).toList();
    }

    @PostMapping("/admin/learning/check-links")
    public LinkChecker.Result checkLinks() {
        return linkChecker.checkAll();
    }
}
