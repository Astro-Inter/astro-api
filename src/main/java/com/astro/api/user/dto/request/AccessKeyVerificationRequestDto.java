package com.astro.api.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AccessKeyVerificationRequestDto(

        @Email(message = "{user.email.invalid}")
        @NotBlank(message = "{user.email.notblank}")
        String email,

        @NotBlank(message = "{user.accessKey.notblank}")
        @Pattern(regexp = "\\d{6}", message = "{user.accessKey.invalid}")
        String accessKey

) {}
