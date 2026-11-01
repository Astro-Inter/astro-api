package com.astro.api.home.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.service.ViewedService;
import com.astro.api.form.model.Form;
import com.astro.api.form.service.FormService;
import com.astro.api.home.dto.response.*;
import com.astro.api.home.mapper.ManagerHomeMapper;
import com.astro.api.home.repository.HomeRepository;
import com.astro.api.home.mapper.WorkspaceHomeMapper;
import com.astro.api.notification.model.Notification;
import com.astro.api.notification.service.NotificationService;
import com.astro.api.user.service.UserAvatarService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Service
public class HomeService {
    private static final String EVENTS_RESOURCE = "events";
    private static final String NOTIFICATIONS_RESOURCE = "notifications";
    private static final String VALIDATIONS_RESOURCE = "validations";
    private static final String FORMS_RESOURCE = "forms";

    private final HomeRepository homeRepository;
    private final JsonMapper objectMapper;
    private final NotificationService notificationService;
    private final ViewedService viewedService;
    private final FormService formService;
    private final UserAvatarService userAvatarService;
    private final ManagerHomeMapper managerHomeMapper;
    private final WorkspaceHomeMapper workspaceHomeMapper;
    private final WorkspaceHomeProfileService workspaceHomeProfileService;

    public HomeService(HomeRepository homeRepository, JsonMapper objectMapper, NotificationService notificationService,
                       ViewedService viewedService, FormService formService, UserAvatarService userAvatarService,
                       ManagerHomeMapper managerHomeMapper, WorkspaceHomeMapper workspaceHomeMapper,
                       WorkspaceHomeProfileService workspaceHomeProfileService) {
        this.homeRepository = homeRepository;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.viewedService = viewedService;
        this.formService = formService;
        this.userAvatarService = userAvatarService;
        this.managerHomeMapper = managerHomeMapper;
        this.workspaceHomeMapper = workspaceHomeMapper;
        this.workspaceHomeProfileService = workspaceHomeProfileService;
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
            WorkspaceHomeDatabaseResponse response = workspaceHomeMapper.fromDatabase(homeJson);
            if (response == null || response.userName() == null || response.statistics() == null) {
                throw new IllegalStateException("Dados da Home retornados pelo banco são inválidos");
            }
            String profilePhotoUrl = workspaceHomeProfileService.findPhotoUrl(user.firebaseUid());
            return workspaceHomeMapper.toResponse(response, profilePhotoUrl);
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
                databaseHome.username(), userAvatarService.findUrl(userId), databaseHome.workspaceName(),
                new CollaboratorHomeResponse.EventsMetricsResponse(metrics.pending(), metrics.underAnalysis(), metrics.done()),
                databaseHome.statusMessage(),
                events.stream().map(event -> new CollaboratorHomeResponse.PendingEventResponse(
                        event.id(), event.title(), event.limitDate(), viewedEvents.contains(event.id().toString()))).toList(),
                notifications.stream().map(notification -> new CollaboratorHomeResponse.HomeNotificationResponse(
                        String.valueOf(notification.getId()), notification.getMessage(),
                        notificationService.elapsedWholeHours(notification.getCreatedAt()),
                        viewedNotifications.contains(String.valueOf(notification.getId())))).toList());
    }

    private Long userId(String firebaseUid) {
        return homeRepository.findUserIdByFirebaseUid(firebaseUid)
                .map(HomeRepository.UserIdProjection::getUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador elegível não encontrado"));
    }

    private CollaboratorHomeDatabaseDto readDatabaseHome(String json) {
        try {
            return objectMapper.rebuild().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()
                    .readValue(json, CollaboratorHomeDatabaseDto.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível interpretar os dados da Home", exception);
        }
    }

    @Transactional(readOnly = true)
    public ManagerHomeResponse loadManagerHome(AuthenticatedUser user) {
        String firebaseUid = authenticatedUid(user);
        ManagerHomeDatabaseResponse databaseHome = readManagerDatabaseHome(homeRepository.loadManagerHome(firebaseUid));
        Long managerId = homeRepository.findByFirebaseUid(firebaseUid)
                .map(id -> id.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Gestor não encontrado"));

        List<Form> forms = formService.findTop3PendingForManager(managerId);
        List<ManagerHomeDatabaseResponse.ManagerPendingValidationDatabaseResponse> validations =
                databaseHome.pendingValidations() == null ? List.of() : databaseHome.pendingValidations();
        Set<String> viewedValidations = viewedService.findViewed(managerId, VALIDATIONS_RESOURCE,
                validations.stream().map(ManagerHomeDatabaseResponse.ManagerPendingValidationDatabaseResponse::id).toList());
        Set<String> viewedForms = viewedService.findViewed(managerId, FORMS_RESOURCE,
                forms.stream().map(Form::getId).toList());

        return new ManagerHomeResponse(
                databaseHome.username(), userAvatarService.findUrl(managerId), databaseHome.workspaceName(),
                managerHomeMapper.toPendingValidationResponses(validations, viewedValidations),
                forms.stream().map(form -> new PendingFormResponse(
                        form.getId(), form.getTitle(), daysLeft(form.getDueDate()),
                        viewedForms.contains(String.valueOf(form.getId())))).toList(),
                databaseHome.indicators());
    }

    static Long daysLeft(Instant dueDate, LocalDate currentDate, ZoneId zoneId) {
        return dueDate == null ? null : ChronoUnit.DAYS.between(currentDate, dueDate.atZone(zoneId).toLocalDate());
    }

    private Long daysLeft(Instant dueDate) {
        ZoneId zoneId = ZoneId.of("America/Sao_Paulo");
        return daysLeft(dueDate, LocalDate.now(zoneId), zoneId);
    }

    private String authenticatedUid(AuthenticatedUser user) {
        if (user == null || user.firebaseUid() == null || user.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Usuário autenticado necessário para consultar a Home");
        }
        return user.firebaseUid();
    }

    private ManagerHomeDatabaseResponse readManagerDatabaseHome(String json) {
        if (json == null) {
            throw new ResourceNotFoundException("Gestor inexistente, inativo ou sem permissão para acessar a Home");
        }
        try {
            ManagerHomeDatabaseResponse response = objectMapper.rebuild().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()
                    .readValue(json, ManagerHomeDatabaseResponse.class);
            if (response == null || response.username() == null || response.workspaceName() == null
                    || response.indicators() == null) {
                throw new IllegalStateException("Dados da Home do gestor retornados pelo banco são inválidos");
            }
            return response;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível interpretar os dados da Home do gestor", exception);
        }
    }
}
