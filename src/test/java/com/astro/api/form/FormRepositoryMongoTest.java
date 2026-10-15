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
import static com.astro.api.form.FormTestFixtures.form;
import static com.astro.api.form.FormTestFixtures.manager;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    @Test void updateAtomicallyChecksVersionPermissionsAndResponseState() throws Exception {
        Form value = form(1, 1, 10, 20, FormStatus.PENDENTE); value.setVersion(0L); mongo.insert(value);
        var scope = new Scope(10L, 1L, false);
        Form first = repository.findById(1L, scope).orElseThrow(); first.setTitle("Primeira edição");
        Form second = repository.findById(1L, scope).orElseThrow(); second.setTitle("Segunda edição");
        assertTrue(repository.update(first, new Scope(20L, 1L, false), 0L).isEmpty());
        assertTrue(repository.update(first, new Scope(10L, 2L, true), 0L).isEmpty());
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(
                    () -> repository.update(first, scope, 0L).isPresent(),
                    () -> repository.update(second, scope, 0L).isPresent()));
            int successful = 0;
            for (var result : results) if ((Boolean) result.get()) successful++;
            assertEquals(1, successful);
        }
        Form saved = repository.findById(1L, scope).orElseThrow(); assertEquals(1L, saved.getVersion());
        // Simulate a mobile submission after the web loaded the current version.
        mongo.getCollection("formularios").updateOne(new org.bson.Document("_id", 1L),
                new org.bson.Document("$set", new org.bson.Document("status", "CONCLUIDO")));
        assertTrue(repository.update(saved, scope, 1L).isEmpty());
        assertEquals(FormStatus.CONCLUIDO, repository.findById(1L, scope).orElseThrow().getStatus());
    }

    @Test void updateDoesNotEraseAnswersEvenWhenTheStatusHasNotBeenConcluded() {
        Form value = form(1, 1, 10, 20, FormStatus.PENDENTE); value.setVersion(0L);
        value.getQuestions().getFirst().setAnswer(List.of("Resposta")); mongo.insert(value);
        assertTrue(repository.update(value, new Scope(10L, 1L, false), 0L).isEmpty());
        assertEquals(List.of("Resposta"), repository.findById(1L, new Scope(10L, 1L, false)).orElseThrow()
                .getQuestions().getFirst().getAnswer());
    }

    @Test void deletionChecksOwnershipWorkspaceVersionAndCompletionAtomically() {
        Form value = form(1, 1, 10, 20, FormStatus.PENDENTE); value.setVersion(0L); mongo.insert(value);
        assertFalse(repository.delete(value, new Scope(20L, 1L, false)));
        assertFalse(repository.delete(value, new Scope(10L, 2L, true)));
        mongo.getCollection("formularios").updateOne(new org.bson.Document("_id", 1L),
                new org.bson.Document("$set", new org.bson.Document("versao", 1L)));
        assertFalse(repository.delete(value, new Scope(10L, 1L, false)));
        value.setVersion(1L);
        mongo.getCollection("formularios").updateOne(new org.bson.Document("_id", 1L),
                new org.bson.Document("$set", new org.bson.Document("status", "CONCLUIDO")));
        assertFalse(repository.delete(value, new Scope(99L, 1L, true)));
        assertNotNull(mongo.findById(1L, Form.class));
        mongo.getCollection("formularios").updateOne(new org.bson.Document("_id", 1L),
                new org.bson.Document("$set", new org.bson.Document("status", "PENDENTE")));
        assertTrue(repository.delete(value, new Scope(99L, 1L, true)));
        assertNull(mongo.findById(1L, Form.class));
    }

    @Test void publicationAllowsOneTransitionAndRejectsStaleEditsDeletesAndForeignManagers() {
        Form draft = form(1, 1, 10, 20, FormStatus.RASCUNHO); draft.setVersion(0L); mongo.insert(draft);
        assertTrue(repository.publish(draft, new Scope(20L, 1L, false)).isEmpty());
        assertTrue(repository.publish(draft, new Scope(10L, 2L, true)).isEmpty());
        var saved = repository.publish(draft, new Scope(99L, 1L, true)).orElseThrow();
        assertEquals(FormStatus.PENDENTE, saved.getStatus()); assertEquals(1L, saved.getVersion());
        assertTrue(saved.getQuestions().getFirst().getAnswer().isEmpty());
        assertTrue(repository.publish(draft, new Scope(10L, 1L, false)).isEmpty());
        assertTrue(repository.update(draft, new Scope(10L, 1L, false), 0L).isEmpty());
        assertFalse(repository.delete(draft, new Scope(10L, 1L, false)));
        assertTrue(repository.findById(1L, new Scope(20L, 1L, false)).isPresent());
    }

    @Test void httpWorkflowPersistsDraftEditingPublicationAndAdministrativeRulesInMongo() throws Exception {
        var users = mock(com.astro.api.user.repository.UserRepository.class);
        var units = mock(com.astro.api.unit.repository.UnitRepository.class);
        var nrs = mock(com.astro.api.conformity.repository.NrCatalogRepository.class);
        when(users.findByFirebaseUid("sender")).thenReturn(Optional.of(manager(10, 1, com.astro.api.user.model.UserType.GESTOR)));
        when(users.findByFirebaseUid("recipient")).thenReturn(Optional.of(manager(20, 1, com.astro.api.user.model.UserType.GESTOR)));
        when(users.findByFirebaseUid("owner")).thenReturn(Optional.of(manager(99, 1, com.astro.api.user.model.UserType.GESTOR_WORKSPACE)));
        when(users.findByFirebaseUid("outsider")).thenReturn(Optional.of(manager(30, 2, com.astro.api.user.model.UserType.GESTOR_WORKSPACE)));
        when(users.findById(20L)).thenReturn(Optional.of(manager(20, 1, com.astro.api.user.model.UserType.GESTOR)));
        var service = new com.astro.api.form.service.FormService(new MongoRepositoryFactory(mongo).getRepository(FormRepository.class),
                repository, new com.astro.api.form.service.FormAccessService(users), new com.astro.api.form.mapper.FormMapper(),
                FormTestFixtures.validator(), new com.astro.api.form.service.FormReferenceService(users, units, nrs));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new com.astro.api.form.controller.FormController(service))
                .setControllerAdvice(new com.astro.api.common.handler.GlobalExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build();
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        try {
            authenticate("sender", com.astro.api.auth.security.Role.GESTOR);
            mvc.perform(post("/forms").contentType("application/json").content("{\"status\":\"RASCUNHO\"}"))
                    .andExpect(status().isCreated());
            Long id = mongo.findAll(Form.class).getFirst().getId();
            String url = "/forms/" + id;
            mvc.perform(post(url + "/publish")).andExpect(status().isBadRequest());
            mvc.perform(put(url).contentType("application/json").content(json.writeValueAsString(FormTestFixtures.content(FormStatus.RASCUNHO))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.perguntas.length()").value(4));
            authenticate("recipient", com.astro.api.auth.security.Role.GESTOR);
            mvc.perform(get(url)).andExpect(status().isNotFound());
            authenticate("sender", com.astro.api.auth.security.Role.GESTOR);
            mvc.perform(post(url + "/publish")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDENTE"));
            mvc.perform(post(url + "/publish")).andExpect(status().isConflict());
            mvc.perform(get("/forms").param("status", "PENDENTE").param("search", "Inspeção"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
            authenticate("recipient", com.astro.api.auth.security.Role.GESTOR);
            mvc.perform(get(url)).andExpect(status().isOk());
            mvc.perform(put(url).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete(url)).andExpect(status().isForbidden());
            authenticate("outsider", com.astro.api.auth.security.Role.GESTOR_WORKSPACE);
            mvc.perform(get(url)).andExpect(status().isNotFound());
            mvc.perform(delete(url)).andExpect(status().isNotFound());
            authenticate("owner", com.astro.api.auth.security.Role.GESTOR_WORKSPACE);
            mvc.perform(put(url).contentType("application/json").content(json.writeValueAsString(FormTestFixtures.content(FormStatus.PENDENTE))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.id_gestor_remetente").value(10));
            // Simulate the future mobile completion without adding response endpoints to this scope.
            mongo.getCollection("formularios").updateOne(new org.bson.Document("_id", id), new org.bson.Document("$set",
                    new org.bson.Document("status", "CONCLUIDO").append("perguntas.0.resposta", List.of("Resposta única"))));
            mvc.perform(delete(url)).andExpect(status().isConflict());
            mvc.perform(put(url).contentType("application/json").content("{}"))
                    .andExpect(status().isConflict());
            mvc.perform(get(url)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.perguntas[0].resposta[0]").value("Resposta única"));
            mvc.perform(post("/forms").contentType("application/json").content("{\"status\":\"RASCUNHO\"}"))
                    .andExpect(status().isCreated());
            Long removable = mongo.findAll(Form.class).stream().map(Form::getId).filter(value -> !value.equals(id)).findFirst().orElseThrow();
            mvc.perform(delete("/forms/" + removable)).andExpect(status().isNoContent());
            assertEquals(1, mongo.count(new org.springframework.data.mongodb.core.query.Query(), Form.class));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String uid, com.astro.api.auth.security.Role role) {
        var principal = new com.astro.api.auth.security.AuthenticatedUser(uid, uid + "@astro.com", role);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
