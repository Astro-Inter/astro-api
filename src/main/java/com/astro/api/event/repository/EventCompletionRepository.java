package com.astro.api.event.repository;

import com.astro.api.event.model.EventCompletion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

@Repository
public interface EventCompletionRepository extends JpaRepository<EventCompletion, Long> {
    Optional<EventCompletion> findByParticipation_Id(Long participationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select completion from EventCompletion completion where completion.participation.id = :participationId")
    Optional<EventCompletion> findLockedByParticipationId(@Param("participationId") Long participationId);
    List<EventCompletion> findByParticipation_IdIn(Collection<Long> participationIds);
}
