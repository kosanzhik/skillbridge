package kz.skillbridge.user.dto;

import java.util.List;
import kz.skillbridge.user.DesiredLevel;
import kz.skillbridge.user.UserProfile;
import kz.skillbridge.user.WorkFormat;

public record ProfileDto(
        String fullName,
        String location,
        String headline,
        List<String> languages,
        DesiredLevel desiredLevel,
        WorkFormat workFormat,
        boolean onboarded) {

    public static ProfileDto from(UserProfile p) {
        return new ProfileDto(p.getFullName(), p.getLocation(), p.getHeadline(), p.getLanguageList(),
                p.getDesiredLevel(), p.getWorkFormat(), p.getOnboardedAt() != null);
    }
}
