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

    @Test void updatesOnlyTheRequestedIdAndRejectsApplicableNrConflicts() {
        jdbc.update("""
                INSERT INTO conformidade(id_conformidade,usuario_id,nr_id,aplicavel,data_validade,origem)
                VALUES (10,2,10,true,'2027-01-01','IMPORTADA'),(11,2,10,true,'2030-01-01','MANUAL'),
                       (12,2,11,true,'2030-01-01','MANUAL'),(13,2,10,false,'2027-01-01','MANUAL')
                """);
        var result = service.update(10L, new com.astro.api.conformity.dto.request.UpdateConformityRequest(10, null), principal);
        assertEquals(10L, result.id()); assertNull(result.expiresAt()); assertEquals("IMPORTADA", result.origin());
        assertTrue(result.applicable()); assertEquals(2L, result.collaboratorId());
        assertEquals(11L, service.findAll(principal).getFirst().id());
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.update(10L,
                new com.astro.api.conformity.dto.request.UpdateConformityRequest(11, null), principal));
        assertEquals(11, service.update(13L,
                new com.astro.api.conformity.dto.request.UpdateConformityRequest(11, null), principal).nrId());
        assertFalse(service.findById(13L, principal).applicable());
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM conformidade", Integer.class));
    }

    void seedCompletion() {
        jdbc.update("INSERT INTO evento(id_evento,gestor_id,nr_id,titulo,descricao,modo_conclusao,evidencia_obrigatoria,status) VALUES (1,1,10,'Evento','Treinamento','GESTOR',false,'ATIVO')");
        jdbc.update("INSERT INTO turma(id_turma,evento_id,nome,data_inicial,data_termino) VALUES (1,1,'Turma',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + interval '1 hour')");
        jdbc.update("INSERT INTO turma_funcionario(id_turma_funcionario,turma_id,usuario_id) VALUES (1,1,2)");
        jdbc.update("INSERT INTO conclusao_evento(id_conclusao_evento,turma_funcionario_id,status,data_conclusao,data_validacao,data_validade) VALUES (1,1,'CONCLUIDO',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'2027-01-01')");
        jdbc.update("INSERT INTO conformidade(id_conformidade,usuario_id,nr_id,aplicavel,data_validade,origem,conclusao_evento_id) VALUES (20,2,10,true,'2027-01-01','CONCLUSAO_EVENTO',1)");
    }

    @Test void keepsEventNrAndCompletionValidityConsistentDuringEdits() {
        seedCompletion();
        var result = service.update(20L, new com.astro.api.conformity.dto.request.UpdateConformityRequest(10, null), principal);
        assertNull(result.expiresAt()); assertEquals(1L, result.eventCompletionId());
        assertEquals("CONCLUSAO_EVENTO", result.origin());
        assertEquals(java.sql.Date.valueOf("2027-01-01"), jdbc.queryForObject("SELECT data_validade FROM conclusao_evento WHERE id_conclusao_evento=1", java.sql.Date.class));
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.update(20L,
                new com.astro.api.conformity.dto.request.UpdateConformityRequest(11, null), principal));
        assertEquals(10, service.findById(20L, principal).nrId());
    }

    @Test void togglesOnlyTheSpecifiedRecordAndReactivationChecksPolicyAndConflicts() {
        seedCompletion();
        jdbc.update("INSERT INTO conformidade(id_conformidade,usuario_id,nr_id,aplicavel,data_validade,origem) VALUES (21,2,10,true,'2030-01-01','MANUAL')");
        var inactive = new com.astro.api.conformity.dto.request.ConformityApplicabilityRequest(false);
        var active = new com.astro.api.conformity.dto.request.ConformityApplicabilityRequest(true);
        var result = service.changeApplicability(21L, inactive, principal);
        assertFalse(result.applicable()); assertFalse(service.changeApplicability(21L, inactive, principal).applicable());
        assertEquals(20L, service.findAll(principal).getFirst().id());
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.changeApplicability(21L, active, principal));
        var event = service.changeApplicability(20L, inactive, principal);
        assertEquals("CONCLUSAO_EVENTO", event.origin()); assertEquals(1L, event.eventCompletionId());
        assertEquals(java.time.LocalDate.of(2027,1,1), event.expiresAt());
        assertTrue(service.changeApplicability(21L, active, principal).applicable());
        assertTrue(service.changeApplicability(21L, active, principal).applicable());
        service.changeApplicability(21L, inactive, principal);
        jdbc.update("UPDATE usuario SET status='DESATIVADO' WHERE id_usuario=2");
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.changeApplicability(21L, active, principal));
        jdbc.update("UPDATE usuario SET status='ATIVO' WHERE id_usuario=2");
        jdbc.update("UPDATE nr_catalogo SET revogada=true WHERE codigo_nr=10");
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.changeApplicability(21L, active, principal));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM conformidade", Integer.class));
    }

    @Test void importsMixedRowsAndOnlyUpdatesTheRepresentativePreservingItsEventOrigin() throws Exception {
        seedCompletion();
        jdbc.update("INSERT INTO conformidade(id_conformidade,usuario_id,nr_id,aplicavel,data_validade,origem) VALUES (21,2,10,true,NULL,'MANUAL')");
        var importer = context.getBean(com.astro.api.conformity.service.ConformityImportService.class);
        var result = importer.importFile(ConformityTestSpreadsheets.file(false,
                new Object[]{2D, "NR 10", "2030-01-01"},
                new Object[]{"00000000002", "10", "2031-01-01"},
                new Object[]{"00000000002", "11", "31/02/2030"},
                new Object[]{"00000000002", "11", "31/12/2030"},
                new Object[]{"00000000004", "11", "2030-01-01"},
                new Object[]{"00000000001", "11", "2030-01-01"},
                new Object[]{"00000000002", "999", "2030-01-01"}), principal);
        assertEquals(7, result.processed()); assertEquals(1, result.inserted()); assertEquals(1, result.updated());
        assertEquals(5, result.rejected()); assertEquals(3, result.rejectedRows().getFirst().row());
        assertEquals(java.time.LocalDate.of(2030,1,1), service.findById(20L, principal).expiresAt());
        assertEquals("CONCLUSAO_EVENTO", service.findById(20L, principal).origin());
        assertEquals(1L, service.findById(20L, principal).eventCompletionId());
        assertNull(service.findById(21L, principal).expiresAt());
        assertEquals(java.sql.Date.valueOf("2027-01-01"), jdbc.queryForObject("SELECT data_validade FROM conclusao_evento WHERE id_conclusao_evento=1", java.sql.Date.class));
    }

    @Test void rollsBackAFailedDatabaseRowAndCommitsSubsequentValidRows() throws Exception {
        jdbc.update("INSERT INTO nr_catalogo VALUES (13,'NR 13',12,false)");
        jdbc.execute("CREATE FUNCTION reject_test_nr() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.nr_id=11 THEN RAISE EXCEPTION 'isolated row failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_test_nr BEFORE INSERT ON conformidade FOR EACH ROW EXECUTE FUNCTION reject_test_nr()");
        try {
            var importer = context.getBean(com.astro.api.conformity.service.ConformityImportService.class);
            var result = importer.importFile(ConformityTestSpreadsheets.file(true,
                    new Object[]{2D, 10, "2030-01-01"}, new Object[]{2D, 11, "2030-01-01"},
                    new Object[]{2D, 13, "2030-01-01"}), principal);
            assertEquals(3, result.processed()); assertEquals(2, result.inserted()); assertEquals(1, result.rejected());
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM conformidade", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM conformidade WHERE nr_id=11", Integer.class));
            assertFalse(result.rejectedRows().getFirst().reason().contains("isolated row failure"));
        } finally {
            jdbc.execute("DROP TRIGGER reject_test_nr ON conformidade"); jdbc.execute("DROP FUNCTION reject_test_nr()");
        }
    }

    @Test void serializesImportAgainstManualCreation() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var manual = executor.submit(() -> {
                start.await();
                try { service.create(new com.astro.api.conformity.dto.request.CreateConformityRequest(2L, 10, null), principal); return true; }
                catch (com.astro.api.common.exception.ConflictException conflict) { return false; }
            });
            var imported = executor.submit(() -> { start.await(); return service.importRow("00000000002", 10, java.time.LocalDate.of(2030,1,1), principal); });
            start.countDown();
            assertNotEquals(manual.get(15, java.util.concurrent.TimeUnit.SECONDS), imported.get(15, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM conformidade WHERE aplicavel", Integer.class));
        assertEquals(java.time.LocalDate.of(2030,1,1), service.findAll(principal).getFirst().expiresAt());
    }

    @Test void serializesReactivationsOfTwoHistoricalRecords() throws Exception {
        jdbc.update("INSERT INTO conformidade(id_conformidade,usuario_id,nr_id,aplicavel,origem) VALUES (10,2,10,false,'MANUAL'),(11,2,10,false,'MANUAL')");
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.function.Function<Long, java.util.concurrent.Callable<Boolean>> action = id -> () -> {
                start.await();
                try { service.changeApplicability(id, new com.astro.api.conformity.dto.request.ConformityApplicabilityRequest(true), principal); return true; }
                catch (com.astro.api.common.exception.ConflictException conflict) { return false; }
            };
            var first = executor.submit(action.apply(10L)); var second = executor.submit(action.apply(11L)); start.countDown();
            assertNotEquals(first.get(15, java.util.concurrent.TimeUnit.SECONDS), second.get(15, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM conformidade WHERE aplicavel", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM conformidade", Integer.class));
    }

    @Test void derivedNrLookupKeepsTheSharedLockUntilTheTransactionCompletes() throws Exception {
        var nrCatalogRepository = context.getBean(com.astro.api.conformity.repository.NrCatalogRepository.class);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(status -> {
                assertFalse(nrCatalogRepository.findLockedByCodeNr(10).orElseThrow().isRevoked());
                var concurrentUpdate = executor.submit(() -> {
                    try (var connection = database.getConnection(); var statement = connection.createStatement()) {
                        statement.execute("SET lock_timeout = '300ms'");
                        // Shared locks allow reads but must block a change to the NR being validated.
                        try (var result = statement.executeQuery("SELECT revogada FROM nr_catalogo WHERE codigo_nr=10")) {
                            assertTrue(result.next()); assertFalse(result.getBoolean(1));
                        }
                        try { statement.executeUpdate("UPDATE nr_catalogo SET revogada=true WHERE codigo_nr=10"); return "updated"; }
                        catch (java.sql.SQLException error) { return error.getSQLState(); }
                    }
                });
                try { assertEquals("55P03", concurrentUpdate.get(5, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (Exception error) { throw new AssertionError(error); }
            });
        }
        assertEquals(1, jdbc.update("UPDATE nr_catalogo SET revogada=true WHERE codigo_nr=10"));
        transaction.executeWithoutResult(status -> assertTrue(nrCatalogRepository.findLockedByCodeNr(10).orElseThrow().isRevoked()));
    }

    @Test void importRepresentativeSelectionKeepsNullsLastAndUsesTheHighestIdOnTies() {
        jdbc.update("""
                INSERT INTO conformidade(id_conformidade,usuario_id,nr_id,aplicavel,data_validade,origem)
                VALUES (10,2,10,true,NULL,'MANUAL'),(11,2,10,true,'2030-01-01','ANTIGA'),
                       (12,2,10,true,'2030-01-01','IMPORTADA'),(13,2,10,false,'2099-01-01','MANUAL'),
                       (14,2,11,true,NULL,'MANUAL'),(15,2,11,true,NULL,'IMPORTADA')
                """);
        assertFalse(service.importRow("00000000002", 10, java.time.LocalDate.of(2031,1,1), principal));
        assertEquals(java.time.LocalDate.of(2031,1,1), service.findById(12L, principal).expiresAt());
        assertEquals("IMPORTADA", service.findById(12L, principal).origin());
        assertEquals(java.time.LocalDate.of(2030,1,1), service.findById(11L, principal).expiresAt());
        assertNull(service.findById(10L, principal).expiresAt());
        assertFalse(service.importRow("00000000002", 11, java.time.LocalDate.of(2031,1,1), principal));
        assertEquals(java.time.LocalDate.of(2031,1,1), service.findById(15L, principal).expiresAt());
        assertNull(service.findById(14L, principal).expiresAt());
        assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM conformidade", Integer.class));
    }

    @Test void exercisesAllSixEndpointsOverRealLocalHttpWithIsolatedPostgres() throws Exception {
        var tomcat = new org.apache.catalina.startup.Tomcat();
        var directory = java.nio.file.Files.createTempDirectory("conformity-http-");
        tomcat.setBaseDir(directory.toString());
        var connector = new org.apache.catalina.connector.Connector("org.apache.coyote.http11.Http11Nio2Protocol"); connector.setPort(0); tomcat.setConnector(connector);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var servletContext = tomcat.addContext("", directory.toString());
        servletContext.setParentClassLoader(getClass().getClassLoader());
        var web = new org.springframework.web.context.support.AnnotationConfigWebApplicationContext();
        web.setParent(context); web.setServletContext(servletContext.getServletContext());
        web.register(HttpConfig.class); web.refresh();
        var tokens = web.getBean(com.astro.api.auth.service.FirebaseTokenService.class);
        var authorization = web.getBean(com.astro.api.auth.service.AuthorizationService.class);
        var token = org.mockito.Mockito.mock(com.google.firebase.auth.FirebaseToken.class);
        org.mockito.Mockito.when(token.getUid()).thenReturn("manager");
        org.mockito.Mockito.when(tokens.verify("valid-local-test")).thenReturn(token);
        org.mockito.Mockito.when(tokens.verify("invalid-local-test")).thenThrow(org.mockito.Mockito.mock(com.google.firebase.auth.FirebaseAuthException.class));
        org.mockito.Mockito.when(authorization.getRole("manager")).thenReturn(Optional.of(Role.GESTOR));
        var servlet = org.apache.catalina.startup.Tomcat.addServlet(servletContext, "dispatcher", new org.springframework.web.servlet.DispatcherServlet(web));
        servlet.setLoadOnStartup(1);
        servlet.setMultipartConfigElement(new jakarta.servlet.MultipartConfigElement(directory.toString(), 10L*1024*1024, 11L*1024*1024, 0));
        servletContext.addServletMappingDecoded("/", "dispatcher");
        var filter = new org.apache.tomcat.util.descriptor.web.FilterDef();
        filter.setFilterName("security"); filter.setFilter(web.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class));
        servletContext.addFilterDef(filter);
        var mapping = new org.apache.tomcat.util.descriptor.web.FilterMap();
        mapping.setFilterName("security"); mapping.addURLPattern("/*"); servletContext.addFilterMapBefore(mapping);
        try {
            tomcat.start();
            String base = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort();
            var missingAuth = sendHttp(base + "/conformities", "GET", null, null, null);
            assertTrue(missingAuth.statusCode() == 401 || missingAuth.statusCode() == 403);
            var invalidAuth = sendHttp(base + "/conformities", "GET", null, null, "invalid-local-test");
            assertTrue(invalidAuth.statusCode() == 401 || invalidAuth.statusCode() == 403);
            var created = sendHttp(base + "/conformities", "POST",
                    "{\"collaboratorId\":2,\"nrId\":10,\"expiresAt\":null}".getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json", "valid-local-test");
            assertEquals(201, created.statusCode(), created.body());
            var json = tools.jackson.databind.json.JsonMapper.builder().build();
            long id = json.readTree(created.body()).path("data").path("id").longValue();
            String resource = base + "/conformities/" + id;
            assertEquals(200, sendHttp(base + "/conformities", "GET", null, null, "valid-local-test").statusCode());
            var details = sendHttp(resource, "GET", null, null, "valid-local-test");
            assertEquals(200, details.statusCode()); assertFalse(details.body().contains("firebaseUid")); assertFalse(details.body().contains("password"));
            assertEquals(400, sendHttp(resource, "PUT", "{\"nrId\":10}".getBytes(), "application/json", "valid-local-test").statusCode());
            assertEquals(200, sendHttp(resource, "PUT", "{\"nrId\":10,\"expiresAt\":\"2030-01-01\"}".getBytes(), "application/json", "valid-local-test").statusCode());
            assertEquals(200, sendHttp(resource + "/applicability", "PATCH", "{\"applicable\":false}".getBytes(), "application/json", "valid-local-test").statusCode());
            var excel = ConformityTestSpreadsheets.file(false, new Object[]{2D, 10, "2031-01-01"});
            var multipart = new java.io.ByteArrayOutputStream();
            multipart.write("--conformity-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.xlsx\"\r\nContent-Type: application/octet-stream\r\n\r\n".getBytes());
            multipart.write(excel.getBytes()); multipart.write("\r\n--conformity-boundary--\r\n".getBytes());
            var imported = sendHttp(base + "/conformities/import", "POST", multipart.toByteArray(),
                    "multipart/form-data; boundary=conformity-boundary", "valid-local-test");
            assertEquals(200, imported.statusCode(), imported.body());
            assertEquals(1, json.readTree(imported.body()).path("data").path("inserted").intValue());
            assertEquals(409, sendHttp(base + "/conformities", "POST", "{\"collaboratorId\":2,\"nrId\":10}".getBytes(), "application/json", "valid-local-test").statusCode());
            assertFalse(service.findById(id, principal).applicable());
        } finally {
            tomcat.stop(); tomcat.destroy(); web.close();
        }
    }

    record HttpResult(int statusCode, String body) { }
    static HttpResult sendHttp(String url, String method, byte[] body, String contentType, String token) throws Exception {
        var uri = java.net.URI.create(url);
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(uri.getHost(), uri.getPort()), 20000);
            socket.setSoTimeout(20000);
            // HTTP/1.0 uses a closing response, avoiding chunked encoding and JDK selector pipes on Windows.
            String headers = method + " " + uri.getRawPath() + " HTTP/1.0\r\nHost: " + uri.getHost()
                    + "\r\nConnection: close\r\nContent-Length: " + (body == null ? 0 : body.length) + "\r\n";
            if (contentType != null) headers += "Content-Type: " + contentType + "\r\n";
            if (token != null) headers += "Authorization: Bearer " + token + "\r\n";
            socket.getOutputStream().write((headers + "\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            if (body != null) socket.getOutputStream().write(body);
            socket.getOutputStream().flush();
            String reply = new String(socket.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            int separator = reply.indexOf("\r\n\r\n");
            return new HttpResult(Integer.parseInt(reply.substring(0, reply.indexOf("\r\n")).split(" ")[1]), reply.substring(separator + 4));
        }
    }
    @Configuration
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @Import({com.astro.api.auth.security.SecurityConfig.class, com.astro.api.config.CorsConfig.class,
            com.astro.api.conformity.controller.ConformityController.class, com.astro.api.common.handler.GlobalExceptionHandler.class})
    static class HttpConfig {
        @Bean org.springframework.web.multipart.support.StandardServletMultipartResolver multipartResolver() {
            return new org.springframework.web.multipart.support.StandardServletMultipartResolver();
        }
        @Bean com.astro.api.auth.service.FirebaseTokenService firebaseTokenService() { return org.mockito.Mockito.mock(com.astro.api.auth.service.FirebaseTokenService.class); }
        @Bean com.astro.api.auth.service.AuthorizationService authorizationService() { return org.mockito.Mockito.mock(com.astro.api.auth.service.AuthorizationService.class); }
        @Bean FirebaseAuthenticationFilter firebaseAuthenticationFilter(com.astro.api.auth.service.FirebaseTokenService tokens,
                com.astro.api.auth.service.AuthorizationService authorization) { return new FirebaseAuthenticationFilter(tokens, authorization); }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {"com.astro.api.user.repository", "com.astro.api.conformity.repository"},
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = com.astro.api.conformity.repository.NrDocumentRepository.class))
    @Import({ConformityService.class, ConformityMapper.class,
            com.astro.api.conformity.service.ConformityImportService.class,
            com.astro.api.conformity.service.ConformitySpreadsheetReader.class})
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
