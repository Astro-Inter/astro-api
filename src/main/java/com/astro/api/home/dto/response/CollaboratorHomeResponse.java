package com.astro.api.home.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record CollaboratorHomeResponse(
        String username,
        String profilePhotoUrl,
        @JsonProperty("workspace_name") String workspaceName,
        @JsonProperty("events_metrics") EventsMetricsResponse eventsMetrics,
        @JsonProperty("status_message") String statusMessage,
        @JsonProperty("pending_events") List<PendingEventResponse> pendingEvents,
        @JsonProperty("last_notifications") List<HomeNotificationResponse> lastNotifications
) {
    public record EventsMetricsResponse(int pending, @JsonProperty("under_analysis") int underAnalysis, int done) {}
    public record PendingEventResponse(Long id, String title, @JsonProperty("limit_date") LocalDate limitDate, boolean viewed) {}
    public record HomeNotificationResponse(String id, String title, @JsonProperty("sent_at_in_hours") long sentAtInHours, boolean viewed) {}
}
