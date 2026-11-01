package com.astro.api.role.dto.response;


public record RoleNrOptionResponse(
        Integer nr,
        String description,
        boolean active
) {
}
