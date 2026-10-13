package com.astro.api.collaborator.controller;

import com.astro.api.auth.repository.AuthRepository;
import com.astro.api.auth.security.*;
import com.astro.api.auth.service.*;
import com.astro.api.collaborator.mapper.CollaboratorMapper;
import com.astro.api.collaborator.repository.CollaboratorRepository;
import com.astro.api.collaborator.service.*;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.config.CorsConfig;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.user.controller.UserController;
import com.astro.api.user.model.*;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.UserService;
import com.astro.api.workspace.model.Workspace;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CollaboratorDeactivationSecurityTest {
    @Test
    void deactivationDeniesTheSameFirebaseTokenOnTheNextProtectedRequestAndPreservesTheManager() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
            var tokens = context.getBean(FirebaseTokenService.class);
            var authRepository = context.getBean(AuthRepository.class);
            var userService = context.getBean(UserService.class);
            var users = context.getBean(UserRepository.class);
            var repository = context.getBean(CollaboratorRepository.class);
            Workspace workspace = new Workspace(); workspace.id = 3L;
            Unit unit = new Unit(); unit.id = 12L; unit.workspace = workspace; unit.name = "Matriz"; unit.isActive = true;
            com.astro.api.role.model.Role role = new com.astro.api.role.model.Role();
            role.setId(5L); role.setName("Operador"); role.setActive(true); role.setWorkspace(workspace);
            User manager = new User(); manager.setId(1L); manager.setStatus(UserStatus.ATIVO);
            manager.setType(UserType.GESTOR); manager.setUnit(unit);
            User collaborator = new User(); collaborator.setId(2L); collaborator.setStatus(UserStatus.ATIVO);
            collaborator.setType(UserType.COLABORADOR); collaborator.setUnit(unit); collaborator.setRole(role);
            collaborator.setFirebaseUid("collaborator");
            when(users.findByFirebaseUid("manager")).thenReturn(Optional.of(manager));
            when(repository.findForUpdateInScope(2L, "manager", 3L)).thenReturn(Optional.of(collaborator));
            when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(authRepository.findAccessLevel("manager")).thenReturn("GESTOR");
            when(authRepository.findAccessLevel("collaborator")).thenReturn("COLABORADOR");
            when(userService.findStatusByFirebaseUid("manager")).thenAnswer(invocation -> Optional.of(manager.getStatus()));
            when(userService.findStatusByFirebaseUid("collaborator")).thenAnswer(invocation -> Optional.of(collaborator.getStatus()));
            for (String uid : new String[]{"manager", "collaborator"}) {
                FirebaseToken token = mock(FirebaseToken.class); when(token.getUid()).thenReturn(uid);
                when(tokens.verify(uid + "-token")).thenReturn(token);
            }
            mvc.perform(get("/user/me").header("Authorization", "Bearer collaborator-token")).andExpect(status().isOk());
            mvc.perform(patch("/collaborators/2/status").header("Authorization", "Bearer manager-token")
                            .contentType("application/json").content("{\"status\":\"DESATIVADO\"}"))
                    .andExpect(status().isOk());
            assertEquals(UserStatus.DESATIVADO, collaborator.getStatus());
            clearInvocations(userService);
            mvc.perform(get("/user/me").header("Authorization", "Bearer collaborator-token"))
                    .andExpect(result -> assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403));
            verify(userService, never()).findProfileByFirebaseUid("collaborator");
            mvc.perform(get("/user/me").header("Authorization", "Bearer manager-token")).andExpect(status().isOk());
            assertEquals(UserStatus.ATIVO, manager.getStatus());
        }
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @org.springframework.transaction.annotation.EnableTransactionManagement
    @Import({SecurityConfig.class, CorsConfig.class, CollaboratorController.class, UserController.class,
            GlobalExceptionHandler.class, FirebaseAuthenticationFilter.class, AuthorizationService.class,
            CollaboratorService.class, CollaboratorMapper.class, CollaboratorStatusPolicy.class})
    static class Config {
        @Bean FirebaseTokenService firebaseTokenService() { return mock(FirebaseTokenService.class); }
        @Bean AuthRepository authRepository() { return mock(AuthRepository.class); }
        @Bean UserService userService() { return mock(UserService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean CollaboratorRepository collaboratorRepository() { return mock(CollaboratorRepository.class); }
        @Bean RoleRepository roleRepository() { return mock(RoleRepository.class); }
        @Bean UnitRepository unitRepository() { return mock(UnitRepository.class); }
        @Bean FirebaseIdentityService firebaseIdentityService() { return mock(FirebaseIdentityService.class); }
        @Bean PlatformTransactionManager transactionManager() {
            var manager = mock(PlatformTransactionManager.class);
            when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
            return manager;
        }
    }
}
