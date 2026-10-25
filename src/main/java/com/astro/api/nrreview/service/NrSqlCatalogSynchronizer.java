package com.astro.api.nrreview.service;

import com.astro.api.nrreview.mapper.NrReviewMapper;
import com.astro.api.nrreview.repository.NrReviewRepository;
import com.astro.api.nrreview.repository.NrSqlCatalogRepository;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NrSqlCatalogSynchronizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(NrSqlCatalogSynchronizer.class);
    private static final int RETRY_BATCH_SIZE = 50;

    private final NrReviewRepository nrReviewRepository;
    private final NrSqlCatalogRepository nrSqlCatalogRepository;
    private final NrReviewMapper nrReviewMapper;

    public NrSqlCatalogSynchronizer(NrReviewRepository nrReviewRepository,
            NrSqlCatalogRepository nrSqlCatalogRepository, NrReviewMapper nrReviewMapper) {
        this.nrReviewRepository = nrReviewRepository;
        this.nrSqlCatalogRepository = nrSqlCatalogRepository;
        this.nrReviewMapper = nrReviewMapper;
    }

    public boolean synchronize(String jobId) {
        try {
            Document job = nrReviewRepository.findSqlSyncJob(jobId);
            if (job == null) {
                return true;
            }
            return synchronizeJob(job);
        } catch (RuntimeException exception) {
            logPending(jobId, exception);
            return false;
        }
    }

    @Scheduled(fixedDelayString = "${astro.nr-review.sql-sync-delay-ms:30000}")
    public void retryPending() {
        try {
            List<Document> jobs = nrReviewRepository.findPendingSqlSyncJobs(RETRY_BATCH_SIZE);
            for (Document job : jobs) {
                synchronizeJob(job);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Não foi possível consultar a fila de sincronização de NRs ({})",
                    exception.getClass().getSimpleName());
        }
    }

    private boolean synchronizeJob(Document job) {
        String jobId = job.getString("_id");
        try {
            int nrId = job.getInteger("nrId");
            nrSqlCatalogRepository.synchronizeLocked(nrId, () -> latestPublishedPayload(nrId));
            nrReviewRepository.deleteSqlSyncJob(jobId);
            return true;
        } catch (RuntimeException exception) {
            logPending(jobId, exception);
            return false;
        }
    }

    private String latestPublishedPayload(int nrId) {
        Document approved = nrReviewRepository.findById(nrId, false);
        if (approved == null) {
            throw new IllegalStateException("NR publicada não encontrada para sincronização");
        }
        return nrReviewMapper.toSqlPayload(nrId, approved);
    }

    private void logPending(String jobId, RuntimeException exception) {
        LOGGER.warn("Sincronização do catálogo SQL pendente para o job {} ({})",
                jobId, exception.getClass().getSimpleName());
    }
}
