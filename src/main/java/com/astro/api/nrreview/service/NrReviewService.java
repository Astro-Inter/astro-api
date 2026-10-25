package com.astro.api.nrreview.service;

import com.astro.api.common.exception.*;
import com.astro.api.nrreview.dto.*;
import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDate;
import java.time.format.*;
import java.util.*;

import static com.mongodb.client.model.Filters.eq;

@Service
public class NrReviewService {
    private final MongoClient client;
    private final MongoDatabase database;
    private final String catalog;
    private final String pending;
    private final NrSqlCatalogSynchronizer sqlSynchronizer;
    private static final TransactionOptions TRANSACTION = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY).build();
    private static final List<String> FIELDS = List.of("_id", "nome", "descricao", "objetivo", "aplicabilidade",
            "usabilidade", "revogada", "tempo_reciclagem_meses", "ultima_atualizacao", "data_criacao");

    public NrReviewService(MongoClient client, MongoTemplate template, NrSqlCatalogSynchronizer sqlSynchronizer,
            @Value("${astro.mongodb.nr-collection:nrs}") String catalog,
            @Value("${astro.mongodb.nr-pending-collection:nrs_pendentes}") String pending) {
        if (catalog.equals(pending)) throw new IllegalArgumentException("As coleções de NRs devem ser distintas.");
        this.sqlSynchronizer = sqlSynchronizer;
        this.client = client;
        this.database = template.getDb();
        this.catalog = catalog;
        this.pending = pending;
    }

    public List<NrSnapshot> list(boolean candidates) {
        List<NrSnapshot> result = new ArrayList<>();
        try (var cursor = collection(candidates).find().sort(new Document("_id", 1)).iterator()) {
            while (cursor.hasNext()) {
                Document document = cursor.next();
                result.add(snapshot(document, candidates ? collection(false).find(eq("_id", document.get("_id"))).first() : null));
            }
        }
        return result;
    }

    public NrSnapshot get(int id, boolean candidate) {
        validId(id);
        Document document = required(collection(candidate).find(eq("_id", id)).first());
        return snapshot(document, candidate ? collection(false).find(eq("_id", id)).first() : null);
    }

    public NrSnapshot update(int id, NrUpdateRequest request) {
        validId(id);
        return transaction(session -> {
            Document current = required(collection(false).find(session, eq("_id", id)).first());
            checkRevision(current, request.revision());
            Document updated = request.content().applyTo(current);
            validate(updated);
            collection(false).replaceOne(session, eq("_id", id), updated);
            return snapshot(updated, null);
        });
    }

    public NrApprovalResult approve(int id, NrApprovalRequest request) {
        validId(id);
        String jobId = UUID.randomUUID().toString();
        NrSnapshot result = transaction(session -> {
            Document candidate = collection(true).find(session, eq("_id", id)).first();
            if (candidate == null) throw new ConflictException("A análise já foi aprovada ou removida. Atualize a listagem.");
            checkRevision(candidate, request.revision());
            Document current = collection(false).find(session, eq("_id", id)).first();
            checkRevision(current, request.approvedRevision());
            Document approved = request.corrections() == null ? new Document(candidate) : request.corrections().applyTo(candidate);
            validate(approved);
            collection(false).replaceOne(session, eq("_id", id), approved, new ReplaceOptions().upsert(true));
            // The read and delete share the transaction snapshot: an updater replacing the candidate causes a write conflict.
            collection(true).deleteOne(session, eq("_id", id));
            database.getCollection(NrSqlCatalogSynchronizer.JOBS).insertOne(session,
                    new Document("_id", jobId).append("nrId", id).append("createdAt", new java.util.Date()));
            return snapshot(approved, null);
        });
        return NrApprovalResult.from(result, sqlSynchronizer.synchronize(jobId));
    }

    private NrSnapshot transaction(java.util.function.Function<ClientSession, NrSnapshot> operation) {
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> operation.apply(session), TRANSACTION);
        } catch (MongoException exception) {
            if (exception.getCode() == 112 || exception.getCode() == 11000) {
                throw new ConflictException("A NR mudou durante a operação. Reabra a revisão.");
            }
            throw exception;
        }
    }

    private MongoCollection<Document> collection(boolean candidates) { return database.getCollection(candidates ? pending : catalog); }
    private static void validId(int id) { if (id <= 0) throw new BusinessException("Número de NR inválido."); }
    private static Document required(Document document) {
        if (document == null) throw new ResourceNotFoundException("NR não encontrada.");
        return document;
    }
    private static void checkRevision(Document document, String expected) {
        if (!revision(document).equals(expected)) throw new ConflictException("A versão da NR mudou. Reabra a revisão para conferir os dados atuais.");
    }
    private static NrSnapshot snapshot(Document document, Document approved) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String field : FIELDS) fields.put(field, document.get(field));
        return new NrSnapshot(fields, revision(document), revision(approved));
    }
    /** Canonical BSON snapshot, including fields unknown to the UI. Object key order does not change the revision. */
    public static String revision(Document document) {
        if (document == null) return "absent";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(new Document(canonical(document)).toJson().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static Map<String, Object> canonical(Map<String, Object> value) {
        Map<String, Object> sorted = new TreeMap<>();
        value.forEach((key, item) -> sorted.put(key, item instanceof Document nested ? new Document(canonical(nested)) : item));
        return sorted;
    }
    private static void validate(Document document) {
        for (String field : List.of("nome", "descricao", "objetivo", "aplicabilidade")) {
            Object value = document.get(field);
            if (!(value instanceof String text) || text.isBlank() || text.length() > (field.equals("nome") ? 300 : 20000)) {
                throw new BusinessException("Confira o campo " + field + " antes de aprovar.");
            }
        }
        if (!(document.get("usabilidade") instanceof String usability) || !Set.of("Colaborador", "Empresa").contains(usability)
                || !(document.get("revogada") instanceof Boolean)
                || !(document.get("tempo_reciclagem_meses") instanceof Number months)
                || months.doubleValue() != months.intValue() || months.intValue() <= 0) {
            throw new BusinessException("Confira usabilidade, revogação e tempo de reciclagem.");
        }
        try { LocalDate.parse(document.getString("ultima_atualizacao"), DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)); }
        catch (RuntimeException exception) { throw new BusinessException("A data da fonte deve ser válida no formato DD/MM/AAAA."); }
    }
}
