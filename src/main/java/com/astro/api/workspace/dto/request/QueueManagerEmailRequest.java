package com.astro.api.workspace.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record QueueManagerEmailRequest(

        @Email(message = "{user.email.invalid}")
        @NotBlank(message = "{user.email.notblank}")
        String email

) {}
