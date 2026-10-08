package com.astro.api.event.repository;

import com.astro.api.event.model.Event;
import com.astro.api.event.model.EventClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventClassRepository extends JpaRepository<EventClass, Long> {
    List<EventClass> findByEventOrderByStartDate(Event event);
}
