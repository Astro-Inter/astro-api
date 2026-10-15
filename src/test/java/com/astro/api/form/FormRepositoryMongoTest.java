package com.astro.api.form;

import com.astro.api.form.model.*;
import com.astro.api.form.repository.*;
import com.astro.api.form.service.FormAccessService.Scope;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "FORM_TEST_MONGODB_URI", matches = "mongodb://(127\\.0\\.0\\.1|localhost):[0-9]+")
class FormRepositoryMongoTest {
    static MongoClient client;
    static MongoTemplate mongo;
    static FormManagementRepository repository;
    static final String DATABASE = "forms_test_" + UUID.randomUUID().toString().replace("-", "");

    @BeforeAll static void connect() {
        client = MongoClients.create(System.getenv("FORM_TEST_MONGODB_URI"));
        mongo = new MongoTemplate(client, DATABASE);
        repository = new FormManagementRepository(mongo);
    }

    @AfterAll static void close() {
        if (mongo != null && DATABASE.matches("forms_test_[a-f0-9]{32}")) mongo.getDb().drop();
        if (client != null) client.close();
    }

    @BeforeEach void clear() { mongo.dropCollection(Form.class); mongo.dropCollection("sequencias"); }

    static Form form(long id, long workspace, long sender, long recipient, FormStatus status) {
        Form form = new Form(); form.setId(id); form.setWorkspaceId(workspace); form.setSenderManagerId(sender);
        form.setManagerId(recipient); form.setStatus(status); form.setTitle("Clima");
        form.setCreatedAt(Instant.parse("2026-10-15T12:00:00Z"));
        Form.FormQuestion question = new Form.FormQuestion(); question.setId(1L); question.setAnswer(List.of());
        form.setQuestions(List.of(question));
        return form;
    }

    @Test void scopesDraftsAndRecipientsAndUsesStablePaginationAndLiteralSearch() {
        mongo.insert(form(1, 1, 10, 20, FormStatus.RASCUNHO));
        mongo.insert(form(2, 1, 30, 10, FormStatus.RASCUNHO));
        mongo.insert(form(3, 1, 30, 10, FormStatus.PENDENTE));
        mongo.insert(form(4, 1, 30, 20, FormStatus.CONCLUIDO));
        mongo.insert(form(5, 2, 10, 20, FormStatus.PENDENTE));
        var manager = new Scope(10L, 1L, false);
        var result = repository.findAll(manager, "clima", null, 0, 1);
        assertEquals(2, result.total()); assertEquals(3L, result.forms().getFirst().getId());
        assertEquals(1, result.forms().getFirst().getQuestions().size());
        assertNull(result.forms().getFirst().getQuestions().getFirst().getAnswer());
        assertEquals(1L, repository.findAll(manager, null, null, 1, 1).forms().getFirst().getId());
        assertEquals(0, repository.findAll(manager, ".*", null, 0, 20).total());
        assertEquals(4, repository.findAll(new Scope(99L, 1L, true), null, null, 0, 20).total());
        assertEquals(1, repository.findAll(manager, null, FormStatus.PENDENTE, 0, 20).total());
    }

    @Test void homeFindsOnlyPublishedPendingFormsIncludingThoseWithNoRequiredQuestions() {
        mongo.insert(form(1, 1, 10, 20, FormStatus.RASCUNHO));
        mongo.insert(form(2, 1, 10, 20, FormStatus.CONCLUIDO));
        Form pending = form(3, 1, 10, 20, FormStatus.PENDENTE); pending.setQuestions(List.of()); mongo.insert(pending);
        var home = new MongoRepositoryFactory(mongo).getRepository(FormRepository.class);
        assertEquals(List.of(3L), home.findTop3PendingByManagerId(20L).stream().map(Form::getId).toList());
    }

    @Test void detailHidesOtherWorkspacesAndUnpublishedDraftsButPreservesOrderAndConfiguration() {
        Form value = form(1, 1, 10, 20, FormStatus.RASCUNHO);
        value.getQuestions().getFirst().setType("foto");
        value.getQuestions().getFirst().setMaxFiles(3);
        mongo.insert(value);
        assertTrue(repository.findById(1L, new Scope(20L, 1L, false)).isEmpty());
        assertTrue(repository.findById(1L, new Scope(10L, 2L, true)).isEmpty());
        assertEquals(3, repository.findById(1L, new Scope(10L, 1L, false)).orElseThrow()
                .getQuestions().getFirst().getMaxFiles());
        assertTrue(repository.findById(1L, new Scope(99L, 1L, true)).isPresent());
    }

    @Test void numericSequenceStartsAboveExistingIdsAndIsUniqueUnderConcurrentInitialization() throws Exception {
        mongo.insert(form(100, 1, 10, 20, FormStatus.PENDENTE));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var calls = java.util.stream.IntStream.range(0, 32)
                    .<java.util.concurrent.Callable<Long>>mapToObj(i -> repository::nextId).toList();
            var ids = new HashSet<Long>();
            for (var future : executor.invokeAll(calls)) ids.add(future.get());
            assertEquals(32, ids.size()); assertEquals(101L, Collections.min(ids)); assertEquals(132L, Collections.max(ids));
        }
    }

    @Test void persistsTypedOptionsAndEmptyEmbeddedAnswersInASingleFormDocument() {
        Form form = new Form();
        new com.astro.api.form.mapper.FormMapper().applyContent(FormTestFixtures.content(FormStatus.PENDENTE), form);
        form.setId(repository.nextId()); form.setStatus(FormStatus.PENDENTE); form.setWorkspaceId(1L); form.setSenderManagerId(10L);
        repository.insert(form);
        var raw = mongo.getCollection("formularios").find().first();
        assertNotNull(raw); assertEquals(1L, raw.get("id_workspace"));
        var questions = raw.getList("perguntas", org.bson.Document.class);
        assertEquals(4, questions.size()); assertEquals(List.of(), questions.getFirst().get("resposta"));
        assertEquals(1L, questions.getFirst().getLong("id_pergunta"));
        assertEquals(1L, questions.get(1).getList("opcoes", org.bson.Document.class).getFirst().getLong("id_opcao"));
        assertEquals("alternativa", questions.get(1).getString("tipo"));
        assertTrue(questions.get(1).getList("opcoes", org.bson.Document.class).get(1).getBoolean("outra"));
        assertEquals(java.util.Date.from(Instant.parse("2026-10-30T03:00:00Z")), raw.get("data_limite"));
        assertEquals(4, repository.findById(form.getId(), new Scope(10L, 1L, false)).orElseThrow().getQuestions().size());
    }
}
