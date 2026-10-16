package com.astro.api.event.repository;

import com.astro.api.event.model.EventConclusion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventConclusionRepository extends JpaRepository<EventConclusion, Long> {
    Optional<EventConclusion> findByParticipation_Id(Long participationId);
}
