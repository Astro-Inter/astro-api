package com.astro.api.home;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.home.controller.HomeController;
import com.astro.api.home.dto.response.ManagerHomeResponse;
import com.astro.api.home.dto.response.PendingValidationResponse;
import com.astro.api.home.service.HomeService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ManagerHomeControllerTest {
    @Test
    void returnsTheManagerHomeAtTheSpecifiedRoute() throws Exception {
        HomeService service = mock(HomeService.class);
        AuthenticatedUser user = new AuthenticatedUser("manager", "manager@astro.com", Role.GESTOR);
        when(service.loadManagerHome(user)).thenReturn(new ManagerHomeResponse("Ana", "https://example.com/manager.jpg", "Workspace",
                List.of(new PendingValidationResponse(201L, 11L, 21L, "João", "Treinamento", false)), List.of(), null));

        var mvc = MockMvcBuilders.standaloneSetup(new HomeController(service))
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

        mvc.perform(get("/home/manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.path").value("/home/manager"))
                .andExpect(jsonPath("$.data.username").value("Ana"))
                .andExpect(jsonPath("$.data.profilePhotoUrl").value("https://example.com/manager.jpg"))
                .andExpect(jsonPath("$.data.pendingValidations").isArray())
                .andExpect(jsonPath("$.data.pendingValidations[0].participantId").value(11))
                .andExpect(jsonPath("$.data.pendingValidations[0].eventId").value(21))
                .andExpect(jsonPath("$.data.pendingValidations[0].collaboratorName").value("João"))
                .andExpect(jsonPath("$.data.pendingValidations[0].eventTitle").value("Treinamento"))
                .andExpect(jsonPath("$.data.pendingForms").isArray());
        verify(service).loadManagerHome(user);
    }
}
