package kz.skillbridge.progress;

import kz.skillbridge.common.CurrentUser;
import kz.skillbridge.progress.dto.ProgressResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService service;

    @GetMapping("/me/progress")
    public ProgressResponse progress(@AuthenticationPrincipal Jwt jwt) {
        return service.progress(CurrentUser.id(jwt));
    }
}
