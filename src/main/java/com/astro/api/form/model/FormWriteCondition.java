package com.astro.api.form.model;

public record FormWriteCondition(String formId, Long workspaceId, Long senderManagerId,
                                 Long expectedVersion, FormStatus expectedStatus) {}
