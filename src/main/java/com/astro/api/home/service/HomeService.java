package com.astro.api.home.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.service.ViewedService;
import com.astro.api.home.dto.response.CollaboratorHomeDatabaseDto;
import com.astro.api.home.dto.response.CollaboratorHomeResponse;
import com.astro.api.home.dto.response.WorkspaceHomeResponse;
import com.astro.api.home.repository.HomeRepository;
import com.astro.api.notification.model.Notification;
import com.astro.api.notification.service.NotificationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

@Service
public class HomeService {
    private static final String EVENTS_RESOURCE = "events";
    private static final String NOTIFICATIONS_RESOURCE = "notifications";

    private final HomeRepository homeRepository;
    private final JsonMapper objectMapper;
    private final NotificationService notificationService;
    private final ViewedService viewedService;

    public HomeService(HomeRepository homeRepository, JsonMapper objectMapper, NotificationService notificationService,
                       ViewedService viewedService) {
        this.homeRepository = homeRepository;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.viewedService = viewedService;
    }

    @Transactional(readOnly = true)
    public WorkspaceHomeResponse loadHome(AuthenticatedUser user) {
        if (user == null || user.firebaseUid() == null || user.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Usuário autenticado necessário para consultar a Home");
        }
        String homeJson = homeRepository.loadHome(user.firebaseUid());
        if (homeJson == null) {
            throw new AccessDeniedException("Gestor ativo sem acesso à Home do workspace");
        }
        try {
            WorkspaceHomeResponse response = objectMapper.readValue(homeJson, WorkspaceHomeResponse.class);
            if (response == null || response.userName() == null || response.statistics() == null) {
                throw new IllegalStateException("Dados da Home retornados pelo banco são inválidos");
            }
            return response;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível interpretar os dados da Home", exception);
        }
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
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível interpretar os dados da Home", exception);
        }
    }
}
