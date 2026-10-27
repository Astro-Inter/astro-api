package com.astro.api.event.repository;

public interface MyEventProjection {
    Long getId();
    String getTitle();
    String getStatus();
    String getCompletionMode();
    Long getPendingCount();
}
