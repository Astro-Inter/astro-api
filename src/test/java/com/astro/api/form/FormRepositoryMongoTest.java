package com.astro.api.form;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import com.astro.api.auth.security.AuthenticatedUser;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import com.astro.api.conformity.repository.NrCatalogRepository;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.unit.repository.UnitLocationProjection;
import com.astro.api.user.repository.UserRepository;
import org.bson.Document;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import com.astro.api.form.service.FormService;
import com.astro.api.form.service.FormLocationService;
import com.astro.api.form.service.FormAccessService;
import com.astro.api.form.model.*;
import com.astro.api.form.repository.*;
import com.astro.api.form.model.FormScope;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static com.astro.api.form.FormTestFixtures.form;
import static com.astro.api.form.FormTestFixtures.formId;
import static com.astro.api.form.FormTestFixtures.manager;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "FORM_TEST_MONGODB_URI", matches = "mongodb://(127\\.0\\.0\\.1|localhost):[0-9]+")
class FormRepositoryMongoTest {
    static MongoClient client;
    static MongoTemplate mongo;
    static FormRepository repository;
    static final FormAccessService formAccessService = new FormAccessService(mock(UserRepository.class));
    static final String DATABASE = "forms_test_" + UUID.randomUUID().toString().replace("-", "");

    @BeforeAll static void connect() {
        client = MongoClients.create(System.getenv("FORM_TEST_MONGODB_URI"));
        mongo = new MongoTemplate(client, DATABASE);
        repository = new MongoRepositoryFactory(mongo).getRepository(FormRepository.class,
                org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments.just(new FormRepositoryCustomImpl(mongo)));
    }

    @AfterAll static void close() {
        if (mongo != null && DATABASE.matches("forms_test_[a-f0-9]{32}")) mongo.getDb().drop();
        if (client != null) client.close();
    }

    @BeforeEach void clear() { mongo.dropCollection(Form.class); }

    @Test void scopesDraftsAndRecipientsAndUsesStablePaginationAndLiteralSearch() {
        mongo.insert(form(1, 1, 10, 20, FormStatus.DRAFT));
        mongo.insert(form(2, 1, 30, 10, FormStatus.DRAFT));
        mongo.insert(form(3, 1, 30, 10, FormStatus.PENDING));
        mongo.insert(form(4, 1, 30, 20, FormStatus.COMPLETED));
        mongo.insert(form(5, 2, 10, 20, FormStatus.PENDING));
        FormScope manager = new FormScope(10L, 1L, false);
        FormRepositoryCustom.Listing result = repository.findAll(formAccessService.readFilter(manager), "clima", null, 0, 1);
        assertEquals(2, result.total()); assertEquals(formId(3), result.forms().getFirst().getId());
        assertEquals(1, result.forms().getFirst().getQuestions().size());
        assertNull(result.forms().getFirst().getQuestions().getFirst().getAnswer());
        assertEquals(formId(1), repository.findAll(formAccessService.readFilter(manager), null, null, 1, 1).forms().getFirst().getId());
        assertEquals(0, repository.findAll(formAccessService.readFilter(manager), ".*", null, 0, 20).total());
        assertEquals(4, repository.findAll(formAccessService.readFilter(new FormScope(99L, 1L, true)), null, null, 0, 20).total());
        assertEquals(1, repository.findAll(formAccessService.readFilter(manager), null, FormStatus.PENDING, 0, 20).total());
    }

    @Test void homeFindsOnlyPublishedPendingFormsIncludingThoseWithNoRequiredQuestions() {
        mongo.insert(form(1, 1, 10, 20, FormStatus.DRAFT));
        mongo.insert(form(2, 1, 10, 20, FormStatus.COMPLETED));
        Form pending = form(3, 1, 10, 20, FormStatus.PENDING); pending.setQuestions(List.of()); mongo.insert(pending);
        FormRepository home = repository;
        assertEquals(List.of(formId(3)), home.findTop3PendingByManagerId(20L).stream().map(Form::getId).toList());
    }

    @Test void detailHidesOtherWorkspacesAndUnpublishedDraftsButPreservesOrderAndConfiguration() {
        Form value = form(1, 1, 10, 20, FormStatus.DRAFT);
        value.getQuestions().getFirst().setType("foto");
        value.getQuestions().getFirst().setMaxFiles(3);
        mongo.insert(value);
        assertTrue(repository.findById(formId(1), formAccessService.readFilter(new FormScope(20L, 1L, false))).isEmpty());
        assertTrue(repository.findById(formId(1), formAccessService.readFilter(new FormScope(10L, 2L, true))).isEmpty());
        assertEquals(3, repository.findById(formId(1), formAccessService.readFilter(new FormScope(10L, 1L, false))).orElseThrow()
                .getQuestions().getFirst().getMaxFiles());
        assertTrue(repository.findById(formId(1), formAccessService.readFilter(new FormScope(99L, 1L, true))).isPresent());
    }

    @Test void generatesUuidStringsForConcurrentCreationsWithoutAnAuxiliaryCollection() throws Exception {
        FormAccessService access = mock(com.astro.api.form.service.FormAccessService.class);
        when(access.requireManager(any())).thenReturn(new FormScope(10L, 1L, false));
        FormService service = new com.astro.api.form.service.FormService(repository, access,
                new com.astro.api.form.mapper.FormMapper(), FormTestFixtures.validator(),
                mock(com.astro.api.form.service.FormReferenceService.class), mock(UserRepository.class));
        try (ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            List<Callable<String>> calls = java.util.stream.IntStream.range(0, 32)
                    .<java.util.concurrent.Callable<String>>mapToObj(index -> () ->
                            service.create(FormTestFixtures.content(FormStatus.PENDING), null).id()).toList();
            Set<String> ids = new HashSet<String>();
            for (Future<String> future : executor.invokeAll(calls)) {
                String id = future.get();
                assertEquals(4, UUID.fromString(id).version());
                assertTrue(ids.add(id));
                assertTrue(repository.findById(id).isPresent());
            }
            assertEquals(32, ids.size());
            assertEquals(List.of("forms"), mongo.getDb().listCollectionNames().into(new ArrayList<>()));
        }
    }

    @Test void persistsTypedOptionsAndEmptyEmbeddedAnswersInASingleFormDocument() {
        Form form = new Form();
        form.setId(UUID.randomUUID().toString());
        new com.astro.api.form.mapper.FormMapper().applyContent(FormTestFixtures.content(FormStatus.PENDING), form);
        form.setStatus(FormStatus.PENDING); form.setWorkspaceId(1L); form.setSenderManagerId(10L);
        form.setUnitId(3L); form.setVersion(0L);
        repository.insert(form);
        Document raw = mongo.getCollection("forms").find().first();
        assertNotNull(raw);
        assertInstanceOf(String.class, raw.get("_id"));
        assertEquals(form.getId(), raw.getString("_id"));
        assertEquals(1, raw.getInteger("id_workspace"));
        assertEquals(10, raw.getInteger("id_gestor_remetente"));
        assertEquals(20, raw.getInteger("id_gestor"));
        assertEquals(3, raw.getInteger("id_unidade"));
        assertEquals(0, raw.getInteger("versao"));
        assertEquals("PENDENTE", raw.getString("status"));
        assertEquals(FormStatus.PENDING, repository.findById(form.getId()).orElseThrow().getStatus());
        List<Document> questions = raw.getList("perguntas", org.bson.Document.class);
        assertEquals(4, questions.size()); assertEquals(List.of(), questions.getFirst().get("resposta"));
        assertEquals(1, questions.getFirst().getInteger("id_pergunta"));
        assertEquals(1, questions.get(1).getList("opcoes", org.bson.Document.class).getFirst().getInteger("id_opcao"));
        assertEquals("alternativa", questions.get(1).getString("tipo"));
        assertTrue(questions.get(1).getList("opcoes", org.bson.Document.class).get(1).getBoolean("outra"));
        assertEquals(java.util.Date.from(Instant.parse("2026-10-30T03:00:00Z")), raw.get("data_limite"));
        assertEquals(4, repository.findById(form.getId(), formAccessService.readFilter(new FormScope(10L, 1L, false))).orElseThrow().getQuestions().size());
    }

    @ParameterizedTest
    @ValueSource(longs = {-2147483648L, 2147483647L, -2147483649L, 2147483648L, Long.MIN_VALUE, Long.MAX_VALUE})
    void storesSmallIntegersWithoutLongWrappersAndPreservesLargeIdentifiers(long value) {
        Form form = form(1, value, value, value, FormStatus.DRAFT);
        form.setUnitId(value); form.setVersion(value);
        Form.FormQuestion question = form.getQuestions().getFirst(); question.setId(value);
        Form.FormOption option = new Form.FormOption(); option.setId(value);
        question.setOptions(List.of(option));
        repository.insert(form);
        Document raw = mongo.getCollection("forms").find().first(); assertNotNull(raw);
        Class<? extends Number> expectedType = value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                ? Integer.class : Long.class;
        for (String field : List.of("id_workspace", "id_gestor", "id_gestor_remetente", "id_unidade", "versao")) {
            Number storedNumber = assertInstanceOf(expectedType, raw.get(field));
            assertEquals(value, storedNumber.longValue());
        }
        Document storedQuestion = raw.getList("perguntas", Document.class).getFirst();
        Number questionId = assertInstanceOf(expectedType, storedQuestion.get("id_pergunta"));
        Number optionId = assertInstanceOf(expectedType, storedQuestion.getList("opcoes", Document.class).getFirst().get("id_opcao"));
        assertEquals(value, questionId.longValue()); assertEquals(value, optionId.longValue());
        Form restored = repository.findById(form.getId()).orElseThrow();
        assertEquals(value, restored.getWorkspaceId()); assertEquals(value, restored.getManagerId());
        assertEquals(value, restored.getSenderManagerId()); assertEquals(value, restored.getUnitId());
        assertEquals(value, restored.getVersion());
        assertEquals(value, restored.getQuestions().getFirst().getId());
        assertEquals(value, restored.getQuestions().getFirst().getOptions().getFirst().getId());
    }

    @Test void readsLegacyLongsAndWritesPlainNumbersDuringConditionalEditingAndPublication() {
        Document legacy = new Document("_id", formId(1)).append("id_workspace", 1L)
                .append("id_gestor_remetente", 10L).append("id_gestor", 20L).append("id_unidade", 3L)
                .append("status", "RASCUNHO").append("versao", 0L)
                .append("perguntas", List.of(new Document("id_pergunta", 1L).append("tipo", "alternativa")
                        .append("resposta", List.of()).append("opcoes", List.of(new Document("id_opcao", 1L)))));
        mongo.getCollection("forms").insertOne(legacy);
        FormScope scope = new FormScope(10L, 1L, false);
        Form saved = repository.findById(formId(1), formAccessService.readFilter(scope)).orElseThrow();
        assertEquals(1L, saved.getQuestions().getFirst().getId());
        assertEquals(1L, saved.getQuestions().getFirst().getOptions().getFirst().getId());
        Form updated = repository.update(saved, formAccessService.writeCondition(saved, scope)).orElseThrow();
        Document raw = mongo.getCollection("forms").find().first(); assertNotNull(raw);
        assertEquals(20, raw.getInteger("id_gestor")); assertEquals(3, raw.getInteger("id_unidade"));
        assertEquals(1, raw.getInteger("versao"));
        assertEquals(1, raw.getList("perguntas", Document.class).getFirst().getInteger("id_pergunta"));
        assertEquals(1, raw.getList("perguntas", Document.class).getFirst().getList("opcoes", Document.class).getFirst().getInteger("id_opcao"));
        Form published = repository.updateStatus(formAccessService.writeCondition(updated, scope), FormStatus.PENDING, Instant.now()).orElseThrow();
        assertEquals(2L, published.getVersion());
        Document publishedRaw = mongo.getCollection("forms").find().first(); assertNotNull(publishedRaw);
        assertEquals(2, publishedRaw.getInteger("versao"));
        assertTrue(repository.deleteIfUnanswered(formAccessService.writeCondition(published, scope)));
    }

    @Test void updateAtomicallyChecksVersionPermissionsAndResponseState() throws Exception {
        Form value = form(1, 1, 10, 20, FormStatus.PENDING); value.setVersion(0L); mongo.insert(value);
        FormScope scope = new FormScope(10L, 1L, false);
        Form first = repository.findById(formId(1), formAccessService.readFilter(scope)).orElseThrow(); first.setTitle("Primeira edição");
        Form second = repository.findById(formId(1), formAccessService.readFilter(scope)).orElseThrow(); second.setTitle("Segunda edição");
        assertTrue(repository.update(first, formAccessService.writeCondition(first, new FormScope(20L, 1L, false))).isEmpty());
        assertTrue(repository.update(first, formAccessService.writeCondition(first, new FormScope(10L, 2L, true))).isEmpty());
        try (ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = executor.invokeAll(List.of(
                    () -> repository.update(first, formAccessService.writeCondition(first, scope)).isPresent(),
                    () -> repository.update(second, formAccessService.writeCondition(second, scope)).isPresent()));
            int successful = 0;
            for (Future<Boolean> result : results) if (result.get()) successful++;
            assertEquals(1, successful);
        }
        Form saved = repository.findById(formId(1), formAccessService.readFilter(scope)).orElseThrow(); assertEquals(1L, saved.getVersion());
        // Simulate a mobile submission after the web loaded the current version.
        mongo.getCollection("forms").updateOne(new org.bson.Document("_id", formId(1)),
                new org.bson.Document("$set", new org.bson.Document("status", "CONCLUIDO")));
        assertTrue(repository.update(saved, formAccessService.writeCondition(saved, scope)).isEmpty());
        assertEquals(FormStatus.COMPLETED, repository.findById(formId(1), formAccessService.readFilter(scope)).orElseThrow().getStatus());
    }

    @Test void updateDoesNotEraseAnswersEvenWhenTheStatusHasNotBeenConcluded() {
        Form value = form(1, 1, 10, 20, FormStatus.PENDING); value.setVersion(0L);
        value.getQuestions().getFirst().setAnswer(List.of("Resposta")); mongo.insert(value);
        assertTrue(repository.update(value, formAccessService.writeCondition(value, new FormScope(10L, 1L, false))).isEmpty());
        assertEquals(List.of("Resposta"), repository.findById(formId(1), formAccessService.readFilter(new FormScope(10L, 1L, false))).orElseThrow()
                .getQuestions().getFirst().getAnswer());
    }

    @Test void deletionChecksOwnershipWorkspaceVersionAndCompletionAtomically() {
        Form value = form(1, 1, 10, 20, FormStatus.PENDING); value.setVersion(0L); mongo.insert(value);
        assertFalse(repository.deleteIfUnanswered(formAccessService.writeCondition(value, new FormScope(20L, 1L, false))));
        assertFalse(repository.deleteIfUnanswered(formAccessService.writeCondition(value, new FormScope(10L, 2L, true))));
        mongo.getCollection("forms").updateOne(new org.bson.Document("_id", formId(1)),
                new org.bson.Document("$set", new org.bson.Document("versao", 1L)));
        assertFalse(repository.deleteIfUnanswered(formAccessService.writeCondition(value, new FormScope(10L, 1L, false))));
        value.setVersion(1L);
        mongo.getCollection("forms").updateOne(new org.bson.Document("_id", formId(1)),
                new org.bson.Document("$set", new org.bson.Document("status", "CONCLUIDO")));
        assertFalse(repository.deleteIfUnanswered(formAccessService.writeCondition(value, new FormScope(99L, 1L, true))));
        assertNotNull(mongo.findById(formId(1), Form.class));
        mongo.getCollection("forms").updateOne(new org.bson.Document("_id", formId(1)),
                new org.bson.Document("$set", new org.bson.Document("status", "PENDENTE")));
        assertTrue(repository.deleteIfUnanswered(formAccessService.writeCondition(value, new FormScope(99L, 1L, true))));
        assertNull(mongo.findById(formId(1), Form.class));
    }

    @Test void publicationAllowsOneTransitionAndRejectsStaleEditsDeletesAndForeignManagers() {
        Form draft = form(1, 1, 10, 20, FormStatus.DRAFT); draft.setVersion(0L); mongo.insert(draft);
        assertTrue(repository.updateStatus(formAccessService.writeCondition(draft, new FormScope(20L, 1L, false)), FormStatus.PENDING, Instant.now()).isEmpty());
        assertTrue(repository.updateStatus(formAccessService.writeCondition(draft, new FormScope(10L, 2L, true)), FormStatus.PENDING, Instant.now()).isEmpty());
        Form saved = repository.updateStatus(formAccessService.writeCondition(draft, new FormScope(99L, 1L, true)), FormStatus.PENDING, Instant.now()).orElseThrow();
        assertEquals(FormStatus.PENDING, saved.getStatus()); assertEquals(1L, saved.getVersion());
        assertTrue(saved.getQuestions().getFirst().getAnswer().isEmpty());
        assertTrue(repository.updateStatus(formAccessService.writeCondition(draft, new FormScope(10L, 1L, false)), FormStatus.PENDING, Instant.now()).isEmpty());
        assertTrue(repository.update(draft, formAccessService.writeCondition(draft, new FormScope(10L, 1L, false))).isEmpty());
        assertFalse(repository.deleteIfUnanswered(formAccessService.writeCondition(draft, new FormScope(10L, 1L, false))));
        assertTrue(repository.findById(formId(1), formAccessService.readFilter(new FormScope(20L, 1L, false))).isPresent());
    }

    @Test void conditionalWritesRejectAnswersAddedAfterTheSnapshotWithoutChangingStatusOrVersion() {
        Form draft = form(1, 1, 10, 20, FormStatus.DRAFT);
        draft.setVersion(0L);
        mongo.insert(draft);
        FormWriteCondition senderCondition = formAccessService.writeCondition(draft, new FormScope(10L, 1L, false));
        FormWriteCondition workspaceCondition = formAccessService.writeCondition(draft, new FormScope(99L, 1L, true));
        mongo.getCollection("forms").updateOne(new Document("_id", draft.getId()),
                new Document("$set", new Document("perguntas.0.resposta", List.of("Resposta recebida"))));

        for (FormWriteCondition writeCondition : List.of(senderCondition, workspaceCondition)) {
            assertTrue(repository.update(draft, writeCondition).isEmpty());
            assertFalse(repository.deleteIfUnanswered(writeCondition));
            assertTrue(repository.updateStatus(writeCondition, FormStatus.PENDING, Instant.now()).isEmpty());
        }
        Form saved = repository.findById(draft.getId()).orElseThrow();
        assertEquals(FormStatus.DRAFT, saved.getStatus());
        assertEquals(0L, saved.getVersion());
        assertEquals(List.of("Resposta recebida"), saved.getQuestions().getFirst().getAnswer());
    }

    @Test void httpWorkflowPersistsDraftEditingPublicationAndAdministrativeRulesInMongo() throws Exception {
        UserRepository users = mock(com.astro.api.user.repository.UserRepository.class);
        UnitRepository units = mock(com.astro.api.unit.repository.UnitRepository.class);
        NrCatalogRepository nrs = mock(com.astro.api.conformity.repository.NrCatalogRepository.class);
        when(users.findByFirebaseUid("sender")).thenReturn(Optional.of(manager(10, 1, com.astro.api.user.model.UserType.GESTOR)));
        when(users.findByFirebaseUid("recipient")).thenReturn(Optional.of(manager(20, 1, com.astro.api.user.model.UserType.GESTOR)));
        when(users.findByFirebaseUid("owner")).thenReturn(Optional.of(manager(99, 1, com.astro.api.user.model.UserType.GESTOR_WORKSPACE)));
        when(users.findByFirebaseUid("outsider")).thenReturn(Optional.of(manager(30, 2, com.astro.api.user.model.UserType.GESTOR_WORKSPACE)));
        when(users.findById(20L)).thenReturn(Optional.of(manager(20, 1, com.astro.api.user.model.UserType.GESTOR)));
        FormService service = new com.astro.api.form.service.FormService(repository, new com.astro.api.form.service.FormAccessService(users), new com.astro.api.form.mapper.FormMapper(),
                FormTestFixtures.validator(), new com.astro.api.form.service.FormReferenceService(users, units, nrs), users);
        MockMvc mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new com.astro.api.form.controller.FormController(service))
                .setControllerAdvice(new com.astro.api.common.handler.GlobalExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build();
        JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
        try {
            authenticate("sender", com.astro.api.auth.security.Role.GESTOR);
            MvcResult creation = mvc.perform(post("/forms").contentType("application/json").content("{\"status\":\"RASCUNHO\"}"))
                    .andExpect(status().isCreated()).andReturn();
            String createdAt = json.readTree(creation.getResponse().getContentAsString()).get("data").get("createdAt").asString();
            String id = mongo.findAll(Form.class).getFirst().getId();
            String url = "/forms/" + id;
            assertEquals(4, UUID.fromString(id).version());
            mvc.perform(get("/forms/invalid")).andExpect(status().isBadRequest());
            mvc.perform(put("/forms/invalid").contentType("application/json").content("{}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(delete("/forms/invalid")).andExpect(status().isBadRequest());
            mvc.perform(post("/forms/invalid/publish")).andExpect(status().isBadRequest());
            mvc.perform(get(url)).andExpect(status().isOk()).andExpect(jsonPath("$.data.createdAt").value(createdAt));
            mvc.perform(post(url + "/publish")).andExpect(status().isBadRequest());
            mvc.perform(put(url).contentType("application/json").content(json.writeValueAsString(FormTestFixtures.content(FormStatus.DRAFT))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.questions.length()").value(4))
                    .andExpect(jsonPath("$.data.createdAt").value(createdAt));
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
            mvc.perform(put(url).contentType("application/json").content(json.writeValueAsString(FormTestFixtures.content(FormStatus.PENDING))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.senderManagerId").value(10));
            // Simulate the future mobile completion without adding response endpoints to this scope.
            mongo.getCollection("forms").updateOne(new org.bson.Document("_id", id), new org.bson.Document("$set",
                    new org.bson.Document("status", "CONCLUIDO").append("perguntas.0.resposta", List.of("Resposta única"))));
            mvc.perform(delete(url)).andExpect(status().isConflict());
            mvc.perform(put(url).contentType("application/json").content("{}"))
                    .andExpect(status().isConflict());
            mvc.perform(get(url)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.questions[0].answer[0]").value("Resposta única"));
            mvc.perform(post("/forms").contentType("application/json").content("{\"status\":\"RASCUNHO\"}"))
                    .andExpect(status().isCreated());
            String removable = mongo.findAll(Form.class).stream().map(Form::getId).filter(value -> !value.equals(id)).findFirst().orElseThrow();
            mvc.perform(delete("/forms/" + removable)).andExpect(status().isNoContent());
            assertEquals(1, mongo.count(new org.springframework.data.mongodb.core.query.Query(), Form.class));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String uid, com.astro.api.auth.security.Role role) {
        AuthenticatedUser principal = new com.astro.api.auth.security.AuthenticatedUser(uid, uid + "@astro.com", role);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test void discoversTheCustomFragmentThroughSpringDataRepositoryConfiguration() {
        try (AnnotationConfigApplicationContext context = new org.springframework.context.annotation.AnnotationConfigApplicationContext(RepositoryConfiguration.class)) {
            FormRepository configuredRepository = context.getBean(FormRepository.class);
            configuredRepository.insert(form(1, 1, 10, 20, FormStatus.DRAFT));
            assertEquals(1, configuredRepository.findAll(formAccessService.readFilter(new FormScope(10L, 1L, false)), null, FormStatus.DRAFT, 0, 20).total());
            assertEquals(FormStatus.DRAFT, configuredRepository.findById(formId(1)).orElseThrow().getStatus());
        }
    }

    @Test void storesUnitDistanceAndCoordinateSnapshotWithTheAnswers() {
        Instant respondedAt = Instant.now();
        Form form = form(1, 1, 10, 20, FormStatus.PENDING); form.setVersion(0L); form.setUnitId(3L); repository.insert(form);
        FormLocationReading reading = new FormLocationReading(-23.5, -46.6, 5.0, respondedAt);
        form.setLocationCheck(new FormLocationCheck(3L, -23.5, -46.6, 100, 0.0, reading));
        form.setLocations(List.of(reading)); form.setRespondedAt(respondedAt); form.setStatus(FormStatus.COMPLETED);
        form.getQuestions().getFirst().setAnswer(List.of("No local"));
        Form saved = repository.submitResponse(form, new FormResponseCondition(form.getId(), 1L, 20L, 0L, FormStatus.PENDING)).orElseThrow();
        assertEquals(0.0, saved.getLocationCheck().distanceMeters()); assertEquals(3L, saved.getLocationCheck().unitId());
        Document raw = mongo.getCollection("forms").find(new Document("_id", form.getId())).first(); assertNotNull(raw);
        Document check = raw.get("validacao_localizacao", Document.class);
        assertEquals(3, check.getInteger("id_unidade")); assertEquals(100, check.getInteger("raio_metros"));
        assertEquals(-46.6, check.getDouble("longitude_unidade")); assertEquals(-23.5, check.getDouble("latitude_unidade"));
        assertEquals(0.0, check.getDouble("distancia_metros")); assertFalse(check.containsKey("distanceMeters"));
        assertEquals(java.util.Date.from(respondedAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)),
                check.get("leitura", Document.class).getDate("registrado_em"));
    }

    @Test void recipientListingExcludesDraftsOtherRecipientsAndWorkspaces() {
        Form pending = form(1, 1, 10, 20, FormStatus.PENDING); pending.setNrCode(6); mongo.insert(pending);
        Form completed = form(2, 1, 10, 20, FormStatus.COMPLETED); completed.setNrCode(6); mongo.insert(completed);
        mongo.insert(form(3, 1, 10, 20, FormStatus.DRAFT));
        mongo.insert(form(4, 1, 20, 10, FormStatus.PENDING));
        mongo.insert(form(5, 2, 10, 20, FormStatus.PENDING));
        assertEquals(2, repository.findReceived(formAccessService.recipientFilter(new FormScope(20L, 1L, false)), "Clima", 6, null, 0, 20).total());
        assertEquals(1, repository.findReceived(formAccessService.recipientFilter(new FormScope(20L, 1L, false)), null, 6, FormStatus.PENDING, 0, 20).total());
        assertTrue(repository.findReceived(formAccessService.recipientFilter(new FormScope(99L, 1L, true)), null, null, null, 0, 20).forms().isEmpty());
        assertEquals(0, repository.findReceived(formAccessService.recipientFilter(new FormScope(20L, 1L, false)), ".*", null, null, 0, 20).total());
    }

    @Test void submissionAndReviewAreAtomicAndPreservePortugueseMongoFields() throws Exception {
        Form pending = form(1, 1, 10, 20, FormStatus.PENDING); pending.setVersion(0L); mongo.insert(pending);
        pending.getQuestions().getFirst().setAnswer(List.of("Resposta única"));
        pending.setLocationConsent(true);
        pending.setLocations(List.of(new FormLocationReading(-23.0, -46.0, 4.0, Instant.now())));
        pending.setRespondedAt(Instant.now());
        pending.setStatus(FormStatus.COMPLETED);
        FormResponseCondition condition = new FormResponseCondition(pending.getId(), 1L, 20L, 0L, FormStatus.PENDING);
        assertTrue(repository.submitResponse(pending, new FormResponseCondition(pending.getId(), 1L, 99L, 0L, FormStatus.PENDING)).isEmpty());
        assertTrue(repository.submitResponse(pending, new FormResponseCondition(pending.getId(), 2L, 20L, 0L, FormStatus.PENDING)).isEmpty());
        try (ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = executor.invokeAll(List.of(
                    () -> repository.submitResponse(pending, condition).isPresent(),
                    () -> repository.submitResponse(pending, condition).isPresent()));
            int successes = 0;
            for (Future<Boolean> result : results) if (result.get()) successes++;
            assertEquals(1, successes);
        }
        Form completed = repository.findById(pending.getId()).orElseThrow();
        assertEquals(FormStatus.COMPLETED, completed.getStatus()); assertEquals(1L, completed.getVersion());
        assertEquals(List.of("Resposta única"), completed.getQuestions().getFirst().getAnswer());
        Document raw = mongo.getCollection("forms").find(new Document("_id", pending.getId())).first(); assertNotNull(raw);
        assertEquals("CONCLUIDO", raw.getString("status")); assertEquals(1, raw.getInteger("versao"));
        assertTrue(raw.getBoolean("consentimento_localizacao")); assertFalse(raw.containsKey("analise"));
        assertTrue(raw.getList("localizacoes", Document.class).getFirst().containsKey("registrado_em"));
        assertFalse(repository.deleteIfUnanswered(formAccessService.writeCondition(completed, new FormScope(10L, 1L, false))));
        FormReview approved = new FormReview(FormReviewStatus.APPROVED, 10L, Instant.now(), null);
        assertTrue(repository.review(formAccessService.writeCondition(completed, new FormScope(20L, 1L, false)), approved).isEmpty());
        assertTrue(repository.review(formAccessService.writeCondition(completed, new FormScope(99L, 2L, true)), approved).isEmpty());
        FormWriteCondition reviewCondition = formAccessService.writeCondition(completed, new FormScope(10L, 1L, false));
        try (ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = executor.invokeAll(List.of(
                    () -> repository.review(reviewCondition, approved).isPresent(),
                    () -> repository.review(reviewCondition, new FormReview(FormReviewStatus.REJECTED, 10L, Instant.now(), "Foto ilegível")).isPresent()));
            int successes = 0;
            for (Future<Boolean> result : results) if (result.get()) successes++;
            assertEquals(1, successes);
        }
        Form reviewed = repository.findById(pending.getId()).orElseThrow(); assertEquals(2L, reviewed.getVersion());
        assertEquals(FormStatus.COMPLETED, reviewed.getStatus());
        Document storedReview = mongo.getCollection("forms").find(new Document("_id", pending.getId())).first().get("analise", Document.class);
        assertEquals(reviewed.getReview().status() == FormReviewStatus.REJECTED, storedReview.containsKey("descricao"));
        assertInstanceOf(Integer.class, storedReview.get("id_gestor_revisor"));
        assertFalse(storedReview.containsKey("description"));
        assertTrue(repository.review(formAccessService.writeCondition(reviewed, new FormScope(99L, 1L, true)), approved).isEmpty());
    }

    @Test void approvedReviewOmitsDescriptionAndRejectedReviewStoresItInSameDocument() {
        for (FormReviewStatus decision : FormReviewStatus.values()) {
            Form value = form(decision.ordinal() + 1, 1, 10, 20, FormStatus.COMPLETED);
            value.setVersion(1L); value.setRespondedAt(Instant.now());
            value.setReview(new FormReview(decision, 10L, Instant.now(), decision == FormReviewStatus.REJECTED ? "Foto ilegível" : null));
            repository.insert(value);
            Document raw = mongo.getCollection("forms").find(new Document("_id", value.getId())).first(); assertNotNull(raw);
            Document review = raw.get("analise", Document.class);
            assertEquals(decision.storedValue(), review.getString("status"));
            assertEquals(decision == FormReviewStatus.REJECTED, review.containsKey("descricao"));
            assertEquals(decision, repository.findById(value.getId()).orElseThrow().getReview().status());
        }
        assertEquals(2, mongo.getCollection("forms").countDocuments());
    }

    @Test void httpResponseAndReviewWorkflowUsesMongoAndTheExistingAccessRules() throws Exception {
        UserRepository users = mock(UserRepository.class);
        UnitRepository units = mock(UnitRepository.class);
        when(users.findByFirebaseUid("sender")).thenReturn(Optional.of(manager(10, 1, com.astro.api.user.model.UserType.GESTOR)));
        when(users.findByFirebaseUid("recipient")).thenReturn(Optional.of(manager(20, 1, com.astro.api.user.model.UserType.GESTOR)));
        when(users.findByFirebaseUid("owner")).thenReturn(Optional.of(manager(99, 1, com.astro.api.user.model.UserType.GESTOR_WORKSPACE)));
        when(users.findByFirebaseUid("outsider")).thenReturn(Optional.of(manager(30, 2, com.astro.api.user.model.UserType.GESTOR_WORKSPACE)));
        com.astro.api.form.service.FormImageStorageService storage = mock(com.astro.api.form.service.FormImageStorageService.class);
        FormImage image = new FormImage("test-image", "formularios/private/image.png", "camera.png", "image/png", 80);
        when(storage.upload(any(), any(), any())).thenReturn(image);
        when(storage.download(image)).thenReturn(new com.astro.api.form.dto.response.FormImageDownloadResponse(
                image.id(), "https://example.test/signed-image", Instant.now().plusSeconds(900)));
        Form pending = form(1, 1, 10, 20, FormStatus.PENDING); pending.setQuestions(List.of()); pending.setVersion(0L);
        new com.astro.api.form.mapper.FormMapper().applyContent(FormTestFixtures.content(FormStatus.PENDING), pending);
        repository.insert(pending);
        try (jakarta.validation.ValidatorFactory factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            com.astro.api.form.service.FormAnswerValidationService validation = new com.astro.api.form.service.FormAnswerValidationService(
                    List.of(new com.astro.api.form.strategy.answer.TextAnswerStrategy(), new com.astro.api.form.strategy.answer.DateAnswerStrategy(),
                            new com.astro.api.form.strategy.answer.OptionAnswerStrategy(), new com.astro.api.form.strategy.answer.PhotoAnswerStrategy()), factory.getValidator());
            com.astro.api.form.service.FormResponseService service = new com.astro.api.form.service.FormResponseService(repository,
                    new FormAccessService(users), validation, new com.astro.api.form.mapper.FormAnswerMapper(),
                    new com.astro.api.form.mapper.FormResponseMapper(new com.astro.api.form.mapper.FormMapper()), storage, users, units, factory.getValidator(), new FormLocationService(units));
            MockMvc mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                    .standaloneSetup(new com.astro.api.form.controller.FormResponseController(service))
                    .setControllerAdvice(new com.astro.api.common.handler.GlobalExceptionHandler())
                    .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build();
            String url = "/forms/" + pending.getId();
            authenticate("sender", com.astro.api.auth.security.Role.GESTOR);
            mvc.perform(get("/forms/received")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
            authenticate("owner", com.astro.api.auth.security.Role.GESTOR_WORKSPACE);
            mvc.perform(get("/forms/received/" + pending.getId())).andExpect(status().isNotFound());
            authenticate("recipient", com.astro.api.auth.security.Role.GESTOR);
            mvc.perform(get("/forms/received").param("workspaceId", "999").param("managerId", "999"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(pending.getId()));
            mvc.perform(get("/forms/received/" + pending.getId())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.locationPolicy.enforceUnitRadius").value(false));
            mvc.perform(get(url + "/responses")).andExpect(status().isConflict());
            com.astro.api.form.dto.request.FormResponseRequest request = new com.astro.api.form.dto.request.FormResponseRequest(true,
                    List.of(new com.astro.api.form.dto.request.FormLocationReadingRequest(80.0, 170.0, 5.0, Instant.now())),
                    List.of(new com.astro.api.form.dto.request.FormQuestionAnswerRequest(1L, "Condição segura", null, null, null, null),
                            new com.astro.api.form.dto.request.FormQuestionAnswerRequest(2L, null, List.of(1L), null, null, null),
                            new com.astro.api.form.dto.request.FormQuestionAnswerRequest(3L, null, null, null, null, List.of(0)),
                            new com.astro.api.form.dto.request.FormQuestionAnswerRequest(4L, null, null, null, java.time.LocalDate.of(2026, 10, 24), null)));
            byte[] body = JsonMapper.builder().build().writeValueAsBytes(request);
            org.springframework.mock.web.MockMultipartFile responsePart = new org.springframework.mock.web.MockMultipartFile(
                    "response", "response.json", "application/json", body);
            org.springframework.mock.web.MockMultipartFile photo = new org.springframework.mock.web.MockMultipartFile(
                    "files", "camera.png", "image/png", new byte[]{1});
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(url + "/responses").file(responsePart).file(photo))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.form.status").value("CONCLUIDO"))
                    .andExpect(jsonPath("$.data.review.status").value("awaiting_review"))
                    .andExpect(jsonPath("$.data.canReview").value(false))
                    .andExpect(jsonPath("$.data.form.questions[2].answer[0].id").value("test-image"))
                    .andExpect(jsonPath("$.data.form.questions[2].answer[0].chave_objeto").doesNotExist());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(url + "/responses").file(responsePart).file(photo))
                    .andExpect(status().isConflict());
            mvc.perform(post(url + "/review").contentType("application/json").content("{\"status\":\"approved\"}"))
                    .andExpect(status().isForbidden());
            authenticate("outsider", com.astro.api.auth.security.Role.GESTOR_WORKSPACE);
            mvc.perform(get(url + "/responses")).andExpect(status().isNotFound());
            mvc.perform(get(url + "/images/test-image")).andExpect(status().isNotFound());
            authenticate("sender", com.astro.api.auth.security.Role.GESTOR);
            mvc.perform(get(url + "/responses")).andExpect(status().isOk()).andExpect(jsonPath("$.data.canReview").value(true));
            mvc.perform(get(url + "/images/test-image")).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(post(url + "/review").contentType("application/json").content("{\"status\":\"rejected\",\"description\":\" \"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post(url + "/review").contentType("application/json").content("{\"status\":\"rejected\",\"description\":\"Foto ilegível\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.review.status").value("rejected"))
                    .andExpect(jsonPath("$.data.review.description").value("Foto ilegível"));
            authenticate("owner", com.astro.api.auth.security.Role.GESTOR_WORKSPACE);
            mvc.perform(post(url + "/review").contentType("application/json").content("{\"status\":\"approved\"}"))
                    .andExpect(status().isConflict());
            Document raw = mongo.getCollection("forms").find(new Document("_id", pending.getId())).first(); assertNotNull(raw);
            assertEquals("REPROVADO", raw.get("analise", Document.class).getString("status"));
            assertEquals("Foto ilegível", raw.get("analise", Document.class).getString("descricao"));
            assertEquals(1, raw.getList("localizacoes", Document.class).size());
            assertEquals("formularios/private/image.png", raw.getList("perguntas", Document.class).get(2)
                    .getList("resposta", Document.class).getFirst().getString("chave_objeto"));
            Form bound = form(2, 1, 10, 20, FormStatus.PENDING); bound.setUnitId(3L); bound.setVersion(0L);
            bound.getQuestions().getFirst().setType("texto"); bound.getQuestions().getFirst().setRequired(true);
            repository.insert(bound);
            UnitLocationProjection unitLocation = mock(UnitLocationProjection.class);
            when(units.findLocationByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.of(unitLocation));
            when(unitLocation.getUnitId()).thenReturn(3L); when(unitLocation.getActive()).thenReturn(true);
            when(unitLocation.getLatitude()).thenReturn(0.0); when(unitLocation.getLongitude()).thenReturn(0.0);
            authenticate("recipient", com.astro.api.auth.security.Role.GESTOR);
            String boundUrl = "/forms/" + bound.getId();
            mvc.perform(get("/forms/received/" + bound.getId())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.locationPolicy.enforceUnitRadius").value(true))
                    .andExpect(jsonPath("$.data.locationPolicy.radiusMeters").value(100));
            String positionBody = "{\"locationConsent\":true,\"unitId\":999,\"workspaceId\":999,\"locations\":[{\"latitude\":%s,\"longitude\":%s,\"accuracyMeters\":5,\"recordedAt\":\"%s\"}],\"answers\":[{\"questionId\":1,\"text\":\"No local\"}]}";
            mvc.perform(post(boundUrl + "/responses").contentType("application/json")
                    .content(positionBody.formatted("80", "170", Instant.now())))
                    .andExpect(status().isBadRequest());
            assertEquals(FormStatus.PENDING, repository.findById(bound.getId()).orElseThrow().getStatus());
            assertNull(repository.findById(bound.getId()).orElseThrow().getRespondedAt());
            mvc.perform(post(boundUrl + "/responses").contentType("application/json")
                    .content(positionBody.formatted("0", "0", Instant.now())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.locationCheck.unitId").value(3))
                    .andExpect(jsonPath("$.data.locationCheck.distanceMeters").value(0));
            verify(units, times(2)).findLocationByIdAndWorkspaceId(3L, 1L);
            verify(units, never()).findLocationByIdAndWorkspaceId(999L, 999L);
            assertNotNull(repository.findById(bound.getId()).orElseThrow().getLocationCheck());
            verify(storage, times(1)).upload(any(), any(), any()); verify(storage, never()).delete(any());
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.data.mongodb.repository.config.EnableMongoRepositories(basePackageClasses = FormRepository.class)
    static class RepositoryConfiguration {
        @org.springframework.context.annotation.Bean
        MongoTemplate mongoTemplate() { return mongo; }
    }
}
