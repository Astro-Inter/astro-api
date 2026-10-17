package com.astro.api.form.model;

import java.util.List;

public record FormReadFilter(Long workspaceId, Long senderManagerId, Long recipientManagerId,
                             List<FormStatus> recipientStatuses) {}
