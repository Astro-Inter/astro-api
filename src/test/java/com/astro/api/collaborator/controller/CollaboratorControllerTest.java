package com.astro.api.collaborator.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.collaborator.dto.response.CollaboratorSummaryResponse;
import com.astro.api.collaborator.dto.response.CollaboratorDetailsResponse;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.user.model.WorkModel;
import com.astro.api.collaborator.service.CollaboratorService;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.user.model.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CollaboratorControllerTest {
    final CollaboratorService service = mock(CollaboratorService.class);
    final AuthenticatedUser principal = new AuthenticatedUser("manager", "manager@example.com", Role.GESTOR);
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CollaboratorController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void cleanUp() { SecurityContextHolder.clearContext(); }

    @Test
    void listsWithoutParametersAndExposesOnlyAdministrativeSummaryFields() throws Exception {
        when(service.findAll(principal)).thenReturn(List.of(new CollaboratorSummaryResponse(
                2L, "Colaborador", "test@example.com", 5L, "Operador", 12L, "Matriz", UserStatus.PRE_CADASTRADO)));
        mvc.perform(get("/collaborators").param("workspaceId", "999").param("firebase_uid", "untrusted"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id_usuario").value(2))
                .andExpect(jsonPath("$.data[0].nome").value("Colaborador"))
                .andExpect(jsonPath("$.data[0].cargo_id").value(5))
                .andExpect(jsonPath("$.data[0].unidade_id").value(12))
                .andExpect(jsonPath("$.data[0].status").value("PRE_CADASTRADO"))
                .andExpect(jsonPath("$.data[0].cpf").doesNotExist())
                .andExpect(jsonPath("$.data[0].senha").doesNotExist())
                .andExpect(jsonPath("$.data[0].firebase_uid").doesNotExist());
        verify(service).findAll(principal);
    }

    @Test
    void returnsDetailsIncludingCpfWithoutInternalFields() throws Exception {
        when(service.findById(2L, principal)).thenReturn(new CollaboratorDetailsResponse(2L, "Colaborador",
                "52998224725", "test@example.com", WorkModel.HIBRIDO, UserStatus.ATIVO,
                5L, "Operador", 12L, "Matriz", 3L));
        mvc.perform(get("/collaborators/2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cpf").value("52998224725"))
                .andExpect(jsonPath("$.data.modalidade").value("HIBRIDO"))
                .andExpect(jsonPath("$.data.workspace_id").value(3))
                .andExpect(jsonPath("$.data.firebase_uid").doesNotExist())
                .andExpect(jsonPath("$.data.firebaseUid").doesNotExist())
                .andExpect(jsonPath("$.data.senha").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void mapsNotFoundAndMalformedIdsToExistingHttpErrors() throws Exception {
        when(service.findById(999L, principal)).thenThrow(new ResourceNotFoundException("Colaborador não encontrado"));
        mvc.perform(get("/collaborators/999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Colaborador não encontrado"));
        mvc.perform(get("/collaborators/invalid")).andExpect(status().isBadRequest());
    }
}
