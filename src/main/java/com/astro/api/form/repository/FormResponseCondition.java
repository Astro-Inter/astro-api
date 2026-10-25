package com.astro.api.form.repository;

import com.astro.api.form.model.FormStatus;

public record FormResponseCondition(
        String formId,
        Long workspaceId,
        Long recipientManagerId,
        Long expectedVersion,
        FormStatus expectedStatus
) {}
