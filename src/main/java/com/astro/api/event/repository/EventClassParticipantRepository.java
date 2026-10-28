package com.astro.api.event.repository;

import com.astro.api.event.model.EventClass;
import com.astro.api.event.model.EventClassParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventClassParticipantRepository extends JpaRepository<EventClassParticipant, Long> {
    @EntityGraph(attributePaths = {"user", "user.role", "user.role.workspace", "user.unit", "user.unit.workspace", "user.unit.address"})
    List<EventClassParticipant> findByEventClassIn(List<EventClass> eventClasses);
    // Conclusions and evidence remain owned by their existing modules/database contracts.
    @Query(value = "select exists (select 1 from conclusao_evento where turma_colaborador_id in (:ids))", nativeQuery = true)
    boolean hasConclusions(@Param("ids") Collection<Long> ids);

    List<EventClassParticipant> findByUser_IdOrderByEventClass_EndDateDesc(Long userId);

    Optional<EventClassParticipant> findFirstByEventClass_Event_IdAndUser_Id(Long eventId, Long userId);

    @EntityGraph(attributePaths = {"user", "user.role", "user.unit", "eventClass", "eventClass.event"})
    Optional<EventClassParticipant> findFirstByEventClass_Event_IdAndUser_IdOrderByEventClass_EndDateDesc(
            Long eventId, Long userId);

    Optional<EventClassParticipant> findByEventClass_Event_IdAndUser_Id(Long eventId, Long userId);

    @Query("select count(distinct participation.user.id) from EventClassParticipant participation "
            + "where participation.eventClass.event.id = :eventId")
    long countDistinctParticipantsByEventId(@Param("eventId") Long eventId);

    @Query("""
            select count(distinct participation.user.id) as participantCount,
                   count(distinct case when completion.status = com.astro.api.event.model.EventCompletionStatus.PENDENTE
                         then participation.user.id end) as pendingValidationCount,
                   count(distinct case when completion.status in (
                         com.astro.api.event.model.EventCompletionStatus.CONCLUIDO,
                         com.astro.api.event.model.EventCompletionStatus.REJEITADO)
                         then participation.user.id end) as validatedCount,
                   count(distinct case when completion.id is null
                         then participation.user.id end) as notSubmittedCount
            from EventClassParticipant participation
            left join EventCompletion completion on completion.participation = participation
            where participation.eventClass.event.id = :eventId
            """)
    EventCompletionMetricsProjection findCompletionMetricsByEventId(@Param("eventId") Long eventId);
}
