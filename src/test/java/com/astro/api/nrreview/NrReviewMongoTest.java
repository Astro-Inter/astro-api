package com.astro.api.nrreview;

import static org.mockito.Mockito.*;

import com.astro.api.nrreview.mapper.NrReviewMapper;
import com.astro.api.nrreview.service.NrSqlCatalogSynchronizer;
import com.astro.api.nrreview.repository.NrReviewRepository;
import com.astro.api.nrreview.validation.NrReviewValidator;
import static org.mockito.ArgumentMatchers.anyString;
import com.mongodb.MongoException;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.nrreview.dto.NrApprovalRequest;
import com.astro.api.nrreview.dto.NrApprovalResult;
import com.astro.api.nrreview.dto.NrContent;
import com.astro.api.nrreview.dto.NrSnapshot;
import com.astro.api.nrreview.dto.NrUpdateRequest;
import com.astro.api.nrreview.service.NrReviewService;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "NR_TEST_MONGODB_URI", matches = "mongodb://127\\.0\\.0\\.1:27019/.*")


class NrReviewMongoTest {
    static MongoClient client;
    static MongoTemplate mongo;
    static NrReviewService service;
    static NrSqlCatalogSynchronizer synchronizer = mock(NrSqlCatalogSynchronizer.class);
    static final String DATABASE = "nr_review_test_" + UUID.randomUUID().toString().replace("-", "");
    @BeforeAll static void connect() {
        client = MongoClients.create(System.getenv("NR_TEST_MONGODB_URI"));
        mongo = new MongoTemplate(client, DATABASE);
        service = new NrReviewService(new NrReviewRepository(client, mongo, "nrs", "nrs_pendentes"), new NrReviewMapper(), new NrReviewValidator(new NrReviewMapper()), synchronizer);
        mongo.createCollection("nrs"); mongo.createCollection("nrs_pendentes"); mongo.createCollection(NrReviewRepository.SQL_SYNC_COLLECTION);
    }
    @AfterAll static void close() {
        if (mongo != null && DATABASE.matches("nr_review_test_[a-f0-9]{32}")) mongo.getDb().drop();
        if (client != null) client.close();
    }
    @BeforeEach void seed() {
        reset(synchronizer);
        when(synchronizer.synchronize(anyString())).thenReturn(true);
        mongo.getCollection(NrReviewRepository.SQL_SYNC_COLLECTION).deleteMany(new Document());
        mongo.getCollection("nrs").deleteMany(new Document());
        mongo.getCollection("nrs_pendentes").deleteMany(new Document());
        mongo.getCollection("nrs").insertOne(document(20, "Atual"));
        mongo.getCollection("nrs_pendentes").insertOne(document(20, "Análise da IA"));
    }
    static Document document(int id, String name) {
        return new Document("_id", id).append("nome", name).append("descricao", "Descrição da norma")
                .append("objetivo", "Objetivo").append("aplicabilidade", "Aplicabilidade")
                .append("usabilidade", "Colaborador").append("revogada", false)
                .append("tempo_reciclagem_meses", 12).append("ultima_atualizacao", "27/01/2025")
                .append("data_criacao", "2026-10-24T12:00:00.000Z");
    }
    static NrContent corrected(String name) { return new NrContent(name, "Descrição corrigida", "Objetivo", "Aplicabilidade", "Empresa", false, 24); }
    @Test void publishesCorrectionsAndRemovesCandidatePreservingSourceMetadata() {
        NrSnapshot candidate = service.get(20, true);
        NrApprovalResult result = service.approve(20, new NrApprovalRequest(candidate.revision(), candidate.approvedRevision(), corrected("Corrigida")));
        assertEquals("Corrigida", result.document().get("nome"));
        assertEquals(candidate.document().get("data_criacao"), result.document().get("data_criacao"));
        assertEquals(candidate.document().get("ultima_atualizacao"), result.document().get("ultima_atualizacao"));
        assertEquals(0, service.list(true).size());
        assertEquals(result.revision(), service.get(20, false).revision());
        assertThrows(ConflictException.class, () -> service.approve(20, new NrApprovalRequest(candidate.revision(), candidate.approvedRevision(), null)));
    }
    @Test void rejectsStaleCandidateEvenWhenSourceDateIsUnchanged() {
        NrSnapshot before = service.get(20, true);
        mongo.getCollection("nrs_pendentes").updateOne(eq("_id", 20), new Document("$set", new Document("objetivo", "Nova análise")));
        assertThrows(ConflictException.class, () -> service.approve(20, new NrApprovalRequest(before.revision(), before.approvedRevision(), null)));
        assertEquals("Atual", service.get(20, false).document().get("nome")); verifyNoInteractions(synchronizer);
        assertEquals("Nova análise", service.get(20, true).document().get("objetivo"));
    }
    @Test void staleCatalogBlocksApprovalAndEditingWithoutChangingPending() {
        NrSnapshot candidate = service.get(20, true); NrSnapshot current = service.get(20, false);
        service.update(20, new NrUpdateRequest(current.revision(), corrected("Edição humana")));
        assertThrows(ConflictException.class, () -> service.approve(20, new NrApprovalRequest(candidate.revision(), candidate.approvedRevision(), null)));
        assertThrows(ConflictException.class, () -> service.update(20, new NrUpdateRequest(current.revision(), corrected("Sobrescrever"))));
        assertEquals(candidate.revision(), service.get(20, true).revision());
        assertEquals("Edição humana", service.get(20, false).document().get("nome"));
    }
    @Test void approvesNewNormWithoutCorrectionsAndRejectsInvalidStoredContent() {
        mongo.getCollection("nrs_pendentes").insertOne(document(35, "Nova norma"));
        NrSnapshot candidate = service.get(35, true);
        assertEquals("absent", candidate.approvedRevision());
        service.approve(35, new NrApprovalRequest(candidate.revision(), "absent", null));
        assertEquals("Nova norma", service.get(35, false).document().get("nome")); clearInvocations(synchronizer);
        mongo.getCollection("nrs_pendentes").updateOne(eq("_id", 20), new Document("$set", new Document("ultima_atualizacao", "31/02/2025")));
        NrSnapshot invalid = service.get(20, true);
        assertThrows(BusinessException.class, () -> service.approve(20, new NrApprovalRequest(invalid.revision(), invalid.approvedRevision(), null)));
        assertNotNull(service.get(20, true));
        assertEquals("Atual", service.get(20, false).document().get("nome")); verifyNoInteractions(synchronizer);
    }
    @Test void failureAfterCatalogWriteRollsBackBothCollections() {
        NrSnapshot candidate = service.get(20, true);
        client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand")
                .append("mode", new Document("times", 1)).append("data", new Document("failCommands", List.of("delete"))
                        .append("errorCode", 2).append("namespace", DATABASE + ".nrs_pendentes")));
        try {
            assertThrows(MongoException.class, () -> service.approve(20, new NrApprovalRequest(candidate.revision(), candidate.approvedRevision(), corrected("Não publicar"))));
            assertEquals("Atual", service.get(20, false).document().get("nome")); verifyNoInteractions(synchronizer);
            assertEquals(candidate.revision(), service.get(20, true).revision());
        } finally { client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand").append("mode", "off")); }
    }
    @Test void revisionsIgnoreKeyOrderButDetectEveryContentChange() {
        Document first = document(20, "Nome"); Document reversed = new Document();
        ArrayList<String> keys = new ArrayList<>(first.keySet()); Collections.reverse(keys); keys.forEach(key -> reversed.put(key, first.get(key)));
        assertEquals(new NrReviewMapper().revision(first), new NrReviewMapper().revision(reversed));
        reversed.put("descricao", "Alterada");
        assertNotEquals(new NrReviewMapper().revision(first), new NrReviewMapper().revision(reversed));
        assertThrows(ResourceNotFoundException.class, () -> service.get(999, true));
        assertThrows(BusinessException.class, () -> service.get(0, true));
    }
}
