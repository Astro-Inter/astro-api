package com.astro.api.event.repository;

import com.astro.api.event.model.EventEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventEvidenceRepository extends JpaRepository<EventEvidence, Long> {
    List<EventEvidence> findByCompletion_Id(Long completionId);

    void deleteByCompletion_Id(Long completionId);
}
