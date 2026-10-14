package com.astro.api.conformity;

import com.astro.api.auth.security.*;
import com.astro.api.conformity.controller.ConformityController;
import com.astro.api.conformity.service.ConformityService;
import com.astro.api.conformity.dto.response.ConformityResponse;
import com.astro.api.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConformityControllerTest {
    final ConformityService service = mock(ConformityService.class);
    final AuthenticatedUser principal = new AuthenticatedUser("manager", "manager@example.com", Role.GESTOR);
    MockMvc mvc;
    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ConformityController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
    @AfterEach void cleanUp() { SecurityContextHolder.clearContext(); }
    ConformityResponse response() {
        return new ConformityResponse(2L, 3L, "Colaborador", "col@example.com", 10, "NR 10", null,
                "CONCLUSAO_EVENTO", true, 42L, "Sem validade");
    }
    @Test void listsTheAuthenticatedScopeWithoutExposingCredentials() throws Exception {
        when(service.findAll(principal)).thenReturn(List.of(response()));
        mvc.perform(get("/conformities").param("workspaceId", "999").param("firebaseUid", "fake"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].origin").value("CONCLUSAO_EVENTO"))
                .andExpect(jsonPath("$.data[0].status").value("Sem validade"))
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.data[0].senha").doesNotExist())
                .andExpect(jsonPath("$.data[0].firebaseUid").doesNotExist())
                .andExpect(jsonPath("$.data[0].firebase_uid").doesNotExist());
        verify(service).findAll(principal);
    }

    @Test void returnsDetailsAndStandardNotFoundAndMalformedIdErrors() throws Exception {
        when(service.findById(2L, principal)).thenReturn(response());
        mvc.perform(get("/conformities/2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventCompletionId").value(42))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.firebaseUid").doesNotExist());
        when(service.findById(99L, principal)).thenThrow(
                new com.astro.api.common.exception.ResourceNotFoundException("Conformidade não encontrada"));
        mvc.perform(get("/conformities/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Conformidade não encontrada"));
        mvc.perform(get("/conformities/invalid")).andExpect(status().isBadRequest());
    }

    @Test void createsWithLocationAndRejectsInvalidRequests() throws Exception {
        when(service.create(any(), eq(principal))).thenReturn(response());
        mvc.perform(post("/conformities").contentType("application/json")
                        .content("{\"collaboratorId\":3,\"nrId\":10,\"expiresAt\":null,\"origin\":\"FAKE\",\"workspaceId\":999}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/conformities/2"));
        verify(service).create(new com.astro.api.conformity.dto.request.CreateConformityRequest(3L, 10, null), principal);
        clearInvocations(service);
        for (String body : List.of("{}", "{\"collaboratorId\":0,\"nrId\":10}",
                "{\"collaboratorId\":3,\"nrId\":-1}", "{\"collaboratorId\":3,\"nrId\":10,\"expiresAt\":\"bad\"}")) {
            mvc.perform(post("/conformities").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
