package kz.skillbridge.challenge;

import jakarta.validation.Valid;
import java.util.List;
import kz.skillbridge.challenge.dto.ChallengeDto;
import kz.skillbridge.challenge.dto.SubmissionResult;
import kz.skillbridge.challenge.dto.SubmitRequest;
import kz.skillbridge.common.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService service;

    @GetMapping("/challenges")
    public List<ChallengeDto> bySkill(@RequestParam String skill) {
        return service.bySkill(skill);
    }

    @GetMapping("/challenges/{id}")
    public ChallengeDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/challenges/{id}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionResult submit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                   @Valid @RequestBody SubmitRequest req) {
        return service.submit(CurrentUser.id(jwt), id, req);
    }

    @GetMapping("/me/submissions")
    public List<SubmissionResult> history(@AuthenticationPrincipal Jwt jwt) {
        return service.history(CurrentUser.id(jwt));
    }
}
