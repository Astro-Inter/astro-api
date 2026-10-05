package com.astro.api.conformidade.repository;

import com.astro.api.conformidade.model.NrDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NrDocumentRepository extends MongoRepository<NrDocument, Integer> {
}
