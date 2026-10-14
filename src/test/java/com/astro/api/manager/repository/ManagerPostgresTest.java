package com.astro.api.manager.repository;

import com.astro.api.manager.mapper.ManagerMapper;
import com.astro.api.manager.service.ManagerService;
import com.astro.api.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MANAGER_TEST_DATABASE_URL", matches = ".+")
class ManagerPostgresTest {
    static final String SCHEMA = "manager_test_" + UUID.randomUUID().toString().replace("-", "");
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    ManagerRepository repository;

    @BeforeAll static void open() {
        var source = dataSource();
        // Use a unique schema in a local disposable database, never application tables.
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
        org.mockito.Mockito.reset(context.getBean(com.astro.api.auth.service.FirebaseIdentityService.class));
        repository = context.getBean(ManagerRepository.class);
        jdbc.execute("TRUNCATE usuario, unidade, cargo, workspace CASCADE");
        jdbc.update("INSERT INTO workspace VALUES (1,'Astro','1'), (2,'Outro','2')");
        jdbc.update("INSERT INTO cargo VALUES (1,1,'Ativo',true), (2,1,'Inativo',false), (3,2,'Outro',true)");
        jdbc.update("INSERT INTO unidade VALUES (1,1,'Ativa',true), (2,1,'Inativa',false), (3,2,'Outra',true)");
        insert(100, "Owner", "GESTOR_WORKSPACE", "ATIVO", 1, 1);
        insert(1, "Mesmo", "GESTOR", "ATIVO", 1, 1);
        insert(2, "Mesmo", "GESTOR", "PRE_CADASTRADO", 2, 2);
        insert(3, "Mesmo", "GESTOR", "DESATIVADO", 1, 1);
        insert(4, "Colaborador", "COLABORADOR", "ATIVO", 1, 1);
        insert(5, "Outro", "GESTOR", "ATIVO", 3, 3);
    }
    static void insert(long id, String name, String type, String status, long role, long unit) {
        jdbc.update("INSERT INTO usuario VALUES (?, ?, ?, ?, ?, ?, 'PRESENCIAL', ?, '2026-01-01', ?, ?)",
                id, name, "user" + id + "@astro.com", "uid" + id, String.format("%011d", id), type, status, role, unit);
    }
    @Test void realViewIsScopedOrderedAndPreservesStatusesAndInactiveLinks() {
        var rows = repository.findAllInWorkspace(1L);
        assertEquals(List.of(1L, 2L, 3L), rows.stream().map(ManagerSummaryProjection::getId).toList());
        assertEquals(List.of("ATIVO", "PRE_CADASTRADO", "DESATIVADO"), rows.stream().map(ManagerSummaryProjection::getStatus).toList());
        assertEquals(2L, rows.get(1).getUnitId());
        assertTrue(repository.findAllInWorkspace(99L).isEmpty());
        assertEquals(List.of(5L), repository.findAllInWorkspace(2L).stream().map(ManagerSummaryProjection::getId).toList());
    }
    @Test void detailsExcludeOtherProfilesAndWorkspaces() {
        var row = repository.findDetailsInWorkspace(2L, 1L).orElseThrow();
        assertEquals("00000000002", row.getCpf()); assertEquals(2L, row.getRoleId());
        assertEquals(1L, row.getWorkspaceId()); assertEquals("PRESENCIAL", row.getWorkModel());
        for (long id : List.of(4L, 5L, 100L, 999L)) assertTrue(repository.findDetailsInWorkspace(id, 1L).isEmpty());
    }
    com.astro.api.auth.security.AuthenticatedUser owner() {
        return new com.astro.api.auth.security.AuthenticatedUser("uid100", "user100@astro.com", com.astro.api.auth.security.Role.GESTOR_WORKSPACE);
    }
    @Test void promotionPreservesThePersistedAccountAndBothRequestedStatuses() {
        var service = context.getBean(ManagerService.class);
        var before = jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=4");
        jdbc.update("INSERT INTO conformidade VALUES (1,4,'2027-01-01')");
        for (var status : com.astro.api.manager.dto.request.ManagerAccountStatus.values()) {
            jdbc.update("UPDATE usuario SET tipo='COLABORADOR',status='ATIVO' WHERE id_usuario=4");
            var response = service.promote(new com.astro.api.manager.dto.request.PromoteManagerRequest(4L, status), owner());
            assertEquals(4L, response.id()); assertEquals(status.name(), response.status().name());
            var after = jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=4");
            for (String key : before.keySet()) if (!List.of("tipo", "status").contains(key)) assertEquals(before.get(key), after.get(key), key);
            assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM usuario", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM conformidade WHERE usuario_id=4", Integer.class));
        }
    }
    @Test void concurrentPromotionCommitsExactlyOnce() throws Exception {
        var service = context.getBean(ManagerService.class);
        var start = new java.util.concurrent.CountDownLatch(1);
        var ready = new java.util.concurrent.CountDownLatch(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> operation = () -> {
                ready.countDown(); assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS));
                try {
                    service.promote(new com.astro.api.manager.dto.request.PromoteManagerRequest(4L,
                            com.astro.api.manager.dto.request.ManagerAccountStatus.ATIVO), owner()); return true;
                } catch (com.astro.api.common.exception.ConflictException conflict) { return false; }
            };
            var first = executor.submit(operation); var second = executor.submit(operation);
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)); start.countDown();
            assertNotEquals(first.get(20, java.util.concurrent.TimeUnit.SECONDS), second.get(20, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals("GESTOR", jdbc.queryForObject("SELECT tipo FROM usuario WHERE id_usuario=4", String.class));
    }
    com.astro.api.manager.dto.request.UpdateManagerRequest update(long roleId, long unitId, String email, com.astro.api.user.model.UserStatus status) {
        return new com.astro.api.manager.dto.request.UpdateManagerRequest("Gestor editado", "529.982.247-25", email,
                roleId, unitId, com.astro.api.user.model.WorkModel.REMOTO, status);
    }
    @Test void fullUpdateValidatesTheDestinationAndKeepsOldInactiveLinks() {
        var service = context.getBean(ManagerService.class);
        service.update(2L, update(2, 2, "USER2@ASTRO.COM", com.astro.api.user.model.UserStatus.PRE_CADASTRADO), owner());
        assertEquals("52998224725", jdbc.queryForObject("SELECT cpf FROM usuario WHERE id_usuario=2", String.class));
        assertThrows(com.astro.api.common.exception.BusinessException.class,
                () -> service.update(1L, update(1, 2, "user1@astro.com", com.astro.api.user.model.UserStatus.ATIVO), owner()));
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class,
                () -> service.update(1L, update(1, 3, "user1@astro.com", com.astro.api.user.model.UserStatus.ATIVO), owner()));
        assertEquals(1L, jdbc.queryForObject("SELECT unidade_id FROM usuario WHERE id_usuario=1", Long.class));
    }
    @Test void updateRollsBackDatabaseChangesWhenFirebaseFailsAndContainsFailedCompensation() {
        var service = context.getBean(ManagerService.class);
        var firebase = context.getBean(com.astro.api.auth.service.FirebaseIdentityService.class);
        var before = jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=1");
        var previous = new com.astro.api.auth.service.FirebaseIdentityService.EmailState("user1@astro.com", true);
        org.mockito.Mockito.when(firebase.readEmailState("uid1")).thenReturn(previous);
        org.mockito.Mockito.doThrow(new IllegalStateException("Firebase transport failure")).when(firebase).updateEmail("uid1", "new@astro.com");
        assertThrows(IllegalStateException.class,
                () -> service.update(1L, update(1, 1, "new@astro.com", com.astro.api.user.model.UserStatus.DESATIVADO), owner()));
        assertEquals(before, jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=1"));
        org.mockito.Mockito.verify(firebase).restoreEmail("uid1", previous);
        org.mockito.Mockito.doThrow(new IllegalStateException("compensation failure")).when(firebase).restoreEmail("uid1", previous);
        assertThrows(IllegalStateException.class,
                () -> service.update(1L, update(1, 1, "new@astro.com", com.astro.api.user.model.UserStatus.ATIVO), owner()));
        assertEquals("DESATIVADO", jdbc.queryForObject("SELECT status FROM usuario WHERE id_usuario=1", String.class));
        assertEquals("user1@astro.com", jdbc.queryForObject("SELECT email FROM usuario WHERE id_usuario=1", String.class));
        assertEquals("GESTOR", jdbc.queryForObject("SELECT tipo FROM usuario WHERE id_usuario=1", String.class));
    }
    @Test void statusChangesPersistAndCannotActivatePreRegistrationOrInactiveLinks() {
        var service = context.getBean(ManagerService.class);
        var inactive = new com.astro.api.manager.dto.request.ManagerStatusRequest(com.astro.api.manager.dto.request.ManagerAccountStatus.DESATIVADO);
        var active = new com.astro.api.manager.dto.request.ManagerStatusRequest(com.astro.api.manager.dto.request.ManagerAccountStatus.ATIVO);
        service.changeStatus(1L, inactive, owner()); service.changeStatus(1L, inactive, owner());
        assertEquals("DESATIVADO", jdbc.queryForObject("SELECT status FROM usuario WHERE id_usuario=1", String.class));
        service.changeStatus(1L, active, owner());
        assertEquals("ATIVO", jdbc.queryForObject("SELECT status FROM usuario WHERE id_usuario=1", String.class));
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.changeStatus(2L, active, owner()));
        jdbc.update("UPDATE usuario SET status='DESATIVADO' WHERE id_usuario=2");
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.changeStatus(2L, active, owner()));
        jdbc.update("UPDATE usuario SET firebase_uid=null,status='DESATIVADO' WHERE id_usuario=1");
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.changeStatus(1L, active, owner()));
        for (long id : List.of(4L, 5L, 100L, 999L)) {
            assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.changeStatus(id, inactive, owner()));
        }
        assertEquals("GESTOR_WORKSPACE", jdbc.queryForObject("SELECT tipo FROM usuario WHERE id_usuario=100", String.class));
    }
    @Test void cpfAndEmailConflictsAreDetectedGloballyAndPreserveTheTarget() {
        var service = context.getBean(ManagerService.class);
        jdbc.update("UPDATE usuario SET cpf='52998224725' WHERE id_usuario=5");
        assertThrows(com.astro.api.common.exception.ConflictException.class,
                () -> service.update(1L, update(1,1,"user1@astro.com",com.astro.api.user.model.UserStatus.ATIVO),owner()));
        jdbc.update("UPDATE usuario SET cpf='11144477735' WHERE id_usuario=5");
        assertThrows(com.astro.api.common.exception.ConflictException.class,
                () -> service.update(1L, update(1,1,"USER5@ASTRO.COM",com.astro.api.user.model.UserStatus.ATIVO),owner()));
        assertEquals("user1@astro.com", jdbc.queryForObject("SELECT email FROM usuario WHERE id_usuario=1", String.class));
    }
    static DriverManagerDataSource dataSource() {
        String url = System.getenv("MANAGER_TEST_DATABASE_URL");
        if (!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/astro525")) {
            throw new IllegalArgumentException("Tests require a local disposable astro525 database");
        }
        var source = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("MANAGER_TEST_DATABASE_USER", "postgres"),
                System.getenv().getOrDefault("MANAGER_TEST_DATABASE_PASSWORD", "astro525-isolated"));
        Properties properties = new Properties(); properties.setProperty("currentSchema", SCHEMA);
        source.setConnectionProperties(properties);
        return source;
    }
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {ManagerRepository.class, UserRepository.class,
            com.astro.api.role.repository.RoleRepository.class, com.astro.api.unit.repository.UnitRepository.class})
    static class Config {
        @Bean DataSource source() { return dataSource(); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source); factory.setPackagesToScan("com.astro.api");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean ManagerMapper mapper() { return new ManagerMapper(); }
        @Bean com.astro.api.auth.service.FirebaseIdentityService firebase() { return org.mockito.Mockito.mock(com.astro.api.auth.service.FirebaseIdentityService.class); }
        @Bean ManagerService service(ManagerRepository repository, UserRepository users, ManagerMapper mapper,
                                    com.astro.api.role.repository.RoleRepository roles, com.astro.api.unit.repository.UnitRepository units,
                                    com.astro.api.auth.service.FirebaseIdentityService firebase, PlatformTransactionManager transactions) {
            return new ManagerService(repository, users, mapper, roles, units, firebase, new com.astro.api.manager.service.ManagerStatusPolicy(), transactions);
        }
    }
}
