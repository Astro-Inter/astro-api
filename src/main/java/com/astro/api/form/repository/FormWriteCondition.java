package com.astro.api.form.repository;

import com.astro.api.form.model.FormStatus;

public record FormWriteCondition(
        String formId,
        Long workspaceId,
        Long senderManagerId,
        Long expectedVersion,
        FormStatus expectedStatus
) {}
