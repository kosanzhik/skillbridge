package kz.skillbridge.match;

import kz.skillbridge.common.CurrentUser;
import kz.skillbridge.match.dto.MatchesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MatchController {

    private final MatchingService service;

    /** Vacancies of the primary career goal, best match first. */
    @GetMapping("/me/job-matches")
    public MatchesResponse matches(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam(defaultValue = "20") int limit) {
        return service.matchesForPrimaryGoal(CurrentUser.id(jwt), Math.max(1, Math.min(limit, 100)));
    }
}
