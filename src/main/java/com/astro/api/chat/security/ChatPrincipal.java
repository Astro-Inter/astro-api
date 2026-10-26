package com.astro.api.chat.security;

import com.astro.api.auth.security.Role;
import java.security.Principal;

public record ChatPrincipal(Long collaboratorId, Long workspaceId, Role role, String firebaseUid) implements Principal {
    @Override
    public String getName() {
        return collaboratorId.toString();
    }
}
