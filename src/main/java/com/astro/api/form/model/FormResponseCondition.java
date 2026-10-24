package com.astro.api.form.model;

public record FormResponseCondition(String formId, Long workspaceId, Long recipientManagerId, Long expectedVersion, FormStatus expectedStatus) {}
