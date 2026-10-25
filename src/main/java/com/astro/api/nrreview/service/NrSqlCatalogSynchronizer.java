package com.astro.api.nrreview.service;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.List;
import static com.mongodb.client.model.Filters.eq;

/** Durable jobs are inserted by the same Mongo transaction that publishes an NR. */
@Service
public class NrSqlCatalogSynchronizer {
    public static final String JOBS = "nr_catalog_sql_sync";
    private static final Logger LOG = LoggerFactory.getLogger(NrSqlCatalogSynchronizer.class);
    private final MongoTemplate mongo;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate sql;
    private final String catalog;

    public NrSqlCatalogSynchronizer(MongoTemplate mongo, JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
            @Value("${astro.mongodb.nr-collection:nrs}") String catalog) {
        this.mongo = mongo; this.jdbc = jdbc; this.sql = new TransactionTemplate(transactionManager); this.catalog = catalog; this.sql.setTimeout(10);
    }

    public boolean synchronize(String jobId) {
        try {
            Document job = mongo.getCollection(JOBS).find(eq("_id", jobId)).first();
            if (job == null) return true;
            int nrId = job.getInteger("nrId");
            sql.executeWithoutResult(status -> {
                // All API instances serialize SQL publication for this norm, then read the latest approved version.
                jdbc.queryForList("SELECT pg_advisory_xact_lock(619, ?)", nrId);
                Document approved = mongo.getCollection(catalog).find(eq("_id", nrId)).first();
                if (approved == null) throw new IllegalStateException("NR publicada não encontrada para sincronização");
                String payload = "[" + new Document("id", nrId).append("nome", approved.getString("nome"))
                        .append("revogada", approved.getBoolean("revogada"))
                        .append("tempo_reciclagem_mes", approved.get("tempo_reciclagem_meses")).toJson() + "]";
                jdbc.update("CALL ler_json_nr(CAST(? AS text))", payload);
            });
            // Delete only this job after SQL commit. A concurrent approval owns a separate job.
            mongo.getCollection(JOBS).deleteOne(eq("_id", jobId));
            return true;
        } catch (RuntimeException exception) {
            LOG.warn("Sincronização do catálogo SQL pendente para o job {} ({})", jobId, exception.getClass().getSimpleName());
            return false;
        }
    }

    @Scheduled(fixedDelayString = "${astro.nr-review.sql-sync-delay-ms:30000}")
    public void retryPending() {
        try (var cursor = mongo.getCollection(JOBS).find().sort(new Document("createdAt", 1)).limit(50).iterator()) {
            while (cursor.hasNext()) synchronize(cursor.next().getString("_id"));
        } catch (RuntimeException exception) {
            LOG.warn("Não foi possível consultar a fila de sincronização de NRs ({})", exception.getClass().getSimpleName());
        }
    }
}
