package com.astro.api.home.service;

import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.service.ViewedService;
import com.astro.api.home.dto.CollaboratorHomeDatabaseDto;
import com.astro.api.home.dto.CollaboratorHomeResponse;
import com.astro.api.home.repository.HomeRepository;
import com.astro.api.notification.model.Notification;
import com.astro.api.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class HomeService {
    private static final String EVENTS_RESOURCE = "events";
    private static final String NOTIFICATIONS_RESOURCE = "notifications";

    private final HomeRepository homeRepository;
    private final NotificationService notificationService;
    private final ViewedService viewedService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public HomeService(HomeRepository homeRepository, NotificationService notificationService,
                       ViewedService viewedService) {
        this.homeRepository = homeRepository;
        this.notificationService = notificationService;
        this.viewedService = viewedService;
    }

    public CollaboratorHomeResponse getCollaboratorHome(String firebaseUid) {
        String json = homeRepository.loadCollaboratorHome(firebaseUid);
        if (json == null) {
            throw new ResourceNotFoundException("Colaborador elegível não encontrado");
        }
        CollaboratorHomeDatabaseDto databaseHome = readDatabaseHome(json);
        Long userId = userId(firebaseUid);
        List<CollaboratorHomeDatabaseDto.PendingEventDto> events = databaseHome.pendingEvents() == null
                ? List.of() : databaseHome.pendingEvents().stream().limit(3).toList();
        List<Notification> notifications = notificationService.findLatestForUser(userId);

        Set<String> viewedEvents = viewedService.findViewed(userId, EVENTS_RESOURCE,
                events.stream().map(CollaboratorHomeDatabaseDto.PendingEventDto::id).toList());
        Set<String> viewedNotifications = viewedService.findViewed(userId, NOTIFICATIONS_RESOURCE,
                notifications.stream().map(Notification::getId).toList());

        CollaboratorHomeDatabaseDto.EventsMetricsDto metrics = databaseHome.eventsMetrics();
        if (metrics == null) {
            throw new IllegalStateException("A função da Home retornou métricas inválidas");
        }
        return new CollaboratorHomeResponse(
                databaseHome.username(), databaseHome.workspaceName(),
                new CollaboratorHomeResponse.EventsMetricsResponse(metrics.pending(), metrics.underAnalysis(), metrics.done()),
                databaseHome.statusMessage(),
                events.stream().map(event -> new CollaboratorHomeResponse.PendingEventResponse(
                        event.id(), event.title(), event.limitDate(), viewedEvents.contains(event.id().toString()))).toList(),
                notifications.stream().map(notification -> new CollaboratorHomeResponse.HomeNotificationResponse(
                        notification.getId(), notification.getMessage(), notificationService.elapsedWholeHours(notification.getCreatedAt()),
                        viewedNotifications.contains(notification.getId()))).toList());
    }

    private Long userId(String firebaseUid) {
        return homeRepository.findUserIdByFirebaseUid(firebaseUid)
                .map(HomeRepository.UserIdProjection::getUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador elegível não encontrado"));
    }

    private CollaboratorHomeDatabaseDto readDatabaseHome(String json) {
        try {
            return objectMapper.readValue(json, CollaboratorHomeDatabaseDto.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível interpretar os dados da Home", exception);
        }
    }

}
