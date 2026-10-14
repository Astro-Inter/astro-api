package com.astro.api.conformity.repository;

import com.astro.api.conformity.model.NrDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface NrDocumentRepository extends MongoRepository<NrDocument, Integer> {
    List<NrDocument> findByUsability(String usability);
}
