package com.astro.api.auth.security;

public record AuthenticatedUser(
        String firebaseUid,
        String email,
        Role role
) {
}
