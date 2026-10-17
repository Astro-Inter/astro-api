package com.astro.api.event.dto.response;

import com.astro.api.user.model.WorkModel;

public record EventCandidateResponse(Long id, String name, String email, Long roleId, String roleName,
                                     Long unitId, String unitName, WorkModel workModel) {
}
