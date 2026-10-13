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
    @EnableJpaRepositories(basePackageClasses = {ManagerRepository.class, UserRepository.class})
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
        @Bean ManagerService service(ManagerRepository repository, UserRepository users, ManagerMapper mapper) {
            return new ManagerService(repository, users, mapper);
        }
    }
}
