package kz.skillbridge.common;

import org.springframework.security.oauth2.jwt.Jwt;

/** JWT subject is the numeric user id. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
