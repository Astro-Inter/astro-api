package com.astro.api.conformity.repository;

import com.astro.api.conformity.model.EventEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventEvidenceRepository extends JpaRepository<EventEvidence, Long> {
    List<EventEvidence> findByConclusion_IdOrderById(Long conclusionId);
}
