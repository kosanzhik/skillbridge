package kz.skillbridge.user.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import kz.skillbridge.user.DesiredLevel;
import kz.skillbridge.user.WorkFormat;

/** Full replace of editable profile fields. {@code completeOnboarding} marks the onboarding step as done. */
public record UpdateProfileRequest(
        @Size(max = 255) String fullName,
        @Size(max = 255) String location,
        @Size(max = 255) String headline,
        @Size(max = 10) List<@Size(max = 40) String> languages,
        DesiredLevel desiredLevel,
        WorkFormat workFormat,
        boolean completeOnboarding) {
}
