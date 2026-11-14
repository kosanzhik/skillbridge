package kz.skillbridge.user;

import java.time.Instant;
import kz.skillbridge.common.ApiException;
import kz.skillbridge.user.dto.MeResponse;
import kz.skillbridge.user.dto.UpdateProfileRequest;
import kz.skillbridge.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final FileStorage storage;

    /** Storage prefix holding all documents of a user. */
    public static String userFilesPrefix(Long userId) {
        return "users/" + userId;
    }

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
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.deletePrefix(userFilesPrefix(userId));
            }
        });
    }

    private UserProfile profile(Long userId) {
        return profiles.findById(userId).orElseThrow(() -> ApiException.notFound("Profile"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
