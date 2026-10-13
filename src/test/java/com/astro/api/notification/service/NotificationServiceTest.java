package com.astro.api.notification.service;

import com.astro.api.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class NotificationServiceTest {
    @Test
    void calculatesWholeHoursFromInstantsAndClampsFutureDates() {
        Instant now = Instant.parse("2026-10-11T12:30:00Z");
        assertEquals(2, NotificationService.elapsedWholeHours(Instant.parse("2026-10-11T10:10:00Z"), now));
        assertEquals(0, NotificationService.elapsedWholeHours(Instant.parse("2026-10-11T13:00:00Z"), now));
    }
}
