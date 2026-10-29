package com.astro.api.notification.controller;

import com.astro.api.notification.swagger.NotificationSwagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.common.service.ViewedService;
import com.astro.api.notification.dto.response.NotificationDetailsResponse;
import com.astro.api.notification.dto.response.NotificationResponse;
import com.astro.api.notification.model.Notification;
import com.astro.api.notification.service.AuthenticatedNotifications;
import com.astro.api.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/notifications")
public class NotificationController implements NotificationSwagger {
    private static final String NOTIFICATIONS_RESOURCE = "notifications";

    private final NotificationService notificationService;
    private final ViewedService viewedService;

    public NotificationController(NotificationService notificationService, ViewedService viewedService) {
        this.notificationService = notificationService;
        this.viewedService = viewedService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<List<NotificationResponse>>> findAll(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        AuthenticatedNotifications result =
                notificationService.findAllForAuthenticatedUser(authenticatedUser.firebaseUid());
        List<Notification> notifications = result.notifications();
        Set<String> viewed = notifications.isEmpty() ? Set.of() : viewedService.findViewed(result.userId(), NOTIFICATIONS_RESOURCE,
                notifications.stream().map(Notification::getId).toList());
        List<NotificationResponse> response = notifications.stream().map(notification -> new NotificationResponse(
                String.valueOf(notification.getId()),
                notification.getTitle() == null ? notification.getMessage() : notification.getTitle(),
                viewed.contains(String.valueOf(notification.getId())),
                notificationService.elapsedWholeHours(notification.getCreatedAt())
        )).toList();
        return ResponseEntity.ok(ApiResult.success("Notificações retornadas com sucesso", response,
                request.getRequestURI()));
    }

    @GetMapping("/{notificationId}")
    @PreAuthorize("hasAnyRole('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<NotificationDetailsResponse>> findById(
            @PathVariable String notificationId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        Notification notification = notificationService.findByIdForAuthenticatedUser(
                authenticatedUser.firebaseUid(), notificationId);
        NotificationDetailsResponse response = new NotificationDetailsResponse(
                notification.getMessage(), notification.getTitle(), notification.getEventId());
        return ResponseEntity.ok(ApiResult.success("Notificação retornada com sucesso", response,
                request.getRequestURI()));
    }

    @PatchMapping("/{notificationId}/viewed")
    @PreAuthorize("hasAnyRole('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<Void>> markAsViewed(
            @PathVariable String notificationId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        Long userId = notificationService.requireNotificationOwner(
                authenticatedUser.firebaseUid(), notificationId);
        viewedService.markViewed(userId, NOTIFICATIONS_RESOURCE, notificationId);
        return ResponseEntity.ok(ApiResult.success("Notificação marcada como visualizada", null,
                request.getRequestURI()));
    }
}
