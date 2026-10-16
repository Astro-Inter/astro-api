package com.astro.api.notification;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.common.service.ViewedService;
import com.astro.api.notification.controller.NotificationController;
import com.astro.api.notification.model.Notification;
import com.astro.api.notification.repository.NotificationRepository;
import com.astro.api.notification.service.NotificationService;
import com.astro.api.user.model.User;
import com.astro.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerTest {
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ViewedService viewedService = mock(ViewedService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var service = new NotificationService(notificationRepository, userRepository);
        var user = new AuthenticatedUser("firebase-user", "user@astro.com", Role.COLABORADOR);
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(service, viewedService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticatedUser.class;
                    }

                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                  NativeWebRequest request, WebDataBinderFactory factory) {
                        return user;
                    }
                }).build();
    }

    @Test
    void returnsAllNotificationsForTheAuthenticatedUserWithoutChangingViewedState() throws Exception {
        User user = new User();
        user.setId(42L);
        Notification notification = new Notification();
        notification.setId("670f8df36a2ce84f6c12d230");
        notification.setUserId(42L);
        notification.setTitle("Treinamento pendente");
        notification.setMessage("Conclua o treinamento pendente.");
        notification.setEventId(10L);
        notification.setCreatedAt(Instant.now().minusSeconds(7_200));
        when(userRepository.findByFirebaseUid("firebase-user")).thenReturn(Optional.of(user));
        when(notificationRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(42L)).thenReturn(java.util.List.of(notification));
        when(viewedService.findViewed(42L, "notifications", java.util.List.of(notification.getId())))
                .thenReturn(java.util.Set.of(notification.getId()));

        mvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.path").value("/notifications"))
                .andExpect(jsonPath("$.data[0].id").value("670f8df36a2ce84f6c12d230"))
                .andExpect(jsonPath("$.data[0].titulo").value("Treinamento pendente"))
                .andExpect(jsonPath("$.data[0].horas").value(2))
                .andExpect(jsonPath("$.data[0].viewed").value(true))
                .andExpect(jsonPath("$.data[0].message").doesNotExist())
                .andExpect(jsonPath("$.data[0].event_id").doesNotExist());

        verify(notificationRepository).findAllByUserIdOrderByCreatedAtDescIdDesc(42L);
        verify(viewedService).findViewed(42L, "notifications", java.util.List.of(notification.getId()));
        verifyNoMoreInteractions(viewedService);
    }
}
