package kz.skillbridge.user;

import jakarta.validation.Valid;
import kz.skillbridge.common.CurrentUser;
import kz.skillbridge.user.dto.MeResponse;
import kz.skillbridge.user.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.me(CurrentUser.id(jwt));
    }

    @PutMapping("/profile")
    public MeResponse updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(CurrentUser.id(jwt), req);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal Jwt jwt) {
        userService.deleteAccount(CurrentUser.id(jwt));
        return ResponseEntity.noContent().build();
    }
}
