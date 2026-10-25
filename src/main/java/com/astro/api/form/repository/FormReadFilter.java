package com.astro.api.form.repository;

import com.astro.api.form.model.FormStatus;
import java.util.List;

public record FormReadFilter(
        Long workspaceId,
        Long senderManagerId,
        Long recipientManagerId,
        List<FormStatus> recipientStatuses
) {}
