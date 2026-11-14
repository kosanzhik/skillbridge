package kz.skillbridge.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kz.skillbridge.auth.dto.AuthResponse;
import kz.skillbridge.auth.dto.LoginRequest;
import kz.skillbridge.auth.dto.RegisterRequest;
import kz.skillbridge.common.ApiException;
import kz.skillbridge.config.AdminProperties;
import kz.skillbridge.user.Role;
import kz.skillbridge.user.DesiredLevel;
import kz.skillbridge.user.User;
import kz.skillbridge.user.UserProfile;
import kz.skillbridge.user.UserProfileRepository;
import kz.skillbridge.user.UserRepository;
import kz.skillbridge.user.WorkFormat;
import kz.skillbridge.user.dto.MeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminProperties adminProperties;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
        }
        User user = newUser(email, passwordEncoder.encode(req.password()), false);
        UserProfile profile = new UserProfile(user);
        profile.setFullName(req.fullName() == null || req.fullName().isBlank() ? null : req.fullName().trim());
        profiles.save(profile);
        return respond(user, profile);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(normalize(req.email()))
                .filter(u -> !u.isDemo())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
                        "Invalid email or password"));
        UserProfile profile = profiles.findById(user.getId()).orElseThrow(() -> ApiException.notFound("Profile"));
        return respond(user, profile);
    }

    /**
     * Creates an isolated, pre-onboarded demo account (spec §11: "демонстрационный профиль").
     * Each call gets its own account so parallel demos don't interfere; it cannot be logged into by password.
     */
    @Transactional
    public AuthResponse demo() {
        String email = "demo-" + UUID.randomUUID() + "@demo.skillbridge.local";
        User user = newUser(email, passwordEncoder.encode(UUID.randomUUID().toString()), true);
        UserProfile profile = new UserProfile(user);
        profile.setFullName("Demo User");
        profile.setLocation("Almaty, Kazakhstan");
        profile.setHeadline("Junior accountant looking to move into data analytics");
        profile.setLanguageList(List.of("Kazakh", "Russian", "English"));
        profile.setDesiredLevel(DesiredLevel.JUNIOR);
        profile.setWorkFormat(WorkFormat.HYBRID);
        profile.setOnboardedAt(Instant.now());
        profiles.save(profile);
        return respond(user, profile);
    }

    private User newUser(String email, String passwordHash, boolean demo) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setDemo(demo);
        user.setConsentAcceptedAt(Instant.now());
        return users.save(user);
    }

    private AuthResponse respond(User user, UserProfile profile) {
        if (!user.isDemo() && user.getRole() != Role.ADMIN && adminProperties.isAdmin(user.getEmail())) {
            user.setRole(Role.ADMIN);
        }
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), MeResponse.from(user, profile));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
