package com.astro.api.conformity.repository;

import com.astro.api.conformity.model.NrCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface NrCatalogRepository extends JpaRepository<NrCatalog, Integer> {
    @Query(value = "SELECT * FROM nr_catalogo WHERE codigo_nr = :nrId FOR SHARE", nativeQuery = true)
    Optional<NrCatalog> findForShare(@Param("nrId") Integer nrId);
    List<NrCatalog> findAllByOrderByCodeNrAsc();
}
