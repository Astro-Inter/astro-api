package com.astro.api.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailVerificationRequestDto(

    @Email(message = "{user.email.invalid}")
    @NotBlank(message = "{user.email.notblank}")
    String email

) {}
