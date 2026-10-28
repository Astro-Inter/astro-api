package com.astro.api.conformity;

import com.astro.api.auth.security.*;
import com.astro.api.conformity.controller.CollaboratorConformityController;
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
    final com.astro.api.conformity.service.ConformityImportService importer = mock(com.astro.api.conformity.service.ConformityImportService.class);
    final AuthenticatedUser principal = new AuthenticatedUser("manager", "manager@example.com", Role.GESTOR);
    MockMvc mvc;
    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CollaboratorConformityController(service, importer))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
    @AfterEach void cleanUp() { SecurityContextHolder.clearContext(); }
    ConformityResponse response() {
        return new ConformityResponse(2L, 3L, "Colaborador", "col@example.com", 10, "NR 10", null,
                "CONCLUSAO_EVENTO", true, 42L, "Sem validade", "https://example.com/profile-photo", "C");
    }
    @Test void listsTheAuthenticatedScopeWithoutExposingCredentials() throws Exception {
        when(service.findAll(principal)).thenReturn(List.of(response()));
        mvc.perform(get("/conformities/collaborators").param("workspaceId", "999").param("firebaseUid", "fake"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].origin").value("CONCLUSAO_EVENTO"))
                .andExpect(jsonPath("$.data[0].status").value("Sem validade"))
                .andExpect(jsonPath("$.data[0].profilePhotoUrl").value("https://example.com/profile-photo"))
                .andExpect(jsonPath("$.data[0].avatarInitials").value("C"))
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.data[0].senha").doesNotExist())
                .andExpect(jsonPath("$.data[0].firebaseUid").doesNotExist())
                .andExpect(jsonPath("$.data[0].firebase_uid").doesNotExist());
        verify(service).findAll(principal);
    }

    @Test void returnsDetailsAndStandardNotFoundAndMalformedIdErrors() throws Exception {
        when(service.findById(2L, principal)).thenReturn(response());
        mvc.perform(get("/conformities/collaborators/2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventCompletionId").value(42))
                .andExpect(jsonPath("$.data.profilePhotoUrl").value("https://example.com/profile-photo"))
                .andExpect(jsonPath("$.data.avatarInitials").value("C"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.firebaseUid").doesNotExist());
        when(service.findById(99L, principal)).thenThrow(
                new com.astro.api.common.exception.ResourceNotFoundException("Conformidade não encontrada"));
        mvc.perform(get("/conformities/collaborators/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Conformidade não encontrada"));
        mvc.perform(get("/conformities/collaborators/invalid")).andExpect(status().isBadRequest());
    }

    @Test void createsWithLocationAndRejectsInvalidRequests() throws Exception {
        when(service.create(any(), eq(principal))).thenReturn(response());
        mvc.perform(post("/conformities/collaborators").contentType("application/json")
                        .content("{\"collaboratorId\":3,\"nrId\":10,\"expiresAt\":null,\"origin\":\"FAKE\",\"workspaceId\":999}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/conformities/collaborators/2"));
        verify(service).create(new com.astro.api.conformity.dto.request.CreateConformityRequest(3L, 10, null), principal);
        clearInvocations(service);
        for (String body : List.of("{}", "{\"collaboratorId\":0,\"nrId\":10}",
                "{\"collaboratorId\":3,\"nrId\":-1}", "{\"collaboratorId\":3,\"nrId\":10,\"expiresAt\":\"bad\"}")) {
            mvc.perform(post("/conformities/collaborators").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void putRequiresBothFieldsAndNullExplicitlyRemovesValidity() throws Exception {
        when(service.update(eq(2L), any(), eq(principal))).thenReturn(response());
        mvc.perform(put("/conformities/collaborators/2").contentType("application/json").content("{\"nrId\":10,\"expiresAt\":null}"))
                .andExpect(status().isOk());
        verify(service).update(2L, new com.astro.api.conformity.dto.request.UpdateConformityRequest(10, null), principal);
        clearInvocations(service);
        for (String body : List.of("{}", "{\"nrId\":10}", "{\"expiresAt\":null}", "{\"nrId\":0,\"expiresAt\":null}")) {
            mvc.perform(put("/conformities/collaborators/2").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void patchRequiresAnExplicitBooleanAndMapsConflicts() throws Exception {
        when(service.changeApplicability(eq(2L), any(), eq(principal))).thenReturn(response());
        mvc.perform(patch("/conformities/collaborators/2/applicability").contentType("application/json").content("{\"applicable\":false}"))
                .andExpect(status().isOk());
        verify(service).changeApplicability(2L, new com.astro.api.conformity.dto.request.ConformityApplicabilityRequest(false), principal);
        clearInvocations(service);
        for (String body : List.of("{}", "{\"applicable\":null}", "{\"applicable\":\"invalid\"}",
                "{\"applicable\":\"true\"}", "{\"applicable\":1}")) {
            mvc.perform(patch("/conformities/collaborators/2/applicability").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
        when(service.changeApplicability(eq(2L), any(), eq(principal))).thenThrow(
                new com.astro.api.common.exception.ConflictException("Já existe uma conformidade aplicável para este colaborador e NR"));
        mvc.perform(patch("/conformities/collaborators/2/applicability").contentType("application/json").content("{\"applicable\":true}"))
                .andExpect(status().isConflict());
    }

    @Test void importsMultipartWithTotalsAndRequiresTheFilePart() throws Exception {
        org.springframework.mock.web.MockMultipartFile file = ConformityTestSpreadsheets.file(false, new Object[]{"00000000002", 10, "2030-01-01"});
        when(importer.importFile(any(), eq(principal))).thenReturn(
                new com.astro.api.conformity.dto.response.ConformityImportResponse(1,1,0,0,List.of()));
        mvc.perform(multipart("/conformities/collaborators/import").file(file)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed").value(1)).andExpect(jsonPath("$.data.inserted").value(1));
        mvc.perform(multipart("/conformities/collaborators/import")).andExpect(status().isBadRequest());
        when(importer.importFile(any(), eq(principal))).thenThrow(new org.springframework.web.multipart.MaxUploadSizeExceededException(10L*1024*1024));
        mvc.perform(multipart("/conformities/collaborators/import").file(file)).andExpect(status().isPayloadTooLarge());
    }
}
