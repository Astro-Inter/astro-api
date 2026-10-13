package com.astro.api.unit.repository;

import com.astro.api.conformidade.model.NrDocument;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UnitNrCatalogRepository {
    private final MongoTemplate mongoTemplate;

    public UnitNrCatalogRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<NrDocument> findCompanyNrs() {
        return mongoTemplate.find(Query.query(Criteria.where("usabilidade").is("Empresa")),
                NrDocument.class, "NRs");
    }
}
