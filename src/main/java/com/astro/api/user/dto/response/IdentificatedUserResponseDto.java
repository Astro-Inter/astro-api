package com.astro.api.user.dto.response;

import com.astro.api.user.model.UserStatus;
import com.astro.api.auth.security.Role;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record IdentificatedUserResponseDto(
    Role userType,
    UserStatus userStatus
) {}
