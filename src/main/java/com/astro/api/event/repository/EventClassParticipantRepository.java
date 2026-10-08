package com.astro.api.event.repository;

import com.astro.api.event.model.EventClass;
import com.astro.api.event.model.EventClassParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventClassParticipantRepository extends JpaRepository<EventClassParticipant, Long> {
    List<EventClassParticipant> findByEventClassIn(List<EventClass> eventClasses);
    List<EventClassParticipant> findByEventClass(EventClass eventClass);
}
