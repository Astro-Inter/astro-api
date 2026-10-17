package com.astro.api.event.repository;

import com.astro.api.event.model.Event;
import com.astro.api.event.model.EventClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.astro.api.workspace.model.Workspace;
import java.time.LocalDateTime;

import java.util.List;

@Repository
public interface EventClassRepository extends JpaRepository<EventClass, Long> {
    List<EventClass> findByEventOrderByStartDate(Event event);

    @Query("""
            select c.event.id as eventId, c.event.title as eventTitle,
                   c.id as eventClassId, c.name as className,
                   c.startDate as startDate, c.endDate as endDate, c.event.status as status
            from EventClass c
            where c.event.manager.unit.workspace = :workspace
              and c.endDate >= :startDate and c.startDate <= :endDate
            order by c.startDate, c.id
            """)
    List<EventCalendarProjection> findCalendar(
            @Param("workspace") Workspace workspace,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
