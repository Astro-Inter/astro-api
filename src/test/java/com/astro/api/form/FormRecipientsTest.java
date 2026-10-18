package com.astro.api.form;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.form.controller.FormController;
import com.astro.api.form.mapper.FormMapper;
import com.astro.api.form.repository.FormRepository;
import com.astro.api.form.service.FormAccessService;
import com.astro.api.form.service.FormReferenceService;
import com.astro.api.form.service.FormService;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.FormRecipientProjection;
import com.astro.api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FormRecipientsTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FormRepository formRepository = mock(FormRepository.class);
    private final FormService formService = new FormService(formRepository,
            new FormAccessService(userRepository), new FormMapper(), FormTestFixtures.validator(),
            mock(FormReferenceService.class), userRepository);
    private MockMvc mockMvc;
    private User currentManager;

    @BeforeEach void setUp() {
        currentManager = FormTestFixtures.manager(7, 1, UserType.GESTOR);
        when(userRepository.findByFirebaseUid("current-manager")).thenReturn(Optional.of(currentManager));
        mockMvc = MockMvcBuilders.standaloneSetup(new FormController(formService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        authenticate(Role.GESTOR);
    }

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"GESTOR", "GESTOR_WORKSPACE"})
    void usesAuthenticatedScopeAndNumericIdsForBothManagerProfiles(Role role) throws Exception {
        currentManager.setType(UserType.valueOf(role.name()));
        authenticate(role);
        FormRecipientProjection recipient = mock(FormRecipientProjection.class);
        when(recipient.getId()).thenReturn(8L);
        when(recipient.getName()).thenReturn("Ana");
        when(recipient.getUnitName()).thenReturn("Sede");
        when(userRepository.findFormRecipients(1L, 7L, UserStatus.ATIVO,
                List.of(UserType.GESTOR, UserType.GESTOR_WORKSPACE))).thenReturn(List.of(recipient));

        mockMvc.perform(get("/forms/recipients").param("workspaceId", "999")
                        .param("currentManagerId", "999").param("managerId", "999")
                        .param("userId", "999").param("firebaseUid", "forged")
                        .param("unitId", "999").param("status", "DESATIVADO").param("type", "COLABORADOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Destinatários retornados com sucesso"))
                .andExpect(jsonPath("$.data", aMapWithSize(2)))
                .andExpect(jsonPath("$.data.currentManagerId").isNumber())
                .andExpect(jsonPath("$.data.currentManagerId").value(7))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0]", aMapWithSize(3)))
                .andExpect(jsonPath("$.data.items[0].id").isNumber())
                .andExpect(jsonPath("$.data.items[0].id").value(8))
                .andExpect(jsonPath("$.data.items[0].name").value("Ana"))
                .andExpect(jsonPath("$.data.items[0].unitName").value("Sede"))
                .andExpect(jsonPath("$.errors").value(nullValue()))
                .andExpect(jsonPath("$.path").value("/forms/recipients"));
        verify(userRepository).findByFirebaseUid("current-manager");
        verify(userRepository).findFormRecipients(1L, 7L, UserStatus.ATIVO,
                List.of(UserType.GESTOR, UserType.GESTOR_WORKSPACE));
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(formRepository);
    }

    @Test void noRecipientsReturnsSuccessWithEmptyItemsInsteadOfResolvingAFormUuid() throws Exception {
        when(userRepository.findFormRecipients(anyLong(), anyLong(), any(), anyCollection())).thenReturn(List.of());
        mockMvc.perform(get("/forms/recipients"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentManagerId").value(7))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty());
        verifyNoInteractions(formRepository);
    }

    @Test void inactiveAuthenticatedManagerCannotQueryRecipients() throws Exception {
        currentManager.setStatus(UserStatus.DESATIVADO);
        expectForbiddenBeforeRecipientQuery();
    }

    @Test void inactiveAuthenticatedUnitCannotQueryRecipients() throws Exception {
        currentManager.getUnit().isActive = false;
        expectForbiddenBeforeRecipientQuery();
    }

    @Test void missingAuthenticatedAccountCannotQueryRecipients() throws Exception {
        when(userRepository.findByFirebaseUid("current-manager")).thenReturn(Optional.empty());
        expectForbiddenBeforeRecipientQuery();
    }

    @Test void mismatchingAuthenticatedProfileCannotQueryRecipients() throws Exception {
        currentManager.setType(UserType.GESTOR_WORKSPACE);
        expectForbiddenBeforeRecipientQuery();
    }

    @Test void collaboratorAccountCannotQueryRecipientsEvenWithManagerClaims() throws Exception {
        currentManager.setType(UserType.COLABORADOR);
        expectForbiddenBeforeRecipientQuery();
    }

    @Test void missingAuthenticatedUnitOrWorkspaceCannotQueryRecipients() throws Exception {
        currentManager.getUnit().workspace = null;
        expectForbiddenBeforeRecipientQuery();
        currentManager.setUnit(null);
        expectForbiddenBeforeRecipientQuery();
    }

    private void expectForbiddenBeforeRecipientQuery() throws Exception {
        mockMvc.perform(get("/forms/recipients")).andExpect(status().isForbidden());
        verify(userRepository, never()).findFormRecipients(anyLong(), anyLong(), any(), anyCollection());
        verifyNoInteractions(formRepository);
    }

    private void authenticate(Role role) {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser("current-manager", "manager@astro.test", role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, List.of()));
    }
}
