package com.astro.api.form;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.common.storage.R2StorageService.PresignedDownload;
import com.astro.api.conformity.repository.NrCatalogRepository;
import com.astro.api.form.controller.FormController;
import com.astro.api.form.controller.FormResponseController;
import com.astro.api.common.service.ViewedService;
import com.astro.api.form.dto.internal.FormImage;
import com.astro.api.form.dto.request.FormQuestionAnswerRequest;
import com.astro.api.form.dto.request.FormResponseRequest;
import com.astro.api.form.mapper.FormAnswerMapper;
import com.astro.api.form.mapper.FormMapper;
import com.astro.api.form.mapper.FormResponseMapper;
import com.astro.api.form.model.*;
import com.astro.api.form.repository.*;
import com.astro.api.form.repository.FormResponseCondition;
import com.astro.api.form.repository.FormWriteCondition;
import com.astro.api.form.service.FormAccessService;
import com.astro.api.form.service.FormAnswerValidationService;
import com.astro.api.form.service.FormImageStorageService;
import com.astro.api.form.service.FormLocationService;
import com.astro.api.form.service.FormReferenceService;
import com.astro.api.form.service.FormResponseService;
import com.astro.api.form.service.FormScope;
import com.astro.api.form.service.FormService;
import com.astro.api.form.strategy.answer.DateAnswerStrategy;
import com.astro.api.form.strategy.answer.OptionAnswerStrategy;
import com.astro.api.form.strategy.answer.PhotoAnswerStrategy;
import com.astro.api.form.strategy.answer.TextAnswerStrategy;
import com.astro.api.unit.repository.UnitLocationProjection;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.data.repository.core.support.RepositoryComposition;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import static com.astro.api.form.FormTestFixtures.form;
import static com.astro.api.form.FormTestFixtures.formId;
import static com.astro.api.form.FormTestFixtures.manager;
import static org.junit.jupiter.api.Assertions.*;
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
                RepositoryComposition.RepositoryFragments.just(new FormRepositoryCustomImpl(mongo)));
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

    @Test void listingIncludesReviewDecisionWithoutAnswersOrReviewerDetails() {
        for (FormReviewStatus status : FormReviewStatus.values()) {
            Form value = form(status.ordinal() + 1, 1, 10, 20, FormStatus.COMPLETED);
            value.setReview(new FormReview(status, 10L, Instant.now(), null));
            mongo.insert(value);
        }
        FormRepositoryCustom.Listing result = repository.findAll(
                formAccessService.readFilter(new FormScope(10L, 1L, false)), null, null, 0, 20);
        assertEquals(2, result.total());
        FormMapper mapper = new FormMapper();
        assertEquals(Set.of("approved", "rejected"), result.forms().stream()
                .map(value -> mapper.toSummary(value).reviewStatus()).collect(Collectors.toSet()));
        for (Form value : result.forms()) {
            assertEquals(FormStatus.COMPLETED, value.getStatus());
            assertNull(value.getReview().reviewerManagerId());
            assertNull(value.getReview().reviewedAt());
            assertNull(value.getQuestions().getFirst().getAnswer());
        }
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
        FormAccessService access = mock(FormAccessService.class);
        when(access.requireManager(any())).thenReturn(new FormScope(10L, 1L, false));
        FormService service = new FormService(repository, access,
                new FormMapper(), FormTestFixtures.validator(),
                mock(FormReferenceService.class), mock(UserRepository.class), mock(FormLocationService.class));
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Callable<String>> calls = IntStream.range(0, 32)
                    .<Callable<String>>mapToObj(index -> () ->
                            service.create(FormTestFixtures.content(FormStatus.PENDING), null).id()).toList();
            Set<String> ids = new HashSet<String>();
            for (Future<String> future : executor.invokeAll(calls)) {
                String id = future.get();
                assertEquals(4, UUID.fromString(id).version());
                assertTrue(ids.add(id));
                assertTrue(repository.findById(id).isPresent());
            }
            assertEquals(32, ids.size());
            assertEquals(List.of("formularios"), mongo.getDb().listCollectionNames().into(new ArrayList<>()));
        }
    }

    @Test void persistsTypedOptionsAndEmptyEmbeddedAnswersInASingleFormDocument() {
        Form form = new Form();
        form.setId(UUID.randomUUID().toString());
        new FormMapper().applyContent(FormTestFixtures.content(FormStatus.PENDING), form);
        form.setStatus(FormStatus.PENDING); form.setWorkspaceId(1L); form.setSenderManagerId(10L);
        form.setUnitId(3L); form.setVersion(0L);
        repository.insert(form);
        Document raw = mongo.getCollection("formularios").find().first();
        assertNotNull(raw);
        assertInstanceOf(String.class, raw.get("_id"));
        assertEquals(form.getId(), raw.getString("_id"));
        assertEquals(1, raw.getInteger("workspace_id"));
        assertEquals(10, raw.getInteger("gestor_remetente_id"));
        assertEquals(20, raw.getInteger("gestor_id"));
        assertEquals(3, raw.getInteger("unidade_id"));
        assertEquals(0, raw.getInteger("versao"));
        assertEquals("PENDENTE", raw.getString("status"));
        assertEquals(FormStatus.PENDING, repository.findById(form.getId()).orElseThrow().getStatus());
        List<Document> questions = raw.getList("perguntas", Document.class);
        assertEquals(4, questions.size()); assertEquals(List.of(), questions.getFirst().get("resposta"));
        assertEquals(1, questions.getFirst().getInteger("pergunta_id"));
        assertEquals(1, questions.get(1).getList("opcoes", Document.class).getFirst().getInteger("opcao_id"));
        assertEquals("alternativa", questions.get(1).getString("tipo"));
        assertTrue(questions.get(1).getList("opcoes", Document.class).get(1).getBoolean("outra"));
        assertEquals(Date.from(Instant.parse("2026-10-30T03:00:00Z")), raw.get("data_limite"));
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
        Document raw = mongo.getCollection("formularios").find().first(); assertNotNull(raw);
        Class<? extends Number> expectedType = value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                ? Integer.class : Long.class;
        for (String field : List.of("workspace_id", "gestor_id", "gestor_remetente_id", "unidade_id", "versao")) {
            Number storedNumber = assertInstanceOf(expectedType, raw.get(field));
            assertEquals(value, storedNumber.longValue());
        }
        Document storedQuestion = raw.getList("perguntas", Document.class).getFirst();
        Number questionId = assertInstanceOf(expectedType, storedQuestion.get("pergunta_id"));
        Number optionId = assertInstanceOf(expectedType, storedQuestion.getList("opcoes", Document.class).getFirst().get("opcao_id"));
        assertEquals(value, questionId.longValue()); assertEquals(value, optionId.longValue());
        Form restored = repository.findById(form.getId()).orElseThrow();
        assertEquals(value, restored.getWorkspaceId()); assertEquals(value, restored.getManagerId());
        assertEquals(value, restored.getSenderManagerId()); assertEquals(value, restored.getUnitId());
        assertEquals(value, restored.getVersion());
        assertEquals(value, restored.getQuestions().getFirst().getId());
        assertEquals(value, restored.getQuestions().getFirst().getOptions().getFirst().getId());
    }

    @Test void readsLegacyLongsAndWritesPlainNumbersDuringConditionalEditingAndPublication() {
        Document legacy = new Document("_id", formId(1)).append("workspace_id", 1L)
                .append("gestor_remetente_id", 10L).append("gestor_id", 20L).append("unidade_id", 3L)
                .append("status", "RASCUNHO").append("versao", 0L)
                .append("perguntas", List.of(new Document("pergunta_id", 1L).append("tipo", "alternativa")
                        .append("resposta", List.of()).append("opcoes", List.of(new Document("opcao_id", 1L)))));
        mongo.getCollection("formularios").insertOne(legacy);
        FormScope scope = new FormScope(10L, 1L, false);
        Form saved = repository.findById(formId(1), formAccessService.readFilter(scope)).orElseThrow();
        assertEquals(1L, saved.getQuestions().getFirst().getId());
        assertEquals(1L, saved.getQuestions().getFirst().getOptions().getFirst().getId());
        Form updated = repository.update(saved, formAccessService.writeCondition(saved, scope)).orElseThrow();
        Document raw = mongo.getCollection("formularios").find().first(); assertNotNull(raw);
        assertEquals(20, raw.getInteger("gestor_id")); assertEquals(3, raw.getInteger("unidade_id"));
        assertEquals(1, raw.getInteger("versao"));
        assertEquals(1, raw.getList("perguntas", Document.class).getFirst().getInteger("pergunta_id"));
        assertEquals(1, raw.getList("perguntas", Document.class).getFirst().getList("opcoes", Document.class).getFirst().getInteger("opcao_id"));
        Form published = repository.updateStatus(formAccessService.writeCondition(updated, scope), FormStatus.PENDING, Instant.now()).orElseThrow();
        assertEquals(2L, published.getVersion());
        Document publishedRaw = mongo.getCollection("formularios").find().first(); assertNotNull(publishedRaw);
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
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = executor.invokeAll(List.of(
                    () -> repository.update(first, formAccessService.writeCondition(first, scope)).isPresent(),
                    () -> repository.update(second, formAccessService.writeCondition(second, scope)).isPresent()));
            int successful = 0;
            for (Future<Boolean> result : results) if (result.get()) successful++;
            assertEquals(1, successful);
        }
        Form saved = repository.findById(formId(1), formAccessService.readFilter(scope)).orElseThrow(); assertEquals(1L, saved.getVersion());
        mongo.getCollection("formularios").updateOne(new Document("_id", formId(1)),
                new Document("$set", new Document("status", "CONCLUIDO")));
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
        mongo.getCollection("formularios").updateOne(new Document("_id", formId(1)),
                new Document("$set", new Document("versao", 1L)));
        assertFalse(repository.deleteIfUnanswered(formAccessService.writeCondition(value, new FormScope(10L, 1L, false))));
        value.setVersion(1L);
        mongo.getCollection("formularios").updateOne(new Document("_id", formId(1)),
                new Document("$set", new Document("status", "CONCLUIDO")));
        assertFalse(repository.deleteIfUnanswered(formAccessService.writeCondition(value, new FormScope(99L, 1L, true))));
        assertNotNull(mongo.findById(formId(1), Form.class));
        mongo.getCollection("formularios").updateOne(new Document("_id", formId(1)),
                new Document("$set", new Document("status", "PENDENTE")));
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
        mongo.getCollection("formularios").updateOne(new Document("_id", draft.getId()),
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
        UserRepository users = mock(UserRepository.class);
        UnitRepository units = mock(UnitRepository.class);
        NrCatalogRepository nrs = mock(NrCatalogRepository.class);
        when(users.findByFirebaseUid("sender")).thenReturn(Optional.of(manager(10, 1, UserType.GESTOR)));
        when(users.findByFirebaseUid("recipient")).thenReturn(Optional.of(manager(20, 1, UserType.GESTOR)));
        when(users.findByFirebaseUid("owner")).thenReturn(Optional.of(manager(99, 1, UserType.GESTOR_WORKSPACE)));
        when(users.findByFirebaseUid("outsider")).thenReturn(Optional.of(manager(30, 2, UserType.GESTOR_WORKSPACE)));
        when(users.findById(20L)).thenReturn(Optional.of(manager(20, 1, UserType.GESTOR)));
        FormService service = new FormService(repository, new FormAccessService(users), new FormMapper(),
                FormTestFixtures.validator(), new FormReferenceService(users, units, nrs), users, new FormLocationService(units));
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new FormController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        JsonMapper json = JsonMapper.builder().build();
        try {
            authenticate("sender", Role.GESTOR);
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
            authenticate("recipient", Role.GESTOR);
            mvc.perform(get(url)).andExpect(status().isNotFound());
            authenticate("sender", Role.GESTOR);
            mvc.perform(post(url + "/publish")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDENTE"));
            mvc.perform(post(url + "/publish")).andExpect(status().isConflict());
            mvc.perform(get("/forms").param("status", "PENDENTE").param("search", "Inspeção"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
            authenticate("recipient", Role.GESTOR);
            mvc.perform(get(url)).andExpect(status().isOk());
            mvc.perform(put(url).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete(url)).andExpect(status().isForbidden());
            authenticate("outsider", Role.GESTOR_WORKSPACE);
            mvc.perform(get(url)).andExpect(status().isNotFound());
            mvc.perform(delete(url)).andExpect(status().isNotFound());
            authenticate("owner", Role.GESTOR_WORKSPACE);
            mvc.perform(put(url).contentType("application/json").content(json.writeValueAsString(FormTestFixtures.content(FormStatus.PENDING))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.senderManagerId").value(10));
            mongo.getCollection("formularios").updateOne(new Document("_id", id), new Document("$set",
                    new Document("status", "CONCLUIDO").append("perguntas.0.resposta", List.of("Resposta única"))));
            mvc.perform(delete(url)).andExpect(status().isConflict());
            mvc.perform(put(url).contentType("application/json").content("{}"))
                    .andExpect(status().isConflict());
            mvc.perform(get(url)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.questions[0].answer[0]").value("Resposta única"));
            mvc.perform(post("/forms").contentType("application/json").content("{\"status\":\"RASCUNHO\"}"))
                    .andExpect(status().isCreated());
            String removable = mongo.findAll(Form.class).stream().map(Form::getId).filter(value -> !value.equals(id)).findFirst().orElseThrow();
            mvc.perform(delete("/forms/" + removable)).andExpect(status().isNoContent());
            assertEquals(1, mongo.count(new Query(), Form.class));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String uid, Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(uid, uid + "@astro.com", role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test void discoversTheCustomFragmentThroughSpringDataRepositoryConfiguration() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RepositoryConfiguration.class)) {
            FormRepository configuredRepository = context.getBean(FormRepository.class);
            configuredRepository.insert(form(1, 1, 10, 20, FormStatus.DRAFT));
            assertEquals(1, configuredRepository.findAll(formAccessService.readFilter(new FormScope(10L, 1L, false)), null, FormStatus.DRAFT, 0, 20).total());
            assertEquals(FormStatus.DRAFT, configuredRepository.findById(formId(1)).orElseThrow().getStatus());
        }
    }

    @Test void preservesLegacyUnitDistanceAndCoordinateSnapshots() {
        Instant respondedAt = Instant.now();
        Form form = form(1, 1, 10, 20, FormStatus.PENDING); form.setVersion(0L); form.setUnitId(3L); repository.insert(form);
        FormLocationReading reading = new FormLocationReading(-23.5, -46.6, 5.0, respondedAt);
        form.setLocationCheck(new FormLocationCheck(3L, -23.5, -46.6, 100, 0.0, reading));
        form.setLocations(List.of(reading)); form.setRespondedAt(respondedAt); form.setStatus(FormStatus.COMPLETED);
        form.getQuestions().getFirst().setAnswer(List.of("No local"));
        mongo.save(form);
        Form saved = repository.findById(form.getId()).orElseThrow();
        assertEquals(0.0, saved.getLocationCheck().distanceMeters()); assertEquals(3L, saved.getLocationCheck().unitId());
        Document raw = mongo.getCollection("formularios").find(new Document("_id", form.getId())).first(); assertNotNull(raw);
        Document check = raw.get("validacao_localizacao", Document.class);
        assertEquals(3, check.getInteger("unidade_id")); assertEquals(100, check.getInteger("raio_metros"));
        assertEquals(-46.6, check.getDouble("longitude_unidade")); assertEquals(-23.5, check.getDouble("latitude_unidade"));
        assertEquals(0.0, check.getDouble("distancia_metros")); assertFalse(check.containsKey("distanceMeters"));
        assertEquals(Date.from(respondedAt.truncatedTo(ChronoUnit.MILLIS)),
                check.get("leitura", Document.class).getDate("registrado_em"));
    }

    @Test void recipientListingExcludesDraftsOtherRecipientsAndWorkspaces() {
        Form pending = form(1, 1, 10, 20, FormStatus.PENDING); pending.setNrCode(6); mongo.insert(pending);
        Form completed = form(2, 1, 10, 20, FormStatus.COMPLETED); completed.setNrCode(6); mongo.insert(completed);
        Form rejected = form(6, 1, 10, 20, FormStatus.COMPLETED); rejected.setNrCode(6);
        rejected.setReview(new FormReview(FormReviewStatus.REJECTED, 10L, Instant.now(), "Reprovado")); mongo.insert(rejected);
        mongo.insert(form(3, 1, 10, 20, FormStatus.DRAFT));
        mongo.insert(form(4, 1, 20, 10, FormStatus.PENDING));
        mongo.insert(form(5, 2, 10, 20, FormStatus.PENDING));
        assertEquals(3, repository.findReceived(formAccessService.recipientFilter(new FormScope(20L, 1L, false)), "Clima", 6, null, 0, 20).total());
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
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
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
        Document raw = mongo.getCollection("formularios").find(new Document("_id", pending.getId())).first(); assertNotNull(raw);
        assertEquals("CONCLUIDO", raw.getString("status")); assertEquals(1, raw.getInteger("versao"));
        assertFalse(raw.containsKey("consentimento_localizacao")); assertFalse(raw.containsKey("analise"));
        assertFalse(raw.containsKey("localizacoes"));
        assertFalse(repository.deleteIfUnanswered(formAccessService.writeCondition(completed, new FormScope(10L, 1L, false))));
        FormReview approved = new FormReview(FormReviewStatus.APPROVED, 10L, Instant.now(), null);
        assertTrue(repository.review(formAccessService.writeCondition(completed, new FormScope(20L, 1L, false)), approved).isEmpty());
        assertTrue(repository.review(formAccessService.writeCondition(completed, new FormScope(99L, 2L, true)), approved).isEmpty());
        FormWriteCondition reviewCondition = formAccessService.writeCondition(completed, new FormScope(10L, 1L, false));
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = executor.invokeAll(List.of(
                    () -> repository.review(reviewCondition, approved).isPresent(),
                    () -> repository.review(reviewCondition, new FormReview(FormReviewStatus.REJECTED, 10L, Instant.now(), "Foto ilegível")).isPresent()));
            int successes = 0;
            for (Future<Boolean> result : results) if (result.get()) successes++;
            assertEquals(1, successes);
        }
        Form reviewed = repository.findById(pending.getId()).orElseThrow(); assertEquals(2L, reviewed.getVersion());
        assertEquals(FormStatus.COMPLETED, reviewed.getStatus());
        Document storedReview = mongo.getCollection("formularios").find(new Document("_id", pending.getId())).first().get("analise", Document.class);
        assertEquals(reviewed.getReview().status() == FormReviewStatus.REJECTED, storedReview.containsKey("descricao"));
        assertInstanceOf(Integer.class, storedReview.get("gestor_revisor_id"));
        assertFalse(storedReview.containsKey("description"));
        assertTrue(repository.review(formAccessService.writeCondition(reviewed, new FormScope(99L, 1L, true)), approved).isEmpty());
    }

    @Test void approvedReviewOmitsDescriptionAndRejectedReviewStoresItInSameDocument() {
        for (FormReviewStatus decision : FormReviewStatus.values()) {
            Form value = form(decision.ordinal() + 1, 1, 10, 20, FormStatus.COMPLETED);
            value.setVersion(1L); value.setRespondedAt(Instant.now());
            value.setReview(new FormReview(decision, 10L, Instant.now(), decision == FormReviewStatus.REJECTED ? "Foto ilegível" : null));
            repository.insert(value);
            Document raw = mongo.getCollection("formularios").find(new Document("_id", value.getId())).first(); assertNotNull(raw);
            Document review = raw.get("analise", Document.class);
            assertEquals(decision.storedValue(), review.getString("status"));
            assertEquals(decision == FormReviewStatus.REJECTED, review.containsKey("descricao"));
            assertEquals(decision, repository.findById(value.getId()).orElseThrow().getReview().status());
        }
        assertEquals(2, mongo.getCollection("formularios").countDocuments());
    }

    @Test void httpResponseAndReviewWorkflowUsesMongoAndTheExistingAccessRules() throws Exception {
        UserRepository users = mock(UserRepository.class);
        UnitRepository units = mock(UnitRepository.class);
        when(users.findByFirebaseUid("sender")).thenReturn(Optional.of(manager(10, 1, UserType.GESTOR)));
        when(users.findByFirebaseUid("recipient")).thenReturn(Optional.of(manager(20, 1, UserType.GESTOR)));
        when(users.findByFirebaseUid("owner")).thenReturn(Optional.of(manager(99, 1, UserType.GESTOR_WORKSPACE)));
        when(users.findByFirebaseUid("outsider")).thenReturn(Optional.of(manager(30, 2, UserType.GESTOR_WORKSPACE)));
        ViewedService viewedService = mock(ViewedService.class);
        Set<String> viewedKeys = new HashSet<>();
        when(viewedService.findViewed(anyLong(), eq("forms"), anyCollection())).thenAnswer(invocation -> {
            Long managerId = invocation.getArgument(0);
            Collection<?> formIds = invocation.getArgument(2);
            return formIds.stream().map(String::valueOf)
                    .filter(formId -> viewedKeys.contains(managerId + ":" + formId)).collect(Collectors.toSet());
        });
        doAnswer(invocation -> {
            viewedKeys.add(invocation.getArgument(0) + ":" + invocation.getArgument(2));
            return null;
        }).when(viewedService).markViewed(anyLong(), eq("forms"), any());
        FormImageStorageService storage = mock(FormImageStorageService.class);
        FormImage image = new FormImage("test-image", "formularios/private/image.png", "camera.png", "image/png", 80);
        when(storage.upload(any(), any(), any())).thenReturn(image);
        when(storage.download(image)).thenReturn(new PresignedDownload(
                "https://example.test/signed-image", Instant.now().plusSeconds(900)));
        Form pending = form(1, 1, 10, 20, FormStatus.PENDING); pending.setQuestions(List.of()); pending.setVersion(0L);
        new FormMapper().applyContent(FormTestFixtures.content(FormStatus.PENDING), pending);
        repository.insert(pending);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            FormAnswerValidationService validation = new FormAnswerValidationService(
                    List.of(new TextAnswerStrategy(), new DateAnswerStrategy(),
                            new OptionAnswerStrategy(), new PhotoAnswerStrategy()), factory.getValidator());
            FormResponseService service = new FormResponseService(repository,
                    new FormAccessService(users), validation, new FormAnswerMapper(),
                    new FormResponseMapper(new FormMapper()), storage, users, units, factory.getValidator(), new FormLocationService(units), viewedService, mock(com.astro.api.conformity.service.FormConformityService.class));
            MockMvc mvc = MockMvcBuilders
                    .standaloneSetup(new FormResponseController(service))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
            String url = "/forms/" + pending.getId();
            authenticate("sender", Role.GESTOR);
            mvc.perform(get("/forms/received")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
            authenticate("owner", Role.GESTOR_WORKSPACE);
            mvc.perform(get("/forms/received/" + pending.getId())).andExpect(status().isNotFound());
            authenticate("recipient", Role.GESTOR);
            mvc.perform(get("/forms/received").param("workspaceId", "999").param("managerId", "999"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(pending.getId()))
                    .andExpect(jsonPath("$.data.items[0].viewed").value(false));
            mvc.perform(get("/forms/received/" + pending.getId())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.locationPolicy.enforceUnitRadius").value(false))
                    .andExpect(jsonPath("$.data.locationPolicy.captureRequired").value(false));
            mvc.perform(get("/forms/received")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].viewed").value(true));
            mvc.perform(get(url + "/responses")).andExpect(status().isConflict());
            FormResponseRequest request = new FormResponseRequest(List.of(new FormQuestionAnswerRequest(1L, "Condição segura", null, null, null, null),
                            new FormQuestionAnswerRequest(2L, null, List.of(1L), null, null, null),
                            new FormQuestionAnswerRequest(3L, null, null, null, null, List.of(0)),
                            new FormQuestionAnswerRequest(4L, null, null, null, LocalDate.of(2026, 10, 24), null)));
            byte[] body = JsonMapper.builder().build().writeValueAsBytes(request);
            MockMultipartFile responsePart = new MockMultipartFile(
                    "response", "response.json", "application/json", body);
            MockMultipartFile photo = new MockMultipartFile(
                    "files", "camera.png", "image/png", new byte[]{1});
            mvc.perform(MockMvcRequestBuilders.multipart(url + "/responses").file(responsePart).file(photo))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.form.status").value("CONCLUIDO"))
                    .andExpect(jsonPath("$.data.review.status").value("awaiting_review"))
                    .andExpect(jsonPath("$.data.canReview").value(false))
                    .andExpect(jsonPath("$.data.form.questions[2].answer[0].id").value("test-image"))
                    .andExpect(jsonPath("$.data.form.questions[2].answer[0].chave_objeto").doesNotExist());
            mvc.perform(MockMvcRequestBuilders.multipart(url + "/responses").file(responsePart).file(photo))
                    .andExpect(status().isConflict());
            mvc.perform(post(url + "/review").contentType("application/json").content("{\"status\":\"approved\"}"))
                    .andExpect(status().isForbidden());
            authenticate("outsider", Role.GESTOR_WORKSPACE);
            mvc.perform(get(url + "/responses")).andExpect(status().isNotFound());
            mvc.perform(get(url + "/images/test-image")).andExpect(status().isNotFound());
            authenticate("sender", Role.GESTOR);
            mvc.perform(get(url + "/responses")).andExpect(status().isOk()).andExpect(jsonPath("$.data.canReview").value(true));
            mvc.perform(get(url + "/images/test-image")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value("test-image"))
                    .andExpect(jsonPath("$.data.url").value("https://example.test/signed-image"))
                    .andExpect(jsonPath("$.data.expiresAt").isString())
                    .andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(post(url + "/review").contentType("application/json").content("{\"status\":\"rejected\",\"description\":\" \"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post(url + "/review").contentType("application/json").content("{\"status\":\"rejected\",\"description\":\"Foto ilegível\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.review.status").value("rejected"))
                    .andExpect(jsonPath("$.data.review.description").value("Foto ilegível"));
            authenticate("owner", Role.GESTOR_WORKSPACE);
            mvc.perform(post(url + "/review").contentType("application/json").content("{\"status\":\"approved\"}"))
                    .andExpect(status().isConflict());
            Document raw = mongo.getCollection("formularios").find(new Document("_id", pending.getId())).first(); assertNotNull(raw);
            assertEquals("REPROVADO", raw.get("analise", Document.class).getString("status"));
            assertEquals("Foto ilegível", raw.get("analise", Document.class).getString("descricao"));
            assertFalse(raw.containsKey("localizacoes"));
            assertFalse(raw.containsKey("consentimento_localizacao"));
            assertEquals("formularios/private/image.png", raw.getList("perguntas", Document.class).get(2)
                    .getList("resposta", Document.class).getFirst().getString("chave_objeto"));
            authenticate("recipient", Role.GESTOR);
            mvc.perform(get("/forms/received")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].status").value("CONCLUIDO"))
                    .andExpect(jsonPath("$.data.items[0].reviewStatus").value("rejected"))
                    .andExpect(jsonPath("$.data.items[0].viewed").value(true));
            mvc.perform(post(url + "/responses").contentType("application/json").content("{\"answers\":[]}"))
                    .andExpect(status().isConflict());
            Form bound = form(2, 1, 10, 20, FormStatus.PENDING); bound.setUnitId(3L); bound.setVersion(0L);
            bound.setDueDate(Instant.now().minusSeconds(86400));
            bound.getQuestions().getFirst().setType("texto"); bound.getQuestions().getFirst().setRequired(true);
            repository.insert(bound);
            UnitLocationProjection unitLocation = mock(UnitLocationProjection.class);
            when(units.findLocationByIdAndWorkspaceId(3L, 1L)).thenReturn(Optional.of(unitLocation));
            when(unitLocation.getUnitId()).thenReturn(3L); when(unitLocation.getActive()).thenReturn(true);
            when(unitLocation.getLatitude()).thenReturn(0.0); when(unitLocation.getLongitude()).thenReturn(0.0);
            authenticate("recipient", Role.GESTOR);
            String boundUrl = "/forms/" + bound.getId();
            mvc.perform(get("/forms/received/" + bound.getId()).param("unitId", "999").param("workspaceId", "999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.form.unitLocation.latitude").value(0.0))
                    .andExpect(jsonPath("$.data.form.unitLocation.longitude").value(0.0))
                    .andExpect(jsonPath("$.data.locationPolicy.enforceUnitRadius").value(false))
                    .andExpect(jsonPath("$.data.locationPolicy.maxReadingAgeSeconds").isEmpty())
                    .andExpect(jsonPath("$.data.locationPolicy.radiusMeters").value(100));
            mvc.perform(get("/forms/received")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(2));
            String answersBody = "{\"unitId\":999,\"workspaceId\":999,\"answers\":[{\"questionId\":1,\"text\":\"Após o prazo\"}]}";
            mvc.perform(post(boundUrl + "/responses").contentType("application/json").content(answersBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.form.status").value("CONCLUIDO"))
                    .andExpect(jsonPath("$.data.locations").isEmpty())
                    .andExpect(jsonPath("$.data.locationCheck").isEmpty());
            verify(units, times(2)).findLocationByIdAndWorkspaceId(3L, 1L);
            verify(units, never()).findLocationByIdAndWorkspaceId(999L, 999L);
            assertNull(repository.findById(bound.getId()).orElseThrow().getLocationCheck());
            Document boundRaw = mongo.getCollection("formularios").find(new Document("_id", bound.getId())).first();
            assertNotNull(boundRaw); assertFalse(boundRaw.containsKey("validacao_localizacao"));
            assertFalse(boundRaw.containsKey("localizacoes")); assertFalse(boundRaw.containsKey("consentimento_localizacao"));
            verify(storage, times(1)).upload(any(), any(), any()); verify(storage, never()).delete(any());
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMongoRepositories(basePackageClasses = FormRepository.class)
    static class RepositoryConfiguration {
        @Bean
        MongoTemplate mongoTemplate() { return mongo; }
    }
}
