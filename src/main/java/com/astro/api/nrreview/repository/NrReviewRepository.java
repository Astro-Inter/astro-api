package com.astro.api.nrreview.repository;

import com.mongodb.ReadConcern;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

@Repository
public class NrReviewRepository {
    public static final String SQL_SYNC_COLLECTION = "sincronizacoes_catalogo_nrs";
    private static final TransactionOptions TRANSACTION_OPTIONS = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT)
            .writeConcern(WriteConcern.MAJORITY)
            .build();

    private final MongoClient mongoClient;
    private final MongoDatabase mongoDatabase;
    private final String catalogCollection;
    private final String pendingCollection;

    public NrReviewRepository(MongoClient mongoClient, MongoTemplate mongoTemplate,
            @Value("${astro.mongodb.nr-collection:nrs}") String catalogCollection,
            @Value("${astro.mongodb.nr-pending-collection:nrs_pendentes}") String pendingCollection) {
        if (catalogCollection.equals(pendingCollection)) {
            throw new IllegalArgumentException("As coleções de NRs devem ser distintas.");
        }
        this.mongoClient = mongoClient;
        this.mongoDatabase = mongoTemplate.getDb();
        this.catalogCollection = catalogCollection;
        this.pendingCollection = pendingCollection;
    }

    public List<Document> findAll(boolean pending) {
        return collection(pending).find().sort(new Document("_id", 1)).into(new ArrayList<>());
    }

    public Map<Object, Document> findPublishedByIds(Collection<Object> nrIds) {
        Map<Object, Document> published = new LinkedHashMap<>();
        if (nrIds.isEmpty()) {
            return published;
        }
        try (MongoCursor<Document> cursor = collection(false).find(in("_id", nrIds)).iterator()) {
            while (cursor.hasNext()) {
                Document document = cursor.next();
                published.put(document.get("_id"), document);
            }
        }
        return published;
    }

    public Document findById(int nrId, boolean pending) {
        return collection(pending).find(eq("_id", nrId)).first();
    }

    public Document findById(ClientSession session, int nrId, boolean pending) {
        return collection(pending).find(session, eq("_id", nrId)).first();
    }

    public void savePublished(ClientSession session, int nrId, Document document, boolean upsert) {
        collection(false).replaceOne(session, eq("_id", nrId), document, new ReplaceOptions().upsert(upsert));
    }

    public void deletePending(ClientSession session, int nrId) {
        collection(true).deleteOne(session, eq("_id", nrId));
    }

    public void enqueueSqlSync(ClientSession session, String jobId, int nrId) {
        Document job = new Document("_id", jobId)
                .append("nr_id", nrId)
                .append("criado_em", new Date());
        mongoDatabase.getCollection(SQL_SYNC_COLLECTION).insertOne(session, job);
    }

    public Document findSqlSyncJob(String jobId) {
        return mongoDatabase.getCollection(SQL_SYNC_COLLECTION).find(eq("_id", jobId)).first();
    }

    public List<Document> findPendingSqlSyncJobs(int limit) {
        return mongoDatabase.getCollection(SQL_SYNC_COLLECTION).find()
                .sort(new Document("criado_em", 1)).limit(limit).into(new ArrayList<>());
    }

    public void deleteSqlSyncJob(String jobId) {
        mongoDatabase.getCollection(SQL_SYNC_COLLECTION).deleteOne(eq("_id", jobId));
    }

    public <T> T inTransaction(Function<ClientSession, T> operation) {
        try (ClientSession session = mongoClient.startSession()) {
            return session.withTransaction(() -> operation.apply(session), TRANSACTION_OPTIONS);
        }
    }

    private MongoCollection<Document> collection(boolean pending) {
        return mongoDatabase.getCollection(pending ? pendingCollection : catalogCollection);
    }
}
