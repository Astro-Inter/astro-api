package com.astro.api.nrreview;

import com.astro.api.nrreview.dto.*;
import com.astro.api.nrreview.service.*;
import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "NR_TEST_MONGODB_URI", matches = "mongodb://127\\.0\\.0\\.1:27019/.*")
@EnabledIfEnvironmentVariable(named = "NR_TEST_POSTGRES_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:5439/astro_nr_local")
class NrSqlCatalogIntegrationTest {
    static final String ISOLATED = "nr_sql_test_" + UUID.randomUUID().toString().replace("-", "");
    static MongoClient client;
    static MongoTemplate mongo;
    static JdbcTemplate admin;
    static JdbcTemplate jdbc;
    static NrSqlCatalogSynchronizer synchronizer;
    static NrReviewService service;
    static String procedure;

    @BeforeAll static void connect() throws Exception {
        var base = new DriverManagerDataSource(System.getenv("NR_TEST_POSTGRES_URL"), "astro_local", "astro_local_only");
        admin = new JdbcTemplate(base);
        admin.execute("CREATE SCHEMA " + ISOLATED);
        var source = new DriverManagerDataSource(System.getenv("NR_TEST_POSTGRES_URL") + "?currentSchema=" + ISOLATED, "astro_local", "astro_local_only");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE nr_catalogo(codigo_nr integer PRIMARY KEY, titulo varchar(300), revogada boolean, tempo_reciclagem_mes integer)");
        try (var input = NrSqlCatalogIntegrationTest.class.getResourceAsStream("/nrreview/ler_json_nr.sql")) {
            procedure = new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
        }
        jdbc.execute(procedure);
        client = MongoClients.create(System.getenv("NR_TEST_MONGODB_URI"));
        mongo = new MongoTemplate(client, ISOLATED);
        mongo.createCollection("nrs"); mongo.createCollection("nrs_pendentes"); mongo.createCollection(NrSqlCatalogSynchronizer.JOBS);
        synchronizer = new NrSqlCatalogSynchronizer(mongo, jdbc, new DataSourceTransactionManager(source), "nrs");
        service = new NrReviewService(client, mongo, synchronizer, "nrs", "nrs_pendentes");
    }
    @AfterAll static void close() {
        if (ISOLATED.matches("nr_sql_test_[a-f0-9]{32}")) {
            if (admin != null) admin.execute("DROP SCHEMA " + ISOLATED + " CASCADE");
            if (mongo != null) mongo.getDb().drop();
        }
        if (client != null) client.close();
    }
    @BeforeEach void seed() {
        jdbc.execute(procedure);
        jdbc.update("DELETE FROM nr_catalogo");
        for (String collection : List.of("nrs", "nrs_pendentes", NrSqlCatalogSynchronizer.JOBS)) mongo.getCollection(collection).deleteMany(new Document());
        mongo.getCollection("nrs_pendentes").insertOne(NrReviewMongoTest.document(20, "NR gerada"));
    }
    @Test void approvalCallsActualProcedureWithCorrectedFieldsAfterMongoCommit() {
        var pending = service.get(20, true);
        var result = service.approve(20, new NrApprovalRequest(pending.revision(), "absent", new NrContent("Nome com 'aspas'", "Descrição", "Objetivo", "Aplicabilidade", "Empresa", true, 24)));
        assertTrue(result.sqlSynchronized());
        assertEquals("Nome com 'aspas'", jdbc.queryForObject("SELECT titulo FROM nr_catalogo WHERE codigo_nr=20", String.class));
        assertEquals(24, jdbc.queryForObject("SELECT tempo_reciclagem_mes FROM nr_catalogo WHERE codigo_nr=20", Integer.class));
        assertEquals(true, jdbc.queryForObject("SELECT revogada FROM nr_catalogo WHERE codigo_nr=20", Boolean.class));
        assertEquals(0, mongo.getCollection("nrs_pendentes").countDocuments());
        assertEquals(0, mongo.getCollection(NrSqlCatalogSynchronizer.JOBS).countDocuments());
        assertThrows(com.astro.api.common.exception.ConflictException.class, () -> service.approve(20, new NrApprovalRequest(pending.revision(), "absent", null)));
    }
    @Test void procedureFailurePreservesApprovalAndDurableJobThenRetryUpdatesSql() {
        jdbc.execute("DROP PROCEDURE ler_json_nr(text)");
        var pending = service.get(20, true);
        var result = service.approve(20, new NrApprovalRequest(pending.revision(), "absent", null));
        assertFalse(result.sqlSynchronized());
        assertEquals("NR gerada", service.get(20, false).document().get("nome"));
        assertEquals(0, mongo.getCollection("nrs_pendentes").countDocuments());
        assertEquals(1, mongo.getCollection(NrSqlCatalogSynchronizer.JOBS).countDocuments());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM nr_catalogo", Integer.class));
        jdbc.execute(procedure);
        synchronizer.retryPending();
        assertEquals("NR gerada", jdbc.queryForObject("SELECT titulo FROM nr_catalogo WHERE codigo_nr=20", String.class));
        assertEquals(0, mongo.getCollection(NrSqlCatalogSynchronizer.JOBS).countDocuments());
    }
    @Test void retriesReadLatestPublishedVersionAndDoNotTouchOtherNorms() {
        jdbc.update("INSERT INTO nr_catalogo VALUES(1, 'Preservada', false, 12)");
        jdbc.execute("DROP PROCEDURE ler_json_nr(text)");
        var pending = service.get(20, true);
        service.approve(20, new NrApprovalRequest(pending.revision(), "absent", null));
        var current = service.get(20, false);
        service.update(20, new NrUpdateRequest(current.revision(), new NrContent("Edição mais recente", "Descrição", "Objetivo", "Aplicabilidade", "Empresa", false, 36)));
        jdbc.execute(procedure);
        synchronizer.retryPending();
        synchronizer.retryPending();
        assertEquals("Edição mais recente", jdbc.queryForObject("SELECT titulo FROM nr_catalogo WHERE codigo_nr=20", String.class));
        assertEquals("Preservada", jdbc.queryForObject("SELECT titulo FROM nr_catalogo WHERE codigo_nr=1", String.class));
    }
}
