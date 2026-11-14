package kz.skillbridge.user;

import java.time.Instant;
import kz.skillbridge.common.ApiException;
import kz.skillbridge.user.dto.MeResponse;
import kz.skillbridge.user.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final UserProfileRepository profiles;

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        return MeResponse.from(user, profile(userId));
    }

    @Transactional
    public MeResponse updateProfile(Long userId, UpdateProfileRequest req) {
        UserProfile p = profile(userId);
        p.setFullName(blankToNull(req.fullName()));
        p.setLocation(blankToNull(req.location()));
        p.setHeadline(blankToNull(req.headline()));
        p.setLanguageList(req.languages());
        p.setDesiredLevel(req.desiredLevel());
        p.setWorkFormat(req.workFormat());
        if (req.completeOnboarding() && p.getOnboardedAt() == null) {
            p.setOnboardedAt(Instant.now());
        }
        return MeResponse.from(p.getUser(), p);
    }

    /** Removes the account and, through FK cascades, every row the user owns (spec §12). */
    @Transactional
    public void deleteAccount(Long userId) {
        if (users.hardDelete(userId) == 0) {
            throw ApiException.notFound("User");
        }
        // TODO(step 2): also delete uploaded CV files from storage.
    }

    private UserProfile profile(Long userId) {
        return profiles.findById(userId).orElseThrow(() -> ApiException.notFound("Profile"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
