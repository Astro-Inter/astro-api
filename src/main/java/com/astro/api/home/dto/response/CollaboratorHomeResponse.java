package com.astro.api.home.dto.response;


import java.time.LocalDate;
import java.util.List;

public record CollaboratorHomeResponse(
        String username,
        String profilePhotoUrl,
        String workspaceName,
        EventsMetricsResponse eventsMetrics,
        String statusMessage,
        List<PendingEventResponse> pendingEvents,
        List<HomeNotificationResponse> lastNotifications
) {
    public record EventsMetricsResponse(int pending, int underAnalysis, int done) {}
    public record PendingEventResponse(Long id, String title, LocalDate limitDate, boolean viewed) {}
    public record HomeNotificationResponse(String id, String title, long sentAtInHours, boolean viewed) {}
}
