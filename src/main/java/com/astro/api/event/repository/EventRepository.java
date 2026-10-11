package com.astro.api.event.repository;

import com.astro.api.event.model.Event;
import com.astro.api.workspace.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    Optional<Event> findByIdAndManager_Unit_Workspace(Long id, Workspace workspace);

    List<Event> findByManager_Unit_Workspace(Workspace workspace);
}
