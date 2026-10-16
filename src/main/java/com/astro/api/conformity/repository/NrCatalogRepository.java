package com.astro.api.conformity.repository;

import com.astro.api.conformity.model.NrCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@Repository
public interface NrCatalogRepository extends JpaRepository<NrCatalog, Integer> {
    // A shared entity lock prevents concurrent revocation while a conformity write is validated.
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<NrCatalog> findLockedByCodeNr(int nrId);
    List<NrCatalog> findAllByOrderByCodeNrAsc();
}
