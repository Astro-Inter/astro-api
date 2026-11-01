package com.astro.api.home.dto.response;


import java.time.LocalDate;
import java.util.List;

public record CollaboratorHomeDatabaseDto(
        String username,
        String workspaceName,
        EventsMetricsDto eventsMetrics,
        List<PendingEventDto> pendingEvents,
        String statusMessage
) {
    public record EventsMetricsDto(int done, int pending, int underAnalysis) {}
    public record PendingEventDto(Long id, String title, LocalDate limitDate) {}
}
