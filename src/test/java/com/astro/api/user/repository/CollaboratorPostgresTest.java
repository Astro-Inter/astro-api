package com.astro.api.user.repository;

import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.model.WorkModel;
import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.user.service.CollaboratorService;
import com.astro.api.user.mapper.CollaboratorMapper;
import com.astro.api.user.dto.request.CreateCollaboratorRequest;
import com.astro.api.user.dto.request.UpdateCollaboratorRequest;
import com.astro.api.user.dto.request.CollaboratorStatusRequest;
import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.user.service.CollaboratorStatusPolicy;
import com.astro.api.user.service.UserAvatarService;
import com.astro.api.common.exception.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Uses only an explicitly configured disposable database with the official SQL objects installed. */
@EnabledIfEnvironmentVariable(named = "COLLABORATOR_TEST_JDBC_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/scrum517")
class CollaboratorPostgresTest {
    @Test
    void loadsProfilePhotosFromTheExistingTableOnlyAfterAuthorizingCollaborators() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            var service = context.getBean(CollaboratorService.class);
            var storage = context.getBean(com.astro.api.common.storage.R2StorageService.class);
            org.mockito.Mockito.when(storage.getPresignedDownload(org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> new com.astro.api.common.storage.R2StorageService.PresignedDownload(
                            "https://r2.example.com/" + invocation.getArgument(0), java.time.Instant.now().plusSeconds(86400)));
            new TransactionTemplate(context.getBean(JpaTransactionManager.class)).executeWithoutResult(transaction -> {
                transaction.setRollbackOnly();
                seed(jdbc);
                jdbc.update("INSERT INTO usuario_foto_perfil(usuario_id,caminho_objeto) VALUES (91002,'usuarios/91002/foto.jpg'), (92002,'usuarios/92002/foto.jpg')");
                var principal = new AuthenticatedUser("scrum517-manager", "scrum517-manager@example.com", Role.GESTOR);
                var result = service.findAll(principal);
                assertEquals("https://r2.example.com/usuarios/91002/foto.jpg", result.getFirst().profilePhotoUrl());
                assertEquals("AT", result.getFirst().avatarInitials());
                assertNull(result.get(1).profilePhotoUrl());
                assertEquals("BT", result.get(1).avatarInitials());
                assertEquals(result.getFirst().profilePhotoUrl(), service.findById(91002L, principal).profilePhotoUrl());
                assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class,
                        () -> service.findById(92002L, principal));
                org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).getPresignedDownload("usuarios/92002/foto.jpg");
            });
        }
    }

    @Test
    void changesStatusThroughTheRealScopedRepositoryAndKeepsTheManagersOwnStatus() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            var service = context.getBean(CollaboratorService.class);
            seed(jdbc);
            try {
                var principal = new AuthenticatedUser("scrum517-manager", "scrum517-manager@example.com", Role.GESTOR);
                var inactive = new CollaboratorStatusRequest(CollaboratorStatusRequest.Status.DESATIVADO);
                var active = new CollaboratorStatusRequest(CollaboratorStatusRequest.Status.ATIVO);
                assertEquals(UserStatus.DESATIVADO, service.changeStatus(91003L, inactive, principal).status());
                assertEquals(UserStatus.DESATIVADO, service.changeStatus(91003L, inactive, principal).status());
                assertEquals(UserStatus.ATIVO, service.changeStatus(91003L, active, principal).status());
                assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.changeStatus(91002L, active, principal));
                assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.changeStatus(91004L, active, principal));
                assertEquals(UserStatus.ATIVO, service.changeStatus(91001L, active, principal).status());
                assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.changeStatus(92002L, inactive, principal));
                service.changeStatus(91003L, inactive, principal);
                jdbc.update("UPDATE cargo SET ativo=false WHERE id_cargo=91001");
                assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.changeStatus(91003L, active, principal));
                assertEquals("ATIVO", jdbc.queryForObject("SELECT status FROM usuario WHERE id_usuario=91001", String.class));
            } finally { cleanSeed(jdbc); }
        }
    }

    @Test
    void editsTheRealUserPreservingInternalFieldsAndUnchangedInactiveLinks() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            var service = context.getBean(CollaboratorService.class);
            seed(jdbc);
            try {
                var createdAt = jdbc.queryForObject("SELECT criado_em FROM usuario WHERE id_usuario=91002", java.sql.Timestamp.class);
                jdbc.update("UPDATE cargo SET ativo=false WHERE id_cargo=91001");
                jdbc.update("UPDATE unidade SET ativo=false WHERE id_unidade=91001");
                var principal = new AuthenticatedUser("scrum517-manager", "scrum517-manager@example.com", Role.GESTOR);
                var response = service.update(91002L, new UpdateCollaboratorRequest("Nome editado", "52998224725",
                        "edited@example.com", 91001L, 91001L, WorkModel.REMOTO, UserStatus.PRE_CADASTRADO), principal);
                assertEquals("Nome editado", response.name());
                assertEquals("edited@example.com", jdbc.queryForObject("SELECT email FROM usuario WHERE id_usuario=91002", String.class));
                assertEquals("COLABORADOR", jdbc.queryForObject("SELECT tipo FROM usuario WHERE id_usuario=91002", String.class));
                assertNull(jdbc.queryForObject("SELECT firebase_uid FROM usuario WHERE id_usuario=91002", String.class));
                assertEquals(createdAt, jdbc.queryForObject("SELECT criado_em FROM usuario WHERE id_usuario=91002", java.sql.Timestamp.class));
                assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.update(92002L,
                        new UpdateCollaboratorRequest("Nome editado", "52998224725", "edited@example.com", 91001L, 91001L,
                                WorkModel.REMOTO, UserStatus.DESATIVADO), principal));
            } finally {
                cleanSeed(jdbc);
            }
        }
    }

    @Test
    void rollsBackAllLocalEditsWhenFirebaseFailsAndRestoresThePreviousIdentity() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            var service = context.getBean(CollaboratorService.class);
            var identities = context.getBean(FirebaseIdentityService.class);
            seed(jdbc);
            try {
                var previous = new FirebaseIdentityService.EmailState("scrum517-b@example.com", true);
                org.mockito.Mockito.when(identities.readEmailState("scrum517-collaborator")).thenReturn(previous);
                org.mockito.Mockito.doThrow(new IllegalStateException("Firebase unavailable")).when(identities)
                        .updateEmail("scrum517-collaborator", "new@example.com");
                var principal = new AuthenticatedUser("scrum517-manager", "scrum517-manager@example.com", Role.GESTOR);
                assertThrows(IllegalStateException.class, () -> service.update(91003L, new UpdateCollaboratorRequest(
                        "Nome editado", "11144477735", "new@example.com", 91001L, 91001L, WorkModel.HIBRIDO,
                        UserStatus.DESATIVADO), principal));
                assertEquals("scrum517-b@example.com", jdbc.queryForObject("SELECT email FROM usuario WHERE id_usuario=91003", String.class));
                assertEquals("Bia teste", jdbc.queryForObject("SELECT nome FROM usuario WHERE id_usuario=91003", String.class));
                assertEquals("ATIVO", jdbc.queryForObject("SELECT status FROM usuario WHERE id_usuario=91003", String.class));
                org.mockito.Mockito.verify(identities).restoreEmail("scrum517-collaborator", previous);
            } finally {
                cleanSeed(jdbc);
            }
        }
    }

    private void cleanSeed(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM usuario WHERE unidade_id IN (91001,91002,92001)");
        jdbc.update("DELETE FROM unidade WHERE id_unidade IN (91001,91002,92001)");
        jdbc.update("DELETE FROM cargo WHERE id_cargo IN (91001,92001)");
        jdbc.update("DELETE FROM workspace WHERE id_workspace IN (91001,92001)");
    }

    @Test
    void createsAndReadsARealPreRegisteredUserWithoutAuthenticationFields() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            var service = context.getBean(CollaboratorService.class);
            var repository = context.getBean(CollaboratorRepository.class);
            new TransactionTemplate(context.getBean(JpaTransactionManager.class)).executeWithoutResult(transaction -> {
                transaction.setRollbackOnly();
                seed(jdbc);
                var principal = new AuthenticatedUser("scrum517-manager", "scrum517-manager@example.com", Role.GESTOR);
                var request = new CreateCollaboratorRequest("Novo teste", "168.995.350-09", " NEW@EXAMPLE.COM ",
                        91001L, 91001L, WorkModel.REMOTO);
                var result = service.create(request, principal);
                User saved = repository.findById(result.id()).orElseThrow();
                assertEquals("16899535009", saved.getCpf());
                assertEquals("new@example.com", saved.getEmail());
                assertEquals(UserStatus.PRE_CADASTRADO, saved.getStatus());
                assertEquals(UserType.COLABORADOR, saved.getType());
                assertNull(saved.getFirebaseUid());
                assertNotNull(saved.getCreatedAt());
                assertTrue(repository.findDetailsInScope(result.id(), "scrum517-manager", 91001L).isPresent());
                assertThrows(ConflictException.class, () -> service.create(request, principal));
            });
        }
    }

    @Test
    void keepsDatabaseUniqueConstraintsAsProtectionAgainstRacingWrites() {
        for (boolean duplicateCpf : List.of(true, false)) {
            try (var context = new AnnotationConfigApplicationContext(Config.class)) {
                var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
                var repository = context.getBean(CollaboratorRepository.class);
                new TransactionTemplate(context.getBean(JpaTransactionManager.class)).executeWithoutResult(transaction -> {
                    transaction.setRollbackOnly();
                    seed(jdbc);
                    User existing = repository.findById(91002L).orElseThrow();
                    User duplicate = new User();
                    duplicate.setName("Duplicado teste");
                    duplicate.setType(UserType.COLABORADOR);
                    duplicate.setStatus(UserStatus.PRE_CADASTRADO);
                    duplicate.setRole(existing.getRole());
                    duplicate.setUnit(existing.getUnit());
                    duplicate.setCpf(duplicateCpf ? existing.getCpf() : "16899535009");
                    duplicate.setEmail(duplicateCpf ? "unique@example.com" : existing.getEmail());
                    assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(duplicate));
                });
            }
        }
    }

    @Test
    void queriesTheRealViewWithWorkspaceUnitProfileAndStatusIsolation() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            var repository = context.getBean(CollaboratorRepository.class);
            new TransactionTemplate(context.getBean(JpaTransactionManager.class)).executeWithoutResult(transaction -> {
                transaction.setRollbackOnly();
                seed(jdbc);
                var rows = repository.findAllInScope("scrum517-manager", 91001L);
                assertEquals(List.of(91002L, 91003L, 91004L, 91001L), rows.stream().map(CollaboratorSummaryProjection::getId).toList());
                assertEquals(List.of("PRE_CADASTRADO", "ATIVO", "DESATIVADO", "ATIVO"),
                        rows.stream().map(CollaboratorSummaryProjection::getStatus).toList());
                assertEquals(4, rows.stream().map(CollaboratorSummaryProjection::getId).distinct().count());
                assertTrue(repository.findAllInScope("scrum517-manager", 92001L).isEmpty());
                assertTrue(repository.findAllInScope("unknown", 91001L).isEmpty());
                assertTrue(repository.findAllInScope("scrum517-collaborator", 91001L).isEmpty());
                assertTrue(repository.findDetailsInScope(92002L, "scrum517-manager", 91001L).isEmpty());
                assertTrue(repository.findDetailsInScope(91001L, "scrum517-manager", 91001L).isPresent());
                assertTrue(repository.findDetailsInScope(99999L, "scrum517-manager", 91001L).isEmpty());
                var details = repository.findDetailsInScope(91002L, "scrum517-manager", 91001L).orElseThrow();
                assertEquals("52998224725", details.getCpf());
                assertEquals("HIBRIDO", details.getWorkModel());
                assertEquals(91001L, details.getWorkspaceId());
                jdbc.update("UPDATE usuario SET tipo = 'GESTOR_WORKSPACE' WHERE id_usuario = 91001");
                assertEquals(4, repository.findAllInScope("scrum517-manager", 91001L).size());
                jdbc.update("UPDATE usuario SET status = 'DESATIVADO' WHERE id_usuario = 91001");
                assertTrue(repository.findAllInScope("scrum517-manager", 91001L).isEmpty());
            });
        }
    }

    private void seed(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO workspace(id_workspace,nome,cnpj) VALUES (91001,'Teste SCRUM-517','91001000000001'), (92001,'Outro workspace','92001000000001')");
        jdbc.update("INSERT INTO cargo(id_cargo,workspace_id,nome,ativo) VALUES (91001,91001,'Operador',true), (92001,92001,'Outro cargo',true)");
        jdbc.update("INSERT INTO unidade(id_unidade,workspace_id,nome,ativo) VALUES (91001,91001,'Matriz',true), (91002,91001,'Filial',false), (92001,92001,'Outra unidade',true)");
        jdbc.update("""
                INSERT INTO usuario(id_usuario,nome,email,tipo,cargo_id,unidade_id,cpf,modalidade,status,firebase_uid) VALUES
                (91001,'Gestor teste','scrum517-manager@example.com','GESTOR',91001,91001,NULL,'HIBRIDO','ATIVO','scrum517-manager'),
                (91002,'Ana teste','scrum517-a@example.com','COLABORADOR',91001,91001,'52998224725','HIBRIDO','PRE_CADASTRADO',NULL),
                (91003,'Bia teste','scrum517-b@example.com','COLABORADOR',91001,91001,'11144477735','REMOTO','ATIVO','scrum517-collaborator'),
                (91004,'Caio teste','scrum517-c@example.com','COLABORADOR',91001,91002,'12345678909','PRESENCIAL','DESATIVADO',NULL),
                (92002,'Outro colaborador','scrum517-other@example.com','COLABORADOR',92001,92001,NULL,'REMOTO','ATIVO',NULL)
                """);
    }

    @Configuration
    @org.springframework.transaction.annotation.EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {"com.astro.api.user.repository",
            "com.astro.api.unit.repository", "com.astro.api.role.repository"})
    @Import({CollaboratorService.class, CollaboratorMapper.class, CollaboratorStatusPolicy.class, UserAvatarService.class})
    static class Config {
        @Bean com.astro.api.common.storage.R2StorageService storage() {
            return org.mockito.Mockito.mock(com.astro.api.common.storage.R2StorageService.class);
        }
        @Bean org.springframework.data.redis.core.StringRedisTemplate redisTemplate() {
            var redis = org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class,
                    org.mockito.Mockito.RETURNS_DEEP_STUBS);
            org.mockito.Mockito.when(redis.opsForValue().multiGet(org.mockito.ArgumentMatchers.any()))
                    .thenAnswer(invocation -> {
                        java.util.Collection<?> keys = invocation.getArgument(0);
                        return new java.util.ArrayList<>(java.util.Collections.nCopies(keys.size(), null));
                    });
            return redis;
        }
        @Bean FirebaseIdentityService firebaseIdentityService() { return org.mockito.Mockito.mock(FirebaseIdentityService.class); }
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("COLLABORATOR_TEST_JDBC_URL"), "postgres", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(User.class.getPackageName(), "com.astro.api.unit.model",
                    "com.astro.api.role.model", "com.astro.api.workspace.model");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
