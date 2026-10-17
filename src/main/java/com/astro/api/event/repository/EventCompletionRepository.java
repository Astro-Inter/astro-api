package com.astro.api.event.repository;

import com.astro.api.event.model.EventCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventCompletionRepository extends JpaRepository<EventCompletion, Long> {
    Optional<EventCompletion> findByParticipation_Id(Long participationId);
}
