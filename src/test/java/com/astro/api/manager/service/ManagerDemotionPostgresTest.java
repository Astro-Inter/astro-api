package com.astro.api.manager.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.manager.mapper.ManagerMapper;
import com.astro.api.manager.repository.ManagerRepository;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.UserAvatarService;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "MANAGER_DEMOTION_TEST_JDBC_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/(manager_demotion_test|forms_recipients_test)")
class ManagerDemotionPostgresTest {
    static final String SCHEMA = "manager_demotion_" + UUID.randomUUID().toString().replace("-", "");
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static DriverManagerDataSource dataSource;
    final AuthenticatedUser principal = new AuthenticatedUser("owner", "owner@example.test",
            com.astro.api.auth.security.Role.GESTOR_WORKSPACE);
    ManagerService service;
    UserAvatarService avatars;

    @BeforeAll static void open() {
        dataSource = new DriverManagerDataSource(System.getenv("MANAGER_DEMOTION_TEST_JDBC_URL"),
                System.getenv().getOrDefault("MANAGER_DEMOTION_TEST_DATABASE_USER", "postgres"),
                System.getenv().getOrDefault("MANAGER_DEMOTION_TEST_DATABASE_PASSWORD", "forms-recipients-isolated"));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA " + SCHEMA);
        Properties properties = new Properties();
        properties.setProperty("currentSchema", SCHEMA);
        dataSource.setConnectionProperties(properties);
        context = new AnnotationConfigApplicationContext(Config.class);
    }

    @AfterAll static void close() {
        if (context != null) context.close();
        if (jdbc != null) jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @BeforeEach void seed() {
        service = context.getBean(ManagerService.class);
        avatars = context.getBean(UserAvatarService.class);
        reset(avatars);
        jdbc.execute("TRUNCATE usuario, unidade, cargo, workspace CASCADE");
        jdbc.update("INSERT INTO workspace (id_workspace,nome,cnpj) VALUES (1,'Astro','1'),(2,'Outro','2')");
        jdbc.update("INSERT INTO cargo (id_cargo,workspace_id,nome,ativo) VALUES (1,1,'Cargo',false),(2,2,'Outro',true)");
        jdbc.update("INSERT INTO unidade (id_unidade,workspace_id,nome,ativo) VALUES (1,1,'Sede',false),(2,2,'Outra',true)");
        jdbc.update("""
                INSERT INTO usuario (id_usuario,nome,email,firebase_uid,cpf,tipo,modalidade,status,criado_em,unidade_id,cargo_id) VALUES
                (1,'Responsável','owner@example.test','owner',NULL,'GESTOR_WORKSPACE',NULL,'ATIVO','2026-01-01',1,1),
                (2,'Ana Silva','ana@example.test','ana','52998224725','GESTOR','REMOTO','ATIVO','2026-01-01',1,1),
                (3,'Outro','other@example.test','other',NULL,'GESTOR',NULL,'ATIVO','2026-01-01',2,2)
                """);
    }

    @ParameterizedTest @EnumSource(UserStatus.class)
    void commitsOnlyTheProfileChangeForEveryStatus(UserStatus status) {
        jdbc.update("UPDATE usuario SET status=? WHERE id_usuario=2", status.name());
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=2");
        service.demote(2L, principal);
        before.put("tipo", "COLABORADOR");
        assertEquals(before, jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=2"));
        assertEquals("GESTOR_WORKSPACE", jdbc.queryForObject("SELECT tipo FROM usuario WHERE id_usuario=1", String.class));
        assertThrows(ResourceNotFoundException.class, () -> service.demote(2L, principal));
    }

    @Test void rejectsForeignAndWorkspaceManagerTargetsWithoutChangingAnyAccount() {
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM usuario ORDER BY id_usuario");
        assertThrows(ResourceNotFoundException.class, () -> service.demote(3L, principal));
        assertThrows(ResourceNotFoundException.class, () -> service.demote(1L, principal));
        assertEquals(before, jdbc.queryForList("SELECT * FROM usuario ORDER BY id_usuario"));
    }

    @Test void rollsBackTheFlushedChangeWhenTheResponseCannotBeCompleted() {
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=2");
        when(avatars.findUrl(2L)).thenThrow(new IllegalStateException("Simulated avatar failure"));
        assertThrows(IllegalStateException.class, () -> service.demote(2L, principal));
        assertEquals(before, jdbc.queryForMap("SELECT * FROM usuario WHERE id_usuario=2"));
    }

    @Test void concurrentDemotionsLockAndRevalidateTheSameTarget() throws Exception {
        Callable<Boolean> demotion = () -> {
            try {
                service.demote(2L, principal);
                return true;
            } catch (ResourceNotFoundException exception) {
                return false;
            }
        };
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(demotion);
            Future<Boolean> second = executor.submit(demotion);
            int successes = (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
        }
        assertEquals("COLABORADOR", jdbc.queryForObject("SELECT tipo FROM usuario WHERE id_usuario=2", String.class));
    }

    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {ManagerRepository.class, UserRepository.class, RoleRepository.class, UnitRepository.class})
    static class Config {
        @Bean DataSource dataSource() { return ManagerDemotionPostgresTest.dataSource; }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.astro.api.unit.model", "com.astro.api.workspace.model",
                    "com.astro.api.user.model", "com.astro.api.role.model");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.default_schema", SCHEMA));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean UserAvatarService avatars() { return mock(UserAvatarService.class); }
        @Bean ManagerService managerService(ManagerRepository managers, UserRepository users, RoleRepository roles,
                UnitRepository units, PlatformTransactionManager transactions, UserAvatarService avatars) {
            return new ManagerService(managers, users, new ManagerMapper(), roles, units,
                    mock(FirebaseIdentityService.class), new ManagerStatusPolicy(), transactions, avatars);
        }
    }
}
