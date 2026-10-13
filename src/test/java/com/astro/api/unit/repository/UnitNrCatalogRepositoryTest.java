package com.astro.api.unit.repository;

import com.astro.api.conformidade.model.NrDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnitNrCatalogRepositoryTest {
    @Test
    void selectsOnlyCompanyDocumentsFromTheCaseSensitiveCollection() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        NrDocument document = new NrDocument();
        document.setId(17);
        when(mongo.find(any(Query.class), eq(NrDocument.class), eq("NRs"))).thenReturn(List.of(document));
        var repository = new UnitNrCatalogRepository(mongo);
        assertEquals(List.of(document), repository.findCompanyNrs());
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(NrDocument.class), eq("NRs"));
        assertEquals("Empresa", query.getValue().getQueryObject().getString("usabilidade"));
    }
}
