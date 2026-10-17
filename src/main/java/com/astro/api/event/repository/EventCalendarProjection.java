package com.astro.api.event.repository;

import com.astro.api.event.model.EventStatus;
import java.time.LocalDateTime;

public interface EventCalendarProjection {
    Long getEventId();
    String getEventTitle();
    Long getEventClassId();
    String getClassName();
    LocalDateTime getStartDate();
    LocalDateTime getEndDate();
    EventStatus getStatus();
}
