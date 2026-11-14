package kz.skillbridge.skill;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import kz.skillbridge.common.CurrentUser;
import kz.skillbridge.skill.dto.SkillDto;
import kz.skillbridge.skill.dto.UserSkillDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SkillController {

    private final SkillRepository skills;
    private final UserSkillRepository userSkills;

    /** Public skill catalog, optionally filtered by name/alias substring. */
    @GetMapping("/skills")
    public List<SkillDto> catalog(@RequestParam(required = false) String q) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return skills.findAll().stream()
                .filter(s -> needle.isEmpty()
                        || s.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || (s.getAliases() != null && s.getAliases().toLowerCase(Locale.ROOT).contains(needle)))
                .sorted(Comparator.comparing(Skill::getName))
                .map(SkillDto::from)
                .toList();
    }

    @GetMapping("/me/skills")
    @Transactional(readOnly = true)
    public List<UserSkillDto> mySkills(@AuthenticationPrincipal Jwt jwt) {
        return userSkills.findByUserIdOrderBySkillName(CurrentUser.id(jwt)).stream().map(UserSkillDto::from).toList();
    }
}
