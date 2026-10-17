package com.astro.api.user.repository;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.user.dto.request.UpdateAccountDetailsRequest;
import com.astro.api.user.dto.response.AccountDetailsResponse;
import com.astro.api.user.mapper.AccountMapper;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.service.AccountService;
import com.astro.api.user.service.UserAvatarService;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reuses the CI disposable PostgreSQL service, always within a unique test schema. */
@EnabledIfEnvironmentVariable(named = "MANAGER_TEST_DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/astro525")
class AccountPostgresTest {
    static final String SCHEMA = "account_test_" + UUID.randomUUID().toString().replace("-", "");
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    AccountService service;
    FirebaseIdentityService identities;

    @BeforeAll static void open() {
        DriverManagerDataSource source = dataSource();
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA " + SCHEMA);
        new ResourceDatabasePopulator(new ClassPathResource("manager-fixture.sql")).execute(source);
        context = new AnnotationConfigApplicationContext(Config.class);
    }

    @AfterAll static void close() {
        if (context != null) context.close();
        if (jdbc != null) jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @BeforeEach void seed() {
        service = context.getBean(AccountService.class);
        identities = context.getBean(FirebaseIdentityService.class); reset(identities);
        jdbc.execute("TRUNCATE usuario, unidade, cargo, workspace CASCADE");
        jdbc.update("INSERT INTO workspace VALUES (1,'Astro','1'), (2,'Outro','2')");
        jdbc.update("INSERT INTO cargo VALUES (1,1,'Engenheira',true), (2,1,'Supervisora',true), (3,2,'Outro',true)");
        jdbc.update("INSERT INTO unidade VALUES (1,1,'Osasco',true), (2,1,'São Paulo',true), (3,2,'Outra',true)");
        jdbc.update("""
                INSERT INTO usuario VALUES
                (1,'Maria','one@example.com','uid1',NULL,'GESTOR_WORKSPACE',NULL,'ATIVO','2026-01-01',1,1),
                (2,'Ana','two@example.com','uid2','11144477735','GESTOR','REMOTO','ATIVO','2026-01-01',1,1),
                (3,'Outro','other@example.com','uid3','12345678909','GESTOR','REMOTO','ATIVO','2026-01-01',3,3)
                """);
    }

    AuthenticatedUser principal(int id, Role role) { return new AuthenticatedUser("uid" + id, "stale@example.com", role); }
    UpdateAccountDetailsRequest body(String email, long roleId, long unitId) {
        return new UpdateAccountDetailsRequest("Nome editado", "52998224725", email, roleId, unitId, WorkModel.HIBRIDO);
    }

    @Test void readsOnlyTheAuthenticatedAccountAndPreservesAbsentValuesForBothProfiles() {
        AuthenticatedUser owner = principal(1, Role.GESTOR_WORKSPACE);
        AuthenticatedUser manager = principal(2, Role.GESTOR);
        assertEquals("one@example.com", service.findSummary(owner).email());
        assertEquals("two@example.com", service.findSummary(manager).email());
        AccountDetailsResponse result = service.findDetails(owner);
        assertNull(result.cpf()); assertNull(result.workModel()); assertNull(result.profilePhotoUrl());
        assertEquals(1L, result.roleId()); assertEquals("Engenheira", result.roleName());
        assertEquals(1L, result.unitId()); assertEquals("Osasco", result.unitName());
        assertEquals("11144477735", service.findDetails(manager).cpf());
    }

    @Test void persistsCompleteUpdatePreservingInternalAttributesAndOtherAccounts() {
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=3");
        AccountDetailsResponse result = service.update(body("one@example.com", 2, 2), principal(1, Role.GESTOR_WORKSPACE));
        assertEquals("Nome editado", result.name()); assertEquals("52998224725", result.cpf());
        assertEquals("Supervisora", result.roleName()); assertEquals("São Paulo", result.unitName());
        assertEquals("GESTOR_WORKSPACE", jdbc.queryForObject("SELECT tipo FROM usuario WHERE id_usuario=1", String.class));
        assertEquals("uid1", jdbc.queryForObject("SELECT firebase_uid FROM usuario WHERE id_usuario=1", String.class));
        assertEquals("ATIVO", jdbc.queryForObject("SELECT status FROM usuario WHERE id_usuario=1", String.class));
        assertEquals(before, jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=3"));
        verifyNoInteractions(identities);
    }

    @Test void locksLinksAndRejectsInactiveOrForeignDestinationsAndManagerSelfReassignment() {
        AuthenticatedUser owner = principal(1, Role.GESTOR_WORKSPACE);
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=1");
        assertThrows(ResourceNotFoundException.class, () -> service.update(body("one@example.com", 3, 1), owner));
        assertThrows(ResourceNotFoundException.class, () -> service.update(body("one@example.com", 1, 3), owner));
        jdbc.update("UPDATE cargo SET ativo=false WHERE id_cargo=2");
        jdbc.update("UPDATE unidade SET ativo=false WHERE id_unidade=2");
        assertThrows(BusinessException.class, () -> service.update(body("one@example.com", 2, 1), owner));
        assertThrows(BusinessException.class, () -> service.update(body("one@example.com", 1, 2), owner));
        assertThrows(AccessDeniedException.class,
                () -> service.update(body("two@example.com", 1, 2), principal(2, Role.GESTOR)));
        assertEquals(before, jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=1"));
        TransactionTemplate template = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        template.executeWithoutResult(transaction -> {
            AccountRepository repository = context.getBean(AccountRepository.class);
            assertFalse(repository.lockRoleActive(2L, 1L));
            assertNull(repository.lockRoleActive(3L, 1L));
            assertFalse(repository.lockUnitActive(2L, 1L));
            assertNull(repository.lockUnitActive(3L, 1L));
        });
    }

    @Test void realTransactionRollsBackAllFieldsOnFirebaseFailureAndContainsFailedCompensation() {
        FirebaseIdentityService.EmailState previous = new FirebaseIdentityService.EmailState("one@example.com", true);
        when(identities.readEmailState("uid1")).thenReturn(previous);
        doThrow(new IllegalStateException("timeout")).when(identities).updateEmail("uid1", "new@example.com");
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=1");
        UpdateAccountDetailsRequest request = body("new@example.com", 2, 2);
        assertThrows(IllegalStateException.class, () -> service.update(request, principal(1, Role.GESTOR_WORKSPACE)));
        assertEquals(before, jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=1"));
        verify(identities).restoreEmail("uid1", previous);
        doThrow(new IllegalStateException("restore failed")).when(identities).restoreEmail("uid1", previous);
        assertThrows(IllegalStateException.class, () -> service.update(request, principal(1, Role.GESTOR_WORKSPACE)));
        assertEquals("DESATIVADO", jdbc.queryForObject("SELECT status FROM usuario WHERE id_usuario=1", String.class));
        assertEquals("one@example.com", jdbc.queryForObject("SELECT email FROM usuario WHERE id_usuario=1", String.class));
        assertThrows(AccessDeniedException.class, () -> service.findDetails(principal(1, Role.GESTOR_WORKSPACE)));
    }

    @Test void detectsConflictsGloballyAndPreservesUnchangedInactiveLinks() {
        AuthenticatedUser owner = principal(1, Role.GESTOR_WORKSPACE);
        assertThrows(ConflictException.class, () -> service.update(body("other@example.com", 1, 1), owner));
        jdbc.update("UPDATE usuario SET cpf='52998224725' WHERE id_usuario=3");
        assertThrows(ConflictException.class, () -> service.update(body("one@example.com", 1, 1), owner));
        jdbc.update("UPDATE usuario SET cpf=NULL WHERE id_usuario=3");
        jdbc.update("UPDATE cargo SET ativo=false WHERE id_cargo=1");
        jdbc.update("UPDATE unidade SET ativo=false WHERE id_unidade=1");
        assertEquals(1L, service.update(body("one@example.com", 1, 1), owner).unitId());
    }

    static DriverManagerDataSource dataSource() {
        String url = System.getenv("MANAGER_TEST_DATABASE_URL");
        if (!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/astro525")) {
            throw new IllegalArgumentException("Tests require a local disposable astro525 database");
        }
        DriverManagerDataSource source = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("MANAGER_TEST_DATABASE_USER", "postgres"),
                System.getenv().getOrDefault("MANAGER_TEST_DATABASE_PASSWORD", "astro525-isolated"));
        Properties properties = new Properties(); properties.setProperty("currentSchema", SCHEMA);
        source.setConnectionProperties(properties); return source;
    }

    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {UserRepository.class, RoleRepository.class, UnitRepository.class})
    static class Config {
        @Bean DataSource source() { return dataSource(); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source); factory.setPackagesToScan("com.astro.api");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none")); return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean FirebaseIdentityService identities() { return mock(FirebaseIdentityService.class); }
        @Bean UserAvatarService avatars() { return mock(UserAvatarService.class); }
        @Bean AccountMapper mapper() { return new AccountMapper(); }
        @Bean AccountService accounts(UserRepository users, UserAvatarService avatars, AccountMapper mapper,
                                     AccountRepository accounts, RoleRepository roles, UnitRepository units,
                                     FirebaseIdentityService identities, PlatformTransactionManager transactions) {
            return new AccountService(users, avatars, mapper, accounts, roles, units, identities, transactions);
        }
    }
}
