package com.astro.api.nrreview;

import com.astro.api.nrreview.dto.NrSnapshot;
import com.astro.api.nrreview.mapper.NrReviewMapper;
import com.astro.api.nrreview.repository.NrReviewRepository;
import com.astro.api.nrreview.service.NrReviewService;
import com.astro.api.nrreview.service.NrSqlCatalogSynchronizer;
import com.astro.api.nrreview.validation.NrReviewValidator;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class NrReviewServiceTest {
    private final NrReviewRepository nrReviewRepository = mock(NrReviewRepository.class);
    private final NrReviewMapper nrReviewMapper = new NrReviewMapper();
    private final NrReviewService nrReviewService = new NrReviewService(nrReviewRepository, nrReviewMapper,
            new NrReviewValidator(nrReviewMapper), mock(NrSqlCatalogSynchronizer.class));

    @Test
    void loadsPublishedVersionsInOneBatchInsteadOfQueryingEachCandidate() {
        Document first = new Document("_id", 1).append("nome", "Análise 1");
        Document second = new Document("_id", 2).append("nome", "Análise 2");
        Document published = new Document("_id", 1).append("nome", "Versão atual");
        when(nrReviewRepository.findAll(true)).thenReturn(List.of(first, second));
        when(nrReviewRepository.findPublishedByIds(List.of(1, 2))).thenReturn(Map.of(1, published));

        List<NrSnapshot> snapshots = nrReviewService.list(true);

        assertEquals(2, snapshots.size());
        assertEquals(nrReviewMapper.revision(published), snapshots.get(0).approvedRevision());
        assertEquals("absent", snapshots.get(1).approvedRevision());
        verify(nrReviewRepository).findAll(true);
        verify(nrReviewRepository).findPublishedByIds(List.of(1, 2));
        verifyNoMoreInteractions(nrReviewRepository);
    }

    @Test
    void catalogListingDoesNotLoadPublishedVersionsAgain() {
        when(nrReviewRepository.findAll(false)).thenReturn(List.of(new Document("_id", 1)));

        assertEquals(1, nrReviewService.list(false).size());

        verify(nrReviewRepository).findAll(false);
        verifyNoMoreInteractions(nrReviewRepository);
    }
}
