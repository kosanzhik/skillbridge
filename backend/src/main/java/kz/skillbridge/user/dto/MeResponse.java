package kz.skillbridge.user.dto;

import java.time.Instant;
import kz.skillbridge.user.Role;
import kz.skillbridge.user.User;
import kz.skillbridge.user.UserProfile;

public record MeResponse(Long id, String email, Role role, boolean demo, Instant createdAt, ProfileDto profile) {

    public static MeResponse from(User u, UserProfile p) {
        return new MeResponse(u.getId(), u.getEmail(), u.getRole(), u.isDemo(), u.getCreatedAt(), ProfileDto.from(p));
    }
}
