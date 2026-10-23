package com.astro.api.chat;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.chat.controller.ChatController;
import com.astro.api.chat.mapper.ChatContactMapper;
import com.astro.api.chat.service.ChatContactService;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "CHAT_TEST_JDBC_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/astro_chat_test")
class ChatContactsPostgresIntegrationTest {
    private static AnnotationConfigApplicationContext context;
    private static boolean createdAdminTable;
    private static MockMvc mockMvc;

    @BeforeAll
    static void connect() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        DataSource dataSource = context.getBean(DataSource.class);
        try (Connection connection = dataSource.getConnection()) {
            createdAdminTable = !connection.getMetaData().getTables(null, "public", "admin", null).next();
        }
        if (createdAdminTable) {
            new JdbcTemplate(dataSource).execute("""
                    CREATE TABLE admin (
                        id_admin BIGSERIAL PRIMARY KEY,
                        nome VARCHAR(255),
                        email VARCHAR(255),
                        firebase_uid VARCHAR(128)
                    )
                    """);
        }
        UserRepository userRepository = context.getBean(UserRepository.class);
        ChatContactService service = new ChatContactService(userRepository, new ChatContactMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterAll
    static void close() {
        SecurityContextHolder.clearContext();
        if (context != null) {
            if (createdAdminTable) {
                new JdbcTemplate(context.getBean(DataSource.class)).execute("DROP TABLE IF EXISTS admin");
            }
            context.close();
        }
    }

    @Test
    void collaboratorGetsOnlyManagersFromWorkspaceAndIncludesPreRegisteredUsers() throws Exception {
        inTransaction(entityManager -> {
            Workspace workspace = workspace(entityManager, "Workspace colaborador");
            Unit unit = unit(entityManager, workspace, "Sede");
            Workspace otherWorkspace = workspace(entityManager, "Outro workspace");
            Unit otherUnit = unit(entityManager, otherWorkspace, "Outra sede");
            User current = user(entityManager, unit, "Atual", "uid-collaborator", UserType.COLABORADOR, UserStatus.ATIVO);
            User ana = user(entityManager, unit, "Ana", "uid-ana", UserType.GESTOR, UserStatus.PRE_CADASTRADO);
            User bruno = user(entityManager, unit, "Bruno", "uid-bruno", UserType.GESTOR_WORKSPACE, UserStatus.ATIVO);
            user(entityManager, unit, "Colaborador", "uid-peer", UserType.COLABORADOR, UserStatus.ATIVO);
            user(entityManager, unit, "Desativado", "uid-disabled", UserType.GESTOR, UserStatus.DESATIVADO);
            user(entityManager, otherUnit, "Fora do workspace", "uid-foreign", UserType.GESTOR, UserStatus.ATIVO);
            entityManager.flush();

            performAs(current, Role.COLABORADOR)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id").value(List.of(ana.getId().intValue(), bruno.getId().intValue())))
                    .andExpect(jsonPath("$[0].name").value("Ana"))
                    .andExpect(jsonPath("$[0].role").value("GESTOR"))
                    .andExpect(jsonPath("$[0].cpf").doesNotExist())
                    .andExpect(jsonPath("$[0].email").doesNotExist());
        });
    }

    @Test
    void managerCanContactAllTypesAndAdminAccountsNeverAppear() throws Exception {
        assertManagerTypesAndAdminExcluded(UserType.GESTOR);
    }

    @Test
    void workspaceManagerCanContactAllTypesAndAdminAccountsNeverAppear() throws Exception {
        assertManagerTypesAndAdminExcluded(UserType.GESTOR_WORKSPACE);
    }

    @Test
    void contactListIsAnEmptyArrayWhenNoEligibleRecipientsExist() throws Exception {
        inTransaction(entityManager -> {
            Workspace workspace = workspace(entityManager, "Workspace vazio");
            Unit unit = unit(entityManager, workspace, "Sede");
            User current = user(entityManager, unit, "Atual", "uid-empty", UserType.GESTOR, UserStatus.ATIVO);
            entityManager.flush();

            performAs(current, Role.GESTOR)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        });
    }

    private void assertManagerTypesAndAdminExcluded(UserType managerType) throws Exception {
        inTransaction(entityManager -> {
            Workspace workspace = workspace(entityManager, "Workspace " + managerType);
            Unit unit = unit(entityManager, workspace, "Sede");
            User current = user(entityManager, unit, "Atual", "uid-" + managerType, managerType, UserStatus.ATIVO);
            User collaborator = user(entityManager, unit, "Colaborador", "uid-collab-" + managerType,
                    UserType.COLABORADOR, UserStatus.ATIVO);
            User peerManager = user(entityManager, unit, "Outro gestor", "uid-manager-" + managerType,
                    UserType.GESTOR, UserStatus.ATIVO);
            User workspaceManager = user(entityManager, unit, "Gestor workspace", "uid-gw-" + managerType,
                    UserType.GESTOR_WORKSPACE, UserStatus.ATIVO);
            entityManager.createNativeQuery("INSERT INTO admin (nome, email, firebase_uid) VALUES (:name, :email, :uid)")
                    .setParameter("name", "Admin técnico")
                    .setParameter("email", "admin-" + managerType + "@astro.test")
                    .setParameter("uid", "uid-admin-" + managerType)
                    .executeUpdate();
            entityManager.flush();

            performAs(current, Role.valueOf(managerType.name()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Contatos retornados com sucesso"))
                    .andExpect(jsonPath("$.path").value("/chats/contacts"))
                    .andExpect(jsonPath("$.data[*].id").value(List.of(
                            collaborator.getId().intValue(), peerManager.getId().intValue(), workspaceManager.getId().intValue())))
                    .andExpect(jsonPath("$.data[*].name").value(List.of("Colaborador", "Outro gestor", "Gestor workspace")))
                    .andExpect(jsonPath("$.data[*].name").value(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.hasItem("Admin técnico"))));
        });
    }

    private org.springframework.test.web.servlet.ResultActions performAs(User user, Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(user.getFirebaseUid(), user.getEmail(), role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        try {
            return mockMvc.perform(get("/chats/contacts"));
        } catch (Exception exception) {
            throw new IllegalStateException("Falha ao executar GET /chats/contacts", exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void inTransaction(EntityManagerTest test) throws Exception {
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(transaction -> {
            EntityManager entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(
                    context.getBean(EntityManagerFactory.class));
            try {
                test.accept(entityManager);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            transaction.setRollbackOnly();
        });
    }

    @FunctionalInterface
    private interface EntityManagerTest {
        void accept(EntityManager entityManager) throws Exception;
    }

    private static Workspace workspace(EntityManager entityManager, String name) {
        Workspace workspace = new Workspace();
        workspace.name = name;
        workspace.cnpj = "12345678000100";
        entityManager.persist(workspace);
        return workspace;
    }

    private static Unit unit(EntityManager entityManager, Workspace workspace, String name) {
        Unit unit = new Unit();
        unit.name = name;
        unit.workspace = workspace;
        unit.isActive = true;
        entityManager.persist(unit);
        return unit;
    }

    private static User user(EntityManager entityManager, Unit unit, String name, String firebaseUid,
                             UserType type, UserStatus status) {
        User user = new User();
        user.setName(name);
        user.setEmail(firebaseUid + "@astro.test");
        user.setFirebaseUid(firebaseUid);
        user.setUnit(unit);
        user.setType(type);
        user.setStatus(status);
        entityManager.persist(user);
        return user;
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    static class Config {
        @Bean
        DataSource dataSource() {
            String databaseUrl = System.getenv("CHAT_TEST_JDBC_URL");
            if (databaseUrl == null || !databaseUrl.matches(
                    "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/astro_chat_test")) {
                throw new IllegalArgumentException("Tests require the dedicated local astro_chat_test database");
            }
            return new DriverManagerDataSource(databaseUrl, "postgres", "astro-chat-isolated");
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.astro.api");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }
}
