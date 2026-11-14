package kz.skillbridge.auth.dto;

import java.time.Instant;
import kz.skillbridge.user.dto.MeResponse;

public record AuthResponse(String token, Instant expiresAt, MeResponse user) {
}
