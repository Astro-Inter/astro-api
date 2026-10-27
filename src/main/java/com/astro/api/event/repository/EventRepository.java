package com.astro.api.event.repository;

import com.astro.api.event.model.Event;
import com.astro.api.workspace.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    Optional<Event> findByIdAndManager_Unit_Workspace(Long id, Workspace workspace);

    @Query(value = """
            SELECT id_evento AS "id",
                   titulo AS "title",
                   status AS "status",
                   modo_conclusao AS "completionMode",
                   qtd_pendencias AS "pendingCount"
            FROM vw_eventos_gestor
            WHERE id_gestor = :managerId
            ORDER BY qtd_pendencias DESC, id_evento DESC
            """, nativeQuery = true)
    List<MyEventProjection> findMyEvents(@Param("managerId") Long managerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from Event e where e.id = :id and e.manager.id in
            (select u.id from User u where u.unit.workspace = :workspace)
            """)
    Optional<Event> findLockedInWorkspace(@Param("id") Long id,
            @Param("workspace") Workspace workspace);
}
