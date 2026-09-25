package com.astro.api.user.dto.response;

import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;

public record IdentificatedUserResponseDto(
    UserType userType,
    UserStatus userStatus
) {}