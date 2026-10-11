package com.astro.api.conformidade.repository;

import com.astro.api.conformidade.model.NrDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface NrDocumentRepository extends MongoRepository<NrDocument, Integer> {
    List<NrDocument> findByUsability(String usability);
}
