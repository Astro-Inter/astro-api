package com.astro.api.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserActivationRequestDto(

        @Email(message = "{user.email.invalid}")
        @NotBlank(message = "{user.email.notblank}")
        String email,

        @NotBlank(message = "{user.firebaseUid.notblank}")
        String firebaseUid

) {}
