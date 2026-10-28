package com.astro.api.event.repository;

public interface EventCompletionMetricsProjection {
    long getParticipantCount();
    long getPendingValidationCount();
    long getValidatedCount();
    long getNotSubmittedCount();
}
