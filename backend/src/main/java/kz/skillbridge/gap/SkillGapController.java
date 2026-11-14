package kz.skillbridge.gap;

import kz.skillbridge.common.CurrentUser;
import kz.skillbridge.gap.dto.SkillGapResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SkillGapController {

    private final SkillGapService service;

    @GetMapping("/me/skill-gap")
    public SkillGapResponse skillGap(@AuthenticationPrincipal Jwt jwt) {
        return service.forPrimaryGoal(CurrentUser.id(jwt));
    }
}
