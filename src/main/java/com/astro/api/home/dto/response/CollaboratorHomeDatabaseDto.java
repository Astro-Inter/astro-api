package com.astro.api.home.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record CollaboratorHomeDatabaseDto(
        String username,
        @JsonProperty("workspace_name") String workspaceName,
        @JsonProperty("events_metrics") EventsMetricsDto eventsMetrics,
        @JsonProperty("pending_events") List<PendingEventDto> pendingEvents,
        @JsonProperty("status_message") String statusMessage
) {
    public record EventsMetricsDto(int done, int pending, @JsonProperty("under_analysis") int underAnalysis) {}
    public record PendingEventDto(Long id, String title, @JsonProperty("limit_date") LocalDate limitDate) {}
}
