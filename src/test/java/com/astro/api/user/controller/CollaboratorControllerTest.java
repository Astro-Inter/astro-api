package com.astro.api.user.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.user.dto.response.CollaboratorSummaryResponse;
import com.astro.api.user.dto.response.CollaboratorEventResponse;
import com.astro.api.user.dto.response.CollaboratorEventsResponse;
import com.astro.api.user.dto.response.CollaboratorDetailsResponse;
import com.astro.api.user.dto.response.CollaboratorProfileResponse;
import com.astro.api.user.dto.response.UserProfileResponse;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.user.model.WorkModel;
import com.astro.api.event.model.EventStatus;
import com.astro.api.event.model.CompletionMode;
import com.astro.api.event.model.ParticipationStatus;
import com.astro.api.user.dto.request.CreateCollaboratorRequest;
import com.astro.api.user.dto.request.UpdateCollaboratorRequest;
import com.astro.api.user.dto.request.CollaboratorStatusRequest;
import org.springframework.http.MediaType;
import com.astro.api.user.service.CollaboratorService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Test
    void acceptsResendUsingOnlyThePathAndAuthenticatedManager() throws Exception {
        mvc.perform(post("/collaborators/2/access-key/resend").param("email", "untrusted@example.com"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.path").value("/collaborators/2/access-key/resend"));
        verify(service).resendAccessKey(2L, principal);
    }

    @Test
    void reportsResendBusinessScopeAndQueueFailures() throws Exception {
        doThrow(new com.astro.api.common.exception.BusinessException("Pré-cadastro necessário"))
                .when(service).resendAccessKey(2L, principal);
        mvc.perform(post("/collaborators/2/access-key/resend"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        doThrow(new ResourceNotFoundException("Colaborador não encontrado"))
                .when(service).resendAccessKey(2L, principal);
        mvc.perform(post("/collaborators/2/access-key/resend")).andExpect(status().isNotFound());
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("Redis indisponível"))
                .when(service).resendAccessKey(2L, principal);
        mvc.perform(post("/collaborators/2/access-key/resend"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.success").value(false));
    }

    @AfterEach
    void cleanUp() { SecurityContextHolder.clearContext(); }

    @Test
    void listsWithoutParametersAndExposesOnlyAdministrativeSummaryFields() throws Exception {
        when(service.findAll(principal, false)).thenReturn(List.of(new CollaboratorSummaryResponse(
                2L, "Colaborador", "test@example.com", 5L, "Operador", 12L, "Matriz", UserStatus.PRE_CADASTRADO, null, "C", true)));
        mvc.perform(get("/collaborators").param("workspaceId", "999").param("firebase_uid", "untrusted"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id_usuario").value(2))
                .andExpect(jsonPath("$.data[0].nome").value("Colaborador"))
                .andExpect(jsonPath("$.data[0].cargo_id").value(5))
                .andExpect(jsonPath("$.data[0].unidade_id").value(12))
                .andExpect(jsonPath("$.data[0].status").value("PRE_CADASTRADO"))
                .andExpect(jsonPath("$.data[0].profilePhotoUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data[0].avatarInitials").value("C"))
                .andExpect(jsonPath("$.data[0].cpf").doesNotExist())
                .andExpect(jsonPath("$.data[0].senha").doesNotExist())
                .andExpect(jsonPath("$.data[0].firebase_uid").doesNotExist());
        verify(service).findAll(principal, false);
    }

    @Test
    void excludesOnlyTheAuthenticatedUserWhenRequested() throws Exception {
        when(service.findAll(principal, true)).thenReturn(List.of());
        mvc.perform(get("/collaborators").param("excludeSelf", "true").param("userId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        verify(service).findAll(principal, true);
        verifyNoMoreInteractions(service);
    }

    @Test
    void returnsDetailsIncludingCpfWithoutInternalFields() throws Exception {
        when(service.findById(2L, principal)).thenReturn(new CollaboratorDetailsResponse(2L, "Colaborador",
                "52998224725", "test@example.com", WorkModel.HIBRIDO, UserStatus.ATIVO,
                5L, "Operador", 12L, "Matriz", 3L, "https://r2.example.com/foto.jpg", "C", true));
        mvc.perform(get("/collaborators/2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cpf").value("52998224725"))
                .andExpect(jsonPath("$.data.modalidade").value("HIBRIDO"))
                .andExpect(jsonPath("$.data.workspace_id").value(3))
                .andExpect(jsonPath("$.data.profilePhotoUrl").value("https://r2.example.com/foto.jpg"))
                .andExpect(jsonPath("$.data.avatarInitials").value("C"))
                .andExpect(jsonPath("$.data.firebase_uid").doesNotExist())
                .andExpect(jsonPath("$.data.firebaseUid").doesNotExist())
                .andExpect(jsonPath("$.data.senha").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void returnsProfileByIdWithPendingCountAndNrValidityDetails() throws Exception {
        UserProfileResponse.NrProfileResponse nr = new UserProfileResponse.NrProfileResponse(
                10, LocalDate.of(2027, 4, 30), "Segurança", "Prevenir acidentes", "Trabalho industrial");
        when(service.findProfileById(2L, principal)).thenReturn(new CollaboratorProfileResponse(2L,
                "Ana Silva", "Operadora", "https://r2.example.com/ana.jpg", "Matriz", WorkModel.HIBRIDO,
                "ana@example.com", 3L, List.of(nr)));

        mvc.perform(get("/collaborators/2/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.name").value("Ana Silva"))
                .andExpect(jsonPath("$.data.roleName").value("Operadora"))
                .andExpect(jsonPath("$.data.profilePhotoUrl").value("https://r2.example.com/ana.jpg"))
                .andExpect(jsonPath("$.data.unitName").value("Matriz"))
                .andExpect(jsonPath("$.data.workModel").value("HIBRIDO"))
                .andExpect(jsonPath("$.data.email").value("ana@example.com"))
                .andExpect(jsonPath("$.data.pendingCount").value(3))
                .andExpect(jsonPath("$.data.nrs[0].code").value(10))
                .andExpect(jsonPath("$.data.nrs[0].validade").value("2027-04-30"))
                .andExpect(jsonPath("$.data.nrs[0].descricao").value("Segurança"))
                .andExpect(jsonPath("$.data.nrs[0].objetivo").value("Prevenir acidentes"))
                .andExpect(jsonPath("$.data.nrs[0].aplicabilidade").value("Trabalho industrial"));
        verify(service).findProfileById(2L, principal);
    }

    @Test
    void returnsCollaboratorEventsWithBothStatusesAndClassDeadline() throws Exception {
        when(service.findEventsById(2L, principal)).thenReturn(new CollaboratorEventsResponse(
                2L, "Ana Silva", List.of(new CollaboratorEventResponse(10L, "NR-10",
                        LocalDateTime.of(2026, 11, 12, 17, 30), ParticipationStatus.EM_ANALISE,
                        EventStatus.CANCELADO, CompletionMode.COLABORADOR))));

        mvc.perform(get("/collaborators/2/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.collaboratorId").value(2))
                .andExpect(jsonPath("$.data.collaboratorName").value("Ana Silva"))
                .andExpect(jsonPath("$.data.events[0].eventId").value(10))
                .andExpect(jsonPath("$.data.events[0].eventTitle").value("NR-10"))
                .andExpect(jsonPath("$.data.events[0].classLimitDate").value("2026-11-12T17:30:00"))
                .andExpect(jsonPath("$.data.events[0].completionStatus").value("EM_ANALISE"))
                .andExpect(jsonPath("$.data.events[0].eventStatus").value("CANCELADO"))
                .andExpect(jsonPath("$.data.events[0].completionMode").value("COLABORADOR"));
        verify(service).findEventsById(2L, principal);
    }

    @Test
    void mapsMissingAndMalformedCollaboratorEventIdsToExistingHttpErrors() throws Exception {
        when(service.findEventsById(999L, principal))
                .thenThrow(new ResourceNotFoundException("Colaborador não encontrado"));

        mvc.perform(get("/collaborators/999/events"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Colaborador não encontrado"));
        mvc.perform(get("/collaborators/invalid/events"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsMissingAndInvalidProfileIdsToExistingHttpErrors() throws Exception {
        when(service.findProfileById(999L, principal))
                .thenThrow(new ResourceNotFoundException("Colaborador não encontrado"));
        mvc.perform(get("/collaborators/999/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Colaborador não encontrado"));
        mvc.perform(get("/collaborators/invalid/profile")).andExpect(status().isBadRequest());
    }

    @Test
    void mapsNotFoundAndMalformedIdsToExistingHttpErrors() throws Exception {
        when(service.findById(999L, principal)).thenThrow(new ResourceNotFoundException("Colaborador não encontrado"));
        mvc.perform(get("/collaborators/999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Colaborador não encontrado"));
        mvc.perform(get("/collaborators/invalid")).andExpect(status().isBadRequest());
    }

    static final String CREATE_JSON = """
            {"name":"  Colaborador teste  ","cpf":"529.982.247-25","email":" TEST@EXAMPLE.COM ",
             "roleId":5,"unitId":12,"workModel":"HIBRIDO"}
            """;

    @Test
    void createsWithLocationAndNormalizedFieldsWithoutAcceptingInternalAssignments() throws Exception {
        when(service.create(any(), eq(principal))).thenReturn(new CollaboratorDetailsResponse(2L, "Colaborador teste",
                "52998224725", "test@example.com", WorkModel.HIBRIDO, UserStatus.PRE_CADASTRADO,
                5L, "Operador", 12L, "Matriz", 3L, null, "CT", true));
        mvc.perform(post("/collaborators").contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/collaborators/2"))
                .andExpect(jsonPath("$.data.status").value("PRE_CADASTRADO"));
        verify(service).create(new CreateCollaboratorRequest("Colaborador teste", "52998224725", "test@example.com",
                5L, 12L, WorkModel.HIBRIDO), principal);
    }

    @Test
    void rejectsInvalidOrMissingRequiredFieldsWithPortugueseMessages() throws Exception {
        mvc.perform(post("/collaborators").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro de validação"))
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("O CPF é obrigatório"))))
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("A modalidade é obrigatória"))));
        for (String body : List.of(CREATE_JSON.replace("529.982.247-25", "111.111.111-11"),
                CREATE_JSON.replace("TEST@EXAMPLE.COM", "invalid"), CREATE_JSON.replace("\"roleId\":5", "\"roleId\":0"),
                CREATE_JSON.replace("HIBRIDO", "INVALID"), CREATE_JSON.replace("\"unitId\":12", "\"unitId\":null"))) {
            mvc.perform(post("/collaborators").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void preservesTheConflictHttpContract() throws Exception {
        when(service.create(any(), eq(principal))).thenThrow(new com.astro.api.common.exception.ConflictException("Já existe um usuário com este CPF"));
        mvc.perform(post("/collaborators").contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Já existe um usuário com este CPF"));
    }

    @Test
    void requiresAllEditableFieldsForPutAndUsesTheAuthenticatedPrincipal() throws Exception {
        String body = CREATE_JSON.trim().replace("\"workModel\":\"HIBRIDO\"", "\"workModel\":\"HIBRIDO\",\"status\":\"DESATIVADO\"");
        when(service.update(anyLong(), any(), eq(principal))).thenReturn(new CollaboratorDetailsResponse(2L, "Colaborador teste",
                "52998224725", "test@example.com", WorkModel.HIBRIDO, UserStatus.DESATIVADO, 5L, "Operador", 12L, "Matriz", 3L, null, "CT", true));
        mvc.perform(put("/collaborators/2").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DESATIVADO"));
        verify(service).update(2L, new UpdateCollaboratorRequest("Colaborador teste", "52998224725", "test@example.com",
                5L, 12L, WorkModel.HIBRIDO, UserStatus.DESATIVADO), principal);
        clearInvocations(service);
        for (String invalid : List.of("{}", CREATE_JSON, body.replace("\"status\":\"DESATIVADO\"", "\"status\":null"),
                body.replace("\"roleId\":5", "\"roleId\":null"))) {
            mvc.perform(put("/collaborators/2").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void patchesOnlyAnExplicitSupportedStatusAndRejectsMissingBodies() throws Exception {
        when(service.changeStatus(anyLong(), any(), eq(principal))).thenReturn(new CollaboratorDetailsResponse(2L,
                "Colaborador", "52998224725", "test@example.com", WorkModel.HIBRIDO, UserStatus.DESATIVADO,
                5L, "Operador", 12L, "Matriz", 3L, null, "C", true));
        mvc.perform(patch("/collaborators/2/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DESATIVADO\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DESATIVADO"));
        verify(service).changeStatus(2L, new CollaboratorStatusRequest(CollaboratorStatusRequest.Status.DESATIVADO), principal);
        clearInvocations(service);
        for (String invalid : List.of("{}", "{\"status\":null}", "{\"status\":\"PRE_CADASTRADO\"}", "{\"status\":true}")) {
            mvc.perform(patch("/collaborators/2/status").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/collaborators/2/status")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
