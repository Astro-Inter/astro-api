package com.astro.api.home.service;

import com.astro.api.home.mapper.WorkspaceHomeMapper;
import com.astro.api.home.service.WorkspaceHomeProfileService;

import com.astro.api.common.service.ViewedService;
import com.astro.api.home.dto.response.CollaboratorHomeResponse;
import com.astro.api.home.repository.HomeRepository;
import com.astro.api.notification.model.Notification;
import com.astro.api.notification.service.NotificationService;
import com.astro.api.form.service.FormService;
import com.astro.api.user.service.UserAvatarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomeServiceTest {
    private HomeRepository homeRepository;
    private NotificationService notificationService;
    private ViewedService viewedService;
    private UserAvatarService userAvatarService;
    private HomeService service;

    @BeforeEach
    void setUp() {
        homeRepository = mock(HomeRepository.class);
        notificationService = mock(NotificationService.class);
        viewedService = mock(ViewedService.class);
        userAvatarService = mock(UserAvatarService.class);
        service = new HomeService(homeRepository, JsonMapper.builder().build(), notificationService, viewedService,
                mock(FormService.class), userAvatarService, new WorkspaceHomeMapper(), mock(WorkspaceHomeProfileService.class));
        HomeRepository.UserIdProjection identity = mock(HomeRepository.UserIdProjection.class);
        when(identity.getUserId()).thenReturn(42L);
        when(homeRepository.findUserIdByFirebaseUid("uid")).thenReturn(Optional.of(identity));
    }

    @Test
    void combinesSqlMongoAndRedisData() {
        when(homeRepository.loadCollaboratorHome("uid")).thenReturn(json("URGENT", "[{\"id\":101,\"title\":\"EPI\",\"limit_date\":\"2026-10-12\"}]"));
        Notification notification = notification(6L);
        when(notificationService.findLatestForUser(42L)).thenReturn(List.of(notification));
        when(notificationService.elapsedWholeHours(notification.getCreatedAt())).thenReturn(2L);
        when(userAvatarService.findUrl(42L)).thenReturn("https://example.com/collaborator.jpg");
        when(viewedService.findViewed(42L, "events", List.of(101L))).thenReturn(java.util.Set.of("101"));
        when(viewedService.findViewed(42L, "notifications", List.of(6L))).thenReturn(java.util.Set.of());

        CollaboratorHomeResponse response = service.getCollaboratorHome("uid");

        assertEquals("URGENT", response.statusMessage());
        assertEquals("https://example.com/collaborator.jpg", response.profilePhotoUrl());
        assertEquals(true, response.pendingEvents().getFirst().viewed());
        assertEquals("Lembrete", response.lastNotifications().getFirst().title());
        assertEquals(2L, response.lastNotifications().getFirst().sentAtInHours());
    }

    @Test
    void returnsEmptyListsAndPreservesAllStatuses() {
        for (String status : List.of("URGENT", "PENDENT", "ANALYSIS", "OK")) {
            when(homeRepository.loadCollaboratorHome("uid")).thenReturn(json(status, "[]"));
            when(notificationService.findLatestForUser(42L)).thenReturn(List.of());
            when(viewedService.findViewed(eq(42L), any(), eq(List.of()))).thenReturn(java.util.Set.of());
            CollaboratorHomeResponse response = service.getCollaboratorHome("uid");
            assertEquals(status, response.statusMessage());
            assertNull(response.profilePhotoUrl());
            assertEquals(List.of(), response.pendingEvents());
            assertEquals(List.of(), response.lastNotifications());
        }
    }

    @Test
    void rejectsNullReturnedBySqlFunction() {
        when(homeRepository.loadCollaboratorHome("uid")).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.getCollaboratorHome("uid"));
    }

    private String json(String status, String events) {
        return "{\"username\":\"João\",\"workspace_name\":\"Astro\",\"events_metrics\":{\"done\":2,\"pending\":1,\"under_analysis\":0},\"pending_events\":"
                + events + ",\"status_message\":\"" + status + "\"}";
    }

    private Notification notification(Long id) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setMessage("Lembrete");
        notification.setCreatedAt(Instant.parse("2026-10-11T10:00:00Z"));
        return notification;
    }
}
