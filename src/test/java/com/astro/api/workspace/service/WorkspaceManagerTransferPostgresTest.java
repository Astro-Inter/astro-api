package com.astro.api.workspace.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.manager.repository.ManagerRepository;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.dto.request.WorkspaceManagerTransferRequest;
import com.astro.api.workspace.dto.response.WorkspaceManagerTransferResponse;
import com.astro.api.workspace.mapper.WorkspaceMapper;
import com.astro.api.workspace.model.Workspace;
import com.astro.api.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "WORKSPACE_TEST_JDBC_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/forms_recipients_test")
class WorkspaceManagerTransferPostgresTest {
    private static final String SCHEMA = "workspace_transfer_test_" + UUID.randomUUID().toString().replace("-", "");
    private static DriverManagerDataSource dataSource;
    private static AnnotationConfigApplicationContext context;
    private static TransactionTemplate transactions;
    private static UserRepository users;
    private static WorkspaceManagerTransferService service;
    private TransferAccounts accounts;

    @BeforeAll
    static void open() {
        String databaseUrl = System.getenv("WORKSPACE_TEST_JDBC_URL");
        if (!databaseUrl.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/forms_recipients_test")) {
            throw new IllegalArgumentException("Workspace tests require the local disposable PostgreSQL database");
        }
        dataSource = new DriverManagerDataSource(databaseUrl, "postgres", "forms-recipients-isolated");
        Properties properties = new Properties();
        properties.setProperty("currentSchema", SCHEMA);
        dataSource.setConnectionProperties(properties);
        new JdbcTemplate(dataSource).execute("CREATE SCHEMA " + SCHEMA);
        context = new AnnotationConfigApplicationContext(Config.class);
        transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        users = context.getBean(UserRepository.class);
        service = context.getBean(WorkspaceManagerTransferService.class);
    }

    @AfterAll
    static void close() {
        try {
            if (context != null) context.close();
        } finally {
            if (dataSource != null) new JdbcTemplate(dataSource).execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }

    @BeforeEach
    void createAccounts() {
        accounts = transactions.execute(status -> {
            EntityManager entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(
                    context.getBean(EntityManagerFactory.class));
            assertNotNull(entityManager);
            Workspace workspace = new Workspace();
            workspace.name = "Empresa";
            workspace.cnpj = "12345678000199";
            entityManager.persist(workspace);
            Unit headquarters = unit(entityManager, workspace, "Matriz");
            Unit branch = unit(entityManager, workspace, "Filial");
            User owner = user(entityManager, headquarters, UserType.GESTOR_WORKSPACE);
            User successor = user(entityManager, branch, UserType.GESTOR);
            User alternative = user(entityManager, headquarters, UserType.GESTOR);
            return new TransferAccounts(workspace.id, owner.getId(), successor.getId(), alternative.getId(),
                    owner.getFirebaseUid(), successor.getFirebaseUid(), headquarters.id, branch.id);
        });
    }

    @Test
    void changesOnlyTheTwoTypesAndPreservesBothAccountsInTheirOriginalUnits() {
        WorkspaceManagerTransferResponse response = service.transfer(
                new WorkspaceManagerTransferRequest(accounts.successorId()), principal());

        assertEquals(new WorkspaceManagerTransferResponse(accounts.workspaceId(), accounts.ownerId(), accounts.successorId()), response);
        transactions.executeWithoutResult(status -> {
            User previousManager = users.findById(accounts.ownerId()).orElseThrow();
            User newManager = users.findById(accounts.successorId()).orElseThrow();
            assertEquals(UserType.GESTOR, previousManager.getType());
            assertEquals(UserType.GESTOR_WORKSPACE, newManager.getType());
            assertEquals(accounts.ownerUid(), previousManager.getFirebaseUid());
            assertEquals(accounts.successorUid(), newManager.getFirebaseUid());
            assertEquals(accounts.headquartersId(), previousManager.getUnit().id);
            assertEquals(accounts.branchId(), newManager.getUnit().id);
            assertEquals(UserStatus.ATIVO, previousManager.getStatus());
            assertEquals(UserStatus.ATIVO, newManager.getStatus());
            assertEquals(Instant.EPOCH, previousManager.getCreatedAt());
            assertEquals(Instant.EPOCH, newManager.getCreatedAt());
            assertEquals(WorkModel.REMOTO, previousManager.getWorkModel());
            assertEquals(WorkModel.REMOTO, newManager.getWorkModel());
            assertEquals(1, users.countByUnit_WorkspaceAndType(newManager.getUnit().workspace, UserType.GESTOR_WORKSPACE));
        });
    }

    @Test
    void rollsBackTheDemotionWhenTheDatabaseRejectsThePromotion() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER TABLE usuario ADD CONSTRAINT reject_transfer_test CHECK (id_usuario <> "
                + accounts.successorId() + " OR tipo <> 'GESTOR_WORKSPACE')");
        try {
            assertThrows(DataIntegrityViolationException.class, () -> service.transfer(
                    new WorkspaceManagerTransferRequest(accounts.successorId()), principal()));
            assertTypes(UserType.GESTOR_WORKSPACE, UserType.GESTOR);
        } finally {
            jdbc.execute("ALTER TABLE usuario DROP CONSTRAINT reject_transfer_test");
        }
    }

    @Test
    void rejectsAConcurrentRequestThatCachedTheFormerOwnersAuthorization() throws Exception {
        CountDownLatch authorizationRead = new CountDownLatch(1);
        CountDownLatch firstTransferCommitted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Class<?>> staleTransfer = executor.submit(() -> {
                try {
                    transactions.executeWithoutResult(status -> {
                        User cachedOwner = users.findByFirebaseUid(accounts.ownerUid()).orElseThrow();
                        assertEquals(UserType.GESTOR_WORKSPACE, cachedOwner.getType());
                        authorizationRead.countDown();
                        await(firstTransferCommitted);
                        service.transfer(new WorkspaceManagerTransferRequest(accounts.alternativeId()), principal());
                    });
                    return null;
                } catch (RuntimeException exception) {
                    return exception.getClass();
                }
            });
            assertTrue(authorizationRead.await(10, TimeUnit.SECONDS));
            service.transfer(new WorkspaceManagerTransferRequest(accounts.successorId()), principal());
            firstTransferCommitted.countDown();

            assertEquals(AccessDeniedException.class, staleTransfer.get(10, TimeUnit.SECONDS));
            assertTypes(UserType.GESTOR, UserType.GESTOR_WORKSPACE);
            transactions.executeWithoutResult(status -> assertEquals(UserType.GESTOR,
                    users.findById(accounts.alternativeId()).orElseThrow().getType()));
        } finally {
            firstTransferCommitted.countDown();
            executor.shutdownNow();
        }
    }

    private void assertTypes(UserType ownerType, UserType successorType) {
        transactions.executeWithoutResult(status -> {
            assertEquals(ownerType, users.findById(accounts.ownerId()).orElseThrow().getType());
            User successor = users.findById(accounts.successorId()).orElseThrow();
            assertEquals(successorType, successor.getType());
            assertEquals(1, users.countByUnit_WorkspaceAndType(successor.getUnit().workspace, UserType.GESTOR_WORKSPACE));
        });
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(accounts.ownerUid(), null, Role.GESTOR_WORKSPACE);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("A transferência não terminou no prazo do teste");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static Unit unit(EntityManager entityManager, Workspace workspace, String name) {
        Unit unit = new Unit();
        unit.name = name;
        unit.workspace = workspace;
        unit.isActive = true;
        entityManager.persist(unit);
        return unit;
    }

    private static User user(EntityManager entityManager, Unit unit, UserType type) {
        String uid = UUID.randomUUID().toString();
        User user = new User();
        user.setName("Gestor");
        user.setEmail(uid + "@astro.test");
        user.setFirebaseUid(uid);
        user.setType(type);
        user.setStatus(UserStatus.ATIVO);
        user.setCreatedAt(Instant.EPOCH);
        user.setWorkModel(WorkModel.REMOTO);
        user.setUnit(unit);
        entityManager.persist(user);
        return user;
    }

    private record TransferAccounts(Long workspaceId, Long ownerId, Long successorId, Long alternativeId,
                                    String ownerUid, String successorUid, Long headquartersId, Long branchId) { }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {UserRepository.class, ManagerRepository.class, WorkspaceRepository.class})
    @Import({WorkspaceAccessService.class, WorkspaceManagerTransferService.class, WorkspaceMapper.class})
    static class Config {
        @Bean DataSource dataSource() { return WorkspaceManagerTransferPostgresTest.dataSource; }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.astro.api.unit.model", "com.astro.api.workspace.model",
                    "com.astro.api.user.model", "com.astro.api.role.model");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.default_schema", SCHEMA));
            return factory;
        }

        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }
}
