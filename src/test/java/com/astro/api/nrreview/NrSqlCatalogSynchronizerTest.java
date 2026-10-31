package com.astro.api.nrreview;

import com.astro.api.nrreview.mapper.NrReviewMapper;
import com.astro.api.nrreview.repository.NrReviewRepository;
import com.astro.api.nrreview.repository.NrSqlCatalogRepository;
import com.astro.api.nrreview.service.NrSqlCatalogSynchronizer;
import java.util.List;
import java.util.function.Supplier;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NrSqlCatalogSynchronizerTest {
    private final NrReviewRepository nrReviewRepository = mock(NrReviewRepository.class);
    private final NrSqlCatalogRepository nrSqlCatalogRepository = mock(NrSqlCatalogRepository.class);
    private final NrSqlCatalogSynchronizer synchronizer = new NrSqlCatalogSynchronizer(
            nrReviewRepository, nrSqlCatalogRepository, new NrReviewMapper());
    private final Document job = new Document("_id", "job-20").append("nr_id", 20);

    @Test
    void readsLatestPublishedVersionInsideLockAndRemovesJobAfterSynchronization() {
        Document published = new Document("nome", "Versão mais recente")
                .append("revogada", false).append("tempo_reciclagem_meses", 24);
        when(nrReviewRepository.findSqlSyncJob("job-20")).thenReturn(job);
        when(nrReviewRepository.findById(20, false)).thenReturn(published);
        doAnswer(invocation -> {
            Supplier<String> payloadSupplier = invocation.getArgument(1);
            List<?> payload = Document.parse("{\"nrs\":" + payloadSupplier.get() + "}").getList("nrs", Document.class);
            Document approved = (Document) payload.getFirst();
            assertEquals("Versão mais recente", approved.getString("nome"));
            assertEquals(24, approved.getInteger("tempo_reciclagem_mes"));
            return null;
        }).when(nrSqlCatalogRepository).synchronizeLocked(eq(20), any());

        assertTrue(synchronizer.synchronize("job-20"));

        InOrder order = inOrder(nrReviewRepository, nrSqlCatalogRepository);
        order.verify(nrReviewRepository).findSqlSyncJob("job-20");
        order.verify(nrSqlCatalogRepository).synchronizeLocked(eq(20), any());
        order.verify(nrReviewRepository).findById(20, false);
        order.verify(nrReviewRepository).deleteSqlSyncJob("job-20");
    }

    @Test
    void retainsFailedJobAndRetriesWithoutLoadingEachJobAgain() {
        when(nrReviewRepository.findSqlSyncJob("job-20")).thenReturn(job);
        doThrow(new IllegalStateException("Indisponível"))
                .doNothing().when(nrSqlCatalogRepository).synchronizeLocked(eq(20), any());

        assertFalse(synchronizer.synchronize("job-20"));
        verify(nrReviewRepository, never()).deleteSqlSyncJob("job-20");
        when(nrReviewRepository.findPendingSqlSyncJobs(50)).thenReturn(List.of(job));

        synchronizer.retryPending();

        verify(nrReviewRepository).findSqlSyncJob("job-20");
        verify(nrReviewRepository).deleteSqlSyncJob("job-20");
    }

    @Test
    void completedJobDoesNotCallProcedureAgain() {
        assertTrue(synchronizer.synchronize("completed"));
        verifyNoInteractions(nrSqlCatalogRepository);
    }
}
