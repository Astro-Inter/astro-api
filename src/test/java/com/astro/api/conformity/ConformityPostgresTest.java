package com.astro.api.conformity;

import com.astro.api.auth.security.*;
import com.astro.api.auth.security.Role;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.conformity.mapper.ConformityMapper;
import com.astro.api.conformity.service.ConformityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Official SQL snapshot from banco-de-dados@72548b1; installs ONLY in a generated test schema. */
@EnabledIfEnvironmentVariable(named = "CONFORMITY_TEST_JDBC_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/scrum536")
class ConformityPostgresTest {
    static AnnotationConfigApplicationContext context;
    static DataSource database;
    static JdbcTemplate jdbc;
    static String schema;
    ConformityService service;
    final AuthenticatedUser principal = new AuthenticatedUser("manager", "manager@example.com", Role.GESTOR);

    @BeforeAll static void start() throws Exception {
        schema = "conformity_test_" + UUID.randomUUID().toString().replace("-", "");
        var source = new DriverManagerDataSource(System.getenv("CONFORMITY_TEST_JDBC_URL"), "postgres", "");
        new JdbcTemplate(source).execute("CREATE SCHEMA " + schema);
        database = new DriverManagerDataSource(System.getenv("CONFORMITY_TEST_JDBC_URL") + "?currentSchema=" + schema,
                "postgres", "");
        jdbc = new JdbcTemplate(database);
        for (String file : List.of("tables.sql", "constraints.sql", "unit-view.sql", "units.sql", "view.sql")) {
            jdbc.execute(new ClassPathResource("conformity/" + file).getContentAsString(StandardCharsets.UTF_8));
        }
        context = new AnnotationConfigApplicationContext(Config.class);
    }

    @AfterAll static void stop() {
        if (context != null) context.close();
        if (schema != null && schema.matches("conformity_test_[a-f0-9]{32}")) {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @BeforeEach void seed() {
        service = context.getBean(ConformityService.class);
        jdbc.execute("TRUNCATE workspace, nr_catalogo RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO workspace(id_workspace,nome,cnpj) VALUES (1,'Teste um','11111111111111'),(2,'Teste dois','22222222222222')");
        jdbc.update("INSERT INTO cargo(id_cargo,workspace_id,nome,ativo) VALUES (1,1,'Operador',true),(2,2,'Operador',true)");
        jdbc.update("INSERT INTO unidade(id_unidade,workspace_id,nome,ativo) VALUES (1,1,'Matriz',true),(2,1,'Filial',true),(3,2,'Outro',true)");
        jdbc.update("""
                INSERT INTO usuario(id_usuario,nome,email,firebase_uid,tipo,cargo_id,unidade_id,cpf,modalidade,status)
                VALUES (1,'Gestor','manager@example.com','manager','GESTOR',1,1,'00000000001','PRESENCIAL','ATIVO'),
                       (2,'Ana','ana@example.com',null,'COLABORADOR',1,1,'00000000002','PRESENCIAL','ATIVO'),
                       (3,'Bia','bia@example.com',null,'COLABORADOR',1,2,'00000000003','PRESENCIAL','DESATIVADO'),
                       (4,'Outro','other@example.com',null,'COLABORADOR',2,3,'00000000004','PRESENCIAL','ATIVO'),
                       (5,'Gestor workspace','workspace@example.com','workspace','GESTOR_WORKSPACE',1,1,'00000000005','PRESENCIAL','ATIVO')
                """);
        jdbc.update("INSERT INTO nr_catalogo VALUES (10,'NR 10',12,false),(11,'NR 11',12,false),(12,'NR 12',12,true)");
    }

    @Test void consolidatesByApplicabilityThenDateAndIdAndReadsEveryHistoricalId() {
        jdbc.update("""
                INSERT INTO conformidade(id_conformidade,usuario_id,nr_id,aplicavel,data_validade,origem)
                VALUES (1,2,10,false,'2099-01-01','MANUAL'),(2,2,10,true,NULL,'OUTRA_ORIGEM'),
                       (3,2,10,true,'2030-01-01','IMPORTADA'),(4,2,10,true,'2030-01-01','MANUAL'),
                       (5,2,11,false,NULL,'MANUAL'),(6,3,12,true,NULL,'MANUAL'),
                       (7,4,10,true,'2030-01-01','MANUAL'),(8,1,10,true,'2030-01-01','MANUAL')
                """);
        var list = service.findAll(principal);
        assertEquals(List.of(4L, 6L), list.stream().map(row -> row.id()).toList());
        assertEquals("MANUAL", list.getFirst().origin()); assertEquals("Válida", list.getFirst().status());
        assertEquals("Sem validade", list.getLast().status());
        assertEquals(2L, service.findById(2L, principal).id());
        assertEquals("OUTRA_ORIGEM", service.findById(2L, principal).origin());
        assertFalse(service.findById(1L, principal).applicable());
        assertFalse(service.findById(5L, principal).applicable());
        assertThrows(ResourceNotFoundException.class, () -> service.findById(7L, principal));
        assertThrows(ResourceNotFoundException.class, () -> service.findById(8L, principal));
        jdbc.update("UPDATE conformidade SET data_validade = CURRENT_DATE WHERE id_conformidade=4");
        assertEquals("Válida", service.findById(4L, principal).status());
        jdbc.update("UPDATE conformidade SET data_validade = CURRENT_DATE-1 WHERE id_conformidade=4");
        assertEquals("Vencida", service.findById(4L, principal).status());
        jdbc.update("UPDATE conformidade SET aplicavel=false WHERE usuario_id=2 AND nr_id=10");
        assertEquals(1L, jdbc.queryForObject("SELECT id_conformidade FROM vw_conformidades WHERE usuario_id=2 AND nr_id=10", Long.class));
        assertEquals(List.of(6L), service.findAll(principal).stream().map(row -> row.id()).toList());
    }

    @Test void appliesTheExistingUnitPolicyForBothManagerProfilesAndKeepsInactiveHistory() {
        jdbc.update("INSERT INTO conformidade(usuario_id,nr_id,aplicavel,origem) VALUES (3,12,true,'MANUAL'),(4,10,true,'MANUAL')");
        assertEquals(1, service.findAll(principal).size());
        var workspaceManager = new AuthenticatedUser("workspace", "workspace@example.com", Role.GESTOR_WORKSPACE);
        assertEquals(1, service.findAll(workspaceManager).size());
        jdbc.update("UPDATE usuario SET status='DESATIVADO' WHERE id_usuario=1");
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.findAll(principal));
    }

    @Test void createsManualWithNullValidityPreservesHistoryAndRejectsInvalidLinks() {
        jdbc.update("INSERT INTO conformidade(usuario_id,nr_id,aplicavel,origem) VALUES (2,10,false,'MANUAL')");
        var request = new com.astro.api.conformity.dto.request.CreateConformityRequest(2L, 10, null);
        var result = service.create(request, principal);
        assertEquals("MANUAL", result.origin()); assertTrue(result.applicable());
        assertNull(result.eventCompletionId()); assertNull(result.expiresAt());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM conformidade", Integer.class));
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.create(request, principal));
        assertThrows(ResourceNotFoundException.class, () -> service.create(
                new com.astro.api.conformity.dto.request.CreateConformityRequest(4L, 10, null), principal));
        assertThrows(ResourceNotFoundException.class, () -> service.create(
                new com.astro.api.conformity.dto.request.CreateConformityRequest(1L, 10, null), principal));
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.create(
                new com.astro.api.conformity.dto.request.CreateConformityRequest(3L, 10, null), principal));
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.create(
                new com.astro.api.conformity.dto.request.CreateConformityRequest(2L, 12, null), principal));
        assertThrows(ResourceNotFoundException.class, () -> service.create(
                new com.astro.api.conformity.dto.request.CreateConformityRequest(2L, 999, null), principal));
    }

    @Test void serializesConcurrentManualCreatesWithoutLosingHistory() throws Exception {
        var request = new com.astro.api.conformity.dto.request.CreateConformityRequest(2L, 10, null);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> action = () -> {
                start.await();
                try { service.create(request, principal); return "created"; }
                catch (com.astro.api.common.exception.ConflictException conflict) { return "conflict"; }
            };
            var first = executor.submit(action); var second = executor.submit(action); start.countDown();
            assertEquals(Set.of("created", "conflict"), Set.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15, java.util.concurrent.TimeUnit.SECONDS)));
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM conformidade WHERE aplicavel", Integer.class));
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {"com.astro.api.user.repository", "com.astro.api.conformity.repository"},
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = com.astro.api.conformity.repository.NrDocumentRepository.class))
    @Import({ConformityService.class, ConformityMapper.class})
    static class Config {
        @Bean DataSource dataSource() { return database; }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source); factory.setPackagesToScan("com.astro.api");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
