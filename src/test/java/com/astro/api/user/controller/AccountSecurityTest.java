package com.astro.api.user.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.FirebaseAuthenticationFilter;
import com.astro.api.auth.security.Role;
import com.astro.api.auth.security.SecurityConfig;
import com.astro.api.auth.service.AuthorizationService;
import com.astro.api.auth.service.FirebaseTokenService;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.config.CorsConfig;
import com.astro.api.user.dto.request.UpdateAccountDetailsRequest;
import com.astro.api.user.dto.response.AccountDetailsResponse;
import com.astro.api.user.dto.response.AccountSummaryResponse;
import com.astro.api.user.dto.response.UserProfileResponse;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.service.AccountService;
import com.astro.api.user.service.UserService;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.Filter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountSecurityTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    AccountService accountService;
    FirebaseTokenService firebaseTokenService;
    AuthorizationService authorizationService;

    @BeforeEach void open() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "astro.cors.allowed-origins=http://localhost");
        context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        accountService = context.getBean(AccountService.class);
        firebaseTokenService = context.getBean(FirebaseTokenService.class);
        authorizationService = context.getBean(AuthorizationService.class);
    }

    @AfterEach void close() { context.close(); }

    void authenticate(Role role) throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(firebaseTokenService.verify("valid")).thenReturn(token);
        when(token.getUid()).thenReturn("own-uid");
        when(token.getEmail()).thenReturn("token@example.com");
        when(authorizationService.getRole("own-uid")).thenReturn(Optional.of(role));
    }

    @Test void rejectsMissingInvalidTokensAndOtherProfiles() throws Exception {
        when(firebaseTokenService.verify("invalid")).thenThrow(mock(FirebaseAuthException.class));
        for (String path : new String[]{"/user/me/account-summary", "/user/me/account-details"}) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
            mvc.perform(get(path).header("Authorization", "Bearer invalid"))
                    .andExpect(status().isForbidden());
            for (Role role : new Role[]{Role.ADMIN, Role.COLABORADOR}) {
                authenticate(role);
                mvc.perform(get(path).header("Authorization", "Bearer valid"))
                        .andExpect(status().isForbidden());
            }
        }
        mvc.perform(put("/user/me/account-details").contentType("application/json").content(validUpdate()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/user/me/account-details").header("Authorization", "Bearer invalid")
                        .contentType("application/json").content(validUpdate())).andExpect(status().isForbidden());
        for (Role role : new Role[]{Role.ADMIN, Role.COLABORADOR}) {
            authenticate(role);
            mvc.perform(put("/user/me/account-details").header("Authorization", "Bearer valid")
                            .contentType("application/json").content(validUpdate())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(accountService);
    }

    @Test void detailsExposeExactlyNineFieldsIncludingExplicitNullsAndStoredLinks() throws Exception {
        for (Role role : new Role[]{Role.GESTOR, Role.GESTOR_WORKSPACE}) {
            authenticate(role);
            AuthenticatedUser principal = new AuthenticatedUser("own-uid", "token@example.com", role);
            when(accountService.findDetails(principal)).thenReturn(new AccountDetailsResponse("Maria Rosa", null,
                    "stored@example.com", 20L, "Engenheira", 10L, "Osasco", WorkModel.REMOTO, null));
            mvc.perform(get("/user/me/account-details?userId=999&workspaceId=2")
                            .header("Authorization", "Bearer valid"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data", aMapWithSize(9)))
                    .andExpect(jsonPath("$.data.name").value("Maria Rosa"))
                    .andExpect(jsonPath("$.data.email").value("stored@example.com"))
                    .andExpect(jsonPath("$.data.roleId").value(20))
                    .andExpect(jsonPath("$.data.roleName").value("Engenheira"))
                    .andExpect(jsonPath("$.data.unitId").value(10))
                    .andExpect(jsonPath("$.data.unitName").value("Osasco"))
                    .andExpect(jsonPath("$.data.workModel").value("REMOTO"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\"cpf\":null")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\"profilePhotoUrl\":null")));
            verify(accountService).findDetails(principal);
        }
    }

    @Test void detailsUseTheStandardNotFoundErrorEnvelope() throws Exception {
        authenticate(Role.GESTOR);
        when(accountService.findDetails(any())).thenThrow(new com.astro.api.common.exception.ResourceNotFoundException("Usuário não encontrado"));
        mvc.perform(get("/user/me/account-details").header("Authorization", "Bearer valid"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0]").value("Usuário não encontrado"));
    }

    @Test void mobileProfileKeepsItsExistingFieldsAndCollaboratorAccess() throws Exception {
        authenticate(Role.COLABORADOR);
        UserService userService = context.getBean(UserService.class);
        when(userService.findProfileByFirebaseUid("own-uid")).thenReturn(new UserProfileResponse(
                "Maria Rosa", "Engenheira", "Osasco", "REMOTO", "stored@example.com", null, java.util.List.of()));
        mvc.perform(get("/user/me").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", aMapWithSize(7)))
                .andExpect(jsonPath("$.data.nrs").isArray()).andExpect(jsonPath("$.data.name").value("Maria Rosa"))
                .andExpect(jsonPath("$.data.role").value("Engenheira"));
        verifyNoInteractions(accountService);
    }

    String validUpdate() {
        return "{\"name\":\"Maria Rosa\",\"cpf\":\"52998224725\",\"email\":\"stored@example.com\",\"roleId\":20,\"unitId\":10,\"workModel\":\"REMOTO\"}";
    }

    @Test void putUsesTheTokenAndReturnsUpdatedDetailsForBothProfiles() throws Exception {
        for (Role role : new Role[]{Role.GESTOR, Role.GESTOR_WORKSPACE}) {
            authenticate(role);
            AuthenticatedUser principal = new AuthenticatedUser("own-uid", "token@example.com", role);
            UpdateAccountDetailsRequest requestBody = new UpdateAccountDetailsRequest("Maria Rosa", "52998224725", "stored@example.com", 20L, 10L, WorkModel.REMOTO);
            when(accountService.update(requestBody, principal)).thenReturn(new AccountDetailsResponse("Maria Rosa", "52998224725",
                    "stored@example.com", 20L, "Engenheira", 10L, "Osasco", WorkModel.REMOTO, null));
            mvc.perform(put("/user/me/account-details?userId=999").header("Authorization", "Bearer valid")
                            .contentType("application/json").content(validUpdate()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", aMapWithSize(9)))
                    .andExpect(jsonPath("$.data.cpf").value("52998224725"));
            verify(accountService).update(requestBody, principal);
        }
    }

    @Test void putRejectsEachMissingOrNullFieldBeforeCallingTheService() throws Exception {
        authenticate(Role.GESTOR_WORKSPACE);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        for (String field : new String[]{"name", "cpf", "email", "roleId", "unitId", "workModel"}) {
            for (boolean omitted : new boolean[]{true, false}) {
                ObjectNode requestBody = (ObjectNode) jsonMapper.readTree(validUpdate());
                if (omitted) requestBody.remove(field); else requestBody.putNull(field);
                mvc.perform(put("/user/me/account-details").header("Authorization", "Bearer valid")
                                .contentType("application/json").content(jsonMapper.writeValueAsString(requestBody)))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.errors").isNotEmpty());
            }
        }
        verifyNoInteractions(accountService);
    }

    @Test void putRejectsInvalidFormatsLimitsAndForbiddenFields() throws Exception {
        authenticate(Role.GESTOR_WORKSPACE);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        List<String> invalid = List.of(
                validUpdate().replace("Maria Rosa", " "), validUpdate().replace("Maria Rosa", "M"),
                validUpdate().replace("Maria Rosa", "M".repeat(256)),
                validUpdate().replace("52998224725", "11111111111"), validUpdate().replace("52998224725", "abc"),
                validUpdate().replace("52998224725", ""), validUpdate().replace("stored@example.com", "invalid"),
                validUpdate().replace("stored@example.com", "x".repeat(250) + "@astro.com"),
                validUpdate().replace("\"roleId\":20", "\"roleId\":0"),
                validUpdate().replace("\"unitId\":10", "\"unitId\":-1"),
                validUpdate().replace("REMOTO", "INVALID"));
        for (String requestBody : invalid) {
            mvc.perform(put("/user/me/account-details").header("Authorization", "Bearer valid")
                            .contentType("application/json").content(requestBody))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
        for (String field : new String[]{"nome", "modalidade", "userId", "firebaseUid", "workspaceId", "status", "tipo", "senha", "password", "passwordHash", "file", "profilePhotoUrl", "nrs"}) {
            ObjectNode requestBody = (ObjectNode) jsonMapper.readTree(validUpdate());
            requestBody.put(field, "not-allowed");
            mvc.perform(put("/user/me/account-details").header("Authorization", "Bearer valid")
                            .contentType("application/json").content(jsonMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
        verifyNoInteractions(accountService);
    }

    @Test void putUsesStandardErrorEnvelopesForConflictInvalidLinkAndForbiddenChanges() throws Exception {
        authenticate(Role.GESTOR);
        List<RuntimeException> failures = List.of(
                new com.astro.api.common.exception.ConflictException("Já existe um usuário com este CPF"),
                new com.astro.api.common.exception.BusinessException("O cargo deve estar ativo"),
                new org.springframework.security.access.AccessDeniedException("Sem permissão"));
        int[] expectedStatuses = {409, 400, 403};
        for (int index = 0; index < failures.size(); index++) {
            doThrow(failures.get(index)).when(accountService).update(any(), any());
            mvc.perform(put("/user/me/account-details").header("Authorization", "Bearer valid")
                            .contentType("application/json").content(validUpdate()))
                    .andExpect(status().is(expectedStatuses[index])).andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errors").isNotEmpty()).andExpect(jsonPath("$.path").value("/user/me/account-details"));
        }
    }

    @Test void exposesOnlyThreeSummaryFieldsForBothManagersAndIgnoresTargetIds() throws Exception {
        for (Role role : new Role[]{Role.GESTOR, Role.GESTOR_WORKSPACE}) {
            authenticate(role);
            AuthenticatedUser principal = new AuthenticatedUser("own-uid", "token@example.com", role);
            when(accountService.findSummary(principal)).thenReturn(new AccountSummaryResponse("Maria Rosa", "stored@example.com", null));
            mvc.perform(get("/user/me/account-summary?userId=999&firebaseUid=foreign-uid")
                            .header("Authorization", "Bearer valid"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", aMapWithSize(3)))
                    .andExpect(jsonPath("$.data.name").value("Maria Rosa"))
                    .andExpect(jsonPath("$.data.email").value("stored@example.com"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\"profilePhotoUrl\":null")))
                    .andExpect(jsonPath("$.path").value("/user/me/account-summary"));
            verify(accountService).findSummary(principal);
        }
    }

    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, CorsConfig.class, FirebaseAuthenticationFilter.class, AccountController.class,
            UserController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean AccountService accountService() { return mock(AccountService.class); }
        @Bean UserService userService() { return mock(UserService.class); }
        @Bean FirebaseTokenService firebaseTokenService() { return mock(FirebaseTokenService.class); }
        @Bean AuthorizationService authorizationService() { return mock(AuthorizationService.class); }
    }
}
