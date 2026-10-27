package com.astro.api.home.dto.response;


public record WorkspaceHomeDatabaseResponse(
        String userName,
        WorkspaceHomeStatisticsResponse statistics
) {
}
