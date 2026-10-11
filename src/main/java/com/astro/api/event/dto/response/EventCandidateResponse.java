package com.astro.api.event.dto.response;

public record EventCandidateResponse(Long id, String name, String email, Long roleId, String roleName) {
}
