package com.astro.api.event.repository;

import com.astro.api.event.model.Event;
import com.astro.api.workspace.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    Optional<Event> findByIdAndManager_Unit_Workspace(Long id, Workspace workspace);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from Event e where e.id = :id and e.manager.id in
            (select u.id from User u where u.unit.workspace = :workspace)
            """)
    Optional<Event> findLockedInWorkspace(@Param("id") Long id,
            @Param("workspace") Workspace workspace);
}
