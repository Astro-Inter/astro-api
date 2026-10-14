package com.astro.api.conformity.repository;

import com.astro.api.conformity.model.NrCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NrCatalogRepository extends JpaRepository<NrCatalog, Integer> {
    List<NrCatalog> findAllByOrderByCodeNrAsc();
}
