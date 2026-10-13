package com.astro.api.collaborator.repository;

import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.model.WorkModel;
import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.collaborator.service.CollaboratorService;
import com.astro.api.collaborator.mapper.CollaboratorMapper;
import com.astro.api.collaborator.dto.request.CreateCollaboratorRequest;
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
                assertEquals(List.of(91002L, 91003L, 91004L), rows.stream().map(CollaboratorSummaryProjection::getId).toList());
                assertEquals(List.of("PRE_CADASTRADO", "ATIVO", "DESATIVADO"),
                        rows.stream().map(CollaboratorSummaryProjection::getStatus).toList());
                assertEquals(3, rows.stream().map(CollaboratorSummaryProjection::getId).distinct().count());
                assertTrue(repository.findAllInScope("scrum517-manager", 92001L).isEmpty());
                assertTrue(repository.findAllInScope("unknown", 91001L).isEmpty());
                assertTrue(repository.findAllInScope("scrum517-collaborator", 91001L).isEmpty());
                assertTrue(repository.findDetailsInScope(92002L, "scrum517-manager", 91001L).isEmpty());
                assertTrue(repository.findDetailsInScope(91001L, "scrum517-manager", 91001L).isEmpty());
                assertTrue(repository.findDetailsInScope(99999L, "scrum517-manager", 91001L).isEmpty());
                var details = repository.findDetailsInScope(91002L, "scrum517-manager", 91001L).orElseThrow();
                assertEquals("52998224725", details.getCpf());
                assertEquals("HIBRIDO", details.getWorkModel());
                assertEquals(91001L, details.getWorkspaceId());
                jdbc.update("UPDATE usuario SET tipo = 'GESTOR_WORKSPACE' WHERE id_usuario = 91001");
                assertEquals(3, repository.findAllInScope("scrum517-manager", 91001L).size());
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
    @EnableJpaRepositories(basePackages = {"com.astro.api.collaborator.repository", "com.astro.api.user.repository",
            "com.astro.api.unit.repository", "com.astro.api.role.repository"})
    @Import({CollaboratorService.class, CollaboratorMapper.class})
    static class Config {
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
