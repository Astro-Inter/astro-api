package com.astro.api.conformidade.repository;

import com.astro.api.conformidade.model.NrCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NrCatalogRepository extends JpaRepository<NrCatalog, Integer> {
}
