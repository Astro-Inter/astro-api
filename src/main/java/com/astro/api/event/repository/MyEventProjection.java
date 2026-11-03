package com.astro.api.event.repository;

import java.time.LocalDateTime;

public interface MyEventProjection {
    Long getId();
    String getTitle();
    String getStatus();
    String getCompletionMode();
    Long getPendingCount();
    LocalDateTime getLastClassLimitDate();
}
